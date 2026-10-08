package io.hkmario.monologue.domain

import kotlinx.collections.immutable.*
import java.util.UUID

enum class Phase { Loading, Ready, Empty, Error, PermissionRequired, Unconfigured, Offline }
enum class LibraryTab(val label: String) { Tracks("單曲"), Artists("歌手"), Albums("專輯"), Folders("資料夾") }
enum class Source { Local, Drive, Online }
enum class DownloadStatus { Queued, Downloading, Complete, Failed, Cancelled }
enum class DownloadPhase { Idle, Running, Waiting, Complete, Cancelled }
enum class Connection { Unconfigured, Verifying, Connected, InvalidToken, NetworkError, AuthorizationRequired }
enum class Period { Week, Month, All }
data class Track(val id: String, val title: String, val artist: String = "未知歌手", val album: String = "未知專輯", val folder: String = "", val uri: String, val durationMs: Long = 0, val artwork: String? = null, val source: Source = Source.Local, val favorite: Boolean = false, val offlinePath: String? = null, val remoteVersion: String? = null, val downloadedVersion: String? = null, val bytes: Long = 0, val checksum: String? = null, val mime: String = "audio/mpeg",
    /** When the file was added: uploaded to Drive or added to the phone (epoch ms); 0 when unknown. */
    val addedMs: Long = 0)
data class QueueEntry(val id: String = UUID.randomUUID().toString(), val track: Track, val removeAfterPlaying: Boolean = false)
data class GroupItem(val id: String, val title: String, val tracks: PersistentList<Track>)
data class Playlist(val id: String, val name: String, val tracks: PersistentList<Track> = persistentListOf(), val entryIds: PersistentList<String> = persistentListOf())
data class SearchRequest(val tab: LibraryTab, val query: String, val generation: Long)
data class LibrarySearchUiState(val request: SearchRequest = SearchRequest(LibraryTab.Tracks, "", 0), val phase: Phase = Phase.Ready, val focused: Boolean = false, val history: PersistentList<String> = persistentListOf(), val tracks: PersistentList<Track> = persistentListOf(), val groups: PersistentList<GroupItem> = persistentListOf())
data class LocalLibraryUiState(val phase: Phase = Phase.Loading, val tracks: PersistentList<Track> = persistentListOf(), val playlists: PersistentList<Playlist> = persistentListOf(), val recent: PersistentList<Track> = persistentListOf(), val search: LibrarySearchUiState = LibrarySearchUiState(), val error: String? = null, val sort: String = "title")
data class PlaylistDetailUiState(val phase: Phase = Phase.Empty, val playlist: Playlist? = null, val error: String? = null)
data class NowPlayingUiState(val phase: Phase = Phase.Empty, val entry: QueueEntry? = null, val isPlaying: Boolean = false, val buffering: Boolean = false, val seekable: Boolean = false, val durationMs: Long = 0, val shuffle: Boolean = false, val repeat: Int = 0, val speed: Float = 1f, val error: String? = null, val audioFormat: String? = null)
data class PlaybackProgress(val entryId: String? = null, val positionMs: Long = 0, val bufferedMs: Long = 0, val seekPreview: Long? = null)
data class VinylPresentationState(val angle: Double = 0.0, val anchorNanos: Long? = null, val degreesPerSecond: Double = 15.0, val enginePlaying: Boolean = false, val allowed: Boolean = true, val visible: Boolean = false)
data class LyricLine(val id: String, val timeMs: Long?, val text: String, val translation: String? = null, val romaji: String? = null)
data class LyricsUiState(val phase: Phase = Phase.Empty, val trackId: String? = null, val lines: PersistentList<LyricLine> = persistentListOf(), val source: String = "未有歌詞", val translationSource: String? = null, val manualScroll: Boolean = false, val error: String? = null, val romajiAvailable: Boolean = false, val romajiGenerated: Boolean = false, val romajiLoading: Boolean = false)
data class PlaybackQueueUiState(val entries: PersistentList<QueueEntry> = persistentListOf(), val currentId: String? = null)
data class EqualizerBand(val index: Int, val hz: Int, val level: Int)
data class EqualizerUiState(val supported: Boolean = false, val enabled: Boolean = false, val bands: PersistentList<EqualizerBand> = persistentListOf(), val min: Int = 0, val max: Int = 0, val presets: PersistentList<String> = persistentListOf(), val reason: String = "播放音訊後偵測裝置能力")
data class SleepTimerUiState(val deadlineElapsedMs: Long? = null, val remainingMs: Long = 0, val endOfTrack: Boolean = false, val fade: Boolean = false)
data class DriveFolder(val id: String, val name: String)
data class DriveLibraryUiState(val phase: Phase = Phase.Unconfigured, val connection: Connection = Connection.Unconfigured, val account: String? = null, val breadcrumbs: PersistentList<DriveFolder> = persistentListOf(DriveFolder("root", "我的雲端")), val folders: PersistentList<DriveFolder> = persistentListOf(), val tracks: PersistentList<Track> = persistentListOf(), val query: String = "", val error: String? = null, val sort: String = "name", val downloads: PersistentMap<String,DownloadItem> = persistentMapOf(), val everywhere: PersistentList<Track> = persistentListOf(), val everywherePhase: Phase = Phase.Empty, val index: DriveIndex = DriveIndex(), val connectStep: Int? = null)
data class DownloadItem(val id: String, val trackId: String, val title: String, val status: DownloadStatus, val bytes: Long = 0, val total: Long = 0, val error: String? = null)
data class DownloadManagerUiState(val phase: DownloadPhase = DownloadPhase.Idle, val items: PersistentList<DownloadItem> = persistentListOf(), val pauseBetween: Boolean = false, val pauseRequested: Boolean = false) {
    val success get() = items.count { it.status == DownloadStatus.Complete }
    val failed get() = items.count { it.status == DownloadStatus.Failed }
    val pending get() = items.count { it.status == DownloadStatus.Queued }
    val current get() = items.firstOrNull { it.status == DownloadStatus.Downloading }
}
data class RankedTrack(val track: Track, val count: Int, val listenedMs: Long)
data class LeaderboardUiState(val phase: Phase = Phase.Empty, val period: Period = Period.Week, val offset: Int = 0, val startMs: Long = 0, val endExclusiveMs: Long = 0, val zone: String = "Asia/Hong_Kong", val sortByTime: Boolean = false, val rows: PersistentList<RankedTrack> = persistentListOf(), val hours: Double = 0.0, val count: Int = 0,
    /** 按最近播放: every library song, last heard first; not limited to the period. */
    val recent: Boolean = false, val history: PersistentList<HistoryEntry> = persistentListOf())
data class ListenBrainzUiState(val connection: Connection = Connection.Unconfigured, val username: String? = null, val pending: Int = 0, val syncEnabled: Boolean = false, val lastSuccess: Long? = null, val error: String? = null)
data class Recommendation(val id: String, val title: String, val artist: String, val match: Track? = null, val artwork: String? = null, val recordingMbid: String? = null)
data class ListeningStatsUiState(val all: PersistentList<RankedTrack> = persistentListOf(), val month: PersistentList<RankedTrack> = persistentListOf(), val detail: PersistentList<RankedTrack> = persistentListOf(), val period: Period = Period.All, val offset: Int = 0, val startMs: Long = 0, val endMs: Long = 0, val zone: String = "Asia/Hong_Kong")
/** One playlist ListenBrainz made for the user: Weekly Jams (每週精選) or Weekly Exploration (每週探索), for the week starting [week]. */
data class RecommendationList(val id: String, val title: String, val week: String?, val tracks: PersistentList<Recommendation>)
/** 探索's 每週推薦: the playlists, and the one shown ([title], [generated] and [tracks] are that one's). */
data class DiscoverUiState(val phase: Phase = Phase.Unconfigured, val title: String = "每週探索", val generated: String? = null, val tracks: PersistentList<Recommendation> = persistentListOf(), val error: String? = null, val resolving: String? = null,
    val lists: PersistentList<RecommendationList> = persistentListOf(), val selected: Int = 0) {
    fun showing(index: Int): DiscoverUiState = lists.getOrNull(index)?.let { copy(selected=index,title=it.title,generated=it.week,tracks=it.tracks) } ?: this
}
data class StorageUiState(val cacheBytes: Long = 0, val limitBytes: Long = 1_000_000_000, val offlineBytes: Long = 0, val freeBytes: Long = 0, val lyricsBytes: Long = 0, val artBytes: Long = 0, val deferredClear: Boolean = false)
data class AppSettingsUiState(val values: PersistentMap<String, String> = persistentMapOf()) {
    fun text(key: String, default: String = "") = values[key] ?: default
    fun bool(key: String, default: Boolean = false) = values[key]?.toBooleanStrictOrNull() ?: default
    fun number(key: String, default: Float) = values[key]?.toFloatOrNull() ?: default
}
data class AppUiState(val online: OnlineUiState = OnlineUiState(), val plugins: PluginUiState = PluginUiState(), val updates: UpdateUiState = UpdateUiState(), val stats: ListeningStatsUiState = ListeningStatsUiState(), val notificationAllowed: Boolean = false, val authorizedFolders: PersistentList<String> = persistentListOf(), val settings: AppSettingsUiState = AppSettingsUiState(), val library: LocalLibraryUiState = LocalLibraryUiState(), val player: NowPlayingUiState = NowPlayingUiState(), val queue: PlaybackQueueUiState = PlaybackQueueUiState(), val lyrics: LyricsUiState = LyricsUiState(), val drive: DriveLibraryUiState = DriveLibraryUiState(), val downloads: DownloadManagerUiState = DownloadManagerUiState(), val leaderboard: LeaderboardUiState = LeaderboardUiState(), val listenBrainz: ListenBrainzUiState = ListenBrainzUiState(), val discover: DiscoverUiState = DiscoverUiState(), val storage: StorageUiState = StorageUiState(), val equalizer: EqualizerUiState = EqualizerUiState(), val sleep: SleepTimerUiState = SleepTimerUiState())

sealed interface UiEvent {
    data class Online(val action: OnlineAction): UiEvent
    data class Statistics(val period: Period, val offset: Int = 0) : UiEvent
    data object Scan : UiEvent
    data object RequestAudioPermission : UiEvent
    data class Query(val value: String) : UiEvent
    data class Tab(val value: LibraryTab) : UiEvent
    data class SearchFocus(val focused: Boolean) : UiEvent
    data object SubmitSearch : UiEvent
    data object ClearSearchHistory : UiEvent
    data class Play(val track: Track) : UiEvent
    data class PlayList(val tracks: PersistentList<Track>) : UiEvent
    data object TogglePlay : UiEvent
    data object Next : UiEvent
    data object Previous : UiEvent
    data object Shuffle : UiEvent
    data object Repeat : UiEvent
    data class PreviewSeek(val position: Long?) : UiEvent
    data object CommitSeek : UiEvent
    data class Favorite(val track: Track) : UiEvent
    data class Enqueue(val track: Track, val next: Boolean = false) : UiEvent
    data class QueuePlay(val id: String) : UiEvent
    data class QueueMove(val id: String, val index: Int) : UiEvent
    data class QueueRemove(val id: String) : UiEvent
    data object QueueClear : UiEvent
    /** Puts back entries removed by [QueueClear], in the order they were removed. */
    data class QueueRestore(val removed: List<Pair<QueueEntry, Int>>) : UiEvent
    /** Creates a playlist; when [track] is given it becomes the first song, so "add to playlist" never dead-ends. */
    data class PlaylistCreate(val name: String, val track: Track? = null) : UiEvent
    data class PlaylistRename(val id: String, val name: String) : UiEvent
    data class PlaylistDelete(val id: String) : UiEvent
    data class PlaylistAdd(val id: String, val track: Track) : UiEvent
    data class PlaylistRemove(val id: String, val position: Int) : UiEvent
    data class PlaylistRestore(val id: String, val position: Int, val trackId: String) : UiEvent
    data class PlaylistMove(val id: String, val from: Int, val to: Int) : UiEvent
    data class Setting(val key: String, val value: String) : UiEvent
    data class Sleep(val minutes: Int, val endOfTrack: Boolean = false) : UiEvent
    data class EqEnabled(val value: Boolean) : UiEvent
    data class EqBand(val band: Int, val level: Int) : UiEvent
    data class EqPreset(val index: Int) : UiEvent
    data object EqReset : UiEvent
    data object ConnectDrive : UiEvent
    data object DisconnectDrive : UiEvent
    data class DriveOpen(val folder: DriveFolder) : UiEvent
    data class DriveBreadcrumb(val index: Int) : UiEvent
    data class DriveQuery(val value: String) : UiEvent
    data object DriveRefresh : UiEvent
    data class DriveChooseRoot(val id: String, val name: String) : UiEvent
    data object DriveCancelConnect : UiEvent
    data object DownloadFolder : UiEvent
    data class DownloadTrack(val track: Track) : UiEvent
    data class PauseBetween(val enabled: Boolean) : UiEvent
    data object ContinueDownloads : UiEvent
    data object CancelDownloads : UiEvent
    data object RetryDownloads : UiEvent
    data class Leaderboard(val period: Period, val offset: Int = 0, val byTime: Boolean = false, val recent: Boolean = false) : UiEvent
    data class VerifyToken(val token: String) : UiEvent
    data class DisconnectListenBrainz(val discardPending: Boolean) : UiEvent
    data object SyncNow : UiEvent
    data object Recommendations : UiEvent
    /** Shows the saved recommendations, fetching them again only when old (探索 opened). */
    data object LoadRecommendations : UiEvent
    data class ShowRecommendationList(val index: Int) : UiEvent
    data class PlayRecommendation(val item: Recommendation) : UiEvent
    data object ClearStreamCache : UiEvent
    data object RefreshStorage : UiEvent
    data class DeleteOffline(val trackId: String) : UiEvent
    data object ClearLyricsCache : UiEvent
    data object RetryLyrics : UiEvent
    data object RefetchLyrics : UiEvent
    data class SetLyricsProvider(val id: String, val enabled: Boolean) : UiEvent
    data class MoveLyricsProvider(val id: String, val by: Int) : UiEvent
    data object ClearArtworkCache : UiEvent
    data object ClearIndex : UiEvent
    data class ClearStatistics(val start: Long, val end: Long) : UiEvent
    data object ResetSettings : UiEvent
    data class ImportLyrics(val uri: String, val translation: Boolean = false) : UiEvent
    data class Export(val kind: String) : UiEvent
    data object ImportSettings : UiEvent
    data object PickFolder : UiEvent
    data class RevokeFolder(val uri: String) : UiEvent
}
sealed interface UiEffect {
    data class OpenUrl(val url: String): UiEffect
    data class InstallApk(val path: String): UiEffect
    data class Message(val text: String) : UiEffect
    data class UndoQueue(val entry: QueueEntry, val index: Int) : UiEffect
    /** A message whose "復原" action dispatches [undo]. */
    data class Undo(val text: String, val undo: UiEvent) : UiEffect
    data object AudioPermission : UiEffect
    data object GoogleAuthorization : UiEffect
    data object PickFolder : UiEffect
    data class Export(val kind: String, val text: String) : UiEffect
    data object ImportSettings : UiEffect
}
