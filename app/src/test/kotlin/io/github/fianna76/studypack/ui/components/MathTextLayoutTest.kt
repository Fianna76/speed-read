package io.github.fianna76.studypack.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.studypack.testing.PHONE
import io.github.fianna76.studypack.ui.theme.StudyPackTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tall formulas (fractions, sums with limits) must sit inside their own line instead of running
 * into the lines above and below; paragraphs without them keep the theme's line height.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE)
class MathTextLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private val layouts = HashMap<String, TextLayoutResult>()

    private fun render(vararg texts: Pair<String, String>, style: @androidx.compose.runtime.Composable () -> TextStyle) {
        compose.setContent {
            StudyPackTheme(darkTheme = false, dynamicColor = false) {
                Column(Modifier.width(360.dp)) {
                    texts.forEach { (name, text) -> MathText(text, style = style(), onTextLayout = { layouts[name] = it }) }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Every formula lies within the top and bottom of the line it is on. */
    private fun assertFormulasInsideTheirLines(name: String) {
        val layout = layouts.getValue(name)
        val formulas = layout.placeholderRects.filterNotNull()
        assertTrue("$name has formulas", formulas.isNotEmpty())
        formulas.forEach { rect ->
            val line = layout.getLineForVerticalPosition(rect.center.y)
            val top = layout.getLineTop(line)
            val bottom = layout.getLineBottom(line)
            assertTrue("$name: formula $rect spills out of line $line ($top–$bottom)", rect.top >= top - 0.5f && rect.bottom <= bottom + 0.5f)
        }
    }

    private val fraction = "In \$\\frac{\\partial SSE}{\\partial a_0} = 2\\sum_j(y_j-a_0)(-1)\$, where does the factor \$(-1)\$ come from?"
    private val sum = "Sum \$\\sum_{j=1}^{K}(y_j-\\hat y_j)^2\$ is the squared error."
    private val wrapped = "Words before the fraction fill this line and the next one too, then " +
        "\$a_1=\\frac{\\sum(x_jy_j-\\bar y x_j)}{\\sum(x_j^2-\\bar x x_j)}\$ and more words after it run on to the following line."

    @Test
    fun tallFormulasStayInsideTheirLineInBodyText() {
        render("fraction" to fraction, "sum" to sum, "wrapped" to wrapped) { MaterialTheme.typography.bodyLarge }
        listOf("fraction", "sum", "wrapped").forEach(::assertFormulasInsideTheirLines)
    }

    @Test
    fun tallFormulasStayInsideTheirLineInQuestionPrompts() {
        render("fraction" to fraction, "wrapped" to wrapped) { MaterialTheme.typography.titleLarge }
        listOf("fraction", "wrapped").forEach(::assertFormulasInsideTheirLines)
    }

    @Test
    fun onlyTheLineWithTheFormulaGrows() {
        render("wrapped" to wrapped, "plain" to "Plain words on one line") { MaterialTheme.typography.bodyLarge }
        val layout = layouts.getValue("wrapped")
        val formulaLine = layout.getLineForVerticalPosition(layout.placeholderRects.filterNotNull().single().center.y)
        val heights = (0 until layout.lineCount).map { layout.getLineBottom(it) - layout.getLineTop(it) }
        val others = heights.filterIndexed { i, _ -> i != formulaLine }
        assertTrue(others.isNotEmpty())
        // Text-only lines stay ordinary text lines, not as tall as the fraction's.
        others.forEach { assertTrue("$heights", it < heights[formulaLine] * 0.75f) }
    }

    @Test
    fun smallFormulasKeepTheThemeLineHeight() {
        render("small" to "Small \$a_0\$ and \$x_{1,j}\$ formulas", "plain" to "Plain words") { MaterialTheme.typography.bodyLarge }
        assertEquals(layouts.getValue("plain").size.height, layouts.getValue("small").size.height)
    }
}
