package io.github.fianna76.speedread.rsvp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Snapshot of the player, observed by the UI. */
data class PlayerState(
    val text: TokenizedText = TokenizedText.EMPTY,
    val index: Int = 0,
    val isPlaying: Boolean = false,
    val settings: PlaybackSettings = PlaybackSettings(),
) {
    val word: RsvpWord? get() = text.words.getOrNull(index)

    /** Words shown together from [index] (1 unless chunk mode is on). */
    val span: Int get() = if (text.isEmpty()) 1 else Chunking.span(text, index, settings.chunkSize)

    /** What is on screen: the current word, or the chunk starting at it. */
    val displayWord: RsvpWord? get() = if (index in 0 until text.size) Chunking.join(text, index, span) else null

    /** Fraction of the loaded text already shown, in `[0, 1]`. */
    val progress: Float get() = if (text.size <= 1) 0f else index.toFloat() / (text.size - 1)

    val isAtEnd: Boolean get() = text.isEmpty() || index >= text.size - 1
}

/**
 * Drives RSVP playback of one [TokenizedText]: shows each word for its paced duration and
 * exposes the current word through [state].
 *
 * Timing is computed against absolute deadlines from a monotonic [clock], so small scheduling
 * delays don't accumulate into a slower-than-requested speed, while a long stall (e.g. a GC
 * pause) never causes a burst of words to "catch up".
 *
 * The player is not thread-safe: call it from the thread of [scope] (the main thread in the app).
 *
 * @param onEndReached invoked after the last word has been shown during playback.
 */
class RsvpPlayer(
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val onEndReached: () -> Unit = {},
) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var playbackJob: Job? = null

    /** Replaces the text and moves to [startIndex]. Playback stops. */
    fun load(text: TokenizedText, startIndex: Int = 0) {
        stopPlayback()
        _state.update { it.copy(text = text, index = clampIndex(text, startIndex), isPlaying = false) }
    }

    fun updateSettings(settings: PlaybackSettings) {
        _state.update { it.copy(settings = settings) }
    }

    fun play() {
        val current = _state.value
        if (playbackJob?.isActive == true || current.text.isEmpty()) return
        _state.update { it.copy(isPlaying = true) }
        playbackJob = scope.launch { runPlayback() }
    }

    fun pause() {
        stopPlayback()
        _state.update { it.copy(isPlaying = false) }
    }

    fun togglePlayPause() {
        if (_state.value.isPlaying) pause() else play()
    }

    /** Moves to [index], continuing playback if it was running. */
    fun seekTo(index: Int) {
        val current = _state.value
        if (current.text.isEmpty()) return
        val wasPlaying = current.isPlaying
        stopPlayback()
        _state.update { it.copy(index = clampIndex(it.text, index), isPlaying = false) }
        if (wasPlaying) play()
    }

    /**
     * Rewinds to the start of the current sentence, or to the previous sentence when already at
     * (or, while playing, just past) the start of the current one — like a music player's
     * "previous" button. Returns `false` if already at the very first word.
     */
    fun skipBackward(): Boolean {
        val current = _state.value
        val text = current.text
        if (text.isEmpty() || current.index == 0) return false
        val sentenceStart = text.sentenceStartOf(current.index)
        // While playing, a few words have usually flashed by before the tap registers.
        val grace = if (current.isPlaying) PLAYING_REWIND_GRACE_WORDS else 1
        val target = if (current.index - sentenceStart < grace && sentenceStart > 0) {
            text.sentenceStartOf(sentenceStart - 1)
        } else {
            sentenceStart
        }
        seekTo(target)
        return true
    }

    /** Jumps to the start of the next sentence. Returns `false` when in the last sentence. */
    fun skipForward(): Boolean {
        val current = _state.value
        val next = current.text.nextSentenceStart(current.index) ?: return false
        seekTo(next)
        return true
    }

    private suspend fun runPlayback() {
        var wordsShown = 0
        var deadline = clock()
        while (true) {
            val current = _state.value
            if (current.word == null) break
            val span = current.span
            // A chunk lasts as long as its words would have, one at a time.
            val duration = (0 until span).sumOf { Pacing.durationMillis(current.text[current.index + it], current.settings, wordsShown + it) }
            val now = clock()
            // Absorb small scheduling delays, but never shorten a word by more than a quarter.
            deadline = maxOf(deadline, now - duration / 4) + duration
            delay(deadline - now)
            wordsShown += span
            if (current.index + span > current.text.size - 1) break
            _state.update { it.copy(index = it.index + span) }
        }
        playbackJob = null
        _state.update { it.copy(isPlaying = false) }
        onEndReached()
    }

    private fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
    }

    private fun clampIndex(text: TokenizedText, index: Int): Int =
        if (text.isEmpty()) 0 else index.coerceIn(0, text.size - 1)

    private companion object {
        const val PLAYING_REWIND_GRACE_WORDS = 3
    }
}
