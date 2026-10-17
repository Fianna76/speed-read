package io.github.fianna76.speedread.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.fianna76.speedread.MainActivity
import io.github.fianna76.speedread.news.StoryGroup
import io.github.fianna76.speedread.testing.TestNews
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class NewsNotificationsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val story = TestNews.item("rte", "fire", title = "Major fire closes Dublin Port")

    @Test
    fun `nothing is posted without the notification permission`() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        NewsNotifications(context).breaking(StoryGroup(story, emptyList()), listOf("RTÉ News"))

        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    @Test
    fun `a breaking story opens its preview, and the briefing offers to play`() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifications = NewsNotifications(context)

        notifications.breaking(StoryGroup(story, emptyList()), listOf("RTÉ News", "TheJournal.ie"))
        notifications.briefing(listOf(story, TestNews.item("bbc", "b")))

        val posted = shadowOf(manager).allNotifications
        assertEquals(2, posted.size)
        val breaking = posted.single { it.channelId == NewsNotifications.CHANNEL_BREAKING }
        assertEquals("Major fire closes Dublin Port", breaking.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertEquals("RTÉ News · TheJournal.ie", breaking.extras.getString(NotificationCompat.EXTRA_TEXT))
        val open = shadowOf(breaking.contentIntent).savedIntent
        assertEquals(MainActivity.ACTION_OPEN_NEWS, open.action)
        assertEquals(story.canonicalUrl, open.dataString)

        val briefing = posted.single { it.channelId == NewsNotifications.CHANNEL_BRIEFING }
        assertEquals("Your briefing: 2 stories", briefing.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertEquals(MainActivity.ACTION_OPEN_BRIEFING, shadowOf(briefing.contentIntent).savedIntent.action)
        assertEquals(MainActivity.ACTION_PLAY_BRIEFING, shadowOf(briefing.actions.single().actionIntent).savedIntent.action)

        notifications.cancelBriefing()
        assertEquals(listOf(NewsNotifications.CHANNEL_BREAKING), shadowOf(manager).allNotifications.map { it.channelId })
    }
}
