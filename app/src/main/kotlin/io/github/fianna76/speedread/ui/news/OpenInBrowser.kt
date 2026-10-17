package io.github.fianna76.speedread.ui.news

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import io.github.fianna76.speedread.R
import io.github.fianna76.speedread.news.FeedItem

/**
 * Opens [url] in a Custom Tab: the user's browser, with their sign-ins and settings, shown on
 * top of the app so Back returns here. Falls back to any app that handles web links.
 */
fun Context.openInBrowser(url: String) {
    try {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(this, url.toUri())
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.news_no_browser, Toast.LENGTH_SHORT).show()
    }
}

/** Opens the Android share sheet with the story's headline and link. */
fun Context.shareStory(item: FeedItem) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, item.title)
        .putExtra(Intent.EXTRA_TEXT, "${item.title}\n${item.url}")
    startActivity(Intent.createChooser(send, getString(R.string.news_share)))
}
