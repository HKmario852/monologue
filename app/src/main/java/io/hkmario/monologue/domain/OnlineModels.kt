package io.hkmario.monologue.domain

import kotlinx.collections.immutable.*

data class OnlineSong(val id: String,val title: String,val artist: String,val album: String="",val durationMs: Long=0,val artwork: String?=null,val provider: String,val url: String="",val audio: Boolean=false)
data class AudioChoice(val url: String,val mime: String,val bitrate: Int=0,val sampleRate: Int=0,val bitDepth: Int=0,val lossless: Boolean=false)
data class SourcePlugin(val id: String,val name: String,val version: String,val category: String,val endpoint: String="",val manifestUrl: String="",val hosts: PersistentList<String> = persistentListOf(),val builtIn: Boolean=false)
data class OnlineUiState(val provider: String="youtube",val query: String="",val phase: Phase=Phase.Empty,val results: PersistentList<OnlineSong> = persistentListOf(),val error: String?=null,val resolvingId: String?=null,val spotifyConnected: Boolean=false,val spotifyUser: String?=null,val searched: Boolean=false,val quality: PersistentMap<String,String> = persistentMapOf(),val browse: BrowseUiState=BrowseUiState())
fun providerLabel(id: String)=when(id) {"youtube"->"YouTube";"spotify"->"Spotify";"musicbrainz"->"MusicBrainz";else->id}
/** Describes what the source itself reported for the chosen stream; decoder output is shown separately in Now Playing. */
fun AudioChoice.describe(): String {
    val format=mime.substringAfter('/').substringBefore(';').uppercase().replace("WEBM","WebM").replace("MP4","AAC/MP4")
    val parts=mutableListOf(format)
    if(bitrate>0) parts+="${bitrate/1000} kbps"
    if(sampleRate>0) parts+="${sampleRate/1000.0} kHz".replace(".0 kHz"," kHz")
    if(bitDepth>0) parts+="$bitDepth-bit"
    if(lossless) parts+="來源標示無損"
    return parts.joinToString(" · ")
}
data class PluginUiState(val plugins: PersistentList<SourcePlugin> = persistentListOf(),val pending: SourcePlugin?=null,val phase: Phase=Phase.Ready,val error: String?=null)
data class UpdateUiState(val phase: Phase=Phase.Unconfigured,val version: String?=null,val notes: String="",val assetUrl: String?=null,val digest: String?=null,val size: Long=0,val downloaded: Long=0,val apkPath: String?=null,val error: String?=null,val downloading: Boolean=false)

/** A 搜尋 tile: a YouTube Music song search shown as a category, in one of the app's warm colours (ARGB). */
data class BrowseCategory(val id: String,val title: String,val query: String,val color: Long)
/** The tiles on 搜尋 before anything is typed, in the order they appear. */
val browseCategories=listOf(
    BrowseCategory("new","熱門新歌","new hit songs",0xFFA74932),BrowseCategory("jpop","日本樂曲","jpop hits",0xFF2F5D50),
    BrowseCategory("kpop","K-pop","K-Pop",0xFF6B3E6E),BrowseCategory("mandopop","華語流行","華語流行",0xFFA36A1E),
    BrowseCategory("cantopop","廣東歌","廣東歌",0xFF2D4B6E),BrowseCategory("anime","動漫歌曲","anime songs",0xFF9C3D5A),
    BrowseCategory("vocaloid","Vocaloid","Vocaloid",0xFF3E6670),BrowseCategory("pop","流行樂","pop hits",0xFF5D6B2E),
    BrowseCategory("rock","搖滾","rock",0xFF7A3B2E),BrowseCategory("hiphop","嘻哈","hip hop",0xFF3F4A5C),
    BrowseCategory("edm","電子","EDM",0xFF4A3F7A),BrowseCategory("jazz","爵士","jazz",0xFF8A5A2B),
    BrowseCategory("classical","古典","classical music",0xFF55606B),BrowseCategory("lofi","讀書 Lo-fi","lofi study",0xFF4E6B5C),
    BrowseCategory("workout","運動","workout music",0xFFA0452F),BrowseCategory("chill","放鬆","chill relaxing music",0xFF365E73),
)
/** Songs per category (the first song's cover is the tile's), which categories are loading, and why any failed. */
data class BrowseUiState(val songs: PersistentMap<String,PersistentList<OnlineSong>> = persistentMapOf(),val loading: PersistentSet<String> = persistentSetOf(),val errors: PersistentMap<String,String> = persistentMapOf())

sealed interface OnlineAction {
    /** Load the 搜尋 tiles' covers (cached for a day). */
    data object BrowseCovers: OnlineAction
    /** Open a 搜尋 tile: load its songs if not loaded yet. */
    data class Browse(val id: String): OnlineAction
    data class Query(val value: String): OnlineAction
    data class Provider(val id: String): OnlineAction
    data object Search: OnlineAction
    data class Play(val song: OnlineSong): OnlineAction
    data class Match(val song: OnlineSong): OnlineAction
    data class Download(val song: OnlineSong): OnlineAction
    data class Inspect(val song: OnlineSong): OnlineAction
    data class SearchFor(val query: String): OnlineAction
    data object SpotifyConnect: OnlineAction
    data object SpotifyDisconnect: OnlineAction
    data class SpotifyPlaylist(val url: String): OnlineAction
    data class InspectPlugin(val url: String): OnlineAction
    data object InstallPlugin: OnlineAction
    data object DismissPlugin: OnlineAction
    data class RemovePlugin(val id: String): OnlineAction
    data object CheckUpdate: OnlineAction
    data object DownloadUpdate: OnlineAction
    data object InstallUpdate: OnlineAction
}
