package io.github.fianna76.studypack.data.pdf

/** Display titles for notes PDFs. */
object NoteTitles {

    /**
     * The PDF's own title if it has one, otherwise one made from the file name:
     * `EE5105-ecg-amp-overview.pdf` → `ecg amp overview` → `Ecg amp overview` (with the module
     * code prefix dropped, as the module screen already shows it).
     */
    fun displayTitle(fileName: String, pdfTitle: String?, moduleCode: String?): String {
        pdfTitle?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        var base = fileName.substringBeforeLast('.', fileName).trim()
        if (moduleCode != null && base.startsWith(moduleCode, ignoreCase = true) && base.length > moduleCode.length) {
            base = base.substring(moduleCode.length)
        }
        val words = base.replace(Regex("[-_]+"), " ").trim().replace(Regex("\\s+"), " ")
        return words.replaceFirstChar { it.uppercaseChar() }.ifEmpty { fileName }
    }
}
