package io.hkmario.monologue

import io.hkmario.monologue.domain.*
import org.junit.Assert.*
import org.junit.Test

class RomajiLyricsTest {
    private fun timed(lines: List<String>) = lines.mapIndexed { i, text -> "[00:%02d.00]%s".format(i, text) }.joinToString("\n")

    private val romaji = timed(listOf(
        "Asa no hikari ga mado wo terasu", "Kimi no koe ga kikoeru yo", "Tooku made hashitte yuku",
        "Yume no naka de matte iru", "Sora wa aoku hirogatte", "Kokoro no oto wo shinjite", "Motto tsuyoku naritai",
        "Kyou mo ashita mo issho ni", "Road of resistance, we are one"))
    private val english = timed(listOf(
        "The morning light is shining through the window", "I can hear your voice calling out to me",
        "We keep running till the night is over", "Hold on to the dream that we believed in",
        "Every step we take brings us closer home", "Never let the fire in your heart burn out"))
    private val japanese = timed(listOf(
        "朝の光が窓を照らす", "君の声が聞こえるよ", "遠くまで走ってゆく", "夢の中で待っている", "空は青く広がって"))

    @Test fun romajiLyricsAreRecognised() {
        assertTrue(looksLikeRomaji(romaji))
    }

    @Test fun englishAndJapaneseLyricsAreNotRomaji() {
        assertFalse(looksLikeRomaji(english))
        assertFalse(looksLikeRomaji(japanese))
        assertTrue(hasJapaneseScript(japanese))
        assertFalse(hasJapaneseScript(romaji))
    }

    @Test fun shortTextIsNeverTakenForRomaji() {
        assertFalse(looksLikeRomaji("Sakura sakura yayoi no sora wa"))
    }
}
