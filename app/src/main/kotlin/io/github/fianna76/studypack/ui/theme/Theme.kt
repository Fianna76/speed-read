package io.github.fianna76.studypack.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle

/**
 * Material 3 theme following the system light/dark setting. Uses wallpaper-based dynamic colour
 * on Android 12+ and the indigo fallback scheme elsewhere; answer colours are fixed (see [StudyColors]).
 */
@Composable
fun StudyPackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalStudyColors provides if (darkTheme) DarkStudyColors else LightStudyColors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

object StudyTheme {
    val colors: StudyColors
        @Composable @ReadOnlyComposable
        get() = LocalStudyColors.current
}

/** Tabular figures, so counters and scores don't jitter as digits change. */
val TextStyle.tabular: TextStyle get() = copy(fontFeatureSettings = "tnum")
