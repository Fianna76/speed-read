package io.github.fianna76.speedread.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.fianna76.speedread.BuildConfig
import io.github.fianna76.speedread.R
import io.github.fianna76.speedread.data.settings.NewsImages
import io.github.fianna76.speedread.data.settings.ReaderDisplay
import io.github.fianna76.speedread.data.settings.ReaderFont
import io.github.fianna76.speedread.data.settings.ThemeMode
import io.github.fianna76.speedread.data.settings.UserPreferences
import io.github.fianna76.speedread.rsvp.Tokenizer
import io.github.fianna76.speedread.rsvp.WordsPerMinute
import io.github.fianna76.speedread.ui.reader.RsvpWordView
import io.github.fianna76.speedread.news.ArticleUrls
import io.github.fianna76.speedread.news.NewsSources
import io.github.fianna76.speedread.ui.news.labelRes
import io.github.fianna76.speedread.ui.theme.SpeedReadTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val preferences by viewModel.uiState.collectAsStateWithLifecycle()
    val withNotifications = rememberNotificationPermission()
    SettingsContent(
        preferences = preferences,
        onBack = onBack,
        onWordsPerMinuteChange = viewModel::setWordsPerMinute,
        onSmartPacingChange = viewModel::setSmartPacing,
        onRampUpChange = viewModel::setRampUp,
        onFontSizeChange = viewModel::setFontSize,
        onFontChange = viewModel::setFont,
        onHighlightChange = viewModel::setHighlightFocusLetter,
        onShowContextChange = viewModel::setShowContextWhenPaused,
        onVolumeKeysChange = viewModel::setVolumeKeysTurnSentences,
        onThemeModeChange = viewModel::setThemeMode,
        onNewsSourceChange = viewModel::setNewsSourceEnabled,
        newsActions = NewsSettingsActions(
            onRegionChange = viewModel::setNewsSourcesEnabled,
            onImagesChange = viewModel::setNewsImages,
            onAutoContinueChange = viewModel::setAutoContinueNews,
            onBackgroundChange = viewModel::setBackgroundNews,
            onBreakingAlertsChange = withNotifications(viewModel::setBreakingNewsAlerts),
            onMorningBriefingChange = withNotifications(viewModel::setMorningBriefing),
            onBriefingTimeChange = viewModel::setBriefingTime,
        ),
    )
}

/** Newsstand settings callbacks. */
data class NewsSettingsActions(
    val onRegionChange: (sourceIds: List<String>, enabled: Boolean) -> Unit = { _, _ -> },
    val onImagesChange: (NewsImages) -> Unit = {},
    val onAutoContinueChange: (Boolean) -> Unit = {},
    val onBackgroundChange: (Boolean) -> Unit = {},
    val onBreakingAlertsChange: (Boolean) -> Unit = {},
    val onMorningBriefingChange: (Boolean) -> Unit = {},
    val onBriefingTimeChange: (minuteOfDay: Int) -> Unit = {},
)

/**
 * Wraps a notification setting's switch: switching it on first asks for the notification
 * permission (Android 13+). If notifications stay off, the setting stays off and a message
 * says where to turn them on.
 */
@Composable
private fun rememberNotificationPermission(): ((Boolean) -> Unit) -> (Boolean) -> Unit {
    val context = LocalContext.current
    var onGranted by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onGranted?.invoke() else Toast.makeText(context, R.string.settings_notifications_denied, Toast.LENGTH_LONG).show()
        onGranted = null
    }
    return { set ->
        { enabled ->
            val needsPermission = enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                onGranted = { set(true) }
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                set(enabled)
                if (enabled && !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                    Toast.makeText(context, R.string.settings_notifications_denied, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    preferences: UserPreferences?,
    onBack: () -> Unit,
    onWordsPerMinuteChange: (Int) -> Unit,
    onSmartPacingChange: (Boolean) -> Unit,
    onRampUpChange: (Boolean) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onFontChange: (ReaderFont) -> Unit,
    onHighlightChange: (Boolean) -> Unit,
    onShowContextChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    onNewsSourceChange: (sourceId: String, enabled: Boolean) -> Unit = { _, _ -> },
    newsActions: NewsSettingsActions = NewsSettingsActions(),
    onVolumeKeysChange: (Boolean) -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (preferences == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            SectionHeader(stringResource(R.string.settings_section_reading))
            val wpmLabel = stringResource(R.string.reader_wpm, preferences.playback.wordsPerMinute)
            SliderSetting(
                title = stringResource(R.string.settings_speed),
                value = preferences.playback.wordsPerMinute,
                range = WordsPerMinute.MIN..WordsPerMinute.MAX,
                step = WordsPerMinute.STEP,
                valueLabel = { stringResource(R.string.reader_wpm, it) },
                stateDescription = wpmLabel,
                onValueCommitted = onWordsPerMinuteChange,
            )
            SwitchSetting(
                title = stringResource(R.string.settings_smart_pacing),
                summary = stringResource(R.string.settings_smart_pacing_summary),
                checked = preferences.playback.smartPacing,
                onCheckedChange = onSmartPacingChange,
            )
            SwitchSetting(
                title = stringResource(R.string.settings_ramp_up),
                summary = stringResource(R.string.settings_ramp_up_summary),
                checked = preferences.playback.rampUp,
                onCheckedChange = onRampUpChange,
            )

            SectionDivider()
            SectionHeader(stringResource(R.string.settings_section_display))
            FontSizeSetting(display = preferences.display, onValueCommitted = onFontSizeChange)
            ChoiceSetting(
                title = stringResource(R.string.settings_font),
                options = ReaderFont.entries,
                selected = preferences.display.font,
                label = { stringResource(it.labelRes()) },
                onSelect = onFontChange,
            )
            SwitchSetting(
                title = stringResource(R.string.settings_highlight),
                summary = stringResource(R.string.settings_highlight_summary),
                checked = preferences.display.highlightFocusLetter,
                onCheckedChange = onHighlightChange,
            )
            SwitchSetting(
                title = stringResource(R.string.settings_context),
                summary = stringResource(R.string.settings_context_summary),
                checked = preferences.display.showContextWhenPaused,
                onCheckedChange = onShowContextChange,
            )
            SwitchSetting(
                title = stringResource(R.string.settings_volume_keys),
                summary = stringResource(R.string.settings_volume_keys_summary),
                checked = preferences.volumeKeysTurnSentences,
                onCheckedChange = onVolumeKeysChange,
            )

            SectionDivider()
            SectionHeader(stringResource(R.string.settings_section_appearance))
            ChoiceSetting(
                title = stringResource(R.string.settings_theme),
                options = ThemeMode.entries,
                selected = preferences.themeMode,
                label = { stringResource(it.labelRes()) },
                onSelect = onThemeModeChange,
            )

            SectionDivider()
            SectionHeader(stringResource(R.string.settings_section_news))
            AboutText(stringResource(R.string.settings_news_body))
            ChoiceSetting(
                title = stringResource(R.string.settings_news_images),
                options = NewsImages.entries,
                selected = preferences.newsImages,
                label = { stringResource(it.labelRes()) },
                onSelect = newsActions.onImagesChange,
            )
            SwitchSetting(
                title = stringResource(R.string.settings_news_auto_continue),
                summary = stringResource(R.string.settings_news_auto_continue_summary),
                checked = preferences.autoContinueNews,
                onCheckedChange = newsActions.onAutoContinueChange,
            )
            SwitchSetting(
                title = stringResource(R.string.settings_news_background),
                summary = stringResource(R.string.settings_news_background_summary),
                checked = preferences.backgroundNews,
                onCheckedChange = newsActions.onBackgroundChange,
            )
            SwitchSetting(
                title = stringResource(R.string.settings_news_breaking),
                summary = stringResource(R.string.settings_news_breaking_summary),
                checked = preferences.breakingNewsAlerts,
                onCheckedChange = newsActions.onBreakingAlertsChange,
            )
            SwitchSetting(
                title = stringResource(R.string.settings_news_briefing),
                summary = stringResource(R.string.settings_news_briefing_summary),
                checked = preferences.morningBriefing,
                onCheckedChange = newsActions.onMorningBriefingChange,
            )
            if (preferences.morningBriefing) {
                BriefingTimeSetting(preferences.briefingTime, newsActions.onBriefingTimeChange)
            }
            NewsSources.ALL.groupBy { it.region }.forEach { (region, sources) ->
                val allOn = sources.all { preferences.isNewsSourceEnabled(it) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp),
                ) {
                    Text(
                        text = stringResource(region.labelRes()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { newsActions.onRegionChange(sources.map { it.id }, !allOn) }) {
                        Text(
                            stringResource(
                                if (allOn) R.string.settings_news_region_off else R.string.settings_news_region_on,
                                stringResource(region.labelRes()),
                            ),
                        )
                    }
                }
                sources.forEach { source ->
                    val host = ArticleUrls.displayHost(source.homepage)
                    SwitchSetting(
                        title = source.name,
                        summary = source.note?.let { stringResource(R.string.settings_news_source_summary, it, host) } ?: host,
                        checked = preferences.isNewsSourceEnabled(source),
                        onCheckedChange = { enabled -> onNewsSourceChange(source.id, enabled) },
                    )
                }
            }

            SectionDivider()
            SectionHeader(stringResource(R.string.settings_section_about))
            AboutText(stringResource(R.string.settings_about_body))
            AboutText(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME))
            Text(
                text = stringResource(R.string.settings_licenses),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
            AboutText(stringResource(R.string.settings_licenses_body))
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(Modifier.padding(top = 8.dp))
}

@Composable
private fun AboutText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun SwitchSetting(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    )
}

/**
 * A slider that keeps its own value while dragged and persists only when released, so
 * dragging doesn't hammer storage. The local value resets whenever the stored value changes.
 */
@Composable
private fun SliderSetting(
    title: String,
    value: Int,
    range: IntRange,
    step: Int,
    valueLabel: @Composable (Int) -> String,
    stateDescription: String,
    onValueCommitted: (Int) -> Unit,
    onLocalValueChange: (Int) -> Unit = {},
) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueLabel(local.roundToInt()), style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = local,
            onValueChange = {
                local = it
                onLocalValueChange(it.roundToInt())
            },
            onValueChangeFinished = { onValueCommitted(local.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / step - 1,
            colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
            modifier = Modifier.semantics {
                contentDescription = title
                this.stateDescription = stateDescription
            },
        )
    }
}

@Composable
private fun FontSizeSetting(display: ReaderDisplay, onValueCommitted: (Int) -> Unit) {
    var previewSize by remember(display.fontSizeSp) { mutableFloatStateOf(display.fontSizeSp.toFloat()) }
    val sample = stringResource(R.string.settings_preview_word)
    val word = remember(sample) { Tokenizer.tokenize(listOf(sample)).words.firstOrNull() }
    SliderSetting(
        title = stringResource(R.string.settings_font_size),
        value = display.fontSizeSp,
        range = ReaderDisplay.MIN_FONT_SIZE_SP..ReaderDisplay.MAX_FONT_SIZE_SP,
        step = FONT_SIZE_STEP,
        valueLabel = { stringResource(R.string.settings_font_size_value, it) },
        stateDescription = stringResource(R.string.settings_font_size_value, display.fontSizeSp),
        onValueCommitted = onValueCommitted,
        onLocalValueChange = { previewSize = it.toFloat() },
    )
    RsvpWordView(
        word = word,
        display = display.copy(fontSizeSp = previewSize.roundToInt()),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun BriefingTimeSetting(minuteOfDay: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val title = stringResource(R.string.settings_news_briefing_time)
    Box {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(formatTimeOfDay(minuteOfDay)) },
            modifier = Modifier.clickable(onClickLabel = title) { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            UserPreferences.BRIEFING_TIMES.forEach { time ->
                DropdownMenuItem(
                    text = { Text(formatTimeOfDay(time)) },
                    onClick = {
                        open = false
                        onChange(time)
                    },
                    trailingIcon = if (time == minuteOfDay) {
                        { Icon(painterResource(R.drawable.ic_check), contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

private fun formatTimeOfDay(minuteOfDay: Int): String =
    LocalTime.of(minuteOfDay / 60, minuteOfDay % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

@Composable
private fun <T> ChoiceSetting(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) { Text(label(option)) }
            }
        }
    }
}

private const val FONT_SIZE_STEP = 4

private fun ReaderFont.labelRes(): Int = when (this) {
    ReaderFont.SANS -> R.string.font_sans
    ReaderFont.SERIF -> R.string.font_serif
    ReaderFont.MONOSPACE -> R.string.font_monospace
}

private fun NewsImages.labelRes(): Int = when (this) {
    NewsImages.ALWAYS -> R.string.settings_news_images_always
    NewsImages.UNMETERED_ONLY -> R.string.settings_news_images_unmetered
    NewsImages.NEVER -> R.string.settings_news_images_never
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@Preview(showBackground = true, widthDp = 400, heightDp = 1200)
@Composable
private fun SettingsPreview() {
    SpeedReadTheme(dynamicColor = false) {
        SettingsContent(
            preferences = UserPreferences(),
            onBack = {}, onWordsPerMinuteChange = {}, onSmartPacingChange = {}, onRampUpChange = {},
            onFontSizeChange = {}, onFontChange = {}, onHighlightChange = {}, onShowContextChange = {},
            onThemeModeChange = {},
        )
    }
}
