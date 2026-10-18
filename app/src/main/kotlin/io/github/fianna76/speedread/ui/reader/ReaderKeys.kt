package io.github.fianna76.speedread.ui.reader

import androidx.compose.ui.input.key.Key

/** What a hardware key does in the reader. */
enum class ReaderKeyAction { PLAY_PAUSE, PREVIOUS_SENTENCE, NEXT_SENTENCE, SLOWER, FASTER, PREVIOUS_CHAPTER, NEXT_CHAPTER }

/**
 * Keyboards, Bluetooth page turners (which send page or arrow keys) and headset buttons:
 * - Space, Enter and media play/pause: play or pause;
 * - left, page up and media previous: previous sentence; right, page down, media next: next;
 * - up and down: faster and slower;
 * - [ and ]: previous and next chapter;
 * - volume down and up: previous and next sentence, only if [volumeKeys] is on, since they
 *   otherwise change the volume.
 */
fun readerKeyAction(key: Key, volumeKeys: Boolean): ReaderKeyAction? = when (key) {
    Key.Spacebar, Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause ->
        ReaderKeyAction.PLAY_PAUSE
    Key.DirectionLeft, Key.PageUp, Key.MediaPrevious, Key.MediaRewind -> ReaderKeyAction.PREVIOUS_SENTENCE
    Key.DirectionRight, Key.PageDown, Key.MediaNext, Key.MediaFastForward -> ReaderKeyAction.NEXT_SENTENCE
    Key.DirectionUp -> ReaderKeyAction.FASTER
    Key.DirectionDown -> ReaderKeyAction.SLOWER
    Key.LeftBracket -> ReaderKeyAction.PREVIOUS_CHAPTER
    Key.RightBracket -> ReaderKeyAction.NEXT_CHAPTER
    Key.VolumeDown -> if (volumeKeys) ReaderKeyAction.PREVIOUS_SENTENCE else null
    Key.VolumeUp -> if (volumeKeys) ReaderKeyAction.NEXT_SENTENCE else null
    else -> null
}
