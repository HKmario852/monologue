package io.hkmario.monologue

import io.hkmario.monologue.domain.*
import org.junit.Assert.*
import org.junit.Test

class LyricsMatchTest {
    private fun c(artist: String, track: String, sec: Double, synced: Boolean = true, instrumental: Boolean = false) =
        LyricsCandidate(1, track, artist, sec, if(synced) "[00:01.00]line" else null, "line", instrumental)

    @Test fun featuredArtistIsMatchedByMainArtist() {
        val pick = pickLyrics("-ERROR", "niki feat. ウォルピスカーター", 234, listOf(c("cillia, niki", "-ERROR (feat. 波音リツ)", 240.0), c("niki; ウォルピスカーター", "-ERROR", 236.0)))
        assertEquals("niki; ウォルピスカーター", pick!!.candidate.artist)
        assertEquals(2, pick.offsetSec)
    }
    @Test fun slightlyDifferentLengthIsAcceptedAndReported() {
        val pick = pickLyrics("1年2ヶ月20日", "BRIGHT", 323, listOf(c("BRIGHT", "1年2ヶ月20日", 145.0), c("BRIGHT", "1年2ヶ月20日", 315.0)))
        assertEquals(8, pick!!.offsetSec)
    }
    @Test fun instrumentalVersionsAndOtherArtistsAreRejected() {
        assertNull(pickLyrics("Orange", "とらドラ!", 278, listOf(c("とらドラ!", "Orange (off vocal ver.)", 278.0), c("とらドラ!", "Orange", 278.0, instrumental = true))))
        assertNull(pickLyrics("Song", "A", 200, listOf(c("Someone Else", "Song", 200.0))))
    }
    @Test fun lengthOnlyPrefersTheClosestVersionAndNeverRejects() {
        val pick = pickLyrics("Song", "A", 200, listOf(c("A", "Song", 260.0)))
        assertEquals(60, pick!!.offsetSec)
        assertEquals(245.0, pickLyrics("Song", "A", 240, listOf(c("A", "Song", 300.0), c("A", "Song", 245.0)))!!.candidate.durationSec, 0.0)
    }
    @Test fun syncedLyricsWinOverPlain() {
        val pick = pickLyrics("A Beautiful Song", "帆足圭吾", 245, listOf(c("帆足圭吾", "A Beautiful Song", 245.0, synced = false), c("帆足圭吾", "A Beautiful Song", 246.0)))
        assertNotNull(pick!!.candidate.synced)
    }
    @Test fun placeholderArtistsCountAsUnknown() {
        assertTrue(isUnknownArtist("未知的演出者")); assertTrue(isUnknownArtist("Unknown Artist")); assertFalse(isUnknownArtist("Ado"))
    }
    @Test fun mainArtistIsTheFirstCredit() {
        assertEquals("niki", primaryArtist("niki feat. ウォルピスカーター"))
        assertEquals("逢坂大河", primaryArtist("逢坂大河・櫛枝実乃梨・川嶋亜美"))
        assertEquals("MY FIRST STORY", primaryArtist("MY FIRST STORY feat. chelly"))
    }
}
