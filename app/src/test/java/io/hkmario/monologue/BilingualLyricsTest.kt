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

    @Test fun postTitleMustNameTheSong() {
        assertTrue(postTitleMentionsSong("【東方Vocal】FELT｜Time and again (中文翻譯)", "Time and again"))
        assertTrue(postTitleMentionsSong("【中日歌詞】YOASOBI - アイドル 翻譯", "アイドル"))
        assertFalse(postTitleMentionsSong("【東方Vocal】FELT｜Landscape (中文翻譯)", "Time and again"))
    }
}
