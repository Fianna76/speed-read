package io.github.fianna76.studypack.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class LatexCompatTest {

    @Test
    fun omegaIsDrawnFromTheGreekFont() {
        assertEquals("10\\,\\mathrm{k}\\text{Ω}", LatexCompat.prepare("10\\,\\mathrm{k}\\Omega"))
        assertEquals("\\text{Ω}_0 + \\textit{Ω}", LatexCompat.prepare("\\Omega_0 + \\varOmega"))
        assertEquals("a \\mathbin{\\scriptstyle\\bigotimes} b", LatexCompat.prepare("a \\otimes b"))
        // Longer command names are left alone.
        assertEquals("\\Omegax", LatexCompat.prepare("\\Omegax"))
    }
}
