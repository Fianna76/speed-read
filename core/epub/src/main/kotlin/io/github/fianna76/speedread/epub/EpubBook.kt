package io.github.fianna76.speedread.epub

/** Dublin Core metadata from the package document. */
data class EpubMetadata(
    val title: String?,
    val authors: List<String>,
    val language: String?,
    val identifier: String?,
    val publisher: String?,
    val description: String?,
)

/** A manifest entry. [href] is already resolved to a path inside the archive. */
data class ManifestItem(
    val id: String,
    val href: String,
    val mediaType: String,
    val properties: Set<String>,
) {
    val isContentDocument: Boolean
        get() = "html" in mediaType || href.endsWith(".xhtml", ignoreCase = true) ||
            href.endsWith(".html", ignoreCase = true) || href.endsWith(".htm", ignoreCase = true)

    val isImage: Boolean get() = mediaType.startsWith("image/")
}

/** An entry of the spine (the default reading order). */
data class SpineItem(
    val item: ManifestItem,
    /** `false` for auxiliary content (e.g. pop-up footnotes) that is not part of the linear reading order. */
    val linear: Boolean,
) {
    val href: String get() = item.href
}

/** A flattened table-of-contents entry in document order. */
data class TocEntry(
    val title: String,
    /** Archive path of the target document (without fragment). */
    val href: String,
    val fragment: String?,
    /** Nesting level, 0 for top-level entries. */
    val depth: Int,
)

/** The parsed structure of an EPUB 2 or EPUB 3 publication. */
data class EpubBook(
    val version: String,
    val metadata: EpubMetadata,
    val manifest: List<ManifestItem>,
    val spine: List<SpineItem>,
    val toc: List<TocEntry>,
    /** Archive path of the cover image, if one could be identified. */
    val coverImageHref: String?,
) {
    /** Linear content documents in reading order: the sections an RSVP reader walks through. */
    val readingOrder: List<SpineItem>
        get() = spine.filter { it.linear && it.item.isContentDocument }

    /** Title of the first table-of-contents entry pointing into [href], if any. */
    fun tocTitleFor(href: String): String? = toc.firstOrNull { it.href == href }?.title
}
