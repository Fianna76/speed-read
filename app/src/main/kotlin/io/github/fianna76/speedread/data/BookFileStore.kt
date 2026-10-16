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

    fun bookFile(fileName: String): File = File(booksDir, fileName)

    fun coverFile(fileName: String): File = File(coversDir, fileName)

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
        fileName?.let { bookFile(it).delete() }
        coverFileName?.let { coverFile(it).delete() }
    }
}
