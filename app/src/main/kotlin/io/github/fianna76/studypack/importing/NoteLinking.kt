package io.github.fianna76.studypack.importing

/** The module a pack belongs to and the notes PDFs it names. */
data class PackNotes(val moduleCode: String, val notes: List<String>)

/** Decides which module an imported notes PDF belongs to. */
object NoteLinking {

    /**
     * 1. A pack lists the file name in `pack.notes`: that pack's module.
     * 2. The file name starts with a known module code (followed by a separator): that module.
     * 3. Otherwise `null`, and the user is asked.
     *
     * Names are compared ignoring case. The longest matching module code wins, so `EE5105A-…`
     * is not filed under `EE5105`.
     */
    fun moduleFor(fileName: String, packs: List<PackNotes>, moduleCodes: Collection<String>): String? {
        packs.firstOrNull { pack -> pack.notes.any { it.equals(fileName, ignoreCase = true) } }?.let { return it.moduleCode }
        return moduleCodes
            .filter { code -> code.isNotEmpty() && startsWithCode(fileName, code) }
            .maxByOrNull { it.length }
    }

    private fun startsWithCode(fileName: String, code: String): Boolean {
        if (!fileName.startsWith(code, ignoreCase = true)) return false
        val next = fileName.getOrNull(code.length) ?: return true
        return !next.isLetterOrDigit()
    }
}
