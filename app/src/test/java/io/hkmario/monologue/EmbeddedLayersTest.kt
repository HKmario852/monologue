package io.hkmario.monologue

import io.hkmario.monologue.cloud.embeddedLayers
import org.junit.Assert.*
import org.junit.Test

/** LRCLIB uploads with a second line under each timestamp. */
class EmbeddedLayersTest {
    // Lines under each timestamp, as LRCLIB uploads with a second language have them (made-up text).
    private fun upload(second: List<String>): String {
        val first = listOf("作曲：ヒグチアイ", "鉄の雨が窓を叩いた", "君の声が遠くなる", "夜明けを待っている", "名前を呼んでくれた", "まだ歩いていける", "光の中で笑った", "明日を信じている", "ここで待っている")
        return first.indices.joinToString("\n") { i -> "[00:%02d.00]%s\n[00:%02d.00]%s".format(i * 4, first[i], i * 4, second[i]) }
    }

    @Test fun aVietnameseLayerIsLeftOutNotShownAsLyricsOrRomaji() {
        // A credit with katakana in the second layer used to make the whole layer count as Japanese.
        val layers = embeddedLayers(upload(listOf("Nhạc: ヒグチアイ (Higuchi Ai)", "Mưa sắt gõ vào cửa sổ", "Giọng em xa dần", "Ta đang chờ bình minh", "Em đã gọi tên anh",
            "Ta vẫn có thể bước tiếp", "Đã cười trong ánh sáng", "Tin vào ngày mai", "Ta chờ ở đây")))
        assertFalse(layers.original.contains("Mưa"))
        assertTrue(layers.original.contains("鉄の雨が窓を叩いた"))
        assertNull(layers.romaji); assertNull(layers.translation)
    }

    @Test fun romajiAndChineseLayersAreKept() {
        val romaji = embeddedLayers(upload(listOf("sakkyoku higuchi ai", "tetsu no ame ga mado wo tataita", "kimi no koe ga tooku naru", "yoake wo matte iru", "namae wo yonde kureta",
            "mada aruite ikeru", "hikari no naka de waratta", "ashita wo shinjite iru", "koko de matte iru")))
        assertNotNull(romaji.romaji); assertFalse(romaji.original.contains("tetsu"))
        val chinese = embeddedLayers(upload(listOf("作曲：樋口愛", "鐵之雨敲打著窗", "你的聲音漸漸遠去", "等待著黎明", "你呼喚了我的名字", "還能夠走下去", "在光中笑了", "相信著明天", "在這裡等待")))
        assertTrue(chinese.translation!!.contains("等待著黎明"))
    }
}
