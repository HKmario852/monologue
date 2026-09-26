package io.hkmario.monologue.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.cache.*
import androidx.media3.session.*
import io.hkmario.monologue.*
import io.hkmario.monologue.data.*
import kotlinx.coroutines.*

@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class PlaybackService: MediaSessionService() {
    private var session: MediaSession?=null
    private var exo: ExoPlayer?=null
    private val graph get()=(application as MonologueApp).graph
    override fun onCreate() {
        super.onCreate()
        val upstream=DefaultDataSource.Factory(this,OkHttpDataSource.Factory(graph.drive.streamingClient).setUserAgent("monologue/${BuildConfig.VERSION_NAME}"))
        val resolved=androidx.media3.datasource.ResolvingDataSource.Factory(upstream) {spec->spec.withUri(graph.online.resolveUri(spec.uri))}
        val cached=CacheDataSource.Factory().setCache(graph.cache.cache).setUpstreamDataSourceFactory(resolved)
            .setCacheWriteDataSinkFactory {SafeCacheSink(graph.cache)}
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val player=ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(androidx.media3.datasource.DataSource.Factory {RoutedDataSource(upstream,cached)})).build()
        player.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true)
        player.setHandleAudioBecomingNoisy(true)
        player.setWakeMode(C.WAKE_MODE_NETWORK)
        exo=player
        session=MediaSession.Builder(this,player).setSessionActivity(PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build()
        graph.playback.attach(player)
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onDestroy() { graph.playback.detach(); session?.release(); exo?.release(); session=null; exo=null; super.onDestroy() }
}
