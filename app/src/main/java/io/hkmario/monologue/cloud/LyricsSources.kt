package io.hkmario.monologue.cloud

import io.hkmario.monologue.data.LyricsRow
import io.hkmario.monologue.domain.*
import kotlinx.coroutines.CancellationException

/**
 * Tries the enabled lyric sources in the chosen order and returns the first match.
 * LRCLIB is always available; NetEase only after the user turns it on in 設定 › 歌詞.
 */
class LyricsSources(private val lrclib: LyricsClient,private val netEase: NetEaseLyrics) {
    suspend fun find(track: Track,settings: AppSettingsUiState): LyricsRow? {
        val order=if(!settings.bool("neteaseLyrics")) listOf("lrclib") else if(settings.text("lyricsOrder","netease")=="lrclib") listOf("lrclib","netease") else listOf("netease","lrclib")
        val language=settings.text("translationLanguage","繁體中文")
        var failure: Exception?=null; var answered=false
        for(source in order) {
            try {
                val row=when(source) {
                    "netease" -> netEase.find(track,creditedArtists(track.artist))?.let { found ->
                        // NetEase translations are Chinese; shown as 繁體中文 after conversion, otherwise kept for reference.
                        val chinese=language=="繁體中文"
                        LyricsRow(track.id,found.original,found.translation?.let { if(chinese) toTraditional(it) else it },found.source,
                            found.translation?.let { if(chinese) "網易雲音樂中文翻譯：繁體中文" else "網易雲音樂中文翻譯：简体中文" },found.romaji)
                    }
                    else -> lrclib.find(track,settings.text("lyricsBase","https://lrclib.net"))
                }
                answered=true
                if(row!=null) return row
            } catch(e: CancellationException) { throw e } catch(e: Exception) { failure=e }
        }
        // Report a connection problem only when no source gave a real answer.
        if(!answered && failure!=null) throw failure
        return null
    }
}
