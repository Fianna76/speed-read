package io.github.fianna76.studypack.quiz

/**
 * A pack's semantic version (`MAJOR.MINOR.PATCH`, optionally `-prerelease` and `+build`).
 * Missing minor or patch numbers count as 0, so `1.2` equals `1.2.0`.
 */
data class PackVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null,
) : Comparable<PackVersion> {

    override fun compareTo(other: PackVersion): Int {
        compareValues(major, other.major).let { if (it != 0) return it }
        compareValues(minor, other.minor).let { if (it != 0) return it }
        compareValues(patch, other.patch).let { if (it != 0) return it }
        // A pre-release sorts before its release: 1.0.0-beta < 1.0.0.
        return when {
            preRelease == other.preRelease -> 0
            preRelease == null -> 1
            other.preRelease == null -> -1
            else -> comparePreRelease(preRelease, other.preRelease)
        }
    }

    override fun toString(): String = "$major.$minor.$patch" + (preRelease?.let { "-$it" } ?: "")

    companion object {
        private val PATTERN = Regex("""^v?(\d{1,9})(?:\.(\d{1,9}))?(?:\.(\d{1,9}))?(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""")

        fun parse(value: String): PackVersion? {
            val match = PATTERN.matchEntire(value.trim()) ?: return null
            val (major, minor, patch, pre) = match.destructured
            return PackVersion(major.toInt(), minor.toIntOrNull() ?: 0, patch.toIntOrNull() ?: 0, pre.ifEmpty { null })
        }

        /** Semver pre-release precedence: dot-separated identifiers, numeric ones compared as numbers. */
        private fun comparePreRelease(a: String, b: String): Int {
            val left = a.split('.')
            val right = b.split('.')
            for (i in 0 until minOf(left.size, right.size)) {
                val x = left[i]
                val y = right[i]
                val xn = x.toLongOrNull()
                val yn = y.toLongOrNull()
                val result = when {
                    xn != null && yn != null -> xn.compareTo(yn)
                    xn != null -> -1
                    yn != null -> 1
                    else -> x.compareTo(y)
                }
                if (result != 0) return result
            }
            return left.size.compareTo(right.size)
        }
    }
}
