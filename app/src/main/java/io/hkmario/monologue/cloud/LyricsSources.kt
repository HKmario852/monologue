package io.hkmario.monologue.cloud

import io.hkmario.monologue.data.LyricsRow
import io.hkmario.monologue.domain.*
import kotlinx.coroutines.CancellationException

/**
 * Tries the enabled lyric sources in the order set in 設定 › 歌詞 and returns the first good match: a romaji-only
 * result, or (with 優先同步歌詞) a plain-text one, only wins when no later source has something better.
 * LRCLIB is on by default; the unofficial sources only after the user turns them on.
 */
class LyricsSources(private val lrclib: LyricsClient,private val netEase: NetEaseLyrics,private val jLyric: JLyricProvider,private val utaTen: UtaTenProvider,private val bahamut: BahamutLyrics,private val vocaDb: VocaDbLyrics) {
    fun enabledNames(settings: AppSettingsUiState)=lyricsProviders(settings).filter { it.enabled }.map { it.info.name }

    suspend fun find(track: Track,settings: AppSettingsUiState): LyricsRow? {
        val language=settings.text("translationLanguage","繁體中文"); val chinese=language=="繁體中文"
        val artists=creditedArtists(track.artist)
        var failure: Exception?=null; var answered=false
        // Romaji-only (and, when synced lyrics are preferred, plain-text) results are kept as fallbacks
        // while later sources are asked for something better; the first best-ranked result wins.
        val preferSynced=settings.bool("preferSyncedLyrics",true)
        val found=mutableListOf<LyricsRow>()
        fun choose(): LyricsRow? {
            val best=found.maxByOrNull { lyricsRank(it.original,preferSynced) } ?: return null
            if(best.romaji!=null) return best
            // Borrow a romaji layer from another result laid out the same way (both synced or both plain), so it can line up.
            val romaji=found.firstOrNull { it!==best && looksLikeRomaji(it.original) && isSyncedLyrics(it.original)==isSyncedLyrics(best.original) }?.original
                ?: found.firstOrNull { it!==best && it.romaji!=null && isSyncedLyrics(it.romaji)==isSyncedLyrics(best.original) }?.romaji
            return if(romaji!=null) best.copy(romaji=romaji) else best
        }
        for(provider in lyricsProviders(settings).filter { it.enabled }) {
            try {
                val result: FoundLyrics?=when(provider.info.id) {
                    "lrclib" -> lrclib.find(track,settings.text("lyricsBase","https://lrclib.net"))
                    "netease" -> netEase.find(track,artists)
                    "jlyric" -> jLyric.find(track,artists)
                    "utaten" -> utaTen.find(track,artists)
                    "bahamut" -> bahamut.find(track,artists)
                    "vocadb" -> vocaDb.find(track,artists)
                    else -> null
                }
                answered=true
                if(result!=null) {
                    // Chinese translations (NetEase, or embedded in LRCLIB uploads) show as 繁體中文 after conversion.
                    val translation=result.translation?.let { if(chinese) toTraditional(it) else it }
                    val label=result.translation?.let { "${result.source.substringBefore(" · ")}中文翻譯：${if(chinese) "繁體中文" else "简体中文"}" }
                    found+=LyricsRow(track.id,result.original,translation,result.source,label,result.romaji)
                    if(lyricsRank(result.original,preferSynced)>=bestLyricsRank(preferSynced)) return choose()
                }
            } catch(e: CancellationException) { throw e } catch(e: Exception) { failure=e }
        }
        choose()?.let { return it }
        // Report a connection problem only when no source gave a real answer.
        if(!answered && failure!=null) throw failure
        return null
    }
}
