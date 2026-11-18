package io.github.fianna76.studypack.ui.components

import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.core.graphics.withScale
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import io.github.fianna76.studypack.quiz.MathMarkup
import io.github.fianna76.studypack.quiz.MathSegment
import ru.noties.jlatexmath.JLatexMathDrawable

/**
 * Text that may contain LaTeX formulas (`$…$` inline, `$$…$$` on their own line; see
 * [MathMarkup]). Inline formulas flow with the text at its size and colour; display formulas are
 * centred, slightly larger, and scroll sideways if wider than the screen. A formula that fails to
 * parse is shown as its LaTeX source in monospace. Screen readers get a plain-text reading.
 *
 * Text without formulas is drawn as a plain [Text], so packs without maths pay nothing.
 */
@Composable
fun MathText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    val segments = remember(text) { MathMarkup.parse(text) }
    MathText(segments, modifier, style, color, textAlign)
}

@Composable
fun MathText(
    segments: List<MathSegment>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    if (segments.none { it is MathSegment.Math }) {
        Text(segments.joinToString("") { (it as MathSegment.Text).text }, modifier, color = color, style = style, textAlign = textAlign)
        return
    }
    val resolvedColor = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    val fontSize = if (style.fontSize.isUnspecified) 16.sp else style.fontSize
    val density = LocalDensity.current
    val sizePx = with(density) { fontSize.toPx() }
    val blocks = remember(segments) { blocks(segments) }

    Column(modifier) {
        blocks.forEach { block ->
            when (block) {
                is Block.Display -> DisplayFormula(block.latex, sizePx * DISPLAY_SCALE, resolvedColor, style)
                is Block.Paragraph -> Paragraph(block.segments, sizePx, resolvedColor, style, textAlign, density)
            }
        }
    }
}

private sealed interface Block {
    data class Paragraph(val segments: List<MathSegment>) : Block
    data class Display(val latex: String) : Block
}

/** Splits segments into paragraphs (text with inline formulas) and display formulas. */
private fun blocks(segments: List<MathSegment>): List<Block> {
    val blocks = ArrayList<Block>()
    val current = ArrayList<MathSegment>()
    fun flush() {
        // Whitespace around a display formula belongs to the line break it implies.
        val trimmed = current.mapIndexedNotNull { i, s ->
            if (s !is MathSegment.Text) return@mapIndexedNotNull s
            var t = s.text
            if (i == 0) t = t.trimStart()
            if (i == current.lastIndex) t = t.trimEnd()
            if (t.isEmpty()) null else MathSegment.Text(t)
        }
        if (trimmed.isNotEmpty()) blocks += Block.Paragraph(trimmed)
        current.clear()
    }
    for (segment in segments) {
        if (segment is MathSegment.Math && segment.display) {
            flush()
            blocks += Block.Display(segment.latex)
        } else {
            current += segment
        }
    }
    flush()
    return blocks
}

@Composable
private fun Paragraph(
    segments: List<MathSegment>,
    sizePx: Float,
    color: Color,
    style: TextStyle,
    textAlign: TextAlign?,
    density: Density,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxWidthPx = constraints.maxWidth.toFloat()
        val argb = color.toArgb()
        val formulas = remember(segments, sizePx, argb) {
            segments.map { (it as? MathSegment.Math)?.let { math -> LatexDrawables.get(math.latex, sizePx, argb) } }
        }
        val inlineContent = HashMap<String, InlineTextContent>()
        val text = buildAnnotatedString {
            segments.forEachIndexed { i, segment ->
                when (segment) {
                    is MathSegment.Text -> append(segment.text)
                    is MathSegment.Math -> {
                        val drawable = formulas[i]
                        if (drawable == null) {
                            withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(segment.latex) }
                        } else {
                            val id = "math$i"
                            // A formula wider than the text is scaled down to fit.
                            val scale = if (maxWidthPx > 0 && drawable.intrinsicWidth > maxWidthPx) maxWidthPx / drawable.intrinsicWidth else 1f
                            val width = drawable.intrinsicWidth * scale
                            val height = drawable.intrinsicHeight * scale
                            inlineContent[id] = InlineTextContent(
                                Placeholder(
                                    width = with(density) { width.toSp() },
                                    height = with(density) { height.toSp() },
                                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                                ),
                            ) { FormulaCanvas(drawable, scale, Modifier.fillMaxSize()) }
                            appendInlineContent(id, MathMarkup.latexToPlain(segment.latex))
                        }
                    }
                }
            }
        }
        val reading = remember(segments) { segments.joinToString("") { if (it is MathSegment.Text) it.text else MathMarkup.latexToPlain((it as MathSegment.Math).latex) } }
        Text(
            text,
            color = color,
            style = style,
            textAlign = textAlign,
            inlineContent = inlineContent,
            modifier = Modifier.semantics { contentDescription = reading },
        )
    }
}

@Composable
private fun DisplayFormula(latex: String, sizePx: Float, color: Color, style: TextStyle) {
    val argb = color.toArgb()
    val drawable = remember(latex, sizePx, argb) { LatexDrawables.get(latex, sizePx, argb) }
    if (drawable == null) {
        Text(latex, color = color, style = style.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.padding(vertical = 4.dp))
        return
    }
    val density = LocalDensity.current
    val reading = remember(latex) { MathMarkup.latexToPlain(latex) }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .widthIn(min = maxWidth),
            horizontalArrangement = Arrangement.Center,
        ) {
            FormulaCanvas(
                drawable,
                scale = 1f,
                modifier = Modifier
                    .size(with(density) { drawable.intrinsicWidth.toDp() }, with(density) { drawable.intrinsicHeight.toDp() })
                    .semantics { contentDescription = reading },
            )
        }
    }
}

@Composable
private fun FormulaCanvas(drawable: Drawable, scale: Float, modifier: Modifier) {
    Canvas(modifier) {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.withScale(scale, scale) {
                drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
                drawable.draw(this)
            }
        }
    }
}

private const val DISPLAY_SCALE = 1.15f

/** Rendered formulas, keyed by source, size and colour (light and dark themes differ). */
private object LatexDrawables {
    private data class Key(val latex: String, val sizePx: Float, val argb: Int)

    /** Parse failures are remembered too, so a broken formula is not re-parsed every frame. */
    private object Failed

    private val cache = LruCache<Key, Any>(MAX_ENTRIES)

    fun get(latex: String, sizePx: Float, argb: Int): Drawable? {
        val key = Key(latex, sizePx, argb)
        when (val cached = cache.get(key)) {
            Failed -> return null
            is Drawable -> return cached
        }
        val drawable = try {
            JLatexMathDrawable.builder(LatexCompat.prepare(latex))
                .textSize(sizePx)
                .color(argb)
                .align(JLatexMathDrawable.ALIGN_LEFT)
                .build()
                .takeIf { it.intrinsicWidth > 0 && it.intrinsicHeight > 0 }
        } catch (_: Exception) {
            null
        } catch (_: LinkageError) {
            null
        }
        cache.put(key, drawable ?: Failed)
        return drawable
    }

    private const val MAX_ENTRIES = 256
}

/**
 * Works around glyphs JLatexMath cannot draw on Android. A few symbols sit at code point 173 in
 * its fonts, which is U+00AD (soft hyphen), and Android's text engine never draws a soft hyphen.
 * `\Omega` is the one that matters (kΩ is everywhere in electronics), so it is drawn from the
 * Greek font instead; `\otimes` uses a small `\bigotimes`.
 */
internal object LatexCompat {
    private val replacements = listOf(
        Regex("""\\varOmega(?![A-Za-z])""") to "\\textit{Ω}",
        Regex("""\\Omega(?![A-Za-z])""") to "\\text{Ω}",
        Regex("""\\otimes(?![A-Za-z])""") to "\\mathbin{\\scriptstyle\\bigotimes}",
    )

    fun prepare(latex: String): String =
        replacements.fold(latex) { text, (pattern, replacement) -> pattern.replace(text, Regex.escapeReplacement(replacement)) }
}
