package io.hkmario.monologue.data

import android.content.ContentUris
import android.content.Context
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.provider.MediaStore
import android.provider.DocumentsContract
import android.net.Uri
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.hkmario.monologue.domain.*

class MediaScanner(private val context: Context, private val dao: MusicDao, private val settings: SettingsRepository) {
    private val mutex=Mutex()
    suspend fun scan() = mutex.withLock { withContext(Dispatchers.IO) {
        val generation=System.currentTimeMillis()
        val prefs=settings.snapshot()
        val hidden=prefs.text("hiddenFolders").split('\n').filter { it.isNotBlank() }
        val minimum=(prefs.number("minDuration",0f)*1000).toLong()
        val projection=arrayOf(MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM,MediaStore.Audio.Media.ALBUM_ID,MediaStore.Audio.Media.DURATION,MediaStore.Audio.Media.DATA,MediaStore.Audio.Media.SIZE,MediaStore.Audio.Media.MIME_TYPE)
        val mediaPermission=if(Build.VERSION.SDK_INT>=33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if(ContextCompat.checkSelfPermission(context,mediaPermission)==PackageManager.PERMISSION_GRANTED) context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,projection,null,null,null)?.use { c ->
            val rows=mutableListOf<TrackRow>()
            while(c.moveToNext()) {
                val mediaId=c.getLong(0); val duration=c.getLong(5); val folder=(c.getString(6) ?: "").substringBeforeLast('/',"")
                if(duration < minimum || hidden.any { folder.contains(it,ignoreCase=true) }) continue
                val id="local:$mediaId"; val old=dao.track(id)
                rows += TrackRow(id,c.getString(1) ?: "未命名",c.getString(2) ?: "未知歌手",c.getString(3) ?: "未知專輯",folder,ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,mediaId).toString(),duration,"content://media/external/audio/albumart/${c.getLong(4)}","Local",favorite=old?.favorite ?: false,bytes=c.getLong(7),mime=c.getString(8) ?: "audio/*",scanGeneration=generation)
            }
            dao.putTracks(rows)
        } ?: error("無法讀取音樂索引")
        context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.forEach { permission -> scanTree(permission.uri,generation,minimum,hidden) }
        dao.finishScan(generation)
    } }
    private suspend fun scanTree(tree: Uri, generation: Long, minimum: Long, hidden: List<String>) {
        val visited=mutableSetOf<String>()
        suspend fun walk(document: String, path: String) {
            if(!visited.add(document)) return
            val audioByStem=mutableMapOf<String,String>()
            val localLyrics=mutableListOf<Pair<String,Uri>>()
            val children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,document)
            context.contentResolver.query(children,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE),null,null,null)?.use { c ->
                while(c.moveToNext()) {
                    val id=c.getString(0); val name=c.getString(1); val mime=c.getString(2)
                    if(hidden.any { "$path/$name".contains(it,true) }) continue
                    if(mime==DocumentsContract.Document.MIME_TYPE_DIR) walk(id,"$path/$name")
                    else if(name.endsWith(".lrc",true)) localLyrics+=name.substringBeforeLast('.').lowercase() to DocumentsContract.buildDocumentUriUsingTree(tree,id)
                    else if(mime.startsWith("audio/")) {
                        val uri=DocumentsContract.buildDocumentUriUsingTree(tree,id)
                        val retriever=MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(context,uri)
                            val duration=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                            if(duration < minimum) continue
                            val key="saf:$uri"; val old=dao.track(key);audioByStem[name.substringBeforeLast('.').lowercase()]=key
                            dao.putTrack(TrackRow(key,retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: name,retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "未知歌手",retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "未知專輯",path,uri.toString(),duration,embeddedArtwork(uri.toString()),"Local",favorite=old?.favorite ?: false,bytes=c.getLong(3),mime=mime,scanGeneration=generation))
                        } catch(e: RuntimeException) { /* Unsupported or corrupt audio is excluded from this scan. */ } finally { retriever.release() }
                    }
                }
            }
            for((stem,uri) in localLyrics) {
                val trackId=audioByStem[stem] ?: continue
                val old=dao.lyrics(trackId)
                if(old?.source?.startsWith("使用者授權")==true) continue
                runCatching {
                    val words=context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {it.readText().take(1_000_000)}
                    if(words!=null) dao.lyrics(LyricsRow(trackId,words,old?.translation,"本機授權資料夾 LRC",old?.translationSource))
                }
            }
        }
        walk(DocumentsContract.getTreeDocumentId(tree),"授權資料夾")
    }
}
