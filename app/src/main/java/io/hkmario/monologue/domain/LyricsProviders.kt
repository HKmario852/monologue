package io.hkmario.monologue.domain

/** A lyric source as the user sees it in 設定 › 歌詞. */
data class LyricsProviderInfo(val id: String, val name: String, val detail: String, val official: Boolean, val warning: String? = null)
data class LyricsProviderSetting(val info: LyricsProviderInfo, val enabled: Boolean)

val lyricsProviderCatalog = listOf(
    LyricsProviderInfo("lrclib", "LRCLIB", "公開歌詞庫 · 同步歌詞", true),
    LyricsProviderInfo("netease", "網易雲音樂", "非官方 · 同步歌詞、中文翻譯、羅馬拼音", false,
        "網易雲音樂沒有公開的官方 API，這裡用的是它網頁播放器使用的非官方介面：可能隨時失效，也不符合網易雲的服務條款。開啟後會把目前歌曲的歌名和歌手傳送到網易雲音樂（中國大陸的服務）。"),
    LyricsProviderInfo("jlyric", "J-Lyric", "非官方 · 讀取網頁 · 純文字", false,
        "J-Lyric 沒有提供 API，這裡是直接讀取它的網頁：只有純文字歌詞（不會跟著播放捲動），網站改版就會失效，也不符合網站的使用條款。開啟後會把歌名傳送到 j-lyric.net。"),
    LyricsProviderInfo("utaten", "うたてん", "非官方 · 讀取網頁 · 純文字、羅馬拼音", false,
        "うたてん沒有提供 API，這裡是直接讀取它的網頁：只有純文字歌詞（不會跟著播放捲動），網站改版就會失效，也不符合網站的使用條款。開啟後會把歌名傳送到 utaten.com。"),
    LyricsProviderInfo("bahamut", "巴哈姆特", "非官方 · 讀取網頁 · 網友中文翻譯、純文字", false,
        "巴哈姆特沒有提供歌詞 API，這裡是用它的站內標籤搜尋找網友發表的歌詞翻譯創作，再讀取文章網頁：只有純文字歌詞（不會跟著播放捲動），譯文屬於各譯者，來源會顯示譯者名稱；網站改版就會失效。開啟後會把歌手名稱和歌名傳送到 gamer.com.tw。"),
)

/**
 * Stored as "lrclib:1,netease:0,…" in the setting `lyricsProviders`: order is priority, 1/0 is on/off.
 * Settings from 0.4.x (neteaseLyrics / lyricsOrder) are read once as the starting point; new sources are appended off.
 */
fun lyricsProviders(settings: AppSettingsUiState): List<LyricsProviderSetting> {
    val stored = settings.text("lyricsProviders").split(',').mapNotNull { part ->
        val (id, on) = part.split(':').let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "0") }
        lyricsProviderCatalog.find { it.id == id }?.let { LyricsProviderSetting(it, on == "1") }
    }
    val base = stored.ifEmpty {
        val netease = settings.bool("neteaseLyrics")
        val order = if(netease && settings.text("lyricsOrder", "netease") == "netease") listOf("netease", "lrclib") else listOf("lrclib", "netease")
        order.map { id -> LyricsProviderSetting(lyricsProviderCatalog.first { it.id == id }, id == "lrclib" || netease) }
    }
    return base + lyricsProviderCatalog.filter { info -> base.none { it.info.id == info.id } }.map { LyricsProviderSetting(it, false) }
}
fun encodeLyricsProviders(list: List<LyricsProviderSetting>) = list.joinToString(",") { "${it.info.id}:${if(it.enabled) 1 else 0}" }
