package io.hkmario.monologue

import io.hkmario.monologue.domain.*
import org.junit.Assert.*
import org.junit.Test

/** Fan translation post layouts seen on 巴哈姆特, and moving a translation onto lyrics from another source. */
class FanTranslationTest {
    // Each line as 日本語 / kana reading / romaji / 中文, under a header of title, credits and a link (like the agony post).
    private val readingsPost = """
        song
        アニメ　ED
        作詞：誰か
        作曲：誰か
        編曲：誰か
        歌：誰か
        中文翻譯：翻譯源不明
        線上試聽：請按我

        いつか見た夢
        いつかみたゆめ
        i tsu ka mi ta yu me
        曾經做過的夢

        叶わないと俯(うつむ)く夜風
        かなわないとうつむくよかぜ
        ka na wa na i to u tsu mu ku yo ka ze
        因無法實現而低頭的晚風

        キラキラ光る星
        きらきらひかるほし
        ki ra ki ra hi ka ru ho shi
        閃閃發光的星星

        さよなら
        さよなら
        sa yo na ra
        再見

        唇は闇に震えていた
        くちびるはやみにふるえていた
        ku chi bi ru wa ya mi ni fu ru e te i ta
        嘴唇在黑暗中顫抖
    """.trimIndent()

    @Test fun readingsAndRomajiUnderEachLineAreLeftOut() {
        val lyrics = splitBilingualLyrics(readingsPost)!!
        assertEquals(listOf("いつか見た夢", "叶わないと俯く夜風", "キラキラ光る星", "さよなら", "唇は闇に震えていた"), lyrics.original.lines())
        assertEquals(listOf("曾經做過的夢", "因無法實現而低頭的晚風", "閃閃發光的星星", "再見", "嘴唇在黑暗中顫抖"), lyrics.translation!!.lines())
    }

    @Test fun aStanzaThenItsReadingsAlsoWorks() {
        val post = "朝の光が窓に\n君の声が聞こえる\nあさのひかりがまどに\nきみのこえがきこえる\n早晨的光照在窗上\n聽得見你的聲音\n" +
            "遠くまで走る\n夢の中で待つ\nとおくまではしる\nゆめのなかでまつ\n跑向遠方\n在夢中等待"
        val lyrics = splitBilingualLyrics(post)!!
        assertEquals(listOf("朝の光が窓に", "君の声が聞こえる", "遠くまで走る", "夢の中で待つ"), lyrics.original.lines())
        assertEquals(4, lyrics.translation!!.lines().size)
    }

    // Credits written in kana count as lyrics unless skipped (like the 君にふれて post).
    @Test fun creditsWithKanaAreSkipped() {
        val post = "作詞：ボンジュール誰か\n作曲．編曲：ボンジュール誰か\n朝の光が窓に\n君の声が聞こえる\n早晨的光照在窗上\n聽得見你的聲音\n" +
            "遠くまで走る\n夢の中で待つ\n跑向遠方\n在夢中等待"
        val lyrics = splitBilingualLyrics(post)!!
        assertEquals(listOf("朝の光が窓に", "君の声が聞こえる", "遠くまで走る", "夢の中で待つ"), lyrics.original.lines())
        assertEquals(listOf("早晨的光照在窗上", "聽得見你的聲音", "跑向遠方", "在夢中等待"), lyrics.translation!!.lines())
    }

    @Test fun realLinesAreNeverTakenForReadings() {
        // An English lyric line under a Japanese one is not romaji; a repeated kana line is a lyric line.
        val english = "君に会いたい\nI want to see you\n夢の中で待つ\n想見你\n在夢中等待\n跑向遠方\nラララ\nラララ\n啦啦啦\n啦啦啦"
        val lyrics = splitBilingualLyrics(english)!!
        assertEquals(listOf("君に会いたい", "I want to see you", "夢の中で待つ", "ラララ", "ラララ"), lyrics.original.lines())
        assertFalse(isRomajiLine("I want to see you"))
        assertTrue(isRomajiLine("jya ne n ka ki ke su"))
        assertFalse(isRomajiLine("君に"))
    }

    private val synced = """
        [00:10.00]朝の光が窓を照らす
        [00:15.50]君の声が聞こえる
        [00:21.00]遠くまで走ってゆく 夢の中で待っている
        [00:28.00]明日へ
        [00:30.00]続く道
        [00:35.00]朝の光が窓を照らす
    """.trimIndent()

    @Test fun aPlainTranslationMovesOntoSyncedLyricsByText() {
        // The post splits line three in two and joins lines four and five; punctuation and spaces differ.
        val original = "朝の光が、窓を照らす\n君の声が聞こえる！\n遠くまで走ってゆく\n夢の中で待っている\n明日へ続く道"
        val translation = "早晨的光照亮窗戶\n聽得見你的聲音\n向遠方奔跑而去\n在夢中等待著\n通往明天的路"
        val borrowed = Lrc.align(Lrc.parse(synced), Lrc.parse(borrowTranslation(synced, original, translation)!!))
        assertEquals(listOf("早晨的光照亮窗戶", "聽得見你的聲音", "向遠方奔跑而去 在夢中等待著", "通往明天的路", null, "早晨的光照亮窗戶"), borrowed.map { it.translation })
    }

    @Test fun anotherSongsTranslationIsRejected() {
        val other = "全然違う歌詞\n別の歌の一行\nまた別の一行\n最後の一行"
        assertNull(borrowTranslation(synced, other, "完全不同的歌詞\n另一首歌的一句\n又一句\n最後一句"))
    }

    @Test fun plainLyricsNeedEveryLine() {
        val plain = "朝の光が窓を照らす\n君の声が聞こえる\n遠くまで走ってゆく\n夢の中で待っている"
        assertEquals("早晨的光\n你的聲音\n奔跑而去\n等待著", borrowTranslation(plain, plain, "早晨的光\n你的聲音\n奔跑而去\n等待著"))
        assertNull(borrowTranslation(plain + "\n知らない行", plain, "早晨的光\n你的聲音\n奔跑而去\n等待著"))
    }
}
