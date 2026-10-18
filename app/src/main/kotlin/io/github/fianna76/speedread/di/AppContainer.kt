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
import io.github.fianna76.speedread.data.news.NetworkMonitor
import io.github.fianna76.speedread.data.news.NewsHistoryRepository
import io.github.fianna76.speedread.data.news.NewsQueue
import io.github.fianna76.speedread.data.news.NewsUpNext
import io.github.fianna76.speedread.data.news.SharedLinkReader
import io.github.fianna76.speedread.data.news.CustomFeedResolver
import io.github.fianna76.speedread.news.NewsRegion
import io.github.fianna76.speedread.news.NewsSources
import io.github.fianna76.speedread.widget.NewsWidget
import io.github.fianna76.speedread.widget.WidgetData
import io.github.fianna76.speedread.widget.WidgetHeadline
import androidx.glance.appwidget.updateAll
import io.github.fianna76.speedread.data.news.background.BreakingNewsAlerts
import io.github.fianna76.speedread.data.news.background.MorningBriefing
import io.github.fianna76.speedread.data.news.background.NewsAlertLog
import io.github.fianna76.speedread.data.news.background.NewsBackgroundScheduler
import io.github.fianna76.speedread.data.news.background.NewsSyncConfig
import io.github.fianna76.speedread.notifications.NewsNotifications
import androidx.work.WorkManager
import io.github.fianna76.speedread.data.news.background.NewsPrefetcher
import io.github.fianna76.speedread.data.news.NewsRepository
import io.github.fianna76.speedread.data.news.OkHttpWebClient
import io.github.fianna76.speedread.data.pdf.PdfBooks
import io.github.fianna76.speedread.data.settings.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

    val newsRepository by lazy {
        NewsRepository(webClient, NewsCache(File(appContext.cacheDir, "news/headlines.json")), onUpdated = ::onHeadlinesUpdated)
    }

    val articleRepository by lazy { ArticleRepository(database.bookDao(), fileStore, webClient) }

    val networkMonitor by lazy { NetworkMonitor(appContext) }

    val newsHistoryRepository by lazy { NewsHistoryRepository(database.newsHistoryDao()) }

    /** The Newsstand list the current article was opened from, for "next story". */
    val newsQueue = NewsQueue()

    val newsPrefetcher by lazy { NewsPrefetcher(newsRepository, articleRepository, newsHistoryRepository) }

    val newsNotifications by lazy { NewsNotifications(appContext) }

    private val newsAlertLog by lazy { NewsAlertLog(File(appContext.noBackupFilesDir, "news/alerts.json")) }

    val breakingNewsAlerts by lazy { BreakingNewsAlerts(newsRepository, newsHistoryRepository, newsAlertLog, newsNotifications) }

    val morningBriefing by lazy { MorningBriefing(newsRepository, newsPrefetcher, newsHistoryRepository, newsNotifications) }

    /** Speed-reads web links shared to the app. */
    val sharedLinkReader by lazy { SharedLinkReader(articleRepository, applicationScope) }

    /** Called after headlines change (in the app or the background): refreshes home-screen widgets. */
    fun onHeadlinesUpdated() {
        applicationScope.launch { runCatching { NewsWidget().updateAll(appContext) } }
    }

    /** Top Irish headlines for the widget (all enabled sources if no Irish one is on). */
    suspend fun widgetHeadlines(limit: Int = 6): WidgetData {
        newsRepository.ensureLoaded()
        val sources = preferencesRepository.preferences.first().enabledNewsSources
        val irish = sources.filter { it.region == NewsRegion.IRELAND }
        val shown = irish.ifEmpty { sources }
        val names = shown.associate { it.id to it.name }
        val headlines = newsRepository.state.value.headlines(shown.map { it.id }).take(limit).map { item ->
            WidgetHeadline(item.title, names[item.sourceId].orEmpty(), item.canonicalUrl)
        }
        return WidgetData(irish = irish.isNotEmpty(), headlines = headlines)
    }

    /**
     * Keeps the background sync and the morning briefing in line with the settings. Started
     * from the Application; only a change from the stored state touches WorkManager (nothing
     * is scheduled by default).
     */
    fun startBackgroundNewsScheduling() {
        val scheduler = NewsBackgroundScheduler(appContext)
        val preferences = preferencesRepository.preferences
        applicationScope.launch {
            var first = true
            preferences.map { NewsSyncConfig(downloads = it.backgroundNews, alerts = it.breakingNewsAlerts) }
                .distinctUntilChanged()
                .collect { config ->
                    if (config.enabled || !first) scheduler.apply(config)
                    // Switching alerts back on starts afresh, without announcing what led meanwhile.
                    if (!config.alerts && !first) withContext(Dispatchers.IO) { newsAlertLog.clear() }
                    first = false
                }
        }
        applicationScope.launch {
            var first = true
            preferences.map { it.briefingTime.takeIf { _ -> it.morningBriefing } }.distinctUntilChanged().collect { time ->
                // Rescheduling on every launch would be harmless, but at start-up only fill a gap.
                if (!first || (time != null && !isBriefingScheduled())) scheduler.applyBriefing(time)
                first = false
            }
        }
    }

    private fun isBriefingScheduled(): Boolean = runCatching {
        WorkManager.getInstance(appContext)
            .getWorkInfosForUniqueWork(NewsBackgroundScheduler.BRIEFING_WORK_NAME).get()
            .any { !it.state.isFinished }
    }.getOrDefault(false)

    /** Finds feeds for "Add a feed" in Settings. */
    val customFeedResolver by lazy { CustomFeedResolver(webClient) }

    /** Keeps [NewsSources.custom] in line with the user's own feeds, for lookups by source id. */
    fun startCustomNewsSources() {
        applicationScope.launch {
            preferencesRepository.preferences.map { it.customNewsSources }.distinctUntilChanged().collect { NewsSources.custom = it }
        }
    }

    val newsUpNext by lazy { NewsUpNext(newsQueue, newsHistoryRepository, articleRepository, applicationScope) }

    val bookRepository: BookRepository by lazy { OfflineBookRepository(database.bookDao(), fileStore, pdfBooks, bookmarks = database.bookmarkDao()) }

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
