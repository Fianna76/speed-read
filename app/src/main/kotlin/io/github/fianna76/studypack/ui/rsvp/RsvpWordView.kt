package io.github.fianna76.studypack.ui.rsvp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.fianna76.speedread.rsvp.RsvpWord
import io.github.fianna76.studypack.ui.theme.StudyTheme

/** Horizontal position of the focus letter, as a fraction of the width (slightly left of centre). */
private const val FOCUS_POSITION = 0.4f

/** Height of the reading strip relative to the font size, leaving room for the guides. */
private const val STRIP_HEIGHT_FACTOR = 2.4f

/**
 * Draws one RSVP word so that its optimal recognition point always lands on the same spot,
 * marked by guide ticks above and below, so the eye can rest there instead of scanning.
 *
 * Text is measured and drawn directly, so the focus letter is positioned in the same frame the
 * word appears (no layout pass, no jitter). Words too wide for the screen are scaled down.
 */
@Composable
fun RsvpWordView(word: RsvpWord?, fontSizeSp: Int, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val colors = StudyTheme.colors
    val textColor = MaterialTheme.colorScheme.onSurface
    val style = remember(fontSizeSp, textColor) {
        TextStyle(color = textColor, fontSize = fontSizeSp.sp, fontWeight = FontWeight.Medium)
    }
    val text = remember(word, colors.focusLetter) {
        word?.let {
            buildAnnotatedString {
                append(it.text)
                addStyle(SpanStyle(color = colors.focusLetter), it.orpStart, it.orpEnd)
            }
        }
    }
    val stripHeight = with(LocalDensity.current) { (fontSizeSp.sp * STRIP_HEIGHT_FACTOR).toDp() }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(stripHeight)
            .clearAndSetSemantics { contentDescription = word?.text.orEmpty() },
    ) {
        val focusX = size.width * FOCUS_POSITION
        val stroke = 1.dp.toPx()
        val tick = size.height * 0.14f
        val inset = size.width * 0.06f
        val guide = colors.guide

        drawLine(guide, Offset(inset, stroke / 2), Offset(size.width - inset, stroke / 2), stroke)
        drawLine(guide, Offset(inset, size.height - stroke / 2), Offset(size.width - inset, size.height - stroke / 2), stroke)
        drawLine(guide, Offset(focusX, 0f), Offset(focusX, tick), stroke * 2)
        drawLine(guide, Offset(focusX, size.height - tick), Offset(focusX, size.height), stroke * 2)

        if (word == null || text == null) return@Canvas
        var layout = textMeasurer.measure(text, style, softWrap = false, maxLines = 1)
        val available = size.width - 2 * inset
        if (layout.size.width > available) {
            val scaled = style.copy(fontSize = style.fontSize * (available / layout.size.width))
            layout = textMeasurer.measure(text, scaled, softWrap = false, maxLines = 1)
        }
        val focusBox = layout.getBoundingBox(word.orpStart.coerceIn(0, word.text.lastIndex))
        // Pin the focus letter to the guide, but never push a long word off screen.
        val x = (focusX - focusBox.center.x).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
        val y = (size.height - layout.size.height) / 2f
        drawText(layout, topLeft = Offset(x, y))
    }
}
