package io.github.fianna76.speedread.epub

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Read-only access to the entries of an EPUB (ZIP) container.
 *
 * Entries are only ever read into memory by name; nothing is extracted to disk, so archive
 * entry names can't escape a target directory ("zip slip"). Entry sizes are bounded to defend
 * against decompression bombs.
 */
class EpubArchive private constructor(private val zip: ZipFile) : Closeable {

    private val entriesByPath: Map<String, ZipEntry>
    private val entriesByLowercasePath: Map<String, ZipEntry>

    init {
        val entries = zip.entries().asSequence().filterNot { it.isDirectory }.toList()
        if (entries.size > MAX_ENTRIES) {
            zip.close()
            throw EpubException.TooLarge("Archive has ${entries.size} entries (limit $MAX_ENTRIES)")
        }
        entriesByPath = entries.associateBy { EpubPaths.normalize(it.name) }
        entriesByLowercasePath = entries.associateBy { EpubPaths.normalize(it.name).lowercase() }
    }

    /** All entry paths in the archive. */
    val paths: Set<String> get() = entriesByPath.keys

    operator fun contains(path: String): Boolean = findEntry(path) != null

    /** Reads an entry fully, failing if it is missing or larger than [limit] bytes once inflated. */
    fun readBytes(path: String, limit: Long = MAX_ENTRY_BYTES): ByteArray {
        val entry = findEntry(path) ?: throw EpubException.Malformed("Missing archive entry: $path")
        if (entry.size > limit) throw EpubException.TooLarge("Entry $path is ${entry.size} bytes (limit $limit)")
        return try {
            zip.getInputStream(entry).use { it.readBounded(limit, path) }
        } catch (e: ZipException) {
            throw EpubException.Malformed("Corrupt archive entry: $path", e)
        }
    }

    /** Like [readBytes] but returns `null` when the entry doesn't exist. */
    fun readBytesOrNull(path: String, limit: Long = MAX_ENTRY_BYTES): ByteArray? =
        if (contains(path)) readBytes(path, limit) else null

    override fun close() = zip.close()

    // Exact match first; fall back to a case-insensitive match because some EPUB producers
    // disagree with themselves about letter case between the manifest and the ZIP entries.
    private fun findEntry(path: String): ZipEntry? {
        val normalized = EpubPaths.normalize(path)
        return entriesByPath[normalized] ?: entriesByLowercasePath[normalized.lowercase()]
    }

    companion object {
        /** Upper bound on the number of entries in a container. */
        const val MAX_ENTRIES = 20_000

        /** Upper bound on the inflated size of a single entry (64 MiB). */
        const val MAX_ENTRY_BYTES = 64L * 1024 * 1024

        /** Opens [file] as an EPUB container. */
        fun open(file: File): EpubArchive {
            val zip = try {
                ZipFile(file)
            } catch (e: ZipException) {
                throw EpubException.NotAnEpub("Not a ZIP archive", e)
            } catch (e: IOException) {
                throw EpubException.NotAnEpub("Unreadable archive: ${e.message}", e)
            }
            return EpubArchive(zip)
        }
    }
}

private fun InputStream.readBounded(limit: Long, path: String): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > limit) throw EpubException.TooLarge("Entry $path inflates beyond $limit bytes")
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
