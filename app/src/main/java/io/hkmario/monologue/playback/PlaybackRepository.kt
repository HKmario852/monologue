package io.hkmario.monologue.playback

import android.content.ComponentName
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.*
import androidx.core.content.ContextCompat
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import androidx.room.withTransaction
import io.hkmario.monologue.*
import io.hkmario.monologue.data.*
import io.hkmario.monologue.domain.*
import io.hkmario.monologue.cloud.WorkScheduler
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.UUID
import org.json.*

@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class PlaybackRepository(private val graph: AppGraph) {
    private val scope=graph.scope
    private var player: ExoPlayer?=null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>?=null
    val state=MutableStateFlow(NowPlayingUiState())
    val progress=MutableStateFlow(PlaybackProgress())
    val queue=MutableStateFlow(PlaybackQueueUiState())
    val sleep=MutableStateFlow(SleepTimerUiState())
    val equalizer=MutableStateFlow(EqualizerUiState())
    val vinyl=VinylClock()
    private var eq: Equalizer?=null
    private var eqSession=0
    private var preferences=AppSettingsUiState()
    private var preferenceJob: Job?=null
    private var entries=persistentListOf<QueueEntry>()
    private val handler=Handler(Looper.getMainLooper())
    private var meter=ListeningMeter()
    private var instanceId=UUID.randomUUID().toString()
    private var instanceStart=System.currentTimeMillis()
    private var lbSubmitted=false
    private var lastPlaying=false
    private var measuredTrack: Track?=null
    private val writes=Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var lastCheckpoint=0L
    private var restoring=false
    private var timerOriginalVolume=1f
    /** A song just ended and the next waits out 歌曲之間的靜音; the app shows it as still playing. */
    private var gapPending=false
    private val endGap=Runnable { if(gapPending) { gapPending=false; player?.play(); publish() } }
    /** Another app's music or video took the audio away at this time (0: not waiting). */
    private var focusLostAt=0L
    private var otherSeenPlaying=false
    private var otherQuietSince=0L
    /** The player's playWhenReady before its latest change. */
    private var playWhenReadyBefore=false
    private val audio by lazy { graph.context.getSystemService(android.media.AudioManager::class.java) }
    /** Whether any player is playing now (this app's is paused while it waits). */
    private fun otherMediaPlaying()=audio.isMusicActive
    /**
     * Android gives no focus back after another app took it for good, so watch the other app's playback instead: Android
     * reports each player starting or stopping, and once the other app has played and then stayed quiet for 0.3 s
     * (a moment, so a short gap between its videos does not count), carry on. A once-a-second check backs this up.
     * Gives up after 30 minutes, so music never starts out of nowhere much later.
     */
    private val watchOtherMedia=object: Runnable {
        override fun run() {
            val p=player ?: return
            if(focusLostAt==0L) return
            val now=SystemClock.elapsedRealtime()
            if(now-focusLostAt>30*60_000L) { stopWatchingOtherMedia(); return }
            if(otherMediaPlaying()) { otherSeenPlaying=true; otherQuietSince=0L }
            else if(otherSeenPlaying) {
                if(otherQuietSince==0L) otherQuietSince=now
                if(now-otherQuietSince>=300) { stopWatchingOtherMedia(); p.play(); return }
                handler.postDelayed(this,300-(now-otherQuietSince)); return
            }
            handler.postDelayed(this,1000)
        }
    }
    private val otherPlayback=object: android.media.AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<android.media.AudioPlaybackConfiguration>?) {
            if(focusLostAt!=0L) { handler.removeCallbacks(watchOtherMedia); handler.post(watchOtherMedia) }
        }
    }
    private fun startWatchingOtherMedia() {
        focusLostAt=SystemClock.elapsedRealtime(); otherSeenPlaying=false; otherQuietSince=0L
        handler.removeCallbacks(watchOtherMedia); handler.postDelayed(watchOtherMedia,1000)
        runCatching { audio.unregisterAudioPlaybackCallback(otherPlayback); audio.registerAudioPlaybackCallback(otherPlayback,handler) }
    }
    private fun stopWatchingOtherMedia() {
        focusLostAt=0L; handler.removeCallbacks(watchOtherMedia)
        runCatching { audio.unregisterAudioPlaybackCallback(otherPlayback) }
    }
    private fun cancelGap() { gapPending=false; handler.removeCallbacks(endGap) }
    init { scope.launch { for(write in writes) runCatching { write() } } }
    fun connect() {
        if(controllerFuture!=null) return
        val future=MediaController.Builder(graph.context,SessionToken(graph.context,ComponentName(graph.context,PlaybackService::class.java))).buildAsync()
        controllerFuture=future
        future.addListener({ runCatching { future.get() }.onFailure { state.value=state.value.copy(phase=Phase.Error,error="無法連接播放服務") } },ContextCompat.getMainExecutor(graph.context))
    }
    fun attach(exo: ExoPlayer) {
        player=exo
        exo.addListener(listener); playWhenReadyBefore=exo.playWhenReady
        preferenceJob=scope.launch {
            graph.settings.state.collect { p ->
                preferences=p; exo.setPlaybackSpeed(p.number("speed",1f).coerceIn(0.25f,2f)); exo.setHandleAudioBecomingNoisy(p.bool("noisyPause",true))
                // Silence between songs: pause at the end of each song and start the next after the gap.
                exo.pauseAtEndOfMediaItems=gapMs()>0
                vinyl.configure(System.nanoTime(),allowed=p.bool("vinyl",true) && !p.bool("reduceMotion"))
                if(eq!=null) runCatching { eq?.enabled=p.bool("eqEnabled") }; refreshEq()
            }
        }
        scope.launch { restore() }
        handler.post(tick)
    }
    fun detach() {
        sample(); closeSegment(); saveQueue(); vinyl.configure(System.nanoTime(),playing=false,visible=false)
        preferenceJob?.cancel(); handler.removeCallbacks(tick); eq?.release(); eq=null
        cancelGap(); stopWatchingOtherMedia()
        player?.removeListener(listener); player=null; lastPlaying=false
    }
    private val tick=object: Runnable {
        override fun run() {
            val p=player ?: return
            sample()
            progress.value=progress.value.copy(entryId=p.currentMediaItem?.mediaId,positionMs=p.currentPosition,bufferedMs=p.bufferedPosition)
            val timer=sleep.value
            timer.deadlineElapsedMs?.let { deadline ->
                val remaining=(deadline-SystemClock.elapsedRealtime()).coerceAtLeast(0)
                sleep.value=timer.copy(remainingMs=remaining)
                if(timer.fade && remaining<=5000) p.volume=timerOriginalVolume*remaining/5000f
                if(remaining==0L) { p.pause(); setSleep(0) }
            }
            if(SystemClock.elapsedRealtime()-lastCheckpoint>5000) { saveQueue(); lastCheckpoint=SystemClock.elapsedRealtime() }
            handler.postDelayed(this,500)
        }
    }
    private val listener=object: Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sample(); lastPlaying=player.isPlaying
            if(!player.isPlaying) closeSegment()
            if(player.playbackState==Player.STATE_ENDED && sleep.value.endOfTrack) setSleep(0)
            if(player.playbackState==Player.STATE_ENDED) {
                val endedId=player.currentMediaItem?.mediaId
                if(entries.any {it.id==endedId && it.removeAfterPlaying}) handler.post {
                    val index=entries.indexOfFirst {it.id==endedId && it.removeAfterPlaying}
                    if(index>=0 && this@PlaybackRepository.player?.playbackState==Player.STATE_ENDED) {
                        entries=entries.removeAt(index);this@PlaybackRepository.player?.removeMediaItem(index);saveQueue();publish()
                    }
                }
            }
            vinyl.configure(System.nanoTime(),playing=player.isPlaying)
            publish()
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            sample(); closeSegment()
            val wasId=queue.value.currentId
            val deferred=entries.firstOrNull { it.id==wasId && it.removeAfterPlaying }
            if(sleep.value.endOfTrack && measuredTrack!=null && reason!=Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) { player?.pause(); setSleep(0) }
            meter=ListeningMeter(); instanceId=UUID.randomUUID().toString(); instanceStart=System.currentTimeMillis(); lbSubmitted=false
            measuredTrack=entries.firstOrNull { it.id==mediaItem?.mediaId }?.track
            lastPlaying=player?.isPlaying==true
            meter.update(SystemClock.elapsedRealtime(),System.currentTimeMillis(),false)
            progress.value=PlaybackProgress(entryId=mediaItem?.mediaId)
            graph.cache.pin(setOfNotNull(mediaItem?.localConfiguration?.customCacheKey))
            if(deferred!=null) handler.post {
                if(deferred.id!=player?.currentMediaItem?.mediaId) remove(deferred.id)
                else if(reason==Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    val index=entries.indexOfFirst {it.id==deferred.id}
                    if(index>=0) {entries=entries.removeAt(index);player?.removeMediaItem(index);saveQueue();publish()}
                }
            }
            saveQueue(); publish()
        }
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // Media3 keeps the audio focus while paused and reports losing it as a pause too, so only music that was
            // playing (or waiting in the silence between songs) when another app took the audio carries on afterwards.
            val wasPlaying=playWhenReadyBefore || gapPending; playWhenReadyBefore=playWhenReady
            when {
                // Another app's music or video took the audio away: wait for it to stop, then carry on.
                !playWhenReady && reason==Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> {
                    if(gapPending) { cancelGap(); publish() }
                    if(wasPlaying && preferences.bool("resumeInterruption",true)) startWatchingOtherMedia()
                }
                playWhenReady || reason==Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> stopWatchingOtherMedia()
            }
            // The song ended with 歌曲之間的靜音 on: wait, then play on (unless it was the last song).
            if(!playWhenReady && reason==Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                val p=player ?: return
                if(!p.hasNextMediaItem()) { p.play(); return }
                gapPending=true; handler.removeCallbacks(endGap); handler.postDelayed(endGap,gapMs().coerceAtLeast(1)); publish()
            }
        }
        override fun onPlaybackSuppressionReasonChanged(reason: Int) {
            if(reason!=Player.PLAYBACK_SUPPRESSION_REASON_NONE && !preferences.bool("resumeInterruption",true)) player?.pause()
        }
        override fun onAudioSessionIdChanged(audioSessionId: Int) { attachEq(audioSessionId) }
        override fun onPlayerError(error: PlaybackException) { state.value=state.value.copy(phase=Phase.Error,error="播放失敗：${error.errorCodeName}。請檢查檔案、授權及音訊格式。") }
    }
    /** The stretch of listening going on now, kept in memory between writes. */
    private var segment: ListenEvent?=null
    private var segmentWrittenAt=0L
    private fun writeSegment() { val s=segment ?: return; segmentWrittenAt=SystemClock.elapsedRealtime(); writes.trySend { graph.db.dao().putEvent(s) } }
    /** Saves the current stretch for good: on pause, song change, a gap in listening, or the player going away. */
    private fun closeSegment() { writeSegment(); segment=null }
    private fun sample() {
        val track=measuredTrack
        val oldTotal=meter.totalMs
        val slice=meter.update(SystemClock.elapsedRealtime(),System.currentTimeMillis(),lastPlaying)
        if(track==null || slice==null || slice.elapsed<=0) return
        val duration=player?.duration?.takeIf { it>0 } ?: track.durationMs
        val shouldCount=meter.markCount(duration)
        val currentInstance=instanceId
        if(preferences.bool("statistics",true)) {
            // One row per stretch of continuous listening, grown slice by slice, rather than a row every half second:
            // every write makes the statistics re-read all events, so writes are kept to one every 15 s while playing.
            val open=segment
            if(open!=null && open.instanceId==currentInstance && slice.start-open.endMs in -1000L..1000L) segment=open.copy(endMs=slice.end,listenedMs=open.listenedMs+slice.elapsed)
            else { closeSegment(); segment=ListenEvent(UUID.randomUUID().toString(),currentInstance,track.id,slice.start,slice.end,slice.elapsed,false) }
            if(SystemClock.elapsedRealtime()-segmentWrittenAt>=15_000 || shouldCount) writeSegment()
            if(shouldCount) writes.trySend {
                val thresholdTime=slice.start+(countThreshold(duration)-oldTotal).coerceIn(0,slice.elapsed)
                graph.db.dao().event(ListenEvent("count:$currentInstance",currentInstance,track.id,thresholdTime,thresholdTime,0,true))
            }
        }
        if(!lbSubmitted && preferences.bool("lbSync") && meter.totalMs>=listenBrainzThreshold(duration)) {
            val owner=graph.secrets.get("lb-user") ?: return
            lbSubmitted=true
            val payload=JSONObject().put("listened_at",instanceStart/1000).put("track_metadata",JSONObject().put("track_name",track.title).put("artist_name",track.artist).put("release_name",track.album).put("additional_info",JSONObject().put("submission_client","monologue").put("submission_client_version",BuildConfig.VERSION_NAME).put("duration_ms",duration.coerceAtLeast(0))))
            writes.trySend { graph.db.dao().enqueueListen(OutboxRow(currentInstance,owner,payload.toString(),System.currentTimeMillis())); WorkScheduler.sync(graph.context) }
        }
    }
    private fun publish() {
        val p=player ?: return
        val entry=entries.firstOrNull { it.id==p.currentMediaItem?.mediaId }
        queue.value=PlaybackQueueUiState(entries,entry?.id)
        progress.value=progress.value.copy(entryId=entry?.id,positionMs=p.currentPosition,bufferedMs=p.bufferedPosition)
        val format=p.audioFormat
        val quality=format?.let {f->listOfNotNull(f.sampleMimeType, f.bitrate.takeIf {it>0}?.let {"${it/1000} kbps"}, f.sampleRate.takeIf {it>0}?.let {"$it Hz"}, f.channelCount.takeIf {it>0}?.let {"$it 聲道"}).joinToString(" · ")}
        state.value=NowPlayingUiState(if(p.playerError!=null) Phase.Error else if(entry==null) Phase.Empty else Phase.Ready,entry,p.isPlaying || gapPending,p.playbackState==Player.STATE_BUFFERING,p.isCurrentMediaItemSeekable,p.duration.coerceAtLeast(0),p.shuffleModeEnabled,p.repeatMode,p.playbackParameters.speed,if(p.playerError!=null) state.value.error else null,audioFormat=quality)
    }
    private fun item(entry: QueueEntry): MediaItem {
        val t=entry.track
        val offline=t.offlinePath?.let(::File)?.takeIf { it.isFile && it.length()>0 }
        val uri=if(preferences.bool("offlineFirst",true) && offline!=null) Uri.fromFile(offline) else if(t.source==Source.Online) Uri.parse(t.uri).buildUpon().appendQueryParameter("quality",preferences.text("audioQuality","best")).build() else Uri.parse(t.uri)
        return MediaItem.Builder().setMediaId(entry.id).setUri(uri).setCustomCacheKey(if(t.source==Source.Drive) "${t.id}:${t.remoteVersion}" else if(t.source==Source.Online) "${t.id}:${t.remoteVersion}:${preferences.text("audioQuality","best")}" else t.id)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).setArtworkUri(t.artwork?.let(Uri::parse)).build()).build()
    }
    fun play(tracks: List<Track>) {
        val p=player ?: return
        if(tracks.isEmpty()) return
        cancelGap(); entries=tracks.map { QueueEntry(track=it) }.toPersistentList()
        p.setMediaItems(entries.map(::item)); p.prepare(); p.play(); saveQueue(); publish()
    }
    fun enqueue(track: Track, next: Boolean) {
        val p=player ?: return; val e=QueueEntry(track=track)
        val index=if(next) (p.currentMediaItemIndex+1).coerceIn(0,entries.size) else entries.size
        entries=entries.add(index,e); p.addMediaItem(index,item(e)); if(p.playbackState==Player.STATE_IDLE) p.prepare(); saveQueue(); publish()
    }
    private fun gapMs()=(preferences.text("gapSeconds","0").toIntOrNull() ?: 0).coerceIn(0,30)*1000L
    fun toggle() {
        // Pausing during the gap keeps the next song from starting.
        if(gapPending) { cancelGap(); publish(); return }
        player?.let { if(it.isPlaying) it.pause() else { if(it.playbackState==Player.STATE_IDLE) it.prepare(); it.play() } }
    }
    // Skipping during the gap starts the chosen song right away, as if it had been playing.
    fun next() { val resume=gapPending; cancelGap(); player?.seekToNextMediaItem(); if(resume) player?.play() }
    fun previous() { val resume=gapPending; cancelGap(); player?.seekToPrevious(); if(resume) player?.play() }
    fun shuffle() { player?.let { it.shuffleModeEnabled=!it.shuffleModeEnabled }; saveQueue() }
    fun repeat() { player?.let { it.repeatMode=when(it.repeatMode) {Player.REPEAT_MODE_OFF->Player.REPEAT_MODE_ONE;Player.REPEAT_MODE_ONE->Player.REPEAT_MODE_ALL;else->Player.REPEAT_MODE_OFF} }; saveQueue() }
    fun seekPreview(ms: Long?) { progress.value=progress.value.copy(seekPreview=ms) }
    fun seekCommit() { val s=progress.value; if(s.entryId==state.value.entry?.id && state.value.seekable) s.seekPreview?.let { player?.seekTo(it) }; seekPreview(null) }
    fun playEntry(id: String) { val index=entries.indexOfFirst { it.id==id }; if(index>=0) { cancelGap(); player?.seekToDefaultPosition(index); player?.play() } }
    fun move(id: String, destination: Int) {
        val from=entries.indexOfFirst { it.id==id }; if(from<0) return
        val to=destination.coerceIn(0,entries.lastIndex); val e=entries[from]
        entries=entries.removeAt(from).add(to,e); player?.moveMediaItem(from,to); saveQueue(); publish()
    }
    fun remove(id: String): Pair<QueueEntry,Int>? {
        val index=entries.indexOfFirst { it.id==id }; if(index<0) return null
        val entry=entries[index]
        if(player?.currentMediaItem?.mediaId==id) {
            entries=entries.set(index,entry.copy(removeAfterPlaying=true)); saveQueue(); publish(); return entry to index
        }
        entries=entries.removeAt(index); player?.removeMediaItem(index); saveQueue(); publish(); return entry to index
    }
    fun undo(entry: QueueEntry, index: Int) {
        val existing=entries.indexOfFirst { it.id==entry.id }
        if(existing>=0) entries=entries.set(existing,entry.copy(removeAfterPlaying=false))
        else { val at=index.coerceIn(0,entries.size); entries=entries.add(at,entry); player?.addMediaItem(at,item(entry)) }
        saveQueue(); publish()
    }
    /** Returns what was removed, in removal order, so [restore] can undo it. */
    fun clearUpcoming(): List<Pair<QueueEntry,Int>> = entries.filter { it.id!=state.value.entry?.id }.map { it.id }.mapNotNull(::remove)
    fun restore(removed: List<Pair<QueueEntry,Int>>) { removed.asReversed().forEach { (entry,index) -> undo(entry,index) } }
    fun refreshTracks(tracks: List<Track>) {
        val map=tracks.associateBy { it.id }
        entries=entries.map { it.copy(track=map[it.track.id] ?: it.track) }.toPersistentList(); publish()
    }
    private fun saveQueue() {
        if(restoring) return
        val p=player ?: return; val snapshot=entries
        val checkpoint=PlaybackCheckpoint(entryId=p.currentMediaItem?.mediaId,positionMs=p.currentPosition,shuffle=p.shuffleModeEnabled,repeat=p.repeatMode)
        writes.trySend { graph.db.withTransaction { val dao=graph.db.dao(); dao.clearQueue(); dao.putQueue(snapshot.mapIndexed { i,e -> QueueRow(e.id,e.track.id,i,e.removeAfterPlaying) }); dao.checkpoint(checkpoint) } }
    }
    private suspend fun restore() {
        restoring=true
        try {
            preferences=graph.settings.snapshot()
            if(!preferences.bool("restoreQueue",true)) return
            val dao=graph.db.dao(); val rows=dao.queue(); val checkpoint=dao.checkpoint()
            if(player?.mediaItemCount!=0) return
            entries=rows.mapNotNull { row -> dao.track(row.trackId)?.model()?.let { QueueEntry(row.id,it,row.removeAfter) } }.toPersistentList()
            if(entries.isNotEmpty()) {
                val index=entries.indexOfFirst { it.id==checkpoint?.entryId }.coerceAtLeast(0)
                player?.setMediaItems(entries.map(::item),index,checkpoint?.positionMs ?: 0)
                player?.shuffleModeEnabled=checkpoint?.shuffle ?: false; player?.repeatMode=when(checkpoint?.repeat) {1->Player.REPEAT_MODE_ONE;2->Player.REPEAT_MODE_ALL;else->Player.REPEAT_MODE_OFF}
                player?.prepare(); if(preferences.bool("autoplay")) player?.play()
            }
        } finally { restoring=false; publish() }
    }
    fun setSleep(minutes: Int, endOfTrack: Boolean=false) {
        val p=player
        if(sleep.value.deadlineElapsedMs!=null) p?.volume=timerOriginalVolume
        timerOriginalVolume=p?.volume ?: 1f
        sleep.value=if(minutes<=0 && !endOfTrack) SleepTimerUiState() else SleepTimerUiState(if(endOfTrack) null else SystemClock.elapsedRealtime()+minutes*60_000L,minutes*60_000L,endOfTrack,preferences.bool("sleepFade"))
    }
    private fun attachEq(session: Int) {
        if(session==eqSession && eq!=null) return
        eq?.release(); eq=null; eqSession=session
        if(session==0) return
        runCatching {
            eq=Equalizer(0,session).apply { enabled=preferences.bool("eqEnabled") }
            scope.launch { eq?.let { effect -> for(i in 0 until effect.numberOfBands) preferences.text("eqBand.$i").toShortOrNull()?.let { effect.setBandLevel(i.toShort(),it) }; refreshEq() } }
        }.onFailure { equalizer.value=EqualizerUiState(reason="此裝置未提供可用等化器") }
        refreshEq()
    }
    private fun refreshEq() {
        val e=eq ?: return
        runCatching { equalizer.value=EqualizerUiState(true,e.enabled,(0 until e.numberOfBands).map { EqualizerBand(it,e.getCenterFreq(it.toShort())/1000,e.getBandLevel(it.toShort()).toInt()) }.toPersistentList(),e.bandLevelRange[0].toInt(),e.bandLevelRange[1].toInt(),(0 until e.numberOfPresets).map { e.getPresetName(it.toShort()) }.toPersistentList(),"") }
    }
    fun eqEnabled(value: Boolean) { runCatching { eq?.enabled=value }; scope.launch { graph.settings.set("eqEnabled",value.toString()) }; refreshEq() }
    fun eqBand(band: Int, value: Int) { runCatching { eq?.setBandLevel(band.toShort(),value.coerceIn(equalizer.value.min,equalizer.value.max).toShort()) }; scope.launch { graph.settings.set("eqBand.$band",value.toString()) }; refreshEq() }
    fun eqPreset(index: Int) { runCatching { eq?.usePreset(index.toShort()) }; refreshEq(); equalizer.value.bands.forEach { eqBand(it.index,it.level) } }
    fun eqReset() { equalizer.value.bands.forEach { eqBand(it.index,0) } }
}
