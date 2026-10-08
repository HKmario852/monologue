package io.hkmario.monologue

import android.app.Application
import android.content.Context
import androidx.room.Room
import io.hkmario.monologue.data.*
import io.hkmario.monologue.cloud.*
import io.hkmario.monologue.playback.PlaybackRepository
import kotlinx.coroutines.*

class MonologueApp: Application(), coil.ImageLoaderFactory, androidx.work.Configuration.Provider {
    /** WorkManager starts the first time it is used instead of while the app launches. */
    override val workManagerConfiguration get()=androidx.work.Configuration.Builder().build()
    override fun newImageLoader()=coil.ImageLoader.Builder(this).components {add(EmbeddedArtworkFetcher.Factory(this@MonologueApp))}.build()
    val graph by lazy { AppGraph(this) }
    override fun onCreate() { super.onCreate(); graph.indexObserver.register(); val launchedAt=System.currentTimeMillis(); graph.scope.launch(Dispatchers.IO) { WorkScheduler.periodicSync(this@MonologueApp,graph.settings.snapshot().bool("lbSync")); runCatching { compactListeningEvents(graph.db,graph.settings,launchedAt) } } }
}
class AppGraph(val context: Context) {
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    val db=Room.databaseBuilder(context,MusicDatabase::class.java,"monologue.db").addMigrations(MIGRATION_1_2,MIGRATION_2_3).build()
    val settings=SettingsRepository(context)
    val secrets=SecretStore(context)
    val spotify=SpotifyClient(secrets)
    val online=OnlineRepository(settings,spotify,context)
    val updates=UpdateRepository(context,settings)
    val drive=DriveClient(context,db.dao())
    val listenBrainz=ListenBrainzClient(context,secrets,db.dao(),settings)
    val cache by lazy { StreamCache(context) }
    val scanner=MediaScanner(context,db.dao(),settings)
    val playback=PlaybackRepository(this)
    val downloads=DownloadCoordinator(this)
    val lyrics=LyricsClient(context)
    val romaji=RomajiGenerator()
    val lyricsSources=LyricsSources(lyrics,NetEaseLyrics(),JLyricProvider(),UtaTenProvider(),BahamutLyrics(romaji),VocaDbLyrics(),KanogomaLyrics(),ThbWikiLyrics())
    val translator=LyricsTranslator(context)
    val indexObserver=IndexObserver(this)
}
