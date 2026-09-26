package io.hkmario.monologue.domain

import kotlinx.collections.immutable.*

data class OnlineSong(val id: String,val title: String,val artist: String,val album: String="",val durationMs: Long=0,val artwork: String?=null,val provider: String,val url: String="",val audio: Boolean=false)
data class AudioChoice(val url: String,val mime: String,val bitrate: Int=0,val sampleRate: Int=0,val bitDepth: Int=0,val lossless: Boolean=false)
data class SourcePlugin(val id: String,val name: String,val version: String,val category: String,val endpoint: String="",val manifestUrl: String="",val hosts: PersistentList<String> = persistentListOf(),val builtIn: Boolean=false)
data class OnlineUiState(val provider: String="youtube",val query: String="",val phase: Phase=Phase.Empty,val results: PersistentList<OnlineSong> = persistentListOf(),val error: String?=null,val resolvingId: String?=null,val spotifyConnected: Boolean=false,val spotifyUser: String?=null,val searched: Boolean=false,val quality: PersistentMap<String,String> = persistentMapOf())
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

sealed interface OnlineAction {
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
