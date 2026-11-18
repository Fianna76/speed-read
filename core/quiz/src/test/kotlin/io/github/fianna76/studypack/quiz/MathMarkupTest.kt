package io.github.fianna76.studypack.quiz

import io.github.fianna76.studypack.quiz.MathSegment.Math
import io.github.fianna76.studypack.quiz.MathSegment.Text
import io.github.fianna76.studypack.quiz.TestPacks.str
import io.github.fianna76.studypack.quiz.TestPacks.text
import io.github.fianna76.studypack.quiz.TestPacks.withQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MathMarkupTest {

    @Test
    fun plainTextIsOneSegment() {
        assertEquals(listOf(Text("No maths here, τ = 68 s.")), MathMarkup.parse("No maths here, τ = 68 s."))
        assertFalse(MathMarkup.hasMath("Plain"))
    }

    @Test
    fun inlineDollars() {
        assertEquals(
            listOf(Text("The corner is "), Math("f_c = \\frac{1}{2\\pi RC}", display = false), Text(".")),
            MathMarkup.parse("The corner is \$f_c = \\frac{1}{2\\pi RC}\$."),
        )
    }

    @Test
    fun displayDollarsAndBrackets() {
        assertEquals(
            listOf(Text("Gain:"), Math("G = 1 + \\frac{R_f}{R_g}", display = true), Text("so 11.")),
            MathMarkup.parse("Gain:\$\$ G = 1 + \\frac{R_f}{R_g} \$\$so 11."),
        )
        assertEquals(listOf(Math("x^2", display = true)), MathMarkup.parse("\\[x^2\\]"))
        assertEquals(listOf(Text("a "), Math("\\tau = RC", display = false)), MathMarkup.parse("a \\(\\tau = RC\\)"))
    }

    @Test
    fun ordinaryDollarSignsStayText() {
        assertFalse(MathMarkup.hasMath("It costs \$5 or \$10."))
        assertFalse(MathMarkup.hasMath("Between \$ 5 and 10 \$ we go."))
        assertFalse(MathMarkup.hasMath("A lone \$ sign"))
        assertEquals(listOf(Text("Price: \$3")), MathMarkup.parse("Price: \\\$3"))
    }

    @Test
    fun escapedDollarInsideFormula() {
        assertEquals(listOf(Math("\\\$x\\\$", display = false)), MathMarkup.parse("\$\\\$x\\\$\$"))
    }

    @Test
    fun formulasDoNotSpanParagraphs() {
        assertFalse(MathMarkup.hasMath("First \$x\n\nSecond y\$ here"))
    }

    @Test
    fun plainTextApproximation() {
        assertEquals("f_c = (1)/(2π RC)", MathMarkup.plain("\$f_c = \\frac{1}{2\\pi RC}\$"))
        assertEquals("(V_in R₂)/(R₁ + R₂)", MathMarkup.plain("\$\\frac{V_{in} R_2}{R_1 + R_2}\$"))
        assertEquals("P = V²/R", MathMarkup.plain("P = \$V^2/R\$"))
        assertEquals("√(2) ≈ 1.41, 10 kΩ ± 1 %", MathMarkup.plain("\$\\sqrt{2} \\approx 1.41\$, \$10\\,\\mathrm{k}\\Omega \\pm 1\\,\\%\$"))
        assertEquals("e^(-t/τ)", MathMarkup.plain("\$e^{-t/\\tau}\$"))
        assertEquals("Nested ((a)/(b))/(c)", MathMarkup.plain("Nested \$\\frac{\\frac{a}{b}}{c}\$"))
    }

    @Test
    fun problemsAreFound() {
        assertTrue(MathMarkup.problems("\$\\frac{1}{2\$").single().contains("unbalanced"))
        assertTrue(MathMarkup.problems("See \$\$x^2 here").single().contains("not closed"))
        assertTrue(MathMarkup.problems("See \\(x").single().contains("\\("))
        assertTrue(MathMarkup.problems("Fine \$\\{a\\}\$ and \$x_{1}\$").isEmpty())
    }

    @Test
    fun validatorWarnsAboutBrokenFormulas() {
        val pack = TestPacks.pack().withQuestion(0, "explanation", str("Because \$\\frac{1}{2\$ ok."))
        val result = PackParser.parse(pack.text())
        assertNotNull(result.pack)
        val warning = result.warnings.single()
        assertTrue(warning.message, warning.message.startsWith("Question alpha-001: in the explanation, the formula"))
    }

    @Test
    fun lengthBiasIgnoresLatexSource() {
        val questions = (1..10).map {
            TestPacks.question(
                "alpha-$it",
                "alpha",
                correct = "\$\\frac{\\Delta V}{\\Delta t}\$",
                distractors = listOf("Slope of V", "Area under V", "Peak of V"),
            )
        }
        val result = PackParser.parse(TestPacks.pack(topics = listOf("alpha"), questions = questions).text())
        assertTrue(result.warnings.none { it.message.contains("longest option") })
    }
}
