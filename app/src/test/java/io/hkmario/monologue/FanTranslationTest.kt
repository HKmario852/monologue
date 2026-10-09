package io.hkmario.monologue

import io.hkmario.monologue.domain.*
import io.hkmario.monologue.cloud.parseThbLyrics
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

    @Test fun kanogomaPagesGiveEachLineAndItsTranslation() {
        // Laid out like a kanogoma.com song page: furigana in <rt>, romaji, then Chinese; one line has no Chinese.
        val html = """
            <h1>誰か - 夜明けの歌</h1>
            <table class="info-table"><tr><th>唱:</th><td><a href="/artist/x">誰か</a></td></tr><tr><th>曲:</th><td>別の人</td></tr></table>
            <div id="kngm">
              <div data-i="0"><p class="ruby"><ruby>朝<rt>あさ</rt></ruby>の<ruby>光<rt>ひかり</rt></ruby></p><p class="rmj">asanohikari</p><p class="zh">早晨的光</p></div>
              <div data-i="1"><p class="ruby"><ruby>君<rt>きみ</rt></ruby>の<ruby>声<rt>こえ</rt></ruby></p><p class="rmj">kiminokoe</p><p class="zh">你的聲音</p></div>
              <div data-i="2"><p class="ruby">Oh yeah</p><p class="rmj">oh yeah</p><p class="zh"></p></div>
              <div data-i="3"><p class="ruby"><ruby>遠<rt>とお</rt></ruby>くまで</p><p class="rmj">tookumade</p><p class="zh">直到遠方</p></div>
            </div>
        """.trimIndent()
        val song = io.hkmario.monologue.cloud.parseKanogomaSong(html)!!
        assertEquals("誰か - 夜明けの歌", song.heading)
        assertEquals("誰か", song.singer)
        assertEquals(listOf("朝の光", "君の声", "Oh yeah", "遠くまで"), song.original)
        assertEquals(listOf("早晨的光", "你的聲音", "Oh yeah", "直到遠方"), song.chinese)
        assertNull(io.hkmario.monologue.cloud.parseKanogomaSong("<h1>x</h1>"))
    }

    @Test fun thbWikiPagesGiveSyncedLyricsAndTranslation() {
        // Laid out like a THBWiki 歌词 page (made-up lines).
        val page = """
            __LYRICS__

            {{歌词信息|
            | 语言 = 日文，英文
            | 翻译 = 中文
            | 译者 = [[用户:某译者|译者甲]]
            }}

            lyrics=

            time=00:00.65
            ja=朝の光が窓を照らす
            zh=晨光照亮窗户

            time=00:05.55
            ja={{ruby|君|きみ}}の声が聞こえる
            zh=听见你的声音

            sep=00:10.00

            time=00:11.11
            en=(again and again)
            zh=（一次又一次）

            time=00:16.42
            ja=[[夢]]の中で待っている
            zh=在梦中等待
        """.trimIndent()
        val lyrics = parseThbLyrics(page)!!
        assertEquals(listOf("[00:00.65]朝の光が窓を照らす", "[00:05.55]君の声が聞こえる", "[00:10.00]", "[00:11.11](again and again)", "[00:16.42]夢の中で待っている"), lyrics.original.lines())
        assertEquals(listOf("[00:00.65]晨光照亮窗户", "[00:05.55]听见你的声音", "[00:11.11]（一次又一次）", "[00:16.42]在梦中等待"), lyrics.translation!!.lines())
        assertEquals("译者甲", lyrics.translator)
        assertNull(parseThbLyrics("{{歌词信息}}"))
    }

    @Test fun kanjiFormsDoNotStopLinesMatching() {
        // One source types 觸/壞 (traditional forms), the other 触/壊 (Japanese forms).
        val target = "[00:01.00]君に觸れていたいよ\n[00:05.00]何か壞れそうで\n[00:09.00]手のぬくもり\n[00:13.00]確かめてた"
        val source = "君に触れていたいよ\n何か壊れそうで\n手のぬくもり\n確かめてた"
        val borrowed = borrowTranslation(target, source, "想觸碰你\n好像會壞掉\n手的溫暖\n確認著")!!
        assertEquals(1.0, translationCoverage(target, borrowed), 0.001)
    }

    @Test fun coverageCountsSungLinesThatGetATranslation() {
        val original = "[00:01.00]一行目\n[00:02.00]二行目\n[00:03.00]\n[00:04.00]三行目\n[00:05.00]四行目"
        assertEquals(0.5, translationCoverage(original, "[00:01.00]第一行\n[00:04.00]第三行"), 0.001)
        assertEquals(0.0, translationCoverage(original, ""), 0.001)
    }

    @Test fun aPastedBlogPostGivesLyricsAndTranslation() {
        // Laid out like a Pixnet post: a short teaser article first, then the post, one line of Japanese then its
        // Chinese, and an English line the translator left as it is.
        val html = """<html><body><article><p>関連記事：ほかの歌</p></article>
            <div class="sidebar"><p>人気記事</p></div>
            <div class="post"><p>作詞：誰か</p><p>歌：誰か</p>
            <p>朝の光が窓を照らす</p><p>晨光照亮窗戶</p>
            <p>I'm dreaming<br>君の声が聞こえる</p><p>聽得見你的聲音</p>
            <p>遠くまで走ってゆく</p><p>向遠方奔跑而去</p>
            <p>夢の中で待っている</p><p>在夢中等待著</p></div></body></html>"""
        val lyrics = io.hkmario.monologue.cloud.bilingualFromHtml(html, "https://example.com/post")!!
        assertEquals(listOf("朝の光が窓を照らす", "I'm dreaming", "君の声が聞こえる", "遠くまで走ってゆく", "夢の中で待っている"), lyrics.original.lines())
        assertEquals(listOf("晨光照亮窗戶", "I'm dreaming", "聽得見你的聲音", "向遠方奔跑而去", "在夢中等待著"), lyrics.translation!!.lines())
    }

    @Test fun plainLyricsNeedEveryLine() {
        val plain = "朝の光が窓を照らす\n君の声が聞こえる\n遠くまで走ってゆく\n夢の中で待っている"
        assertEquals("早晨的光\n你的聲音\n奔跑而去\n等待著", borrowTranslation(plain, plain, "早晨的光\n你的聲音\n奔跑而去\n等待著"))
        assertNull(borrowTranslation(plain + "\n知らない行", plain, "早晨的光\n你的聲音\n奔跑而去\n等待著"))
    }
}
