package io.github.fianna76.speedread.epub

import io.github.fianna76.speedread.epub.testing.TestEpubs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XhtmlTextExtractorTest {

    private fun extract(body: String): ExtractedText =
        XhtmlTextExtractor.extract(TestEpubs.xhtml(body).toByteArray())

    @Test
    fun `block elements delimit paragraphs and inline markup is flattened`() {
        val text = extract(
            """<h1>Title</h1>
               <p>First <em>para</em>graph
                  spans lines.</p>
               <div><p>Nested</p> tail</div>
               <ul><li>One</li><li>Two</li></ul>""",
        )
        assertEquals(listOf("Title", "First paragraph spans lines.", "Nested", "tail", "One", "Two"), text.paragraphs)
        assertEquals("Title", text.firstHeading)
    }

    @Test
    fun `line breaks split paragraphs`() {
        assertEquals(listOf("Roses are red,", "violets are blue."), extract("<p>Roses are red,<br/>violets are blue.</p>").paragraphs)
    }

    @Test
    fun `decodes HTML named entities used by EPUB 2 content`() {
        val text = extract("<p>A&nbsp;B&mdash;C &rsquo;D&rsquo; &amp; caf&eacute;</p>")
        assertEquals(listOf("A B\u2014C \u2019D\u2019 & café"), text.paragraphs)
    }

    @Test
    fun `drops non-textual and out-of-flow content`() {
        val text = extract(
            """<p>Body<a epub:type="noteref" href="#n1">1</a> text<span epub:type="pagebreak" title="7">7</span>.</p>
               <script>var x = 1;</script>
               <svg><text>vector</text></svg>
               <p><ruby>漢<rt>kan</rt></ruby>字</p>
               <nav><ol><li>Contents</li></ol></nav>
               <aside epub:type="footnote" id="n1"><p>The footnote.</p></aside>
               <div role="doc-endnotes"><p>Endnote.</p></div>""",
        )
        assertEquals(listOf("Body text.", "漢字"), text.paragraphs)
    }

    @Test
    fun `removes soft hyphens and collapses exotic whitespace`() {
        assertEquals(listOf("hyphenation works"), extract("<p>hy\u00ADphen\u00ADation\u2003\u202Fworks</p>").paragraphs)
    }

    @Test
    fun `documents without headings or text yield nothing`() {
        val text = extract("<div><img src=\"cover.jpg\" alt=\"Cover\"/></div>")
        assertEquals(emptyList<String>(), text.paragraphs)
        assertNull(text.firstHeading)
    }

    @Test
    fun `tolerates documents without a body element`() {
        val text = XhtmlTextExtractor.extract("<p>Loose text</p>".toByteArray())
        assertEquals(listOf("Loose text"), text.paragraphs)
    }
}
