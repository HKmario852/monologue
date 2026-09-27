package io.hkmario.monologue

import io.hkmario.monologue.cloud.RomajiGenerator
import io.hkmario.monologue.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RomajiGeneratorTest {
    @Test fun kanaFollowsHepburn() {
        assertEquals("arigatou", kanaToRomaji("ありがとう"))
        assertEquals("gakkou", kanaToRomaji("がっこう"))
        assertEquals("matcha", kanaToRomaji("まっちゃ"))
        assertEquals("haato", kanaToRomaji("ハート"))
        assertEquals("kyou", kanaToRomaji("キョウ"))
        assertEquals("fantajii", kanaToRomaji("ファンタジー"))
        assertEquals("shinjitsu", kanaToRomaji("しんじつ"))
    }

    @Test fun kanjiAreReadInContext() = runBlocking {
        val romaji = RomajiGenerator().generate("[00:01.00]私は学校へ行った\n[00:05.50]今日はいい天気ですね\n\n夢を見ていたのだろう")
        val lines = romaji.lines()
        assertEquals("[00:01.00]watashi wa gakkou e itta", lines[0])
        assertEquals("[00:05.50]kyou wa ii tenki desu ne", lines[1])
        assertEquals("", lines[2])
        assertEquals("yume wo miteita no darou", lines[3])
    }

    @Test fun commonReadingsAreFixed() = runBlocking {
        assertEquals("hitori de aruita\nfutari nara", RomajiGenerator().generate("一人で歩いた\n二人なら"))
        assertEquals("te wo nobaseba", RomajiGenerator().generate("手を伸ばせば"))
        // Chinese forms typed into Japanese lyrics still read: 奧 → 奥 (oku), 墮 → 堕 (ochi).
        assertEquals("yami no oku", RomajiGenerator().generate("闇の奧"))
        assertEquals("ochite mo", RomajiGenerator().generate("墮ちても"))
    }

    @Test fun latinWordsAndPunctuationStay() = runBlocking {
        assertEquals("time and again, kimi to", RomajiGenerator().generate("time and again、君と"))
    }

    @Test fun linesLineUpWithTheOriginal() = runBlocking {
        val original = "[00:01.00]空を見上げて\n[00:04.00]♪\n[00:08.00]また明日"
        val aligned = Lrc.alignRomaji(Lrc.parse(original), Lrc.parse(RomajiGenerator().generate(original)))
        assertEquals(listOf("sora wo miagete", "♪", "mata ashita"), aligned.map { it.romaji })
    }
}
