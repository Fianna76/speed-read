package io.github.fianna76.speedread.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import io.github.fianna76.speedread.MainActivity
import io.github.fianna76.speedread.R
import io.github.fianna76.speedread.data.news.background.NewsNotifier
import io.github.fianna76.speedread.news.FeedItem
import io.github.fianna76.speedread.news.StoryGroup

/** Posts breaking-news and briefing notifications; silently does nothing without permission. */
class NewsNotifications(context: Context) : NewsNotifier {

    private val context = context.applicationContext
    private val manager = NotificationManagerCompat.from(this.context)

    override fun breaking(story: StoryGroup<FeedItem>, outlets: List<String>) {
        if (!canNotify()) return
        createChannels()
        val lead = story.lead
        val notification = NotificationCompat.Builder(context, CHANNEL_BREAKING)
            .setSmallIcon(R.drawable.ic_newspaper)
            .setContentTitle(lead.title)
            .setContentText(outlets.joinToString(" · "))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(listOf(outlets.joinToString(" · "), lead.summary).filter { it.isNotBlank() }.joinToString("\n")),
            )
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(lead.publishedAt ?: System.currentTimeMillis())
            .setShowWhen(true)
            .setContentIntent(pendingIntent(openIntent(MainActivity.ACTION_OPEN_NEWS, lead.canonicalUrl), lead.canonicalUrl.hashCode()))
            .setAutoCancel(true)
            .setGroup(GROUP_BREAKING)
            .build()
        post(lead.canonicalUrl.hashCode(), notification)
    }

    override fun briefing(stories: List<FeedItem>) {
        if (!canNotify()) return
        createChannels()
        val title = context.resources.getQuantityString(R.plurals.notification_briefing_title, stories.size, stories.size)
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        stories.forEach { style.addLine(it.title) }
        val notification = NotificationCompat.Builder(context, CHANNEL_BRIEFING)
            .setSmallIcon(R.drawable.ic_newspaper)
            .setContentTitle(title)
            .setContentText(stories.first().title)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(pendingIntent(openIntent(MainActivity.ACTION_OPEN_BRIEFING, null), BRIEFING_ID))
            .addAction(
                R.drawable.ic_play_arrow,
                context.getString(R.string.notification_briefing_play),
                pendingIntent(openIntent(MainActivity.ACTION_PLAY_BRIEFING, null), BRIEFING_ID + 1),
            )
            .setAutoCancel(true)
            .build()
        post(BRIEFING_ID, notification)
    }

    /** The briefing has been opened in the app: its notification is no longer needed. */
    fun cancelBriefing() = manager.cancel(BRIEFING_ID)

    fun canNotify(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun post(id: Int, notification: android.app.Notification) {
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    private fun createChannels() {
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_BREAKING, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.notification_channel_breaking))
                    .setDescription(context.getString(R.string.notification_channel_breaking_description))
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_BRIEFING, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.notification_channel_briefing))
                    .setDescription(context.getString(R.string.notification_channel_briefing_description))
                    .build(),
            ),
        )
    }

    private fun openIntent(action: String, url: String?): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(action)
            .apply { if (url != null) data = url.toUri() }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    private fun pendingIntent(intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    companion object {
        const val CHANNEL_BREAKING = "breaking_news"
        const val CHANNEL_BRIEFING = "morning_briefing"
        private const val GROUP_BREAKING = "breaking_news"
        private const val BRIEFING_ID = 1
    }
}
