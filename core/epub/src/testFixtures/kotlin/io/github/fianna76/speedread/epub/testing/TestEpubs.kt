package io.github.fianna76.speedread.epub.testing

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds small EPUB archives on the fly so tests don't depend on binary fixtures.
 * Published as test fixtures so other modules' tests can create books too.
 */
object TestEpubs {

    const val CONTAINER = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""

    fun write(target: File, entries: Map<String, ByteArray>, includeMimetype: Boolean = true): File {
        ZipOutputStream(target.outputStream()).use { zip ->
            if (includeMimetype) {
                // The spec requires an uncompressed "mimetype" entry first.
                val bytes = "application/epub+zip".toByteArray()
                val entry = ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = bytes.size.toLong()
                    crc = CRC32().apply { update(bytes) }.value
                }
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return target
    }

    fun writeText(target: File, entries: Map<String, String>, includeMimetype: Boolean = true): File =
        write(target, entries.mapValues { it.value.toByteArray() }, includeMimetype)

    fun xhtml(body: String, title: String = "Chapter") = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>$title</title><style>p { margin: 0 }</style></head>
<body>$body</body>
</html>"""

    /**
     * An EPUB 3 book with a navigation document, refinements and a cover-image property.
     * Reading order: "Chapter One" (7 words) then "Chapter Two" (4 words).
     */
    fun epub3(target: File, coverBytes: ByteArray = "not really a jpeg".toByteArray()): File =
        write(target, epub3Entries().mapValues { it.value.toByteArray() } + ("OEBPS/images/cover.jpg" to coverBytes))

    private fun epub3Entries(): Map<String, String> = mapOf(
        "META-INF/container.xml" to CONTAINER,
        "OEBPS/content.opf" to """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="uid">urn:uuid:1234</dc:identifier>
    <dc:title id="sub">A Subtitle</dc:title>
    <dc:title id="main">The Main Title</dc:title>
    <meta refines="#main" property="title-type">main</meta>
    <meta refines="#sub" property="title-type">subtitle</meta>
    <dc:creator id="c1">Ada Author</dc:creator>
    <meta refines="#c1" property="role" scheme="marc:relators">aut</meta>
    <dc:creator id="c2">Ian Illustrator</dc:creator>
    <meta refines="#c2" property="role" scheme="marc:relators">ill</meta>
    <dc:language>en</dc:language>
    <dc:description>&lt;p&gt;A &lt;b&gt;bold&lt;/b&gt; description.&lt;/p&gt;</dc:description>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="cover" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
    <item id="ch1" href="text/chapter%201.xhtml" media-type="application/xhtml+xml"/>
    <item id="ch2" href="text/chapter2.xhtml" media-type="application/xhtml+xml"/>
    <item id="notes" href="text/notes.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="nav" linear="no"/>
    <itemref idref="ch1"/>
    <itemref idref="ch2"/>
    <itemref idref="notes" linear="no"/>
  </spine>
</package>""",
        "OEBPS/nav.xhtml" to xhtml(
            """<nav epub:type="landmarks"><ol><li><a href="text/chapter2.xhtml">Wrong</a></li></ol></nav>
<nav epub:type="toc"><h1>Contents</h1><ol>
  <li><a href="text/chapter%201.xhtml">Chapter One</a>
    <ol><li><a href="text/chapter%201.xhtml#part2">Part Two</a></li></ol>
  </li>
  <li><a href="text/chapter2.xhtml">Chapter Two</a></li>
</ol></nav>""",
        ),
        "OEBPS/text/chapter 1.xhtml" to xhtml("<h1>Chapter One</h1><p>It was a dark night.</p>"),
        "OEBPS/text/chapter2.xhtml" to xhtml("<h1>Chapter Two</h1><p>Morning came.</p>"),
        "OEBPS/text/notes.xhtml" to xhtml("<p>Notes.</p>"),
    )

    /** An EPUB 2 book with an NCX table of contents, `<meta name="cover">` and nested folders. */
    fun epub2(target: File): File = writeText(
        target,
        mapOf(
            "META-INF/container.xml" to CONTAINER,
            "OEBPS/content.opf" to """<?xml version="1.0" encoding="UTF-8"?>
<opf:package xmlns:opf="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="BookId">
  <opf:metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>Old School</dc:title>
    <dc:creator opf:role="edt">Eddie Editor</dc:creator>
    <dc:creator opf:role="aut">Wendy Writer</dc:creator>
    <dc:identifier id="BookId">isbn-42</dc:identifier>
    <opf:meta name="cover" content="cover-img"/>
  </opf:metadata>
  <opf:manifest>
    <opf:item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
    <opf:item id="cover-img" href="Images/front.png" media-type="image/png"/>
    <opf:item id="c1" href="Text/one.html" media-type="application/xhtml+xml"/>
  </opf:manifest>
  <opf:spine toc="ncx">
    <opf:itemref idref="c1"/>
  </opf:spine>
</opf:package>""",
            "OEBPS/toc.ncx" to """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <navMap>
    <navPoint id="p1" playOrder="1">
      <navLabel><text>  The   Beginning </text></navLabel>
      <content src="Text/one.html"/>
      <navPoint id="p2" playOrder="2">
        <navLabel><text>Subsection</text></navLabel>
        <content src="Text/one.html#sub"/>
      </navPoint>
    </navPoint>
  </navMap>
</ncx>""",
            "OEBPS/Images/front.png" to "png",
            "OEBPS/Text/one.html" to xhtml("<p>Once upon a time&nbsp;there was a parser&mdash;a good one.</p>"),
        ),
    )

    /** A minimal single-document book with the given package [title] and XHTML [body]. */
    fun simple(target: File, title: String, body: String): File = writeText(
        target,
        mapOf(
            "META-INF/container.xml" to CONTAINER,
            "OEBPS/content.opf" to """<package version="3.0"><metadata><dc:title>$title</dc:title></metadata>
                <manifest><item id="t" href="text.xhtml" media-type="application/xhtml+xml"/></manifest>
                <spine><itemref idref="t"/></spine></package>""",
            "OEBPS/text.xhtml" to xhtml(body),
        ),
    )

    /**
     * A book whose content document is listed in META-INF/encryption.xml. With [fontOnly] the
     * only encrypted resource is an obfuscated font, which readers must still accept.
     */
    fun encrypted(target: File, fontOnly: Boolean = false): File {
        val algorithm = if (fontOnly) "http://www.idpf.org/2008/embedding" else "http://www.w3.org/2001/04/xmlenc#aes256-cbc"
        val encryptedPath = if (fontOnly) "OEBPS/fonts/serif.otf" else "OEBPS/text.xhtml"
        return writeText(
            target,
            mapOf(
                "META-INF/container.xml" to CONTAINER,
                "META-INF/encryption.xml" to """<encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container"
                    xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
                    <enc:EncryptedData>
                      <enc:EncryptionMethod Algorithm="$algorithm"/>
                      <enc:CipherData><enc:CipherReference URI="$encryptedPath"/></enc:CipherData>
                    </enc:EncryptedData>
                  </encryption>""",
                "OEBPS/content.opf" to """<package version="3.0"><metadata><dc:title>Encrypted?</dc:title></metadata>
                    <manifest><item id="t" href="text.xhtml" media-type="application/xhtml+xml"/></manifest>
                    <spine><itemref idref="t"/></spine></package>""",
                "OEBPS/text.xhtml" to xhtml("<p>Secret</p>"),
            ),
        )
    }
}
