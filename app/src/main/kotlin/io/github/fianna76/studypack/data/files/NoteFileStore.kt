package io.github.fianna76.studypack.data.files

import java.io.File
import java.util.UUID

/**
 * Where files live. Notes PDFs are copied into app-private storage (`files/notes/`), so they
 * keep working if the original is deleted. Incoming files are first copied to `cache/import/`,
 * because access to a shared document can end as soon as the sharing app is closed.
 */
class NoteFileStore(private val filesDir: File, private val cacheDir: File) {

    private val importDir get() = File(cacheDir, "import")

    private val textDir get() = File(cacheDir, "notes-text")

    /** A new, unused path for a notes PDF, relative to the files directory. */
    fun newNotePath(): String = "$NOTES_DIR/${UUID.randomUUID()}.pdf"

    fun file(relativePath: String): File = File(filesDir, relativePath)

    fun delete(relativePath: String) {
        file(relativePath).delete()
        textCacheFile(relativePath).delete()
        // Text cached by 1.x, in the format before sections.
        File(textDir, File(relativePath).nameWithoutExtension + ".json").delete()
    }

    /** Moves [source] to [relativePath] (copying if a rename is not possible). */
    fun store(source: File, relativePath: String): File {
        val target = file(relativePath)
        target.parentFile?.mkdirs()
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
        return target
    }

    fun newTempFile(): File {
        importDir.mkdirs()
        return File.createTempFile("import", ".tmp", importDir)
    }

    /** Scratch files can only be left over from a previous process. */
    fun clearTempFiles() {
        importDir.listFiles()?.forEach { it.delete() }
    }

    /** Cached extracted text of a notes PDF (for speed reading). */
    fun textCacheFile(relativePath: String): File = File(textDir, File(relativePath).nameWithoutExtension + "-v2.json")

    companion object {
        const val NOTES_DIR = "notes"
    }
}
