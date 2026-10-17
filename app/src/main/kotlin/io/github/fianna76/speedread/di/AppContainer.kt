package io.github.fianna76.speedread.di

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.fianna76.speedread.data.BookFileStore
import io.github.fianna76.speedread.data.BookRepository
import io.github.fianna76.speedread.data.OfflineBookRepository
import io.github.fianna76.speedread.data.db.SpeedReadDatabase
import io.github.fianna76.speedread.data.importing.BookImporter
import io.github.fianna76.speedread.data.importing.ImportCoordinator
import io.github.fianna76.speedread.data.news.ArticleRepository
import io.github.fianna76.speedread.data.news.NewsCache
import io.github.fianna76.speedread.data.news.NewsRepository
import io.github.fianna76.speedread.data.news.OkHttpWebClient
import io.github.fianna76.speedread.data.pdf.PdfBooks
import io.github.fianna76.speedread.data.settings.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

/**
 * Manual dependency injection: a single place that creates and owns app-wide singletons.
 * For an app of this size a hand-written container keeps the build simple; the constructor
 * injected classes below can move to Hilt unchanged if the graph grows.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Scope for work that must outlive any single screen (imports, final progress saves). */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database by lazy { SpeedReadDatabase.create(appContext) }

    private val fileStore by lazy { BookFileStore(appContext.filesDir) }

    private val pdfBooks by lazy { PdfBooks(appContext) }

    /** One HTTP client for feeds, articles and news images, sharing connections and a disk cache. */
    val httpClient: OkHttpClient by lazy { OkHttpWebClient.createClient(File(appContext.cacheDir, "http")) }

    private val webClient by lazy {
        val version = appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        // Browser-compatible prefix (some sites' firewalls reject anything else) that still names the app.
        OkHttpWebClient(httpClient, userAgent = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}) SpeedRead/$version")
    }

    val newsRepository by lazy { NewsRepository(webClient, NewsCache(File(appContext.cacheDir, "news/headlines.json"))) }

    val articleRepository by lazy { ArticleRepository(database.bookDao(), fileStore, webClient) }

    val bookRepository: BookRepository by lazy { OfflineBookRepository(database.bookDao(), fileStore, pdfBooks) }

    /** The container is the single owner of the DataStore, as DataStore requires one instance per file. */
    val preferencesRepository by lazy {
        UserPreferencesRepository(
            PreferenceDataStoreFactory.create { appContext.preferencesDataStoreFile("settings") },
        )
    }

    val importCoordinator by lazy {
        val importer = BookImporter(appContext.contentResolver, fileStore, database.bookDao(), pdfBooks)
        ImportCoordinator(
            importer = importer::import,
            scope = applicationScope,
            // Scratch files can only be left over from a previous process, so clean up once,
            // inside the queue, before this process imports anything.
            beforeFirstImport = { withContext(Dispatchers.IO) { fileStore.clearTempFiles() } },
        )
    }
}
