package io.github.fianna76.studypack.ui.quiz

import io.github.fianna76.studypack.quiz.MathMarkup
import io.github.fianna76.studypack.quiz.MathSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TruncateTest {

    @Test
    fun shortTextIsNotCut() {
        assertNull(truncate(MathMarkup.parse("Short \$x^2\$ text"), 40))
    }

    @Test
    fun formulasCountAsShownAndAreNeverSplit() {
        // The LaTeX source is long, but it shows as "(1)/(2π RC)": 11 characters.
        val segments = MathMarkup.parse("Corner: \$\\frac{1}{2\\pi RC}\$ and then a long tail of words")
        val cut = truncate(segments, 25)!!
        assertEquals(MathSegment.Math("\\frac{1}{2\\pi RC}", display = false), cut[1])
        assertEquals(MathSegment.Text(" and t"), cut[2])
        assertEquals(MathSegment.Text("…"), cut.last())
        // A formula that does not fit is dropped whole.
        val dropped = truncate(segments, 10)!!
        assertEquals(listOf(MathSegment.Text("Corner:"), MathSegment.Text("…")), dropped)
    }
}
