package io.github.fianna76.speedread.epub

import io.github.fianna76.speedread.epub.testing.TestEpubs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EpubParserTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val parser = EpubParser()

    private fun parse(file: File): EpubBook = EpubArchive.open(file).use(parser::parse)

    @Test
    fun `parses EPUB 3 metadata, spine, navigation document and cover`() {
        val book = parse(TestEpubs.epub3(tmp.newFile("book3.epub")))

        assertEquals("3.0", book.version)
        assertEquals("The Main Title", book.metadata.title)
        assertEquals(listOf("Ada Author"), book.metadata.authors)
        assertEquals("en", book.metadata.language)
        assertEquals("urn:uuid:1234", book.metadata.identifier)
        assertEquals("A bold description.", book.metadata.description)
        assertEquals("OEBPS/images/cover.jpg", book.coverImageHref)

        // Non-linear items (the nav document and notes) are not part of the reading order.
        assertEquals(
            listOf("OEBPS/text/chapter 1.xhtml", "OEBPS/text/chapter2.xhtml"),
            book.readingOrder.map { it.href },
        )

        // The "toc" nav is used, not the landmarks nav; nesting is flattened with depth.
        assertEquals(
            listOf(
                TocEntry("Chapter One", "OEBPS/text/chapter 1.xhtml", null, 0),
                TocEntry("Part Two", "OEBPS/text/chapter 1.xhtml", "part2", 1),
                TocEntry("Chapter Two", "OEBPS/text/chapter2.xhtml", null, 0),
            ),
            book.toc,
        )
        assertEquals("Chapter Two", book.tocTitleFor("OEBPS/text/chapter2.xhtml"))
    }

    @Test
    fun `parses EPUB 2 package with prefixed elements, NCX and cover meta`() {
        val book = parse(TestEpubs.epub2(tmp.newFile("book2.epub")))

        assertEquals("2.0", book.version)
        assertEquals("Old School", book.metadata.title)
        assertEquals(listOf("Wendy Writer"), book.metadata.authors)
        assertEquals("isbn-42", book.metadata.identifier)
        assertEquals("OEBPS/Images/front.png", book.coverImageHref)
        assertEquals(listOf("OEBPS/Text/one.html"), book.readingOrder.map { it.href })
        assertEquals(
            listOf(
                TocEntry("The Beginning", "OEBPS/Text/one.html", null, 0),
                TocEntry("Subsection", "OEBPS/Text/one.html", "sub", 1),
            ),
            book.toc,
        )
    }

    @Test
    fun `falls back to scanning for the package document when container xml is missing`() {
        val file = TestEpubs.writeText(
            tmp.newFile("nocontainer.epub"),
            mapOf(
                "book.opf" to """<package version="2.0"><metadata><title>Loose</title></metadata>
                    <manifest><item id="a" href="a.xhtml" media-type="application/xhtml+xml"/></manifest>
                    <spine><itemref idref="a"/></spine></package>""",
                "a.xhtml" to TestEpubs.xhtml("<p>Hi</p>"),
            ),
        )
        val book = parse(file)
        assertEquals("Loose", book.metadata.title)
        assertEquals(listOf("a.xhtml"), book.readingOrder.map { it.href })
        assertTrue(book.toc.isEmpty())
        assertNull(book.coverImageHref)
    }

    @Test
    fun `rejects files that are not ZIP archives`() {
        val file = tmp.newFile("plain.epub").apply { writeText("just text") }
        assertThrows(EpubException.NotAnEpub::class.java) { EpubArchive.open(file) }
    }

    @Test
    fun `rejects archives without a package document`() {
        val file = TestEpubs.writeText(tmp.newFile("empty.epub"), mapOf("readme.txt" to "hello"))
        assertThrows(EpubException.NotAnEpub::class.java) { parse(file) }
    }

    @Test
    fun `rejects packages with an empty spine`() {
        val file = TestEpubs.writeText(
            tmp.newFile("nospine.epub"),
            mapOf(
                "META-INF/container.xml" to TestEpubs.CONTAINER,
                "OEBPS/content.opf" to """<package version="3.0"><metadata/><manifest/><spine/></package>""",
            ),
        )
        assertThrows(EpubException.Malformed::class.java) { parse(file) }
    }

    @Test
    fun `detects DRM-encrypted content documents`() {
        val file = TestEpubs.encrypted(tmp.newFile("drm.epub"))
        assertThrows(EpubException.DrmProtected::class.java) { parse(file) }
    }

    @Test
    fun `allows books whose only encryption is font obfuscation`() {
        val file = TestEpubs.encrypted(tmp.newFile("fonts.epub"), fontOnly = true)
        assertEquals("Encrypted?", parse(file).metadata.title)
    }

    @Test
    fun `bounded reads reject oversized entries`() {
        val file = TestEpubs.writeText(tmp.newFile("big.epub"), mapOf("big.txt" to "x".repeat(10_000)))
        EpubArchive.open(file).use { archive ->
            assertThrows(EpubException.TooLarge::class.java) { archive.readBytes("big.txt", limit = 1_000) }
            assertEquals(10_000, archive.readBytes("big.txt").size)
        }
    }

    @Test
    fun `entry lookup tolerates case mismatches`() {
        val file = TestEpubs.writeText(tmp.newFile("case.epub"), mapOf("OEBPS/Text/A.xhtml" to "a"))
        EpubArchive.open(file).use { archive ->
            assertTrue("oebps/text/a.xhtml" in archive)
            assertTrue("OEBPS/./Text/../Text/A.xhtml" in archive)
        }
    }
}
