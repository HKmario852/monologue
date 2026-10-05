package io.hkmario.monologue

import io.hkmario.monologue.domain.*
import kotlinx.collections.immutable.persistentMapOf
import org.junit.Assert.*
import org.junit.Test

class LyricsImprovementsTest {
    @Test fun searchTitleDropsBracketedNotes() {
        assertEquals("夜に駆ける", searchTitleWithoutNotes("夜に駆ける (Single Ver.)"))
        assertEquals("スクラップアート", searchTitleWithoutNotes("スクラップアート(TVアニメ「デッドマウント・デスプレイ」オープニングテーマ)"))
        assertEquals("Song", searchTitleWithoutNotes("Song feat. Someone"))
        assertEquals("Colors", searchTitleWithoutNotes("Colors - TV size ver."))
        assertNull(searchTitleWithoutNotes("Lemon"))
        assertEquals("カラフル", searchTitleWithoutNotes("カラフル(劇場版 魔法少女まどか☆マギカ[新編]叛逆の物語 OP)"))
        assertTrue(sameTitle("カラフル(劇場版 魔法少女まどか☆マギカ[新編]叛逆の物語 OP)", "カラフル"))
    }

    @Test fun embeddedTranslationIsSplitWhenMostTimestampsHaveTwoLines() {
        val lrc = (1..10).joinToString("\n") { i -> "[00:%02d.00]日本語の歌詞$i\n[00:%02d.00]中文歌詞$i".format(i, i) }
        val (original, translation) = splitEmbeddedTranslation(lrc)!!
        assertEquals("日本語の歌詞1", Lrc.parse(original).first().text)
        assertEquals("中文歌詞1", Lrc.parse(translation).first().text)
        assertEquals(10, Lrc.parse(original).size)
    }

    @Test fun ordinaryLyricsAreNotSplit() {
        val lrc = (1..10).joinToString("\n") { i -> "[00:%02d.00]line $i".format(i) }
        assertNull(splitEmbeddedTranslation(lrc))
    }

    @Test fun scoringPrefersExactTitleThenSyncedThenClosestLength() {
        fun c(track: String, sec: Double, synced: Boolean) = LyricsCandidate(1, track, "ClariS", sec, if(synced) "[00:01.00]x" else null, "x")
        assertEquals("[00:01.00]x", pickLyrics("カラフル", listOf("ClariS"), 250, listOf(c("カラフル", 250.0, false), c("カラフル", 251.0, true)))!!.text)
        assertEquals(250.0, pickLyrics("カラフル", listOf("ClariS"), 250, listOf(c("カラフル", 290.0, true), c("カラフル", 250.0, true)))!!.candidate.durationSec, 0.0)
    }

    @Test fun providerListMigratesOldSettingsAndAddsNewSourcesOff() {
        val old = lyricsProviders(AppSettingsUiState(persistentMapOf("neteaseLyrics" to "true", "lyricsOrder" to "netease")))
        assertEquals(listOf("netease", "lrclib", "jlyric", "utaten", "vocadb", "bahamut", "kanogoma"), old.map { it.info.id })
        assertEquals(listOf(true, true, false, false, false, false, false), old.map { it.enabled })
        val fresh = lyricsProviders(AppSettingsUiState())
        assertEquals(listOf(true, false, false, false, false, false, false), fresh.map { it.enabled })
        assertEquals("lrclib:1,netease:0,jlyric:0,utaten:0,vocadb:0,bahamut:0,kanogoma:0", encodeLyricsProviders(fresh))
        val stored = lyricsProviders(AppSettingsUiState(persistentMapOf("lyricsProviders" to "utaten:1,lrclib:1")))
        assertEquals(listOf("utaten", "lrclib", "netease", "jlyric", "vocadb", "bahamut", "kanogoma"), stored.map { it.info.id })
    }
}
