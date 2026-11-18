package io.github.fianna76.studypack.importing

import io.github.fianna76.studypack.data.pdf.NoteTitles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportRulesTest {

    @Test
    fun sniffsByContentNotByName() {
        assertEquals(FileKind.PDF, FileSniffer.kind("%PDF-1.7\n...".toByteArray(), "notes.json", "application/json"))
        assertEquals(FileKind.PACK, FileSniffer.kind("  {\"format\":1}".toByteArray(), "file.bin", "application/octet-stream"))
        assertEquals(FileKind.PACK, FileSniffer.kind("﻿{}".toByteArray(), null, "text/plain"))
        assertEquals(FileKind.UNKNOWN, FileSniffer.kind("hello".toByteArray(), "notes.txt", "text/plain"))
        // A PDF header after some junk bytes still counts.
        assertEquals(FileKind.PDF, FileSniffer.kind("\r\n\u0000%PDF-1.4".toByteArray(), "x", null))
    }

    @Test
    fun linksNotesByPackListFirst() {
        val packs = listOf(PackNotes("EE5105", listOf("ecg-overview.pdf")), PackNotes("EE5110", emptyList()))
        assertEquals("EE5105", NoteLinking.moduleFor("ECG-Overview.PDF", packs, listOf("EE5105", "EE5110")))
    }

    @Test
    fun linksNotesByModuleCodePrefix() {
        val codes = listOf("EE5105", "EE5105A", "CS101")
        assertEquals("EE5105", NoteLinking.moduleFor("EE5105-ecg-amp-overview.pdf", emptyList(), codes))
        assertEquals("EE5105A", NoteLinking.moduleFor("ee5105a_notes.pdf", emptyList(), codes))
        assertEquals("CS101", NoteLinking.moduleFor("CS101.pdf", emptyList(), codes))
        assertNull(NoteLinking.moduleFor("EE51050-other.pdf", emptyList(), codes))
        assertNull(NoteLinking.moduleFor("lecture-notes.pdf", emptyList(), codes))
    }

    @Test
    fun displayTitles() {
        assertEquals("Ecg amp overview", NoteTitles.displayTitle("EE5105-ecg-amp-overview.pdf", null, "EE5105"))
        assertEquals("ECG Amplifier Overview", NoteTitles.displayTitle("EE5105-ecg-amp-overview.pdf", " ECG Amplifier Overview ", "EE5105"))
        assertEquals("Week 3 notes", NoteTitles.displayTitle("week_3 notes.pdf", "", null))
        assertEquals("EE5105", NoteTitles.displayTitle("EE5105.pdf", null, "EE5105"))
    }
}
