package io.hkmario.monologue

import io.hkmario.monologue.domain.*
import org.junit.Assert.*
import org.junit.Test

class LyricsMatchTest {
    private fun c(artist: String, track: String, sec: Double, synced: Boolean = true, instrumental: Boolean = false) =
        LyricsCandidate(1, track, artist, sec, if(synced) "[00:01.00]line" else null, "line", instrumental)
    private fun pick(title: String, artist: String, sec: Int, vararg cs: LyricsCandidate) = pickLyrics(title, creditedArtists(artist), sec, cs.toList())

    @Test fun featuredArtistIsMatchedByAnyCredit() {
        assertEquals(2, pick("-ERROR", "niki feat. ウォルピスカーター", 234, c("cillia, niki", "-ERROR (feat. 波音リツ)", 240.0), c("niki; ウォルピスカーター", "-ERROR", 236.0))!!.offsetSec)
        assertEquals("Sennzai", pick("Freezing Rose", "葵 feat. Sennzai", 366, c("Sennzai", "Freezing Rose", 366.0))!!.candidate.artist)
        assertEquals("ほたる", pick("Deconstruction Star", "sumijun feat. ほたる", 323, c("ほたる", "Deconstruction Star", 323.0))!!.candidate.artist)
    }
    @Test fun romanisedAliasMatchesOnlyWhenSupplied() {
        val candidate = c("Inori Minase", "スクラップアート", 213.0)
        assertNull(pick("スクラップアート", "水瀬いのり", 212, candidate))
        val aliases = creditedArtists("水瀬いのり") + sortNameVariants("Minase, Inori")
        assertNotNull(pickLyrics("スクラップアート", aliases, 212, listOf(candidate)))
    }
    @Test fun sameTitleByAnotherArtistIsNeverUsed() {
        // LRCLIB has a different "Calling Out" of almost the same length.
        assertNull(pick("Calling Out", "塩ノ谷早耶香", 241, c("Dyro feat. Ryder", "Calling Out", 240.0), c("Various Artists", "Calling Out", 259.0)))
    }
    @Test fun smallSpellingDifferencesStillMatch() {
        assertTrue(sameName("MY FIRST STORY", "My First Story"))
        assertTrue(sameName("Shion Miyawaki", "Miyawaki, Shion".let { sortNameVariants(it)[0] }))
        assertFalse(sameName("Ado", "Aco"))
        assertTrue(sameTitle("1日は25時間。", "1日は25時間"))
    }
    @Test fun lengthOnlyPrefersTheClosestVersionAndInstrumentalsAreSkipped() {
        assertEquals(8, pick("1年2ヶ月20日", "BRIGHT", 323, c("BRIGHT", "1年2ヶ月20日", 145.0), c("BRIGHT", "1年2ヶ月20日", 315.0))!!.offsetSec)
        assertNull(pick("Orange", "とらドラ!", 278, c("とらドラ!", "Orange (off vocal ver.)", 278.0), c("とらドラ!", "Orange", 278.0, instrumental = true)))
        assertNotNull(pick("A Beautiful Song", "帆足圭吾", 245, c("帆足圭吾", "A Beautiful Song", 245.0, synced = false), c("帆足圭吾", "A Beautiful Song", 246.0))!!.candidate.synced)
    }
    @Test fun creditsAreSplitIncludingVoiceActors() {
        assertEquals(listOf("涼風青葉", "高田憂希"), creditedArtists("涼風青葉(CV:高田憂希)"))
        assertEquals("niki", primaryArtist("niki feat. ウォルピスカーター"))
        assertTrue(isUnknownArtist("未知的演出者")); assertTrue(creditedArtists("Unknown Artist").isEmpty())
    }
}
