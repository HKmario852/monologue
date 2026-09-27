package io.hkmario.monologue

import android.app.Application
import android.content.Context
import androidx.room.Room
import io.hkmario.monologue.data.*
import io.hkmario.monologue.cloud.*
import io.hkmario.monologue.playback.PlaybackRepository
import kotlinx.coroutines.*

class MonologueApp: Application(), coil.ImageLoaderFactory {
    override fun newImageLoader()=coil.ImageLoader.Builder(this).components {add(EmbeddedArtworkFetcher.Factory(this@MonologueApp))}.build()
    val graph by lazy { AppGraph(this) }
    override fun onCreate() { super.onCreate(); graph.indexObserver.register(); graph.scope.launch { WorkScheduler.periodicSync(this@MonologueApp,graph.settings.snapshot().bool("lbSync")) } }
}
class AppGraph(val context: Context) {
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    val db=Room.databaseBuilder(context,MusicDatabase::class.java,"monologue.db").addMigrations(MIGRATION_1_2).build()
    val settings=SettingsRepository(context)
    val secrets=SecretStore(context)
    val spotify=SpotifyClient(secrets)
    val online=OnlineRepository(settings,spotify)
    val updates=UpdateRepository(context,settings)
    val drive=DriveClient(context,db.dao())
    val listenBrainz=ListenBrainzClient(secrets,db.dao(),settings)
    val cache by lazy { StreamCache(context) }
    val scanner=MediaScanner(context,db.dao(),settings)
    val playback=PlaybackRepository(this)
    val downloads=DownloadCoordinator(this)
    val lyrics=LyricsClient(context)
    val lyricsSources=LyricsSources(lyrics,NetEaseLyrics(),JLyricProvider(),UtaTenProvider())
    val translator=LyricsTranslator()
    val indexObserver=IndexObserver(this)
}
