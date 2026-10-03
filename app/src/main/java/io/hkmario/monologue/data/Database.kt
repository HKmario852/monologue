package io.hkmario.monologue.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import io.hkmario.monologue.domain.*

@Entity(tableName="tracks")
data class TrackRow(@PrimaryKey val id: String, val title: String, val artist: String, val album: String, val folder: String, val uri: String, val durationMs: Long, val artwork: String?, val source: String, val favorite: Boolean = false, val offlinePath: String? = null, val remoteVersion: String? = null, val downloadedVersion: String? = null, val bytes: Long = 0, val checksum: String? = null, val mime: String = "audio/mpeg", val scanGeneration: Long = 0, @androidx.room.ColumnInfo(defaultValue="0") val addedMs: Long = 0) {
    fun model() = Track(id,title,artist,album,folder,uri,durationMs,artwork,Source.valueOf(source),favorite,offlinePath,remoteVersion,downloadedVersion,bytes,checksum,mime,addedMs)
}
fun Track.row(generation: Long = 0) = TrackRow(id,title,artist,album,folder,uri,durationMs,artwork,source.name,favorite,offlinePath,remoteVersion,downloadedVersion,bytes,checksum,mime,generation,addedMs)
@Entity(tableName="playlists") data class PlaylistRow(@PrimaryKey val id: String, val name: String)
@Entity(tableName="playlist_entries", indices=[Index("playlistId")], foreignKeys=[ForeignKey(entity=PlaylistRow::class,parentColumns=["id"],childColumns=["playlistId"],onDelete=ForeignKey.CASCADE)])
data class PlaylistEntryRow(@PrimaryKey val id: String, val playlistId: String, val trackId: String, val position: Int)
@Entity(tableName="queue_entries") data class QueueRow(@PrimaryKey val id: String, val trackId: String, val position: Int, val removeAfter: Boolean = false)
@Entity(tableName="playback_checkpoint") data class PlaybackCheckpoint(@PrimaryKey val id: Int = 1, val entryId: String?, val positionMs: Long, val shuffle: Boolean, val repeat: Int)
@Entity(tableName="listening_events", indices=[Index("trackId"),Index("startMs")])
data class ListenEvent(@PrimaryKey val id: String, val instanceId: String, val trackId: String, val startMs: Long, val endMs: Long, val listenedMs: Long, val counted: Boolean)
@Entity(tableName="outbox", indices=[Index("owner")]) data class OutboxRow(@PrimaryKey val id: String, val owner: String, val payload: String, val createdAt: Long, val error: String? = null)
@Entity(tableName="downloads", indices=[Index("trackId")]) data class DownloadRow(@PrimaryKey val id: String, val trackId: String, val title: String, val version: String, val checksum: String?, val size: Long, val status: String = DownloadStatus.Queued.name, val received: Long = 0, val error: String? = null, val sequence: Long = System.currentTimeMillis()) {
    fun model() = DownloadItem(id,trackId,title,DownloadStatus.valueOf(status),received,size,error)
}
@Entity(tableName="download_control") data class DownloadControl(@PrimaryKey val id: Int = 1, val phase: String = DownloadPhase.Idle.name, val pauseBetween: Boolean = false, val cancelled: Boolean = false)
@Entity(tableName="lyrics") data class LyricsRow(@PrimaryKey val trackId: String, val original: String, val translation: String? = null, val source: String, val translationSource: String? = null, val romaji: String? = null)

@Dao interface MusicDao {
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE") fun observeTracks(): Flow<List<TrackRow>>
    @Query("SELECT * FROM tracks") suspend fun tracks(): List<TrackRow>
    @Query("SELECT * FROM tracks WHERE id=:id") suspend fun track(id: String): TrackRow?
    @Upsert suspend fun putTrack(track: TrackRow)
    @Upsert suspend fun putTracks(tracks: List<TrackRow>)
    @Query("UPDATE tracks SET favorite=:value WHERE id=:id") suspend fun favorite(id: String, value: Boolean)
    @Query("UPDATE tracks SET offlinePath=:path, downloadedVersion=:version WHERE id=:id") suspend fun offline(id: String, path: String?, version: String?)
    @Query("DELETE FROM tracks WHERE source='Local' AND scanGeneration!=:generation") suspend fun finishScan(generation: Long)
    @Query("DELETE FROM tracks WHERE source='Local'") suspend fun clearLocalIndex()
    @Query("SELECT * FROM playlists ORDER BY name") fun observePlaylists(): Flow<List<PlaylistRow>>
    @Query("SELECT * FROM playlist_entries ORDER BY position") fun observePlaylistEntries(): Flow<List<PlaylistEntryRow>>
    @Query("SELECT * FROM playlists") suspend fun playlists(): List<PlaylistRow>
    @Query("SELECT * FROM playlist_entries WHERE playlistId=:id ORDER BY position") suspend fun playlistEntries(id: String): List<PlaylistEntryRow>
    @Upsert suspend fun putPlaylist(row: PlaylistRow)
    @Query("DELETE FROM playlists WHERE id=:id") suspend fun deletePlaylist(id: String)
    @Upsert suspend fun putPlaylistEntries(rows: List<PlaylistEntryRow>)
    @Query("DELETE FROM playlist_entries WHERE playlistId=:id") suspend fun clearPlaylistEntries(id: String)
    @Query("SELECT * FROM queue_entries ORDER BY position") suspend fun queue(): List<QueueRow>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putQueue(rows: List<QueueRow>)
    @Query("DELETE FROM queue_entries") suspend fun clearQueue()
    @Upsert suspend fun checkpoint(row: PlaybackCheckpoint)
    @Query("SELECT * FROM playback_checkpoint WHERE id=1") suspend fun checkpoint(): PlaybackCheckpoint?
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun event(row: ListenEvent)
    @Query("SELECT * FROM listening_events ORDER BY startMs DESC") fun observeEvents(): Flow<List<ListenEvent>>
    @Query("SELECT * FROM listening_events ORDER BY startMs") suspend fun events(): List<ListenEvent>
    @Query("DELETE FROM listening_events WHERE startMs>=:start AND startMs<:end") suspend fun clearEvents(start: Long, end: Long)
    @Query("DELETE FROM listening_events WHERE id IN (:ids)") suspend fun deleteEvents(ids: List<String>)
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun enqueueListen(row: OutboxRow)
    @Query("SELECT * FROM outbox ORDER BY createdAt") fun observeOutbox(): Flow<List<OutboxRow>>
    @Query("SELECT * FROM outbox WHERE owner=:owner ORDER BY createdAt LIMIT 100") suspend fun pending(owner: String): List<OutboxRow>
    @Query("DELETE FROM outbox WHERE id IN (:ids)") suspend fun acknowledge(ids: List<String>)
    @Query("DELETE FROM outbox WHERE owner=:owner") suspend fun discardOutbox(owner: String)
    @Query("UPDATE outbox SET error=:error WHERE id IN (:ids)") suspend fun outboxError(ids: List<String>, error: String)
    @Query("SELECT * FROM downloads ORDER BY sequence,id") fun observeDownloads(): Flow<List<DownloadRow>>
    @Query("SELECT * FROM downloads ORDER BY sequence,id") suspend fun downloads(): List<DownloadRow>
    @Upsert suspend fun putDownload(row: DownloadRow)
    @Query("SELECT * FROM download_control WHERE id=1") fun observeDownloadControl(): Flow<DownloadControl?>
    @Query("SELECT * FROM download_control WHERE id=1") suspend fun control(): DownloadControl?
    @Upsert suspend fun control(value: DownloadControl)
    @Query("UPDATE downloads SET status='Queued',received=0,error=NULL WHERE status='Failed'") suspend fun retryFailed()
    @Query("UPDATE downloads SET status='Queued' WHERE status='Downloading'") suspend fun recoverDownloads()
    @Query("UPDATE downloads SET status='Cancelled' WHERE status IN ('Queued','Downloading')") suspend fun cancelDownloads()
    @Query("SELECT * FROM lyrics WHERE trackId=:id") suspend fun lyrics(id: String): LyricsRow?
    @Upsert suspend fun lyrics(row: LyricsRow)
    @Query("DELETE FROM lyrics") suspend fun clearLyrics()
    @Query("DELETE FROM lyrics WHERE trackId=:id") suspend fun deleteLyrics(id: String)
    @Query("SELECT SUM(LENGTH(CAST(original AS BLOB))+COALESCE(LENGTH(CAST(translation AS BLOB)),0)) FROM lyrics") suspend fun lyricsBytes(): Long?
}
@Database(entities=[TrackRow::class,PlaylistRow::class,PlaylistEntryRow::class,QueueRow::class,PlaybackCheckpoint::class,ListenEvent::class,OutboxRow::class,DownloadRow::class,DownloadControl::class,LyricsRow::class], version=3, exportSchema=true)
abstract class MusicDatabase: RoomDatabase() { abstract fun dao(): MusicDao }
/** 2: lyrics gain an optional romanised (romaji) LRC. Existing lyrics stay as they are. */
val MIGRATION_1_2=object: androidx.room.migration.Migration(1,2) { override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("ALTER TABLE lyrics ADD COLUMN romaji TEXT") } }
/** 加入時間: filled in by the next Drive listing and library scan. */
val MIGRATION_2_3=object: androidx.room.migration.Migration(2,3) { override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("ALTER TABLE tracks ADD COLUMN addedMs INTEGER NOT NULL DEFAULT 0") } }
