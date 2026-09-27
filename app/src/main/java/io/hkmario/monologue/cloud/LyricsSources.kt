package io.hkmario.monologue.cloud

import io.hkmario.monologue.data.LyricsRow
import io.hkmario.monologue.domain.*
import kotlinx.coroutines.CancellationException

/**
 * Tries the enabled lyric sources in the order set in 設定 › 歌詞 and returns the first match.
 * LRCLIB is on by default; the unofficial sources only after the user turns them on.
 */
class LyricsSources(private val lrclib: LyricsClient,private val netEase: NetEaseLyrics,private val jLyric: JLyricProvider,private val utaTen: UtaTenProvider) {
    fun enabledNames(settings: AppSettingsUiState)=lyricsProviders(settings).filter { it.enabled }.map { it.info.name }

    suspend fun find(track: Track,settings: AppSettingsUiState): LyricsRow? {
        val language=settings.text("translationLanguage","繁體中文"); val chinese=language=="繁體中文"
        val artists=creditedArtists(track.artist)
        var failure: Exception?=null; var answered=false
        // Romaji-only lyrics are kept as a fallback while later sources are asked for the Japanese original.
        var romajiOnly: LyricsRow?=null
        for(provider in lyricsProviders(settings).filter { it.enabled }) {
            try {
                val found: FoundLyrics?=when(provider.info.id) {
                    "lrclib" -> lrclib.find(track,settings.text("lyricsBase","https://lrclib.net"))
                    "netease" -> netEase.find(track,artists)
                    "jlyric" -> jLyric.find(track,artists)
                    "utaten" -> utaTen.find(track,artists)
                    else -> null
                }
                answered=true
                if(found!=null) {
                    // Chinese translations (NetEase, or embedded in LRCLIB uploads) show as 繁體中文 after conversion.
                    val translation=found.translation?.let { if(chinese) toTraditional(it) else it }
                    val label=found.translation?.let { "${found.source.substringBefore(" · ")}中文翻譯：${if(chinese) "繁體中文" else "简体中文"}" }
                    val row=LyricsRow(track.id,found.original,translation,found.source,label,found.romaji)
                    if(!looksLikeRomaji(found.original)) return if(romajiOnly!=null && row.romaji==null) row.copy(romaji=romajiOnly.original) else row
                    if(romajiOnly==null) romajiOnly=row
                }
            } catch(e: CancellationException) { throw e } catch(e: Exception) { failure=e }
        }
        romajiOnly?.let { return it }
        // Report a connection problem only when no source gave a real answer.
        if(!answered && failure!=null) throw failure
        return null
    }
}
