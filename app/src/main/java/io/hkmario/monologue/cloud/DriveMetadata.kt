package io.hkmario.monologue.cloud

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import io.hkmario.monologue.domain.Track
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Lets MediaMetadataRetriever read tags from a Drive file with HTTP range requests,
 * so artist/album/cover appear without downloading whole songs.
 * Reads are capped: tags live at the start (ID3v2, FLAC, Ogg) or end (ID3v1, some M4A moov boxes).
 */
class DriveRangeSource(private val http: OkHttpClient, private val url: String, private val token: String, private val size: Long, private val budget: Long = 12L*1024*1024): MediaDataSource() {
    private val block=256*1024
    private val blocks=object: LinkedHashMap<Long,ByteArray>(16,0.75f,true) { override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long,ByteArray>?)=size>24 }
    private var fetched=0L
    /** Network/auth failures, kept so callers can retry later instead of treating the file as untagged. */
    var failure: IOException?=null; private set
    private fun load(index: Long): ByteArray = try { fetch(index) } catch(e: IOException) { if(e !is BudgetExceeded) failure=e; throw e }
    private class BudgetExceeded: IOException("metadata read budget exceeded")
    private fun fetch(index: Long): ByteArray = blocks.getOrPut(index) {
        val start=index*block; val end=minOf(size,start+block)-1
        if(fetched+(end-start+1)>budget) throw BudgetExceeded()
        http.newCall(Request.Builder().url(url).header("Authorization","Bearer $token").header("Range","bytes=$start-$end").build()).execute().use { r ->
            if(r.code==401) throw AuthorizationNeeded()
            if(r.code!=206 && r.code!=200) throw driveFailure(r.code,r.body?.string())
            val bytes=r.body?.bytes() ?: throw IOException("empty range response")
            // A server that ignores Range returns the whole file from byte 0; keep only the requested block.
            val data=if(r.code==200) bytes.copyOfRange(start.toInt().coerceAtMost(bytes.size),(end+1).toInt().coerceAtMost(bytes.size)) else bytes
            fetched+=data.size; data
        }
    }
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if(position>=size) return -1
        var copied=0
        while(copied<length && position+copied<size) {
            val at=position+copied; val data=load(at/block); val inBlock=(at%block).toInt()
            if(inBlock>=data.size) break
            val n=minOf(length-copied,data.size-inBlock)
            System.arraycopy(data,inBlock,buffer,offset+copied,n); copied+=n
        }
        return if(copied==0) -1 else copied
    }
    override fun getSize()=size
    override fun close() { blocks.clear() }
}

/** Reads embedded tags and cover art of a Drive track; returns null when the file carries nothing usable. */
@Throws(IOException::class)
fun readDriveTags(context: Context, http: OkHttpClient, token: String, track: Track): Track? {
    if(track.bytes<=0) return null
    val retriever=MediaMetadataRetriever()
    try {
        val source=DriveRangeSource(http,track.uri,token,track.bytes)
        try { retriever.setDataSource(source) } catch(e: RuntimeException) { source.failure?.let { throw it }; return null }
        fun key(k: Int)=retriever.extractMetadata(k)?.trim()?.takeIf { it.isNotEmpty() }
        val title=key(MediaMetadataRetriever.METADATA_KEY_TITLE)
        val artist=key(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: key(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
        val album=key(MediaMetadataRetriever.METADATA_KEY_ALBUM)
        val duration=key(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val art=retriever.embeddedPicture?.let { saveCover(context,track.id,it) }
        return track.copy(title=title ?: track.title,artist=artist ?: track.artist,album=album ?: track.album,durationMs=if(duration>0) duration else track.durationMs,artwork=art ?: track.artwork)
    } catch(e: RuntimeException) { return null }
    finally { retriever.release() }
}

private fun saveCover(context: Context, trackId: String, data: ByteArray): String? {
    if(data.size>32*1024*1024) return null
    val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
    BitmapFactory.decodeByteArray(data,0,data.size,bounds)
    if(bounds.outWidth<=0) return null
    var sample=1; while(maxOf(bounds.outWidth,bounds.outHeight)/sample>600) sample*=2
    val bitmap=BitmapFactory.decodeByteArray(data,0,data.size,BitmapFactory.Options().apply { inSampleSize=sample }) ?: return null
    val dir=File(context.filesDir,"drive-art").apply { mkdirs() }
    val file=File(dir,trackId.removePrefix("drive:").replace(Regex("[^A-Za-z0-9_-]"),"_")+".jpg")
    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,88,it) }
    bitmap.recycle()
    return android.net.Uri.fromFile(file).toString()+"?v="+file.lastModified()
}
