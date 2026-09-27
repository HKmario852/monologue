package io.hkmario.monologue

import com.google.mlkit.nl.translate.TranslateLanguage
import io.hkmario.monologue.cloud.lyricsLanguage
import io.hkmario.monologue.domain.*
import org.junit.Assert.*
import org.junit.Test

class LyricsSourcesTest {
    @Test fun netEaseCreditLinesAreRemoved() {
        val lrc = "[00:00.000] 作词 : 唐恬\n[00:00.463] 作曲 : 钱雷\n[00:12.00]どうしてここにいるの"
        assertEquals("[00:12.00]どうしてここにいるの", stripCreditLines(lrc))
    }
    @Test fun romajiAttachesByTimestampAndKeepsTranslation() {
        val original = Lrc.align(Lrc.parse("[00:12.00]制裁か救済か"), Lrc.parse("[00:12.00]是制裁还是救赎"))
        val lines = Lrc.alignRomaji(original, Lrc.parse("[00:12.10]seisai ka kyuusai ka"))
        assertEquals("seisai ka kyuusai ka", lines.single().romaji)
        assertEquals("是制裁还是救赎", lines.single().translation)
    }
    @Test fun plainLyricsPairInOrderOnlyWhenBothSidesAreUntimedAndEqualLength() {
        val plain = Lrc.parse("どうしてここにいるの\n制裁か救済か")
        assertEquals("為什麼在這裡", Lrc.align(plain, Lrc.parse("為什麼在這裡\n制裁還是救贖"))[0].translation)
        assertNull(Lrc.align(plain, Lrc.parse("只有一行"))[0].translation)
        assertNull(Lrc.align(Lrc.parse("[00:01.00]a"), Lrc.parse("b"))[0].translation)
    }
    @Test fun scriptVariantOfArtistNeedsMatchingLength() {
        val c = LyricsCandidate(1, "スクラップアート", "水濑いのり", 212.0, "[00:00.00]x", null)
        assertNotNull(pickLyrics("スクラップアート", listOf("水瀬いのり"), 212, listOf(c)))
        assertNull(pickLyrics("スクラップアート", listOf("水瀬いのり"), 240, listOf(c)))
    }
    @Test fun lyricLanguageIsGuessedFromScript() {
        assertEquals(TranslateLanguage.JAPANESE, lyricsLanguage(listOf("どうしてここにいるの")))
        assertEquals(TranslateLanguage.CHINESE, lyricsLanguage(listOf("是制裁还是救赎")))
        assertEquals(TranslateLanguage.ENGLISH, lyricsLanguage(listOf("Calling out your name")))
    }
}
