package io.github.fianna76.speedread.data

import java.io.File

/**
 * Owns the app-private directories that hold imported books and their cover thumbnails.
 * Books are copied here on import, so the library keeps working even if the original file
 * is moved or deleted, or the granting app revokes its URI permission.
 *
 * Path accessors are pure; only [ensureDirectories] and the write/delete helpers touch disk,
 * and they are expected to run on a background dispatcher.
 */
class BookFileStore(filesDir: File) {

    private val booksDir = File(filesDir, "books")
    private val coversDir = File(filesDir, "covers")
    private val tempDir = File(filesDir, "import-tmp")
    private val textCacheDir = File(filesDir, "text-cache")

    fun bookFile(fileName: String): File = File(booksDir, fileName)

    fun coverFile(fileName: String): File = File(coversDir, fileName)

    /**
     * Plain text of one chapter, extracted once and reused. PDF extraction is slow (it lays out
     * every glyph), so PDF chapters are cached at import instead of re-parsed on every open.
     */
    fun readChapterText(bookFileName: String, href: String): List<String>? = runCatching {
        chapterTextFile(bookFileName, href).takeIf { it.isFile }?.readLines()
    }.getOrNull()

    /**
     * Returns whether the text was stored. For books a failed write just means the text is
     * extracted again next time; articles have no other copy, so their callers check.
     */
    fun writeChapterText(bookFileName: String, href: String, paragraphs: List<String>): Boolean = runCatching {
        val file = chapterTextFile(bookFileName, href)
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(paragraphs.joinToString("\n")) // Paragraphs never contain newlines.
        temp.renameTo(file).also { renamed -> if (!renamed) temp.delete() }
    }.getOrDefault(false)

    private fun chapterTextFile(bookFileName: String, href: String): File =
        File(File(textCacheDir, bookFileName), href.replace(UNSAFE_FILE_CHARS, "_") + ".txt")

    fun ensureDirectories() {
        booksDir.mkdirs()
        coversDir.mkdirs()
        tempDir.mkdirs()
    }

    /**
     * A scratch file for an in-progress import. It lives on the same file system as the books
     * directory so the finished import can be moved into place with an atomic rename.
     */
    fun createTempFile(): File {
        ensureDirectories()
        return File.createTempFile("import-", ".epub", tempDir)
    }

    /** Removes scratch files left behind by imports that were interrupted (e.g. process death). */
    fun clearTempFiles() {
        tempDir.listFiles()?.forEach { it.delete() }
    }

    fun delete(fileName: String?, coverFileName: String?) {
        fileName?.let {
            bookFile(it).delete()
            File(textCacheDir, it).deleteRecursively()
        }
        coverFileName?.let { coverFile(it).delete() }
    }

    private companion object {
        val UNSAFE_FILE_CHARS = Regex("[^A-Za-z0-9._-]")
    }
}
