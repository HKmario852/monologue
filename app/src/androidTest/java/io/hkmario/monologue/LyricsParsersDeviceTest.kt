package io.hkmario.monologue

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.hkmario.monologue.cloud.parseKanogomaSong
import io.hkmario.monologue.cloud.parseThbLyrics
import io.hkmario.monologue.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The lyric parsers on Android's own regex engine, which rejects patterns the desktop JVM accepts (a bare "}" or "]"),
 * so a unit test alone can pass while the app fails.
 */
@RunWith(AndroidJUnit4::class)
class LyricsParsersDeviceTest {
    @Test fun parsersRunOnTheDevice() {
        val thb = parseThbLyrics("{{歌词信息|\n| 译者 = [[用户:甲]]\n}}\nlyrics=\n\ntime=00:01.00\nja={{ruby|朝|あさ}}の光\nzh=晨光\n\ntime=00:02.00\nja=[[君]]の声\nzh=你的声音\n\n" +
            "time=00:03.00\nja=遠くまで\nzh=直到远方\n\ntime=00:04.00\nja=夢の中\nzh=梦中")!!
        assertEquals("[00:01.00]朝の光", thb.original.lines().first())
        assertEquals("甲", thb.translator)
        val kanogoma = parseKanogomaSong("<h1>甲 - 歌</h1><div id=\"kngm\">" + (1..4).joinToString("") { "<div data-i=\"$it\"><p class=\"ruby\"><ruby>行<rt>ぎょう</rt></ruby>$it</p><p class=\"zh\">第$it 行</p></div>" } + "</div>")!!
        assertEquals("行1", kanogoma.original.first())
        val post = splitBilingualLyrics("作詞：誰か\n歌：誰か\nいつか見た夢\nいつかみたゆめ\ni tsu ka mi ta yu me\n曾經做過的夢\n叶わないと俯(うつむ)く\nかなわないとうつむく\nka na wa na i to u tsu mu ku\n無法實現而低頭\n" +
            "朝の光\nあさのひかり\na sa no hi ka ri\n晨光\n君の声\nきみのこえ\nki mi no ko e\n你的聲音")!!
        assertEquals(listOf("いつか見た夢", "叶わないと俯く", "朝の光", "君の声"), post.original.lines())
        assertNotNull(borrowTranslation("[00:01.00]いつか見た夢\n[00:02.00]叶わないと俯く\n[00:03.00]朝の光\n[00:04.00]君の声", post.original, post.translation!!))
        assertTrue(isRomajiLine("ka na wa na i"))
    }
}
