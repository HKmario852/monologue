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
        val http=OkHttpDataSource.Factory(graph.drive.streamingClient).setUserAgent("monologue/${BuildConfig.VERSION_NAME}")
        val upstream=DefaultDataSource.Factory(this,http)
        // Streams are fetched half a megabyte at a time, so a skipped song does not keep downloading in the background.
        val network=DefaultDataSource.Factory(this,androidx.media3.datasource.DataSource.Factory {ChunkedDataSource(http.createDataSource())})
        val resolved=androidx.media3.datasource.ResolvingDataSource.Factory(network) {spec->spec.withUri(graph.online.resolveUri(spec.uri))}
        val cached=CacheDataSource.Factory().setCache(graph.cache.cache).setUpstreamDataSourceFactory(resolved)
            .setCacheWriteDataSinkFactory {SafeCacheSink(graph.cache)}
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val player=ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(androidx.media3.datasource.DataSource.Factory {RoutedDataSource(upstream,cached)}))
            // 上一首 always goes to the previous song (in the app, the notification and on headphones), never back to the start.
            .setMaxSeekToPreviousPositionMs(Long.MAX_VALUE)
            // Keep 20–30 s ahead rather than Media3's 50 s: enough to ride out a weak signal, and less wasted on a skip.
            .setLoadControl(androidx.media3.exoplayer.DefaultLoadControl.Builder().setBufferDurationsMs(20_000,30_000,2_500,5_000).build()).build()
        player.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true)
        player.setHandleAudioBecomingNoisy(true)
        player.setWakeMode(C.WAKE_MODE_NETWORK)
        exo=player
        // Covers kept inside downloaded or local audio files need their own loader (see EmbeddedArtworkBitmapLoader).
        val artwork=androidx.media3.session.CacheBitmapLoader(EmbeddedArtworkBitmapLoader(this,androidx.media3.datasource.DataSourceBitmapLoader(this)))
        session=MediaSession.Builder(this,player).setBitmapLoader(artwork).setSessionActivity(PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build()
        graph.playback.attach(player)
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onDestroy() { graph.playback.detach(); session?.release(); exo?.release(); session=null; exo=null; super.onDestroy() }
}
