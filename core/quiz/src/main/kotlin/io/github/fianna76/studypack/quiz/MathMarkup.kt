package io.github.fianna76.studypack.quiz

/** A run of pack text: plain text, or a LaTeX formula. */
sealed interface MathSegment {
    data class Text(val text: String) : MathSegment

    /** A formula's LaTeX source (without delimiters); [display] formulas sit on a line of their own. */
    data class Math(val latex: String, val display: Boolean) : MathSegment
}

/**
 * LaTeX maths inside pack text, with the usual delimiters:
 *
 * - inline: `$…$` or `\(…\)`, for example `the corner is $f_c = \frac{1}{2\pi RC}$`;
 * - display (own line, centred): `$$…$$` or `\[…\]`;
 * - `\$` is a literal dollar sign.
 *
 * To keep ordinary dollar signs ("costs $5 or $10") as text, an inline `$` only opens a formula
 * when the next character is not a space, and only closes one when the previous character is not
 * a space and the next is not a digit (Pandoc's rule). A delimiter that is never closed stays as text.
 */
object MathMarkup {

    fun hasMath(text: String): Boolean = parse(text).any { it is MathSegment.Math }

    fun parse(text: String): List<MathSegment> {
        if (text.none { it == '$' || it == '\\' }) return listOf(MathSegment.Text(text))
        val segments = ArrayList<MathSegment>()
        val plain = StringBuilder()
        fun flush() {
            if (plain.isNotEmpty()) segments += MathSegment.Text(plain.toString())
            plain.setLength(0)
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                when (text[i + 1]) {
                    '$' -> {
                        plain.append('$')
                        i += 2
                        continue
                    }
                    '(', '[' -> {
                        val display = text[i + 1] == '['
                        val close = if (display) "\\]" else "\\)"
                        val end = text.indexOf(close, i + 2)
                        if (end > i + 2) {
                            flush()
                            segments += MathSegment.Math(text.substring(i + 2, end).trim(), display)
                            i = end + 2
                            continue
                        }
                    }
                }
            }
            if (c == '$') {
                if (text.startsWith("$$", i)) {
                    val end = findUnescaped(text, "$$", i + 2)
                    if (end > i + 2 && text.substring(i + 2, end).isNotBlank()) {
                        flush()
                        segments += MathSegment.Math(text.substring(i + 2, end).trim(), display = true)
                        i = end + 2
                        continue
                    }
                } else {
                    val end = inlineEnd(text, i)
                    if (end > 0) {
                        flush()
                        segments += MathSegment.Math(text.substring(i + 1, end), display = false)
                        i = end + 1
                        continue
                    }
                }
            }
            plain.append(c)
            i++
        }
        flush()
        return segments
    }

    /** Index of the `$` closing the inline formula opened at [open], or -1. */
    private fun inlineEnd(text: String, open: Int): Int {
        val first = text.getOrNull(open + 1) ?: return -1
        if (first.isWhitespace() || first == '$') return -1
        var j = open + 1
        while (j < text.length) {
            val ch = text[j]
            if (ch == '\\') {
                j += 2
                continue
            }
            if (ch == '\n' && text.getOrNull(j + 1) == '\n') return -1 // never across paragraphs
            if (ch == '$') {
                val before = text[j - 1]
                val after = text.getOrNull(j + 1)
                if (!before.isWhitespace() && (after == null || !after.isDigit())) return j
            }
            j++
        }
        return -1
    }

    private fun findUnescaped(text: String, token: String, from: Int): Int {
        var j = text.indexOf(token, from)
        while (j > 0 && text[j - 1] == '\\') j = text.indexOf(token, j + 1)
        return j
    }

    /**
     * Problems a renderer would trip over: a formula with unbalanced braces, or a `$$`, `\(` or
     * `\[` that is never closed. Used by the validator for warnings.
     */
    fun problems(text: String): List<String> {
        val issues = ArrayList<String>()
        val segments = parse(text)
        segments.filterIsInstance<MathSegment.Math>().forEach { math ->
            if (!bracesBalanced(math.latex)) issues += "the formula \"${math.latex.take(40)}\" has unbalanced { }"
        }
        val leftover = segments.filterIsInstance<MathSegment.Text>().joinToString("") { it.text }
        when {
            "$$" in leftover -> issues += "a \$\$ formula is not closed"
            "\\(" in leftover -> issues += "a \\( formula is not closed with \\)"
            "\\[" in leftover -> issues += "a \\[ formula is not closed with \\]"
        }
        return issues
    }

    private fun bracesBalanced(latex: String): Boolean {
        var depth = 0
        var i = 0
        while (i < latex.length) {
            when (latex[i]) {
                '\\' -> i++ // skip the escaped character (\{ and \} are literal braces)
                '{' -> depth++
                '}' -> if (--depth < 0) return false
            }
            i++
        }
        return depth == 0
    }

    /**
     * Readable plain text for [text], with formulas turned into Unicode approximations: for
     * screen readers, content descriptions and anywhere a formula can't be drawn.
     * `$\frac{V_{in} R_2}{R_1 + R_2}$` becomes `(V_in R₂)/(R₁ + R₂)`.
     */
    fun plain(text: String): String = parse(text).joinToString("") { segment ->
        when (segment) {
            is MathSegment.Text -> segment.text
            is MathSegment.Math -> latexToPlain(segment.latex)
        }
    }

    fun latexToPlain(latex: String): String {
        var s = latex
        // Structures with arguments, innermost first, repeated for nesting.
        repeat(MAX_NESTING) {
            val before = s
            s = FRAC.replace(s) { m -> "(${m.groupValues[1]})/(${m.groupValues[2]})" }
            s = SQRT_N.replace(s) { m -> "${m.groupValues[1]}√(${m.groupValues[2]})" }
            s = SQRT.replace(s) { m -> "√(${m.groupValues[1]})" }
            s = TEXT_CMD.replace(s) { m -> m.groupValues[2] }
            s = SUPER_GROUP.replace(s) { m -> superscript(m.groupValues[1]) }
            s = SUB_GROUP.replace(s) { m -> subscript(m.groupValues[1]) }
            if (s == before) return@repeat
        }
        s = SUPER_CHAR.replace(s) { m -> superscript(m.groupValues[1]) }
        s = SUB_CHAR.replace(s) { m -> subscript(m.groupValues[1]) }
        s = COMMAND.replace(s) { m -> SYMBOLS[m.groupValues[1]] ?: if (m.groupValues[1] in DROPPED) "" else m.groupValues[1] }
        s = s.replace("\\,", " ").replace("\\;", " ").replace("\\!", "").replace("\\ ", " ")
            .replace("\\{", "{").replace("\\}", "}").replace("\\%", "%").replace("\\$", "$").replace("\\\\", " ")
            .replace("{", "").replace("}", "").replace("~", " ")
        return s.replace(Regex(" {2,}"), " ").trim()
    }

    private fun superscript(value: String): String {
        return value.map { SUPERSCRIPTS[it] ?: return "^($value)" }.joinToString("")
    }

    private fun subscript(value: String): String {
        return value.map { SUBSCRIPTS[it] ?: return "_$value" }.joinToString("")
    }

    private const val MAX_NESTING = 6
    private const val BRACED = """\{([^{}]*)\}"""
    private val FRAC = Regex("""\\(?:d|t)?frac\s*$BRACED\s*$BRACED""")
    private val SQRT_N = Regex("""\\sqrt\s*\[([^\]]*)\]\s*$BRACED""")
    private val SQRT = Regex("""\\sqrt\s*$BRACED""")
    private val TEXT_CMD = Regex("""\\(text|mathrm|mathit|mathbf|operatorname|textrm|mbox)\s*$BRACED""")
    private val SUPER_GROUP = Regex("""\^\s*$BRACED""")
    private val SUB_GROUP = Regex("""_\s*$BRACED""")
    private val SUPER_CHAR = Regex("""\^\s*([A-Za-z0-9+\-])""")
    private val SUB_CHAR = Regex("""_\s*([A-Za-z0-9])""")
    private val COMMAND = Regex("""\\([A-Za-z]+)""")

    private val DROPPED = setOf("left", "right", "big", "Big", "bigg", "Bigg", "displaystyle", "textstyle", "quad", "qquad", "limits")

    private val SUPERSCRIPTS = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾', 'n' to 'ⁿ', 'i' to 'ⁱ',
    )
    private val SUBSCRIPTS = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄', '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
    )

    private val SYMBOLS = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε", "varepsilon" to "ε", "zeta" to "ζ",
        "eta" to "η", "theta" to "θ", "kappa" to "κ", "lambda" to "λ", "mu" to "µ", "nu" to "ν", "xi" to "ξ", "pi" to "π",
        "rho" to "ρ", "sigma" to "σ", "tau" to "τ", "phi" to "φ", "varphi" to "φ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Pi" to "Π", "Sigma" to "Σ", "Phi" to "Φ",
        "Psi" to "Ψ", "Omega" to "Ω",
        "times" to "×", "cdot" to "·", "div" to "÷", "pm" to "±", "mp" to "∓", "approx" to "≈", "sim" to "~", "neq" to "≠",
        "ne" to "≠", "le" to "≤", "leq" to "≤", "ge" to "≥", "geq" to "≥", "ll" to "≪", "gg" to "≫", "infty" to "∞",
        "propto" to "∝", "to" to "→", "rightarrow" to "→", "leftarrow" to "←", "Rightarrow" to "⇒", "deg" to "°",
        "circ" to "°", "angle" to "∠", "parallel" to "∥", "partial" to "∂", "nabla" to "∇", "sum" to "Σ", "prod" to "Π",
        "int" to "∫", "log" to "log", "ln" to "ln", "exp" to "exp", "sin" to "sin", "cos" to "cos", "tan" to "tan",
        "arctan" to "arctan", "max" to "max", "min" to "min", "lim" to "lim", "dots" to "…", "ldots" to "…", "cdots" to "⋯",
        "ohm" to "Ω", "degree" to "°",
    )
}
