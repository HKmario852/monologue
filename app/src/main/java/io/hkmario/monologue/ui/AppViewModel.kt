package io.hkmario.monologue.ui

import android.app.Application
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import androidx.work.WorkManager
import io.hkmario.monologue.*
import io.hkmario.monologue.domain.*
import io.hkmario.monologue.data.*
import io.hkmario.monologue.cloud.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.*
import kotlinx.collections.immutable.*
import java.io.File
import java.time.*
import java.util.UUID
import org.json.*

@OptIn(coil.annotation.ExperimentalCoilApi::class)
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class AppViewModel(app: Application): AndroidViewModel(app) {
    val graph=(app as MonologueApp).graph
    private val dao=graph.db.dao()
    private val mutable=MutableStateFlow(AppUiState())
    val state=mutable.asStateFlow()
    val progress=graph.playback.progress.asStateFlow()
    val vinyl=graph.playback.vinyl
    private val effectsChannel=Channel<UiEffect>(Channel.BUFFERED)
    val effects=effectsChannel.receiveAsFlow()
    private var allTracks=persistentListOf<Track>()
    private var allEvents: List<ListenEvent> = emptyList()
    private var searchJob: Job?=null
    private var driveJob: Job?=null
    private var lyricsJob: Job?=null
    private var scanJob: Job?=null
    private var onlineJob: Job?=null
    private var onlinePlayJob: Job?=null
    private var generation=0L
    private var lastTrack: String?=null
    private var initializedPreferences=false
    init {
        graph.playback.connect()
        viewModelScope.launch {graph.updates.restore()}
        viewModelScope.launch {graph.updates.state.collect {value->mutable.update {it.copy(updates=value)}}}
        viewModelScope.launch {graph.settings.state.collect {val plugins=graph.online.plugins();mutable.update {it.copy(plugins=it.plugins.copy(plugins=plugins),online=it.online.copy(spotifyConnected=graph.spotify.connected,spotifyUser=graph.spotify.user()))}}}
        viewModelScope.launch { graph.settings.state.collect { settings ->
            if(!initializedPreferences) {
                initializedPreferences=true
                val tab=runCatching {LibraryTab.valueOf(settings.text("defaultTab","Tracks"))}.getOrDefault(LibraryTab.Tracks)
                val period=runCatching {io.hkmario.monologue.domain.Period.valueOf(settings.text("rankPeriod","Week"))}.getOrDefault(io.hkmario.monologue.domain.Period.Week)
                mutable.update {it.copy(library=it.library.copy(search=it.library.search.copy(request=it.library.search.request.copy(tab=tab))),leaderboard=it.leaderboard.copy(period=period,sortByTime=settings.bool("rankTime")))}
                // A previously granted Drive account reconnects silently instead of asking the user to connect again after every launch.
                if(graph.drive.enabled && BuildConfig.GOOGLE_AUTH_CONFIGURED) { mutable.update {it.copy(drive=it.drive.copy(breadcrumbs=rootBreadcrumb(settings)))}; driveRefresh() }
            }
            mutable.update { it.copy(settings=settings, drive=it.drive.copy(sort=settings.text("driveSort","name")), listenBrainz=it.listenBrainz.copy(connection=if(settings.bool("lbAuthInvalid")) Connection.InvalidToken else it.listenBrainz.connection,syncEnabled=settings.bool("lbSync"),lastSuccess=settings.text("lbLastSuccess").toLongOrNull(),error=settings.text("lbError").ifBlank { null })) }
            updateSearch(false); updateRanks(); lastTrack?.let { loadLyrics(it) }
            // Off the main thread: the first call also starts WorkManager.
            viewModelScope.launch(Dispatchers.IO) { WorkScheduler.periodicSync(app,settings.bool("lbSync")); WorkScheduler.incremental(app,settings) }
        } }
        viewModelScope.launch { dao.observeTracks().collect { rows ->
            allTracks=rows.map { it.model() }.toPersistentList(); graph.playback.refreshTracks(allTracks)
            val byId=allTracks.associateBy {it.id}
            val phase=when {
                !hasAudioPermission() && getApplication<Application>().contentResolver.persistedUriPermissions.none {it.isReadPermission}->Phase.PermissionRequired
                scanJob?.isActive==true->Phase.Loading
                libraryTracks().isEmpty()->Phase.Empty
                else->Phase.Ready
            }
            mutable.update { it.copy(library=it.library.copy(tracks=libraryTracks(),phase=phase),drive=it.drive.copy(tracks=it.drive.tracks.map {t->byId[t.id] ?: t}.toPersistentList(),everywhere=it.drive.everywhere.map {t->byId[t.id]?.copy(folder=t.folder) ?: t}.toPersistentList())) }
            updateSearch(false); updateRanks()
        } }
        viewModelScope.launch { combine(dao.observePlaylists(),dao.observePlaylistEntries(),dao.observeTracks()) { playlists,entries,tracks ->
            val map=tracks.associateBy { it.id }; playlists.map { p ->
                val valid=entries.filter { it.playlistId==p.id }
                Playlist(p.id,p.name,valid.map {map[it.trackId]?.model() ?: Track(it.trackId,"檔案遺失",uri="")}.toPersistentList(),valid.map {it.id}.toPersistentList())
            }.toPersistentList()
        }.collect { rows -> mutable.update { it.copy(library=it.library.copy(playlists=rows)) } } }
        viewModelScope.launch { graph.playback.state.collect { player ->
            mutable.update { it.copy(player=player) }
            if(lastTrack!=player.entry?.track?.id) { lastTrack=player.entry?.track?.id; player.entry?.track?.id?.let(::loadLyrics) ?: mutable.update { it.copy(lyrics=LyricsUiState()) }; prefetchNextLyrics() }
        } }
        viewModelScope.launch { graph.playback.queue.collect { q -> mutable.update { it.copy(queue=q) } } }
        viewModelScope.launch { graph.playback.equalizer.collect { eq -> mutable.update { it.copy(equalizer=eq) } } }
        viewModelScope.launch { graph.playback.sleep.collect { sleep -> mutable.update { it.copy(sleep=sleep) } } }
        viewModelScope.launch { dao.observeEvents().collect { events -> allEvents=events; updateRanks(); val ids=events.map { it.trackId }.distinct().take(20); mutable.update { it.copy(library=it.library.copy(recent=ids.mapNotNull { id -> allTracks.find { t -> t.id==id } }.toPersistentList())) } } }
        viewModelScope.launch { combine(dao.observeDownloads(),dao.observeDownloadControl()) { jobs,control -> DownloadManagerUiState(DownloadPhase.valueOf(control?.phase ?: "Idle"),jobs.map { it.model() }.toPersistentList(),control?.pauseBetween ?: false,control?.pauseBetween==true && jobs.any { it.status=="Downloading" }) }.collect { d -> mutable.update { it.copy(downloads=d,drive=it.drive.copy(downloads=d.items.associateBy {item->item.trackId}.toPersistentMap())) }; storage() } }
        viewModelScope.launch { dao.observeOutbox().collect { rows -> mutable.update { it.copy(listenBrainz=it.listenBrainz.copy(pending=rows.count { row -> row.owner==graph.secrets.get("lb-user") })) } } }
        viewModelScope.launch {while(isActive) {delay(60_000);updateRanks()}}
        val user=graph.secrets.get("lb-user")
        if(user!=null) { mutable.update { it.copy(listenBrainz=it.listenBrainz.copy(username=user,connection=Connection.Verifying)) }; graph.secrets.get("listenbrainz")?.let(::verifyToken) }
        viewModelScope.launch { storage(); if(hasAudioPermission() || getApplication<Application>().contentResolver.persistedUriPermissions.any {it.isReadPermission}) scan() else mutable.update { it.copy(library=it.library.copy(phase=Phase.PermissionRequired)) } }
    }
    private fun libraryTracks(): PersistentList<Track> {
        val cloud=graph.drive.libraryIds
        val tracks=allTracks.filter { it.source==Source.Local || it.offlinePath!=null || it.source==Source.Online && it.favorite || it.source==Source.Drive && it.id in cloud }
        return sortLibrary(tracks,state.value.settings.text("sort","title")).toPersistentList()
    }
    fun hasAudioPermission()=ContextCompat.checkSelfPermission(getApplication(),if(Build.VERSION.SDK_INT>=33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED
    private fun launch(block: suspend () -> Unit) { viewModelScope.launch { try { block() } catch(e: CancellationException) { throw e } catch(e: Exception) { effectsChannel.send(UiEffect.Message(e.message ?: "操作未完成")) } } }
    fun dispatch(event: UiEvent) {
        when(event) {
            is UiEvent.Online -> onlineDispatch(event.action)
            is UiEvent.Statistics -> { mutable.update {it.copy(stats=it.stats.copy(period=event.period,offset=event.offset))}; updateRanks() }
            UiEvent.Scan -> scan()
            UiEvent.RequestAudioPermission -> launch { effectsChannel.send(UiEffect.AudioPermission) }
            is UiEvent.Query -> { mutable.update { it.copy(library=it.library.copy(search=it.library.search.copy(request=it.library.search.request.copy(query=event.value)))) }; updateSearch(true) }
            is UiEvent.Tab -> { mutable.update { it.copy(library=it.library.copy(search=it.library.search.copy(request=it.library.search.request.copy(tab=event.value)))) }; updateSearch(false) }
            is UiEvent.SearchFocus -> mutable.update { it.copy(library=it.library.copy(search=it.library.search.copy(focused=event.focused))) }
            UiEvent.SubmitSearch -> rememberSearch()
            UiEvent.ClearSearchHistory -> launch { graph.settings.set("history.${state.value.library.search.request.tab.name}","[]") }
            is UiEvent.Play -> { rememberSearch(); play(listOf(event.track)) }
            is UiEvent.PlayList -> play(event.tracks)
            UiEvent.TogglePlay -> graph.playback.toggle()
            UiEvent.Next -> graph.playback.next()
            UiEvent.Previous -> graph.playback.previous()
            UiEvent.Shuffle -> graph.playback.shuffle()
            UiEvent.Repeat -> graph.playback.repeat()
            is UiEvent.PreviewSeek -> graph.playback.seekPreview(event.position)
            UiEvent.CommitSeek -> graph.playback.seekCommit()
            is UiEvent.Favorite -> launch { dao.favorite(event.track.id,!event.track.favorite) }
            is UiEvent.Enqueue -> graph.playback.enqueue(event.track,event.next)
            is UiEvent.QueuePlay -> graph.playback.playEntry(event.id)
            is UiEvent.QueueMove -> graph.playback.move(event.id,event.index)
            is UiEvent.QueueRemove -> graph.playback.remove(event.id)?.let { (entry,index) -> launch { effectsChannel.send(UiEffect.UndoQueue(entry,index)) } }
            UiEvent.QueueClear -> graph.playback.clearUpcoming().takeIf { it.isNotEmpty() }?.let { removed -> launch { effectsChannel.send(UiEffect.Undo("已清除 ${removed.size} 首待播",UiEvent.QueueRestore(removed))) } }
            is UiEvent.QueueRestore -> graph.playback.restore(event.removed)
            is UiEvent.PlaylistCreate -> if(event.name.isNotBlank()) launch {
                val id=UUID.randomUUID().toString(); dao.putPlaylist(PlaylistRow(id,event.name.trim()))
                event.track?.let { dao.putPlaylistEntries(listOf(PlaylistEntryRow(UUID.randomUUID().toString(),id,it.id,0))); effectsChannel.send(UiEffect.Message("已加入「${event.name.trim()}」")) }
            }
            is UiEvent.PlaylistRename -> if(event.name.isNotBlank()) launch { dao.putPlaylist(PlaylistRow(event.id,event.name.trim())) }
            is UiEvent.PlaylistDelete -> launch { dao.deletePlaylist(event.id) }
            is UiEvent.PlaylistAdd -> launch { val rows=dao.playlistEntries(event.id); dao.putPlaylistEntries(listOf(PlaylistEntryRow(UUID.randomUUID().toString(),event.id,event.track.id,rows.size))) }
            is UiEvent.PlaylistRemove -> launch {
                val removed=dao.playlistEntries(event.id).getOrNull(event.position) ?: return@launch
                rewritePlaylist(event.id) { rows -> rows.filterIndexed { i,_ -> i!=event.position } }
                effectsChannel.send(UiEffect.Undo("已從播放清單移除",UiEvent.PlaylistRestore(event.id,event.position,removed.trackId)))
            }
            is UiEvent.PlaylistRestore -> launch { rewritePlaylist(event.id) { rows -> rows.toMutableList().apply { add(event.position.coerceIn(0,size),PlaylistEntryRow(UUID.randomUUID().toString(),event.id,event.trackId,event.position)) } } }
            is UiEvent.PlaylistMove -> launch { rewritePlaylist(event.id) { rows -> rows.toMutableList().apply { if(event.from in indices) add(event.to.coerceIn(0,lastIndex),removeAt(event.from)) } } }
            is UiEvent.Setting -> launch {
                if(event.key=="timezone") ZoneId.of(event.value)
                if(event.key=="speed") require(event.value.toFloatOrNull()?.let {it in 0.25f..2f}==true)
                if(event.key=="minDuration") require(event.value.toFloatOrNull()?.let {it>=0f && it.isFinite()}==true)
                if(event.key=="lbSync" && event.value=="true") require(graph.secrets.get("lb-user")!=null) { "請先驗證 ListenBrainz Token" }
                graph.settings.set(event.key,event.value)
                if(event.key=="sort") { mutable.update { it.copy(library=it.library.copy(tracks=libraryTracks())) }; updateSearch(false) }
                if(event.key=="lbSync" && event.value=="true") WorkScheduler.sync(getApplication())
                if(event.key=="autoIncremental" || event.key=="wifiOnly") WorkScheduler.incremental(getApplication(),graph.settings.snapshot())
            }
            is UiEvent.Sleep -> graph.playback.setSleep(event.minutes,event.endOfTrack)
            is UiEvent.EqEnabled -> graph.playback.eqEnabled(event.value)
            is UiEvent.EqBand -> graph.playback.eqBand(event.band,event.level)
            is UiEvent.EqPreset -> graph.playback.eqPreset(event.index)
            UiEvent.EqReset -> graph.playback.eqReset()
            UiEvent.ConnectDrive -> { if(BuildConfig.GOOGLE_AUTH_CONFIGURED) driveConnecting(); launch { effectsChannel.send(UiEffect.GoogleAuthorization) } }
            UiEvent.DisconnectDrive -> launch { graph.drive.disconnect(); graph.drive.saveLibrary(emptySet()); updateLibrary(); WorkManager.getInstance(getApplication()).cancelUniqueWork("monologue-downloads"); mutable.update { it.copy(drive=DriveLibraryUiState()) } }
            is UiEvent.DriveOpen -> mutable.update { it.copy(drive=it.drive.copy(breadcrumbs=it.drive.breadcrumbs.add(event.folder),query="")) }
            is UiEvent.DriveBreadcrumb -> mutable.update { it.copy(drive=it.drive.copy(breadcrumbs=it.drive.breadcrumbs.take(event.index+1).toPersistentList(),query="")) }
            is UiEvent.DriveQuery -> mutable.update { it.copy(drive=it.drive.copy(query=event.value)) }
            UiEvent.DriveRefresh -> driveRefresh()
            is UiEvent.DriveChooseRoot -> chooseDriveRoot(event.id,event.name)
            UiEvent.DriveCancelConnect -> driveCancelConnect()
            UiEvent.DownloadFolder -> launch { queueDownloads(driveFolderTracks(state.value.drive.breadcrumbs.last().id)) }
            is UiEvent.DownloadTrack -> launch { queueDownloads(listOf(event.track)) }
            is UiEvent.PauseBetween -> launch { val c=dao.control() ?: DownloadControl(); dao.control(c.copy(pauseBetween=event.enabled)); graph.settings.set("pauseBetween",event.enabled.toString()) }
            UiEvent.ContinueDownloads -> launch { val c=dao.control() ?: DownloadControl(); dao.control(c.copy(phase="Running",cancelled=false)); WorkScheduler.download(getApplication(),state.value.settings.bool("wifiOnly",true)) }
            UiEvent.CancelDownloads -> launch { dao.control((dao.control() ?: DownloadControl()).copy(phase="Cancelled",cancelled=true)); WorkManager.getInstance(getApplication()).cancelUniqueWork("monologue-downloads"); dao.cancelDownloads() }
            UiEvent.RetryDownloads -> launch { dao.retryFailed(); dao.control((dao.control() ?: DownloadControl()).copy(phase="Running",cancelled=false)); WorkScheduler.download(getApplication(),state.value.settings.bool("wifiOnly",true)) }
            is UiEvent.Leaderboard -> { mutable.update { it.copy(leaderboard=it.leaderboard.copy(period=event.period,offset=event.offset,sortByTime=event.byTime,recent=event.recent)) }; updateRanks() }
            is UiEvent.VerifyToken -> verifyToken(event.token)
            is UiEvent.DisconnectListenBrainz -> launch { graph.listenBrainz.disconnect(event.discardPending); mutable.update { it.copy(listenBrainz=ListenBrainzUiState(),discover=DiscoverUiState()) } }
            UiEvent.SyncNow -> { WorkScheduler.sync(getApplication()) }
            is UiEvent.PlayRecommendation -> playRecommendation(event.item)
            UiEvent.Recommendations -> launch { mutable.update { it.copy(discover=it.discover.copy(phase=Phase.Loading)) }; try { val d=graph.listenBrainz.recommendations(allTracks); mutable.update { it.copy(discover=d) } } catch(e: Exception) { mutable.update { it.copy(discover=it.discover.copy(phase=Phase.Error,error=e.message)) } } }
            UiEvent.ClearStreamCache -> launch { val deferred=withContext(Dispatchers.IO) { graph.cache.clear() }; storage(); effectsChannel.send(UiEffect.Message(if(deferred) "已清理未使用快取；播放中的部分會在釋放後清理" else "已清除串流快取")) }
            UiEvent.RefreshStorage -> launch { storage() }
            is UiEvent.DeleteOffline -> launch {
                require(state.value.queue.entries.none {it.track.id==event.trackId}) { "此歌曲仍在播放隊列，請先從隊列移除後再刪除下載" }
                val row=dao.track(event.trackId) ?: return@launch
                row.offlinePath?.let { path -> val file=File(path); require(graph.cache.offlineRoots.any {root->file.canonicalFile.parentFile==root.canonicalFile}); if(file.exists() && !file.delete()) error("無法刪除檔案") }
                val versions=dao.downloads().filter {it.trackId==event.trackId}.map {it.id}.filter {it.matches(Regex("[0-9a-f]{64}"))}
                withContext(Dispatchers.IO) {graph.cache.offlineRoots.forEach {root->versions.forEach {id->val file=File(root,"$id.audio");if(file.exists() && !file.delete()) error("部分舊版下載無法刪除")}}}
                dao.offline(event.trackId,null,null); storage()
            }
            UiEvent.ClearArtworkCache -> launch {withContext(Dispatchers.IO) {coil.Coil.imageLoader(getApplication()).diskCache?.clear();coil.Coil.imageLoader(getApplication()).memoryCache?.clear()};storage()}
            UiEvent.RetryLyrics -> lastTrack?.let(::loadLyrics)
            is UiEvent.SetLyricsProvider -> launch { graph.settings.set("lyricsProviders",encodeLyricsProviders(lyricsProviders(state.value.settings).map { if(it.info.id==event.id) it.copy(enabled=event.enabled) else it })) }
            is UiEvent.MoveLyricsProvider -> launch {
                val list=lyricsProviders(state.value.settings).toMutableList(); val from=list.indexOfFirst { it.info.id==event.id }; val to=(from+event.by).coerceIn(0,list.lastIndex)
                if(from>=0 && to!=from) { list.add(to,list.removeAt(from)); graph.settings.set("lyricsProviders",encodeLyricsProviders(list)) }
            }
            // Drop the stored online lyrics for this song and search the enabled sources again.
            UiEvent.RefetchLyrics -> lastTrack?.let { id -> launch { dao.deleteLyrics(id); translationFailed-=id; loadLyrics(id) } }
            UiEvent.ClearLyricsCache -> launch { dao.clearLyrics(); lastTrack?.let(::loadLyrics); storage() }
            UiEvent.ClearIndex -> launch { dao.clearLocalIndex() }
            is UiEvent.ClearStatistics -> launch { clearStatistics(event.start,event.end) }
            UiEvent.ResetSettings -> launch { graph.settings.reset() }
            is UiEvent.ImportLyrics -> importLyrics(event.uri,event.translation)
            is UiEvent.Export -> launch { effectsChannel.send(UiEffect.Export(event.kind,export(event.kind))) }
            UiEvent.ImportSettings -> launch { effectsChannel.send(UiEffect.ImportSettings) }
            UiEvent.PickFolder -> launch { effectsChannel.send(UiEffect.PickFolder) }
            is UiEvent.RevokeFolder -> launch {getApplication<Application>().contentResolver.releasePersistableUriPermission(Uri.parse(event.uri),android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);refreshSystemStatus();scan()}
        }
    }
    private var browseJob: Job?=null
    /** Loads one 搜尋 tile's songs; failures are kept per tile so the others still show. */
    private suspend fun loadBrowse(category: BrowseCategory) {
        if(state.value.online.browse.songs.containsKey(category.id) || category.id in state.value.online.browse.loading) return
        mutable.update { it.copy(online=it.online.copy(browse=it.online.browse.copy(loading=it.online.browse.loading.add(category.id),errors=it.online.browse.errors.remove(category.id)))) }
        try {
            val songs=graph.online.browse(category)
            // Nothing found counts as a failure, so opening the tile tries again.
            if(songs.isEmpty()) error("沒有找到歌曲")
            mutable.update { it.copy(online=it.online.copy(browse=it.online.browse.copy(songs=it.online.browse.songs.put(category.id,songs),loading=it.online.browse.loading.remove(category.id)))) }
        } catch(e: CancellationException) { mutable.update { it.copy(online=it.online.copy(browse=it.online.browse.copy(loading=it.online.browse.loading.remove(category.id)))) }; throw e }
        catch(e: Exception) {
            mutable.update { it.copy(online=it.online.copy(browse=it.online.browse.copy(loading=it.online.browse.loading.remove(category.id),errors=it.online.browse.errors.put(category.id,e.message ?: "無法連線")))) }
        }
    }
    private fun onlineDispatch(action: OnlineAction) {
        when(action) {
            // Covers for all tiles, three at a time, in the order they appear.
            OnlineAction.BrowseCovers -> if(browseJob?.isActive!=true) browseJob=viewModelScope.launch {
                browseCategories.chunked(3).forEach { group -> group.map { c -> launch { loadBrowse(c) } }.joinAll() }
            }
            is OnlineAction.Browse -> browseCategories.find { it.id==action.id }?.let { c -> viewModelScope.launch {
                // A tile that failed before is tried again when opened.
                if(state.value.online.browse.errors.containsKey(c.id)) mutable.update { it.copy(online=it.online.copy(browse=it.online.browse.copy(errors=it.online.browse.errors.remove(c.id)))) }
                loadBrowse(c)
            } }
            is OnlineAction.Query -> {onlineJob?.cancel();mutable.update {it.copy(online=it.online.copy(query=action.value,results=persistentListOf(),phase=Phase.Empty,error=null,searched=false))}}
            is OnlineAction.Provider -> {onlineJob?.cancel();mutable.update {it.copy(online=it.online.copy(provider=action.id,results=persistentListOf(),phase=Phase.Empty,error=null,searched=false))}}
            OnlineAction.Search -> onlineSearch()
            is OnlineAction.Match -> {
                onlineJob?.cancel();val provider=state.value.settings.text("audioProvider","youtube")
                mutable.update {it.copy(online=it.online.copy(provider=provider,query="${action.song.title} ${action.song.artist}",results=persistentListOf()))};onlineSearch()
            }
            is OnlineAction.SearchFor -> {onlineJob?.cancel();mutable.update {it.copy(online=it.online.copy(query=action.query,results=persistentListOf(),phase=Phase.Empty,error=null,searched=false))};onlineSearch()}
            is OnlineAction.Inspect -> launch {
                mutable.update {it.copy(online=it.online.copy(resolvingId=action.song.id,error=null))}
                try {rememberQuality(action.song,graph.online.resolve(action.song))}
                finally {mutable.update {it.copy(online=it.online.copy(resolvingId=null))}}
            }
            is OnlineAction.Play -> onlinePlay(action.song,false)
            is OnlineAction.Download -> onlinePlay(action.song,true)
            OnlineAction.SpotifyConnect -> launch {effectsChannel.send(UiEffect.OpenUrl(graph.spotify.authorizeUrl()))}
            OnlineAction.SpotifyDisconnect -> {graph.spotify.disconnect();mutable.update {it.copy(online=it.online.copy(spotifyConnected=false,spotifyUser=null,results=persistentListOf()))}}
            is OnlineAction.SpotifyPlaylist -> {
                onlineJob?.cancel();onlineJob=viewModelScope.launch {
                    mutable.update {it.copy(online=it.online.copy(provider="spotify",phase=Phase.Loading,results=persistentListOf(),error=null))}
                    try {val rows=graph.spotify.playlist(action.url);ensureActive();mutable.update {it.copy(online=it.online.copy(searched=true,results=rows,phase=if(rows.isEmpty()) Phase.Empty else Phase.Ready))}}
                    catch(e: CancellationException) {throw e} catch(e: Exception) {mutable.update {it.copy(online=it.online.copy(phase=Phase.Error,error=e.message))}}
                }
            }
            is OnlineAction.InspectPlugin -> launch {
                mutable.update {it.copy(plugins=it.plugins.copy(phase=Phase.Loading,error=null))}
                try {val plugin=graph.online.inspectPlugin(action.url);mutable.update {it.copy(plugins=it.plugins.copy(pending=plugin,phase=Phase.Ready))}}
                catch(e: Exception) {mutable.update {it.copy(plugins=it.plugins.copy(phase=Phase.Error,error=e.message))}}
            }
            OnlineAction.InstallPlugin -> launch {state.value.plugins.pending?.let {graph.online.installPlugin(it)};val installed=graph.online.plugins();mutable.update {it.copy(plugins=it.plugins.copy(pending=null,plugins=installed))}}
            OnlineAction.DismissPlugin -> mutable.update {it.copy(plugins=it.plugins.copy(pending=null))}
            is OnlineAction.RemovePlugin -> launch {graph.online.removePlugin(action.id)}
            OnlineAction.CheckUpdate -> launch {graph.updates.check()}
            OnlineAction.DownloadUpdate -> launch {graph.updates.enqueue()}
            OnlineAction.InstallUpdate -> launch {state.value.updates.apkPath?.let {effectsChannel.send(UiEffect.InstallApk(it))}}
        }
    }
    private fun onlineSearch() {
        onlineJob?.cancel();val requested=state.value.online
        if(requested.query.isBlank()) return
        onlineJob=viewModelScope.launch {
            mutable.update {it.copy(online=it.online.copy(phase=Phase.Loading,results=persistentListOf(),error=null))}
            try {
                val results=graph.online.search(requested.provider,requested.query);ensureActive()
                if(state.value.online.provider==requested.provider && state.value.online.query==requested.query) mutable.update {it.copy(online=it.online.copy(searched=true,results=results,phase=if(results.isEmpty()) Phase.Empty else Phase.Ready))}
            } catch(e: CancellationException) {throw e} catch(e: Exception) {mutable.update {it.copy(online=it.online.copy(phase=Phase.Error,error=e.message ?: "音源服務無法連線"))}}
        }
    }
    /** Library match plays directly; otherwise the first YouTube result for "artist title" is streamed, labelled as YouTube audio. */
    private fun playRecommendation(item: Recommendation) {
        item.match?.let { play(listOf(it)); return }
        onlinePlayJob?.cancel()
        onlinePlayJob=viewModelScope.launch {
            mutable.update {it.copy(discover=it.discover.copy(resolving=item.id))}
            try {
                val song=graph.online.search("youtube","${item.artist} ${item.title}").firstOrNull {it.audio} ?: error("YouTube 找不到「${item.title}」的音源")
                rememberQuality(song,graph.online.resolve(song));ensureActive()
                val track=graph.online.track(song);val old=dao.track(track.id)
                dao.putTrack(track.copy(favorite=old?.favorite ?: false,offlinePath=old?.offlinePath,downloadedVersion=old?.downloadedVersion).row())
                play(listOf(dao.track(track.id)!!.model()))
            } catch(e: CancellationException) {throw e} catch(e: Exception) {effectsChannel.send(UiEffect.Message(e.message ?: "未能播放推薦歌曲"))}
            finally {mutable.update {it.copy(discover=it.discover.copy(resolving=null))}}
        }
    }
    private fun onlinePlay(song: OnlineSong,download: Boolean) {
        onlinePlayJob?.cancel();onlinePlayJob=viewModelScope.launch {
            mutable.update {it.copy(online=it.online.copy(resolvingId=song.id,error=null))}
            try {
                rememberQuality(song,graph.online.resolve(song));ensureActive()
                val track=graph.online.track(song);val old=dao.track(track.id)
                dao.putTrack(track.copy(favorite=old?.favorite ?: false,offlinePath=old?.offlinePath,downloadedVersion=old?.downloadedVersion).row())
                val stored=dao.track(track.id)!!.model()
                if(download) queueDownloads(listOf(stored)) else play(listOf(stored))
            } catch(e: CancellationException) {throw e} catch(e: Exception) {mutable.update {it.copy(online=it.online.copy(error=e.message ?: "來源未能提供音訊"))}}
            finally {mutable.update {it.copy(online=it.online.copy(resolvingId=null))}}
        }
    }
    private fun rememberQuality(song: OnlineSong,choice: AudioChoice) {
        mutable.update {it.copy(online=it.online.copy(quality=it.online.quality.put(song.provider+":"+song.id,choice.describe())))}
    }
    fun spotifyCallback(uri: Uri)=launch {
        val user=graph.spotify.callback(uri)
        mutable.update {it.copy(online=it.online.copy(spotifyConnected=true,spotifyUser=user,error=null))}
    }
    private fun play(tracks: List<Track>) {
        val cloud=tracks.any { it.source!=Source.Local && it.offlinePath==null }
        if(cloud && !state.value.settings.bool("mobileStreaming")) {
            val manager=getApplication<Application>().getSystemService(android.net.ConnectivityManager::class.java)
            if(manager.isActiveNetworkMetered) { launch { effectsChannel.send(UiEffect.Message("行動網絡串流已關閉；可於播放設定啟用")) }; return }
        }
        val playable=tracks.filter {it.uri.isNotBlank()}
        if(playable.isEmpty()) {launch {effectsChannel.send(UiEffect.Message("沒有可播放的音源；請重新掃描或連接媒體庫"))};return}
        graph.playback.play(playable)
    }
    private suspend fun rewritePlaylist(id: String, transform: (List<PlaylistEntryRow>)->List<PlaylistEntryRow>) { graph.db.withTransaction { val rows=transform(dao.playlistEntries(id)).mapIndexed { i,r -> r.copy(position=i) }; dao.clearPlaylistEntries(id); dao.putPlaylistEntries(rows) } }
    private fun scan() {
        if(scanJob?.isActive==true) return
        if(!hasAudioPermission() && getApplication<Application>().contentResolver.persistedUriPermissions.none {it.isReadPermission}) { mutable.update { it.copy(library=it.library.copy(phase=Phase.PermissionRequired)) }; return }
        scanJob=viewModelScope.launch {
            mutable.update { it.copy(library=it.library.copy(phase=Phase.Loading,error=null)) }
            try { graph.scanner.scan(); graph.settings.set("lastScan",System.currentTimeMillis().toString()); mutable.update { it.copy(library=it.library.copy(phase=if(it.library.tracks.isEmpty()) Phase.Empty else Phase.Ready)) } }
            catch(e: Exception) { mutable.update { it.copy(library=it.library.copy(phase=Phase.Error,error=e.message ?: "掃描失敗")) } }
        }
    }
    private fun history(tab: LibraryTab): PersistentList<String> = runCatching { val a=JSONArray(state.value.settings.text("history.${tab.name}","[]")); (0 until a.length()).map { a.getString(it) }.toPersistentList() }.getOrDefault(persistentListOf())
    /** Tab and query of the results currently on screen; only these may stay visible while a refresh runs. */
    private var shownSearch: Pair<LibraryTab,String>?=null
    private fun updateSearch(debounce: Boolean) {
        searchJob?.cancel(); val old=state.value.library.search
        val request=old.request.copy(generation=++generation)
        // Same tab and query means only the data changed: keep showing the previous results until the new ones land.
        val sameQuery=shownSearch==(request.tab to request.query)
        mutable.update { it.copy(library=it.library.copy(search=if(sameQuery) old.copy(request=request) else old.copy(request=request,phase=Phase.Loading,tracks=persistentListOf(),groups=persistentListOf(),history=history(request.tab)))) }
        val tracks=libraryTracks()
        searchJob=viewModelScope.launch {
            if(debounce && request.query.isNotEmpty()) delay(200)
            val result=withContext(Dispatchers.Default) {
                val found=search(tracks,request)
                if(state.value.settings.text("groupSort.${request.tab.name}","name")=="count") found.copy(groups=found.groups.sortedByDescending {it.tracks.size}.toPersistentList()) else found
            }
            mutable.update { s -> if(s.library.search.request==request) { shownSearch=request.tab to request.query; s.copy(library=s.library.copy(search=result.copy(focused=s.library.search.focused,history=history(request.tab)))) } else s }
        }
    }
    private fun rememberSearch() {
        val s=state.value.library.search
        if(!state.value.settings.bool("searchHistory",true) || s.request.query.isBlank()) return
        launch { graph.settings.set("history.${s.request.tab.name}",JSONArray(recordSearch(history(s.request.tab),s.request.query)).toString()) }
    }
    private fun rootBreadcrumb(settings: AppSettingsUiState): PersistentList<DriveFolder> {
        val root=settings.text("driveRoot","root").ifBlank {"root"}
        return persistentListOf(DriveFolder(root,settings.text("driveRootName").ifBlank {if(root=="root") "我的雲端硬碟" else "音樂資料夾"}))
    }
    fun driveConnecting() { mutable.update { it.copy(drive=it.drive.copy(connectStep=0,error=null)) } }
    fun driveCancelConnect() { mutable.update { it.copy(drive=it.drive.copy(connectStep=null)) } }
    fun driveAuthorized(token: String) {
        graph.drive.accept(token)
        mutable.update {it.copy(drive=it.drive.copy(breadcrumbs=rootBreadcrumb(state.value.settings),connectStep=if(it.drive.connectStep!=null) 2 else null))}
        driveRefresh()
    }
    fun driveFailed(message: String) { mutable.update { it.copy(drive=it.drive.copy(phase=Phase.Error,connection=if(graph.drive.enabled) Connection.AuthorizationRequired else Connection.Unconfigured,error=message,connectStep=null)) } }
    private fun driveRefresh() {
        driveJob?.cancel()
        driveJob=viewModelScope.launch {
            mutable.update { it.copy(drive=it.drive.copy(phase=Phase.Loading,everywherePhase=Phase.Loading,error=null)) }
            try {
                val account=graph.drive.account()
                // One folder listing plus one audio listing; every folder view and song count is then computed locally.
                val (index,tracks)=coroutineScope {
                    val root=async {graph.drive.rootId()}; val folders=async {graph.drive.folders()}; val audio=async {graph.drive.everywhere()}
                    DriveIndex.build(root.await(),folders.await(),audio.await())
                }
                val all=tracks.toPersistentList()
                mutable.update { it.copy(drive=it.drive.copy(phase=if(all.isEmpty()) Phase.Empty else Phase.Ready,everywherePhase=if(all.isEmpty()) Phase.Empty else Phase.Ready,connection=Connection.Connected,account=account,index=index,everywhere=all,tracks=all,connectStep=null)) }
                refreshCloudLibrary()
                readDriveTags(all)
            }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) {
                // Keep previously indexed Drive songs visible so downloaded ones can still be played offline.
                val cached=state.value.drive.everywhere.ifEmpty { allTracks.filter {it.source==Source.Drive}.toPersistentList() }
                mutable.update { it.copy(drive=it.drive.copy(phase=Phase.Error,everywherePhase=Phase.Error,everywhere=cached,connectStep=null,connection=if(e is AuthorizationNeeded) Connection.AuthorizationRequired else Connection.NetworkError,error=e.message)) }
            }
        }
    }
    fun chooseDriveRoot(id: String,name: String) = launch {
        graph.settings.set("driveRoot",id); graph.settings.set("driveRootName",name); graph.settings.set("driveRootChosen","true")
        mutable.update { it.copy(drive=it.drive.copy(breadcrumbs=persistentListOf(DriveFolder(id,name)),query="")) }
        refreshCloudLibrary(id)
    }
    /** Songs under the chosen Drive music folder join 媒體庫 alongside local files. */
    private fun refreshCloudLibrary(root: String?=null) {
        val d=state.value.drive
        val chosen=root ?: state.value.settings.text("driveRoot","root").takeIf { state.value.settings.bool("driveRootChosen") || root!=null }
        if(chosen==null || d.index.nodes.isEmpty() && d.index.stats.isEmpty()) return
        graph.drive.saveLibrary(d.index.tracksUnder(chosen,d.everywhere).map {it.id}.toSet())
        updateLibrary()
    }
    private fun updateLibrary() {
        val tracks=libraryTracks()
        mutable.update { it.copy(library=it.library.copy(tracks=tracks,phase=if(tracks.isNotEmpty() && it.library.phase!=Phase.Loading) Phase.Ready else it.library.phase)) }
        updateSearch(false)
    }
    fun driveFolderTracks(folder: String): List<Track> = state.value.drive.let { d -> d.index.tracksUnder(folder,d.everywhere) }
    private var tagJob: Job?=null
    /** Fills artist/album/cover from each file's own tags in the background; the database flow pushes results to every screen. */
    private fun readDriveTags(tracks: List<Track>) {
        val pending=tracks.distinctBy {it.id}.filter {graph.drive.needsTags(it)}
        if(pending.isEmpty() || tagJob?.isActive==true) return
        tagJob=viewModelScope.launch(Dispatchers.IO) {
            val gate=kotlinx.coroutines.sync.Semaphore(3)
            pending.map { t -> async { gate.withPermit { try { graph.drive.readTags(t) } catch(e: CancellationException) { throw e } catch(e: Exception) { null } } } }.awaitAll()
        }
    }
    private suspend fun queueDownloads(tracks: List<Track>) {
        val added=graph.downloads.enqueue(tracks)
        effectsChannel.send(UiEffect.Message(if(added==0) "完整下載已是目前版本；沒有新增工作" else "已加入 $added 首下載"))
    }
    private fun updateRanks() {
        updateStats()
        val old=state.value.leaderboard; val zone=runCatching { ZoneId.of(state.value.settings.text("timezone","Asia/Hong_Kong")) }.getOrDefault(ZoneId.of("Asia/Hong_Kong"))
        val range=periodRange(old.period,old.offset,zone,Instant.now()); val start=range.start.toEpochMilli(); val end=range.endExclusive.toEpochMilli()
        val totals=allEvents.groupBy { it.trackId }.mapNotNull { (id,events) ->
            val track=allTracks.find { it.id==id } ?: Track(id,"已移除索引的歌曲",uri="")
            val count=events.count { it.counted && it.startMs>=start && it.startMs<end }
            val ms=events.sumOf { if(it.counted) 0L else overlapMs(it.startMs,it.endMs,start,end) }
            if(count==0 && ms==0L) null else RankedTrack(track,count,ms)
        }
        val rows=(if(old.sortByTime) totals.sortedWith(compareByDescending<RankedTrack> { it.listenedMs }.thenBy { it.track.id }) else totals.sortedWith(compareByDescending<RankedTrack> { it.count }.thenByDescending { it.listenedMs }.thenBy { it.track.id })).toPersistentList()
        // History: library songs plus anything else that was played, by when each was last heard.
        val lastPlayed=HashMap<String,Long>().also { m -> allEvents.forEach { e -> if(e.endMs>(m[e.trackId] ?: 0L)) m[e.trackId]=e.endMs } }
        val history=listeningHistory(libraryTracks()+allTracks.filter { it.id in lastPlayed },lastPlayed).toPersistentList()
        mutable.update { it.copy(leaderboard=old.copy(history=history,phase=if(rows.isEmpty()) Phase.Empty else Phase.Ready,startMs=start,endExclusiveMs=end,zone=zone.id,rows=rows,hours=rows.sumOf { r -> r.listenedMs }/3600000.0,count=rows.sumOf { r -> r.count })) }
    }
    private fun updateStats() {
        val old=state.value.stats
        val zone=runCatching {ZoneId.of(state.value.settings.text("timezone","Asia/Hong_Kong"))}.getOrDefault(ZoneId.of("Asia/Hong_Kong"))
        val now=Instant.now()
        val byId=allTracks.associateBy {it.id}
        fun rows(period: io.hkmario.monologue.domain.Period,offset: Int=0): PersistentList<RankedTrack> {
            val range=periodRange(period,offset,zone,now);val start=range.start.toEpochMilli();val end=range.endExclusive.toEpochMilli()
            return allEvents.groupBy {it.trackId}.mapNotNull { (id,events)->
                val count=events.count {it.counted && it.startMs>=start && it.startMs<end}
                val ms=events.sumOf {if(it.counted) 0L else overlapMs(it.startMs,it.endMs,start,end)}
                if(count==0 && ms==0L) null else RankedTrack(byId[id] ?: Track(id,"已移除索引的歌曲",uri=""),count,ms)
            }.sortedWith(compareByDescending<RankedTrack> {it.listenedMs}.thenBy {it.track.id}).toPersistentList()
        }
        val range=periodRange(old.period,old.offset,zone,now)
        mutable.update {it.copy(stats=old.copy(all=rows(io.hkmario.monologue.domain.Period.All),month=rows(io.hkmario.monologue.domain.Period.Month),detail=rows(old.period,old.offset),startMs=range.start.toEpochMilli(),endMs=range.endExclusive.toEpochMilli(),zone=zone.id))}
    }
    private fun verifyToken(token: String) {
        mutable.update { it.copy(listenBrainz=it.listenBrainz.copy(connection=Connection.Verifying,error=null)) }
        launch {
            try { require(token.isNotBlank()) { "請輸入 Token" }; val user=graph.listenBrainz.verify(token.trim()); graph.settings.set("lbAuthInvalid","false"); mutable.update { it.copy(listenBrainz=it.listenBrainz.copy(connection=Connection.Connected,username=user,error=null)) } }
            catch(e: Exception) { mutable.update { it.copy(listenBrainz=it.listenBrainz.copy(connection=if(e is HttpFailure && e.code==401) Connection.InvalidToken else Connection.NetworkError,error=e.message)) } }
        }
    }
    private var prefetchJob: Job?=null
    /** Fetches the next queued song's lyrics a little after a song starts, so they show at once when it plays. */
    private fun prefetchNextLyrics() {
        prefetchJob?.cancel()
        if(!state.value.settings.bool("onlineLyrics")) return
        prefetchJob=viewModelScope.launch {
            delay(8000)
            val queue=state.value.queue
            val next=queue.entries.getOrNull(queue.entries.indexOfFirst { it.id==queue.currentId }+1)?.track ?: return@launch
            if(dao.lyrics(next.id)!=null) return@launch
            try { graph.lyricsSources.find(next,state.value.settings)?.let { dao.lyrics(it) } } catch(e: CancellationException) { throw e } catch(e: Exception) { /* The song's own lookup will try again and report. */ }
        }
    }
    /** Tracks whose stored romaji-only or plain-text lyrics were already re-checked for a better version in this session. */
    private val lyricsRechecked=mutableSetOf<String>()
    /** Tracks whose machine translation already failed in this session, so it is not retried on every settings change. */
    private val translationFailed=mutableSetOf<String>()
    /** Romaji made on the device this session, by track and lyrics text. */
    private val romajiCache=mutableMapOf<String,String>()
    private fun loadLyrics(trackId: String) {
        lyricsJob?.cancel(); lyricsJob=viewModelScope.launch {
            val settings=state.value.settings
            var row=dao.lyrics(trackId)
            val names=graph.lyricsSources.enabledNames(settings); val sourceName=names.joinToString("、").ifBlank {"LRCLIB"}
            // Lyrics saved earlier as romaji only, or as plain text when synced lyrics are preferred: look once per session
            // for something better (the Japanese original, or a version that scrolls); keep the stored ones if nothing better turns up.
            val stored=row
            val preferSynced=settings.bool("preferSyncedLyrics",true)
            if(stored!=null && settings.bool("onlineLyrics") && trackId !in lyricsRechecked && lyricsRank(stored.original,preferSynced)<bestLyricsRank(preferSynced) && !stored.source.startsWith("使用者") && !stored.source.startsWith("本機")) {
                lyricsRechecked+=trackId
                try {
                    allTracks.find {it.id==trackId}?.let { track -> graph.lyricsSources.find(track,settings) }?.takeIf { lyricsRank(it.original,preferSynced)>lyricsRank(stored.original,preferSynced) }?.let { better ->
                        val merged=if(better.romaji==null && looksLikeRomaji(stored.original)) better.copy(romaji=stored.original) else better
                        dao.lyrics(merged); row=merged
                    }
                } catch(e: CancellationException) { throw e } catch(e: Exception) { /* Keep showing the stored lyrics. */ }
            }
            if(row==null && settings.bool("onlineLyrics")) {
                mutable.update { it.copy(lyrics=LyricsUiState(phase=Phase.Loading,trackId=trackId,source="正在查詢$sourceName")) }
                try {
                    allTracks.find {it.id==trackId}?.let {track -> graph.lyricsSources.find(track,settings)?.let {found -> dao.lyrics(found);row=found} }
                } catch(e: CancellationException) {throw e} catch(e: Exception) { mutable.update {it.copy(lyrics=LyricsUiState(Phase.Error,trackId,source=sourceName,error="歌詞服務暫時無法連線（${e.message ?: "網絡錯誤"}）"))};return@launch }
                // Say so plainly instead of the generic empty text, so it is clear the search ran.
                if(row==null) { mutable.update {it.copy(lyrics=LyricsUiState(Phase.Empty,trackId,source=sourceName,error="$sourceName${if(names.size>1) " 都" else " "}找不到這首歌的歌詞"))};return@launch }
            }
            val language=settings.text("translationLanguage","繁體中文")
            var generatedRomaji: String?=null
            fun build(r: LyricsRow?): PersistentList<LyricLine> {
                var lines=r?.let { Lrc.parse(it.original) } ?: persistentListOf()
                if(r?.translation!=null && settings.bool("translations") && r.translationSource?.endsWith(language)==true) lines=Lrc.align(lines,Lrc.parse(r.translation))
                (r?.romaji ?: generatedRomaji)?.let { lines=Lrc.alignRomaji(lines,Lrc.parse(it)) }
                return lines
            }
            val lines=build(row)
            mutable.update { it.copy(lyrics=LyricsUiState(if(lines.isEmpty()) Phase.Empty else Phase.Ready,trackId,lines,row?.source ?: "未有歌詞；可匯入本機 LRC",row?.translationSource,romajiAvailable=row?.romaji!=null)) }
            val current=row ?: return@launch
            // No romaji from the source: make it on the device for Japanese lyrics when romaji is to be shown.
            // Kept in memory only, so it never passes for the source's own romaji.
            val wantsRomaji=settings.text("lyricsDisplay","both")=="romaji" || settings.bool("showRomaji")
            if(current.romaji==null && lines.isNotEmpty() && wantsRomaji && settings.bool("generateRomaji",true) && hasJapaneseScript(current.original)) {
                val key="$trackId:${current.original.hashCode()}"
                if(romajiCache[key]==null) mutable.update { s -> if(s.lyrics.trackId==trackId) s.copy(lyrics=s.lyrics.copy(romajiLoading=true)) else s }
                generatedRomaji=romajiCache[key] ?: try {
                    graph.romaji.generate(current.original).also { if(romajiCache.size>=200) romajiCache.clear(); romajiCache[key]=it }
                } catch(e: CancellationException) { throw e } catch(e: Throwable) { null }
                val withRomaji=build(current)
                mutable.update { s -> if(s.lyrics.trackId==trackId) s.copy(lyrics=s.lyrics.copy(lines=withRomaji,romajiAvailable=generatedRomaji!=null,romajiGenerated=generatedRomaji!=null,romajiLoading=false)) else s }
            }
            // No translation in the chosen language: translate on the device when the user asked for translations.
            if(lines.isNotEmpty() && settings.bool("translations") && settings.bool("autoTranslate",true) && current.translationSource?.endsWith(language)!=true && trackId !in translationFailed) {
                mutable.update { it.copy(lyrics=it.lyrics.copy(translationSource="正在翻譯成$language…（第一次使用需下載約 30 MB 的翻譯模型）")) }
                try {
                    val translated=graph.translator.translate(current.original,language)
                    if(translated==null) { mutable.update { it.copy(lyrics=it.lyrics.copy(translationSource=null)) }; return@launch }
                    val updated=current.copy(translation=translated,translationSource="裝置上機器翻譯（ML Kit）：$language")
                    dao.lyrics(updated)
                    val withTranslation=build(updated)
                    mutable.update { s -> if(s.lyrics.trackId==trackId) s.copy(lyrics=s.lyrics.copy(lines=withTranslation,translationSource=updated.translationSource)) else s }
                } catch(e: CancellationException) { throw e } catch(e: Exception) {
                    translationFailed+=trackId
                    mutable.update { it.copy(lyrics=it.lyrics.copy(translationSource="翻譯未完成：${e.message ?: "請檢查網絡後再試"}")) }
                }
            }
        }
    }
    private fun importLyrics(uri: String, translation: Boolean) {
        val id=state.value.player.entry?.track?.id ?: return
        launch {
            val text=withContext(Dispatchers.IO) { getApplication<Application>().contentResolver.openInputStream(Uri.parse(uri))?.bufferedReader()?.use { it.readText().take(1_000_000) } ?: error("無法開啟歌詞") }
            val old=dao.lyrics(id)
            if(translation) { require(old!=null) { "請先匯入原文歌詞" }; dao.lyrics(old.copy(translation=text,translationSource="使用者匯入：${state.value.settings.text("translationLanguage","繁體中文")}")) }
            else dao.lyrics(LyricsRow(id,text,source="使用者授權的本機歌詞"))
            loadLyrics(id)
        }
    }
    private suspend fun storage() = withContext(Dispatchers.IO) {
        fun size(file: File) = if(file.exists()) file.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0
        val cache=graph.cache
        dao.tracks().filter {it.offlinePath!=null && !File(it.offlinePath!!).isFile}.forEach {dao.offline(it.id,null,null)}
        val s=StorageUiState(cache.cache.cacheSpace,lyricsBytes=dao.lyricsBytes() ?: 0,offlineBytes=cache.offlineRoots.sumOf {size(it)},freeBytes=runCatching {cache.offlineDirectory(state.value.settings).usableSpace}.getOrDefault(0L),artBytes=size(File(getApplication<Application>().cacheDir,"image_cache")),deferredClear=cache.pendingClear)
        mutable.update { it.copy(storage=s) }
    }
    private suspend fun clearStatistics(start: Long,end: Long) {
        require(start<end)
        graph.db.withTransaction {
            val affected=dao.events().filter {if(it.counted) it.startMs>=start && it.startMs<end else overlapMs(it.startMs,it.endMs,start,end)>0}
            dao.deleteEvents(affected.map {it.id})
            for(event in affected.filterNot {it.counted}) {
                if(event.startMs<start) dao.event(event.copy(id=UUID.randomUUID().toString(),endMs=start,listenedMs=start-event.startMs))
                if(event.endMs>end) dao.event(event.copy(id=UUID.randomUUID().toString(),startMs=end,listenedMs=event.endMs-end))
            }
        }
    }
    private suspend fun export(kind: String): String {
        if(kind=="statistics") return "instance_id,track_id,start_epoch_ms,end_epoch_ms,listened_ms,counted\n"+dao.events().joinToString("\n") { e -> "${e.instanceId},\"${e.trackId.replace("\"","\"\"")}\",${e.startMs},${e.endMs},${e.listenedMs},${e.counted}" }
        if(kind=="diagnostics") return "monologue ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nAndroid API ${Build.VERSION.SDK_INT}\n本機曲目：${state.value.library.tracks.size}\n播放狀態：${state.value.player.phase}\n下載成功：${state.value.downloads.success}\n下載失敗：${state.value.downloads.failed}\n不包含 Token、歌曲名稱、帳號、URI 或私人路徑。\n"
        val playlists=JSONArray(); dao.playlists().forEach { p -> playlists.put(JSONObject().put("id",p.id).put("name",p.name).put("trackIds",JSONArray(dao.playlistEntries(p.id).map { it.trackId }))) }
        val settings=JSONObject(); state.value.settings.values.filterKeys { it in SettingsRepository.allowedSettings && it!="lbSync" }.forEach { (k,v) -> settings.put(k,v) }
        return JSONObject().put("format",1).put("settings",settings).put("playlists",playlists).toString(2)
    }
    fun importSettings(uri: Uri) = launch {
        val text=withContext(Dispatchers.IO) { getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("無法讀取設定") }
        require(text.length<2_000_000)
        val obj=JSONObject(text); require(obj.getInt("format")==1)
        graph.settings.import(obj.getJSONObject("settings"))
        val p=obj.optJSONArray("playlists") ?: JSONArray()
        graph.db.withTransaction { for(i in 0 until p.length()) { val item=p.getJSONObject(i); val id=UUID.randomUUID().toString(); dao.putPlaylist(PlaylistRow(id,item.getString("name"))); val tracks=item.getJSONArray("trackIds"); val rows=mutableListOf<PlaylistEntryRow>(); for(n in 0 until tracks.length()) if(dao.track(tracks.getString(n))!=null) rows+=PlaylistEntryRow(UUID.randomUUID().toString(),id,tracks.getString(n),n); dao.putPlaylistEntries(rows) } }
        effectsChannel.send(UiEffect.Message("已匯入設定及可配對的播放清單；憑證未有匯入"))
    }
    suspend fun diagnosticsPreview() = export("diagnostics")
    fun undoQueue(entry: QueueEntry,index: Int)=graph.playback.undo(entry,index)
    fun refreshSystemStatus() {
        val context=getApplication<Application>()
        mutable.update {it.copy(notificationAllowed=context.getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled(),authorizedFolders=context.contentResolver.persistedUriPermissions.filter {p->p.isReadPermission}.map {p->p.uri.toString()}.toPersistentList())}
    }
    fun permissionResult(granted: Boolean) { if(granted) scan() else mutable.update { it.copy(library=it.library.copy(phase=Phase.PermissionRequired,error="未獲授權；請允許讀取音訊或於設定授權")) } }
}
