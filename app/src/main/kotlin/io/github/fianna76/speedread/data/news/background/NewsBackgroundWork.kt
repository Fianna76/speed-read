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
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Runs [NewsPrefetcher] when the user has turned on background news. */
class NewsBackgroundWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SpeedReadApplication).container
        val prefs = container.preferencesRepository.preferences.first()
        if (!prefs.backgroundNews) return Result.success()
        return try {
            val result = container.newsPrefetcher.sync(prefs.enabledNewsSources)
            Log.i(TAG, "Background news: ${result.downloaded} downloaded, ${result.failed} unavailable")
            container.onHeadlinesUpdated()
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

/** Schedules or cancels the periodic background sync. */
class NewsBackgroundScheduler(private val context: Context) {

    fun apply(enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<NewsBackgroundWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }

    companion object {
        const val WORK_NAME = "news-background-sync"
        const val INTERVAL_HOURS = 4L
    }
}
