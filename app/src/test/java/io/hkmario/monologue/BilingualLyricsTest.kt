package io.hkmario.monologue

import io.hkmario.monologue.domain.*
import org.junit.Assert.*
import org.junit.Test

class BilingualLyricsTest {
    // Laid out like a fan translation post: credits, a 歌詞 heading, then each stanza followed by its translation.
    private val post = """
        翻譯意思可能與原意有所差異。
        Song title
        歌手
        誰か
        原曲
        夜明けの歌
        歌詞
        朝の光が窓を照らす
        君の声が聞こえるよ
        早晨的光照亮窗戶
        聽得見你的聲音
        遠くまで走ってゆく
        (again and again)
        夢の中で待っている
        向遠方奔跑而去
        （一次又一次）
        在夢中等待著
        譯註：第二段為意譯。
        ＥＮＤ
    """.trimIndent()

    @Test fun stanzasPairWithTheirTranslation() {
        val lyrics = splitBilingualLyrics(post)!!
        assertEquals(listOf("朝の光が窓を照らす", "君の声が聞こえるよ", "遠くまで走ってゆく", "(again and again)", "夢の中で待っている"), lyrics.original.lines())
        assertEquals(listOf("早晨的光照亮窗戶", "聽得見你的聲音", "向遠方奔跑而去", "（一次又一次）", "在夢中等待著"), lyrics.translation!!.lines())
    }

    @Test fun unevenStanzasKeepOnlyTheOriginal() {
        val lyrics = splitBilingualLyrics("歌詞\n朝の光が\n君の声が\n早晨的光\n遠くまで\n夢の中で\n向遠方\n在夢中\n等待著\n譯註")!!
        assertEquals(4, lyrics.original.lines().size)
        assertNull(lyrics.translation)
    }

    @Test fun postsWithoutLyricsGiveNothing() {
        assertNull(splitBilingualLyrics("這是一篇心得文。\n今天去看了演唱會。\n很好聽。"))
    }

    @Test fun timedPostsKeepTimestampsAndTranslation() {
        val post = """
            [00:01.55]「藍　Song Title」
            [00:10.13]Original：原曲名
            [00:20.36]Circle：Color&Color
            [00:30.52]Vocal：Singer / Other
            [00:35.36]
            [00:35.76]朝の光が窓を照らす / 早晨的光照亮窗戶
            [00:43.24]君の声が聞こえるよ / 聽得見你的聲音
            [00:51.48]遠くまで走ってゆく / 向遠方奔跑而去
            [00:59.65]夢の中で待っている / 在夢中等待著
            [01:05.64]
            [01:06.00]空は青く広がって / 天空蔚藍而遼闊
            [01:10.78]また明日会えるかな / 明天還能再見嗎
            [01:14.30]心の音を信じて / 相信心的聲音
            [01:18.91]もっと強くなりたい / 想變得更堅強
            --------------LRC檔案---------------
            譯者的話
        """.trimIndent()
        val lyrics = splitBilingualLyrics(post)!!
        assertTrue(isSyncedLyrics(lyrics.original))
        val original = Lrc.parse(lyrics.original)
        assertEquals("朝の光が窓を照らす", original.first().text)
        assertEquals(35760L, original.first().timeMs)
        assertEquals(8, original.size)
        assertEquals("早晨的光照亮窗戶", Lrc.align(original, Lrc.parse(lyrics.translation!!)).first().translation)
    }

    @Test fun ripTitlesGiveTheSongTitle() {
        assertTrue("Second Love♡二人の唇" in songTitleVariants("[Color&Color+] 虹色キャンバス #02 - ♡Second Love♡二人の唇", "虹色キャンバス"))
        assertTrue("Song" in songTitleVariants("Album - Song", "Album"))
        assertEquals(listOf("Rock - Paper"), songTitleVariants("Rock - Paper", "Scissors"))
    }

    @Test fun kanjiAndKanaTitlesReadTheSame() = kotlinx.coroutines.runBlocking {
        val generator = io.hkmario.monologue.cloud.RomajiGenerator()
        assertEquals(looseTitleKey(generator.generate("Second Love♡二人の唇")), looseTitleKey(generator.generate("Second Love♥ふたりの唇")))
    }

    @Test fun bracketedVocalsCanBeHidden() {
        assertEquals("嘆きの夜に", withoutBracketedVocals("嘆きの夜に (In this night)"))
        assertEquals("ずっと", withoutBracketedVocals("ずっと（For you）"))
        assertEquals("", withoutBracketedVocals("(Can you hear me)"))
        assertEquals("No brackets here", withoutBracketedVocals("No brackets here"))
    }

    @Test fun postTitleMustNameTheSong() {
        assertTrue(postTitleMentionsSong("【東方Vocal】FELT｜Time and again (中文翻譯)", "Time and again"))
        assertTrue(postTitleMentionsSong("【中日歌詞】YOASOBI - アイドル 翻譯", "アイドル"))
        assertFalse(postTitleMentionsSong("【東方Vocal】FELT｜Landscape (中文翻譯)", "Time and again"))
    }
}
