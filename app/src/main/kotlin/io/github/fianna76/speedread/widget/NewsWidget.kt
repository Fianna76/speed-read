package io.github.fianna76.speedread.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.fianna76.speedread.MainActivity
import io.github.fianna76.speedread.R
import io.github.fianna76.speedread.SpeedReadApplication

data class WidgetHeadline(val title: String, val sourceName: String, val url: String)

data class WidgetData(val irish: Boolean, val headlines: List<WidgetHeadline>)

/**
 * Home-screen widget with the latest Irish headlines (top headlines when no Irish outlet is
 * on). It shows the cached headlines and updates whenever they are refreshed, in the app or in
 * the background; it never fetches on its own. Tapping a headline previews it in the Newsstand.
 */
class NewsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = (context.applicationContext as SpeedReadApplication).container.widgetHeadlines()
        provideContent { GlanceTheme { WidgetContent(context, data) } }
    }

    @Composable
    private fun WidgetContent(context: Context, data: WidgetData) {
        Column(
            GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(20.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = context.getString(if (data.irish) R.string.widget_title else R.string.widget_title_all),
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GlanceTheme.colors.onSurface),
                modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(openNews(context, url = null))),
            )
            Spacer(GlanceModifier.height(8.dp))
            if (data.headlines.isEmpty()) {
                Text(
                    text = context.getString(R.string.widget_empty),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
                    modifier = GlanceModifier.clickable(actionStartActivity(openNews(context, url = null))),
                )
            } else {
                LazyColumn {
                    items(data.headlines, itemId = { it.url.hashCode().toLong() }) { headline ->
                        Column(
                            GlanceModifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable(actionStartActivity(openNews(context, headline.url))),
                        ) {
                            Text(
                                text = headline.sourceName,
                                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                            )
                            Text(
                                text = headline.title,
                                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun openNews(context: Context, url: String?): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_NEWS)
            .apply { if (url != null) data = url.toUri() }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}

class NewsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = NewsWidget()
}
