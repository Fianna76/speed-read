package io.github.fianna76.speedread.data.news.background

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.fianna76.speedread.SpeedReadApplication
import io.github.fianna76.speedread.news.NewsRegion
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * The periodic news sync: downloads for offline reading (if the user turned on background
 * news and the network is unmetered) and checks for breaking news (if alerts are on).
 */
class NewsBackgroundWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SpeedReadApplication).container
        val prefs = container.preferencesRepository.preferences.first()
        if (!prefs.backgroundNews && !prefs.breakingNewsAlerts) return Result.success()
        val sources = prefs.enabledNewsSources
        return try {
            if (prefs.backgroundNews && !container.networkMonitor.isMetered.first()) {
                val result = container.newsPrefetcher.sync(sources)
                Log.i(TAG, "Background news: ${result.downloaded} downloaded, ${result.failed} unavailable")
            } else {
                // Alerts only need the Irish headlines: a few small feed requests.
                container.newsRepository.refresh(sources.filter { it.region == NewsRegion.IRELAND })
            }
            if (prefs.breakingNewsAlerts) {
                val posted = container.breakingNewsAlerts.check(sources)
                if (posted > 0) Log.i(TAG, "Posted $posted breaking news alert(s)")
            }
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Background news failed", e)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "NewsBackgroundWorker"
    }
}

/** Prepares and announces the morning briefing. */
class MorningBriefingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SpeedReadApplication).container
        val prefs = container.preferencesRepository.preferences.first()
        if (!prefs.morningBriefing) return Result.success()
        return try {
            val stories = container.morningBriefing.prepare(prefs.enabledNewsSources)
            Log.i(TAG, "Morning briefing: ${stories.size} stories")
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Morning briefing failed", e)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "MorningBriefingWorker"
    }
}

/** What the periodic sync has to do; [NONE] cancels it. */
data class NewsSyncConfig(val downloads: Boolean, val alerts: Boolean) {
    val enabled: Boolean get() = downloads || alerts

    companion object {
        val NONE = NewsSyncConfig(downloads = false, alerts = false)
    }
}

/** Schedules or cancels the periodic background sync and the daily briefing. */
class NewsBackgroundScheduler(private val context: Context) {

    /**
     * Downloads alone run every [INTERVAL_HOURS] on unmetered networks. Alerts need fresher
     * headlines, so with alerts on the sync runs every [ALERTS_INTERVAL_HOURS] on any network
     * (downloads are still skipped on metered ones).
     */
    fun apply(config: NewsSyncConfig) {
        val workManager = WorkManager.getInstance(context)
        if (!config.enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val hours = if (config.alerts) ALERTS_INTERVAL_HOURS else INTERVAL_HOURS
        val request = PeriodicWorkRequestBuilder<NewsBackgroundWorker>(hours, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (config.alerts) NetworkType.CONNECTED else NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * Schedules the briefing daily at [minuteOfDay] (or cancels it when `null`). The first run
     * waits until the next such time.
     */
    fun applyBriefing(minuteOfDay: Int?, now: LocalDateTime = LocalDateTime.now()) {
        val workManager = WorkManager.getInstance(context)
        if (minuteOfDay == null) {
            workManager.cancelUniqueWork(BRIEFING_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<MorningBriefingWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntil(minuteOfDay, now).toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(BRIEFING_WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
    }

    companion object {
        const val WORK_NAME = "news-background-sync"
        const val BRIEFING_WORK_NAME = "news-morning-briefing"
        const val INTERVAL_HOURS = 4L
        const val ALERTS_INTERVAL_HOURS = 1L

        /** Time from [now] to the next [minuteOfDay] (tomorrow's if today's has passed). */
        fun delayUntil(minuteOfDay: Int, now: LocalDateTime): Duration {
            val today = now.toLocalDate().atTime(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
            val next = if (today.isAfter(now)) today else today.plusDays(1)
            return Duration.between(now, next)
        }
    }
}
