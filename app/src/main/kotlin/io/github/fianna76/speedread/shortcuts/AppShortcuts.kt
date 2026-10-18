package io.github.fianna76.speedread.shortcuts

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.fianna76.speedread.MainActivity
import io.github.fianna76.speedread.R

/**
 * Launcher shortcuts (long-press the app icon): Continue reading, Play briefing and Speed-read
 * clipboard. They are published at start-up rather than declared in XML, because static
 * shortcuts must name the package, which differs between debug and release builds.
 */
object AppShortcuts {

    /** Shortcut ids by the intent action each one sends. */
    private val IDS = mapOf(
        MainActivity.ACTION_CONTINUE_READING to "continue",
        MainActivity.ACTION_PLAY_BRIEFING to "briefing",
        MainActivity.ACTION_READ_CLIPBOARD to "clipboard",
    )

    fun publish(context: Context) {
        val shortcuts = listOf(
            shortcut(context, MainActivity.ACTION_CONTINUE_READING, R.string.shortcut_continue, R.drawable.ic_shortcut_book),
            shortcut(context, MainActivity.ACTION_PLAY_BRIEFING, R.string.shortcut_briefing, R.drawable.ic_shortcut_play),
            shortcut(context, MainActivity.ACTION_READ_CLIPBOARD, R.string.shortcut_clipboard, R.drawable.ic_shortcut_clipboard),
        )
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    /** Tells the launcher a shortcut's action was used (from the shortcut or elsewhere), to rank shortcuts. */
    fun reportUsed(context: Context, action: String?) {
        val id = IDS[action] ?: return
        runCatching { ShortcutManagerCompat.reportShortcutUsed(context, id) }
    }

    private fun shortcut(context: Context, action: String, label: Int, icon: Int) =
        ShortcutInfoCompat.Builder(context, IDS.getValue(action))
            .setShortLabel(context.getString(label))
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(Intent(context, MainActivity::class.java).setAction(action))
            .build()
}

/** Quick Settings tile: speed-read whatever is on the clipboard. */
class ClipboardTileService : TileService() {

    // The Intent overload is deprecated but is the only one before Android 14.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_READ_CLIPBOARD)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
