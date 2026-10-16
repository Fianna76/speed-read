package io.github.fianna76.speedread.epub

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Parses the structure of an EPUB 2.0.1 / 3.x publication: container, package document
 * (metadata, manifest, spine), table of contents (EPUB 3 navigation document or EPUB 2 NCX)
 * and cover image. Parsing is lenient wherever real-world files commonly deviate from the spec.
 */
class EpubParser {

    fun parse(archive: EpubArchive): EpubBook {
        val opfPath = findPackageDocument(archive)
        val opf = parseXml(archive.readBytes(opfPath))
        val pkg = opf.firstDescendant("package")
            ?: throw EpubException.Malformed("Package document has no <package> element")

        val manifest = parseManifest(opf, opfPath)
        val manifestById = manifest.associateBy { it.id }
        val spineElement = opf.firstDescendant("spine")
            ?: throw EpubException.Malformed("Package document has no <spine>")
        val spine = spineElement.childElements("itemref").mapNotNull { ref ->
            manifestById[ref.attr("idref")]?.let { item -> SpineItem(item, linear = ref.attr("linear") != "no") }
        }
        if (spine.isEmpty()) throw EpubException.Malformed("The spine is empty")

        val book = EpubBook(
            version = pkg.attr("version").ifBlank { "2.0" },
            metadata = parseMetadata(opf, pkg),
            manifest = manifest,
            spine = spine,
            toc = parseToc(archive, manifest, manifestById[spineElement.attr("toc")]),
            coverImageHref = findCover(archive, opf, opfPath, manifest, manifestById),
        )
        ensureNotDrmProtected(archive, book)
        return book
    }

    // --- Container -----------------------------------------------------------------------

    private fun findPackageDocument(archive: EpubArchive): String {
        val container = archive.readBytesOrNull(CONTAINER_PATH)
        if (container != null) {
            val rootfiles = parseXml(container).descendants("rootfile")
            val rootfile = rootfiles.firstOrNull { it.attr("media-type") == OPF_MEDIA_TYPE } ?: rootfiles.firstOrNull()
            val path = rootfile?.attr("full-path")?.takeIf { it.isNotBlank() }?.let(EpubPaths::normalize)
            if (path != null && path in archive) return path
        }
        // Lenient fallback for archives with a missing or wrong container.xml.
        return archive.paths.filter { it.endsWith(".opf", ignoreCase = true) }.minByOrNull { it.length }
            ?: throw EpubException.NotAnEpub("No package document (.opf) found")
    }

    // --- Package document ----------------------------------------------------------------

    private fun parseManifest(opf: Document, opfPath: String): List<ManifestItem> {
        val manifest = opf.firstDescendant("manifest") ?: throw EpubException.Malformed("Missing <manifest>")
        return manifest.childElements("item").mapNotNull { item ->
            val id = item.attr("id")
            val href = EpubPaths.resolve(opfPath, item.attr("href"))?.path
            if (id.isBlank() || href.isNullOrEmpty()) return@mapNotNull null
            ManifestItem(
                id = id,
                href = href,
                mediaType = item.attr("media-type").trim().lowercase(),
                properties = item.attr("properties").lowercase().split(' ').filterTo(mutableSetOf()) { it.isNotBlank() },
            )
        }
    }

    private fun parseMetadata(opf: Document, pkg: Element): EpubMetadata {
        val metadata = opf.firstDescendant("metadata") ?: return EpubMetadata(null, emptyList(), null, null, null, null)
        val refinements = metadata.descendants("meta").filter { it.hasAttr("refines") && it.hasAttr("property") }
        fun refinement(id: String, property: String): String? = refinements.firstOrNull {
            it.attr("refines").removePrefix("#") == id && it.attr("property") == property
        }?.normalizedText()

        // EPUB 3 may list several titles; prefer the one refined as the "main" title.
        val titles = metadata.descendants("title").filter { it.normalizedText().isNotEmpty() }
        val title = titles.firstOrNull { it.id().isNotEmpty() && refinement(it.id(), "title-type") == "main" }
            ?: titles.firstOrNull()

        // Creators: keep authors ("aut" role in EPUB 2 attributes or EPUB 3 refinements) when roles exist.
        val creators = metadata.descendants("creator").mapNotNull { creator ->
            val name = creator.normalizedText().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val role = creator.attrLocal("role") ?: creator.id().takeIf { it.isNotEmpty() }?.let { refinement(it, "role") }
            name to role
        }
        val authors = creators.filter { (_, role) -> role == null || role == "aut" }.map { it.first }
            .ifEmpty { creators.map { it.first } }

        val identifiers = metadata.descendants("identifier")
        val uniqueId = pkg.attr("unique-identifier")
        val identifier = identifiers.firstOrNull { uniqueId.isNotEmpty() && it.id() == uniqueId } ?: identifiers.firstOrNull()

        return EpubMetadata(
            title = title?.normalizedText(),
            authors = authors.distinct(),
            language = metadata.firstDescendant("language")?.normalizedText()?.ifEmpty { null },
            identifier = identifier?.normalizedText()?.ifEmpty { null },
            publisher = metadata.firstDescendant("publisher")?.normalizedText()?.ifEmpty { null },
            // Descriptions frequently contain (escaped) HTML markup; keep only the text.
            description = metadata.firstDescendant("description")?.text()
                ?.let { Jsoup.parse(it).text().collapseWhitespace() }?.ifEmpty { null },
        )
    }

    // --- Table of contents ---------------------------------------------------------------

    private fun parseToc(archive: EpubArchive, manifest: List<ManifestItem>, spineToc: ManifestItem?): List<TocEntry> {
        val nav = manifest.firstOrNull { "nav" in it.properties }
        val fromNav = nav?.let { runCatching { parseNavDocument(archive, it.href) }.getOrNull() }.orEmpty()
        if (fromNav.isNotEmpty()) return fromNav

        val ncx = spineToc ?: manifest.firstOrNull { it.mediaType == NCX_MEDIA_TYPE }
        return ncx?.let { runCatching { parseNcx(archive, it.href) }.getOrNull() }.orEmpty()
    }

    private fun parseNavDocument(archive: EpubArchive, navPath: String): List<TocEntry> {
        val doc = parseXml(archive.readBytes(navPath))
        val navs = doc.descendants("nav")
        val tocNav = navs.firstOrNull { "toc" in it.semanticTypes || "doc-toc" in it.semanticTypes } ?: navs.firstOrNull()
        val rootList = tocNav?.firstDescendant("ol") ?: return emptyList()
        val entries = mutableListOf<TocEntry>()

        fun walk(list: Element, depth: Int) {
            for (li in list.childElements("li")) {
                val label = li.children().firstOrNull { it.localName == "a" || it.localName == "span" }
                val title = label?.normalizedText().orEmpty()
                val target = label?.takeIf { it.localName == "a" }?.attr("href")?.let { EpubPaths.resolve(navPath, it) }
                if (title.isNotEmpty() && target != null) {
                    entries += TocEntry(title, target.path, target.fragment, depth)
                }
                li.childElements("ol").forEach { walk(it, depth + 1) }
            }
        }
        walk(rootList, depth = 0)
        return entries
    }

    private fun parseNcx(archive: EpubArchive, ncxPath: String): List<TocEntry> {
        val doc = parseXml(archive.readBytes(ncxPath))
        val navMap = doc.firstDescendant("navmap") ?: return emptyList()
        val entries = mutableListOf<TocEntry>()

        fun walk(parent: Element, depth: Int) {
            for (point in parent.childElements("navpoint")) {
                val title = point.childElements("navlabel").firstOrNull()?.normalizedText().orEmpty()
                val target = point.childElements("content").firstOrNull()?.attr("src")
                    ?.let { EpubPaths.resolve(ncxPath, it) }
                if (title.isNotEmpty() && target != null) {
                    entries += TocEntry(title, target.path, target.fragment, depth)
                }
                walk(point, depth + 1)
            }
        }
        walk(navMap, depth = 0)
        return entries
    }

    // --- Cover -----------------------------------------------------------------------------

    private fun findCover(
        archive: EpubArchive,
        opf: Document,
        opfPath: String,
        manifest: List<ManifestItem>,
        manifestById: Map<String, ManifestItem>,
    ): String? {
        // EPUB 3: <item properties="cover-image">
        manifest.firstOrNull { "cover-image" in it.properties && it.isImage }?.let { return it.href }

        // EPUB 2: <meta name="cover" content="manifest-id"/>
        val coverMeta = opf.descendants("meta").firstOrNull { it.attr("name").equals("cover", ignoreCase = true) }
        coverMeta?.attr("content")?.let(manifestById::get)?.takeIf { it.isImage }?.let { return it.href }

        // EPUB 2 guide: <reference type="cover" href="cover.xhtml"/> -> first image in that page.
        val guideCover = opf.descendants("reference").firstOrNull { it.attr("type").equals("cover", ignoreCase = true) }
        val coverPagePath = guideCover?.attr("href")?.let { EpubPaths.resolve(opfPath, it)?.path }
        val coverPage = manifest.firstOrNull { it.href == coverPagePath }
        coverPage?.takeIf { it.isContentDocument }?.let { page ->
            firstImageIn(archive, page.href)?.let { return it }
        }

        // Heuristic: an image whose id or file name mentions "cover".
        return manifest.firstOrNull { it.isImage && ("cover" in it.id.lowercase() || "cover" in it.href.lowercase()) }
            ?.href
    }

    private fun firstImageIn(archive: EpubArchive, pagePath: String): String? = runCatching {
        val page = parseXml(archive.readBytes(pagePath))
        val source = page.allElements.firstNotNullOfOrNull { element ->
            when (element.localName) {
                "img" -> element.attr("src")
                "image" -> element.attrLocal("href")
                else -> null
            }?.takeIf { it.isNotBlank() }
        }
        source?.let { EpubPaths.resolve(pagePath, it)?.path }?.takeIf { it in archive }
    }.getOrNull()

    // --- DRM -------------------------------------------------------------------------------

    /**
     * META-INF/encryption.xml is also used for (harmless) font obfuscation, so a book only
     * counts as DRM-protected when content documents in its reading order are encrypted.
     */
    private fun ensureNotDrmProtected(archive: EpubArchive, book: EpubBook) {
        val encryption = archive.readBytesOrNull(ENCRYPTION_PATH) ?: return
        val encrypted = parseXml(encryption).descendants("encrypteddata").mapNotNullTo(mutableSetOf()) { data ->
            val algorithm = data.firstDescendant("encryptionmethod")?.attr("Algorithm")
            if (algorithm in FONT_OBFUSCATION_ALGORITHMS) return@mapNotNullTo null
            data.firstDescendant("cipherreference")?.attr("URI")?.let { EpubPaths.resolve("", it)?.path }
        }
        if (book.readingOrder.any { it.href in encrypted }) throw EpubException.DrmProtected()
    }

    private companion object {
        const val CONTAINER_PATH = "META-INF/container.xml"
        const val ENCRYPTION_PATH = "META-INF/encryption.xml"
        const val OPF_MEDIA_TYPE = "application/oebps-package+xml"
        const val NCX_MEDIA_TYPE = "application/x-dtbncx+xml"

        val FONT_OBFUSCATION_ALGORITHMS = setOf(
            "http://www.idpf.org/2008/embedding",
            "http://ns.adobe.com/pdf/enc#RC",
        )
    }
}
