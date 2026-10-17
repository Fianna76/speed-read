package io.github.fianna76.speedread.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleExtractorTest {

    private val url = "https://news.example/world/story"

    private fun sentence(n: Int) =
        "Paragraph $n reports what officials said on Saturday, adding detail, context, and quotes from witnesses."

    private fun page(body: String, head: String = "") = """<!DOCTYPE html><html><head>
        <title>Fallback title - Example News</title>
        <meta property="og:title" content="Big story | Example News">
        <meta property="og:site_name" content="Example News">
        <meta property="og:image" content="/img/lead.jpg">
        $head
        </head><body>
        <header class="masthead"><nav><a href="/">Home</a> <a href="/world">World</a></nav></header>
        $body
        <footer><p>Copyright Example News 2026. All rights reserved, everywhere, forever and ever.</p></footer>
        </body></html>"""

    @Test
    fun `extracts the story and drops page furniture`() {
        val html = page(
            """<div class="layout">
                 <div class="article-body">
                   <h1>Big story</h1>
                   <ul class="MetadataStrip"><li><span class="visually-hidden">Published</span><time>17 October 2026, 13:37 BST</time></li></ul>
                   <p><span class="sr-only">Image caption,</span>${sentence(1)}</p>
                   <figure><img src="a.jpg"><figcaption>A long caption describing the photo in some detail.</figcaption></figure>
                   <p>${sentence(2)}</p>
                   <div class="share-tools"><p>Share this story on social media with all of your friends today.</p></div>
                   <h2>What happens next</h2>
                   <p>${sentence(3)}</p>
                   <blockquote><p>${sentence(4)}</p></blockquote>
                   <p>Advertisement</p>
                   <ul><li>${sentence(5)}</li><li>Short item</li></ul>
                   <p><a href="/other">Read about another, entirely different story on our website</a></p>
                   <h3>More on this story</h3>
                 </div>
                 <aside><p>${sentence(99)} Most read elsewhere.</p></aside>
               </div>""",
        )

        val article = ArticleExtractor.extract(html, url)

        assertEquals("Big story", article.title)
        assertEquals("https://news.example/img/lead.jpg", article.imageUrl)
        assertEquals(
            listOf(sentence(1), sentence(2), "What happens next", sentence(3), sentence(4), sentence(5)),
            article.paragraphs,
        )
    }

    @Test
    fun `gathers a story that is wrapped block by block`() {
        // Like BBC pages: each paragraph or two sit in their own wrapper inside a layout stack,
        // next to a container whose name mentions a sidebar.
        val blocks = (1..6).chunked(2).joinToString("") { pair ->
            """<div class="ComponentWrapper"><div class="Spacer"><div data-testid="rich-text"><div class="RichTextContainer">""" +
                pair.joinToString("") { "<p>${sentence(it)}</p>" } + "</div></div></div></div>"
        }
        val html = page(
            """<div class="ContainerWithSidebarWrapper"><main><article><div class="Stack">
                 <div class="ComponentWrapper"><h1>Big story</h1></div>
                 $blocks
               </div></article></main>
               <div class="sidebar"><p>${sentence(42)}</p></div></div>""",
        )

        val article = ArticleExtractor.extract(html, url)

        assertEquals((1..6).map(::sentence), article.paragraphs)
    }

    @Test
    fun `the source's body selector is preferred when it matches enough text`() {
        val html = page(
            """<div class="teaser">${(1..8).joinToString("") { "<p>${sentence(it)}</p>" }}</div>
               <div class="story-text">${(10..14).joinToString("") { "<p>${sentence(it)}</p>" }}</div>""",
        )

        assertEquals((10..14).map(::sentence), ArticleExtractor.extract(html, url, ".story-text").paragraphs)
        // A selector that matches nothing falls back to scoring.
        assertEquals((1..8).map(::sentence), ArticleExtractor.extract(html, url, ".missing").paragraphs)
    }

    @Test
    fun `titles keep dashes that are not an outlet name`() {
        val html = page((1..5).joinToString("") { "<p>${sentence(it)}</p>" })
            .replace("Big story | Example News", "Election - the final days")

        assertEquals("Election - the final days", ArticleExtractor.extract(html, url).title)
    }

    @Test
    fun `pages without enough text are rejected`() {
        val html = page("<div class='video-page'><p>Watch the video above for the full story.</p></div>")

        val error = runCatching { ArticleExtractor.extract(html, url) }.exceptionOrNull()

        assertTrue(error is ArticleExtractionException)
    }

    @Test
    fun `word count spans all paragraphs`() {
        assertEquals(5, ExtractedArticle("t", null, listOf("one two", " three  four five ")).wordCount)
    }
}
