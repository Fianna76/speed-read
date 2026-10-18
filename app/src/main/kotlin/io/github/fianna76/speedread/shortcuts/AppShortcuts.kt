package io.github.fianna76.speedread.shortcuts

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

    fun publish(context: Context) {
        val shortcuts = listOf(
            shortcut(context, "continue", R.string.shortcut_continue, R.drawable.ic_shortcut_book, MainActivity.ACTION_CONTINUE_READING),
            shortcut(context, "briefing", R.string.shortcut_briefing, R.drawable.ic_shortcut_play, MainActivity.ACTION_PLAY_BRIEFING),
            shortcut(context, "clipboard", R.string.shortcut_clipboard, R.drawable.ic_shortcut_clipboard, MainActivity.ACTION_READ_CLIPBOARD),
        )
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
    }

    private fun shortcut(context: Context, id: String, label: Int, icon: Int, action: String) =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(context.getString(label))
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(Intent(context, MainActivity::class.java).setAction(action))
            .build()
}

/** Quick Settings tile: speed-read whatever is on the clipboard. */
class ClipboardTileService : TileService() {

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
