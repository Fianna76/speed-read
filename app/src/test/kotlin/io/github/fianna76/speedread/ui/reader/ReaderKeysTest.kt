package io.github.fianna76.speedread.ui.reader

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderKeysTest {

    @Test
    fun `keyboards, page turners and headset buttons control playback`() {
        assertEquals(ReaderKeyAction.PLAY_PAUSE, readerKeyAction(Key.Spacebar, volumeKeys = false))
        assertEquals(ReaderKeyAction.PLAY_PAUSE, readerKeyAction(Key.MediaPlayPause, volumeKeys = false))
        assertEquals(ReaderKeyAction.NEXT_SENTENCE, readerKeyAction(Key.PageDown, volumeKeys = false))
        assertEquals(ReaderKeyAction.PREVIOUS_SENTENCE, readerKeyAction(Key.DirectionLeft, volumeKeys = false))
        assertEquals(ReaderKeyAction.FASTER, readerKeyAction(Key.DirectionUp, volumeKeys = false))
        assertEquals(ReaderKeyAction.NEXT_CHAPTER, readerKeyAction(Key.RightBracket, volumeKeys = false))
        assertNull(readerKeyAction(Key.A, volumeKeys = true))
    }

    @Test
    fun `volume keys turn sentences only when switched on`() {
        assertNull(readerKeyAction(Key.VolumeUp, volumeKeys = false))
        assertEquals(ReaderKeyAction.NEXT_SENTENCE, readerKeyAction(Key.VolumeUp, volumeKeys = true))
        assertEquals(ReaderKeyAction.PREVIOUS_SENTENCE, readerKeyAction(Key.VolumeDown, volumeKeys = true))
    }
}
