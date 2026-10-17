package io.github.fianna76.speedread.data.news

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import io.github.fianna76.speedread.data.BookFileStore
import io.github.fianna76.speedread.data.BookFormat
import io.github.fianna76.speedread.data.db.BookDao
import io.github.fianna76.speedread.data.db.BookEntity
import io.github.fianna76.speedread.data.db.ChapterEntity
import io.github.fianna76.speedread.news.ArticleExtractionException
import io.github.fianna76.speedread.news.ArticleExtractor
import io.github.fianna76.speedread.news.FeedItem
import io.github.fianna76.speedread.news.NewsSources
import io.github.fianna76.speedread.news.PaywalledArticleException
import io.github.fianna76.speedread.rsvp.Tokenizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest

sealed interface ArticleResult {
    /** The article is saved and can be opened in the reader as book [bookId]. */
    data class Ready(val bookId: Long) : ArticleResult

    data class Failed(val reason: ArticleError) : ArticleResult
}

enum class ArticleError {
    /** The page could not be downloaded (offline, timeout, server error). */
    NETWORK,

    /** The page has no readable article text (video page, unusual layout). */
    NO_TEXT,

    /** The outlet marks the article as subscriber-only; it can only be read on their site. */
    PAYWALLED,

    /** The text could not be stored on the device. */
    STORAGE,
}

/**
 * Turns news stories into readable "books": the page is downloaded once, its story text is
 * extracted and saved, and the reader then treats it like any other book, with progress. Saved
 * articles stay out of the library and are deleted when not opened for [RETENTION_MILLIS].
 */
class ArticleRepository(
    private val dao: BookDao,
    private val files: BookFileStore,
    private val web: WebClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Reading progress in `[0, 1]` of saved articles, keyed by canonical URL. */
    fun observeProgress(): Flow<Map<String, Float>> = dao.observeArticleProgress().map { rows ->
        rows.associate { row ->
            row.sourceUrl to if (row.totalWords <= 0) 0f else (row.wordsRead.toFloat() / row.totalWords).coerceIn(0f, 1f)
        }
    }

    /** Returns the saved copy of [item], downloading and extracting it first if needed. */
    suspend fun open(item: FeedItem): ArticleResult {
        val url = item.canonicalUrl
        dao.findArticle(url)?.let { existing ->
            if (files.readChapterText(existing.fileName, ARTICLE_HREF) != null) return ArticleResult.Ready(existing.id)
            delete(listOf(existing)) // Its text went missing; fetch it again.
        }

        val html = try {
            web.fetchText(item.url)
        } catch (e: IOException) {
            Log.w(TAG, "Could not download $url", e)
            return ArticleResult.Failed(ArticleError.NETWORK)
        }
        val paragraphs = try {
            withContext(Dispatchers.Default) {
                ArticleExtractor.extract(html, item.url, NewsSources.byId(item.sourceId)?.bodySelector).paragraphs
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: PaywalledArticleException) {
            return ArticleResult.Failed(ArticleError.PAYWALLED)
        } catch (e: ArticleExtractionException) {
            Log.i(TAG, "No article text in $url: ${e.message}")
            return ArticleResult.Failed(ArticleError.NO_TEXT)
        }
        return save(item, url, paragraphs)
    }

    /** Deletes saved articles not opened within [RETENTION_MILLIS]. */
    suspend fun pruneOld() {
        delete(dao.findArticlesOlderThan(clock() - RETENTION_MILLIS))
    }

    private suspend fun save(item: FeedItem, url: String, paragraphs: List<String>): ArticleResult {
        val fileName = "article-${sha256(url)}"
        val stored = withContext(ioDispatcher) { files.writeChapterText(fileName, ARTICLE_HREF, paragraphs) }
        if (!stored) return ArticleResult.Failed(ArticleError.STORAGE)
        val words = Tokenizer.countWords(paragraphs)
        val book = BookEntity(
            title = item.title,
            author = NewsSources.byId(item.sourceId)?.name,
            language = null,
            format = BookFormat.ARTICLE.name,
            fileName = fileName,
            coverFileName = null,
            contentHash = "article:$fileName",
            totalWords = words,
            addedAt = clock(),
            sourceUrl = url,
        )
        val chapter = ChapterEntity(bookId = 0, index = 0, title = null, href = ARTICLE_HREF, wordCount = words, startWord = 0)
        val id = try {
            dao.insertBookWithChapters(book, listOf(chapter))
        } catch (e: SQLiteConstraintException) {
            // Saved concurrently (e.g. a double tap); use that copy.
            dao.findArticle(url)?.id ?: throw e
        }
        return ArticleResult.Ready(id)
    }

    private suspend fun delete(articles: List<BookEntity>) {
        if (articles.isEmpty()) return
        dao.deleteBooks(articles.map { it.id })
        withContext(ioDispatcher) { articles.forEach { files.delete(it.fileName, it.coverFileName) } }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)

    companion object {
        private const val TAG = "ArticleRepository"

        /** An article is a single section; its text lives in the chapter text cache under this key. */
        const val ARTICLE_HREF = "article"

        const val RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
