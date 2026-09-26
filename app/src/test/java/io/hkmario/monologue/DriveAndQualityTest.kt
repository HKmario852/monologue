package io.hkmario.monologue

import io.hkmario.monologue.cloud.isAudioFile
import io.hkmario.monologue.domain.AudioChoice
import io.hkmario.monologue.domain.describe
import org.junit.Assert.*
import org.junit.Test

class DriveAndQualityTest {
    @Test fun mp3UploadedWithoutAudioMimeIsStillMusic() {
        assertTrue(isAudioFile("Song.MP3","application/octet-stream"))
        assertTrue(isAudioFile("track","audio/mpeg"))
        assertFalse(isAudioFile("cover.jpg","image/jpeg"))
        assertFalse(isAudioFile("mp3","application/octet-stream"))
    }
    @Test fun qualityDescribesOnlyWhatTheSourceReported() {
        assertEquals("OPUS · 160 kbps",AudioChoice("https://a","audio/opus; codecs=opus",160000).describe())
        assertEquals("FLAC · 44.1 kHz · 16-bit · 來源標示無損",AudioChoice("https://a","audio/flac",0,44100,16,true).describe())
    }
}
