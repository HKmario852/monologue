package io.hkmario.monologue.data

import android.net.Uri
import androidx.media3.datasource.*
import androidx.media3.datasource.cache.*

/** Cache write refusal must not interrupt upstream playback. No writes can exceed admitted space. */
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class SafeCacheSink(private val store: StreamCache): DataSink {
    private val sink=CacheDataSink(store.cache,2*1024*1024)
    private var key: String?=null
    private var failed=false
    override fun open(dataSpec: DataSpec) {
        key=dataSpec.key;failed=false
        // Enforce bounded fragments even for unknown Content-Length, so reservation >= pending bytes.
        val bounded=dataSpec.buildUpon().setFlags(dataSpec.flags or DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION).build()
        try {sink.open(bounded)} catch(e: CacheDataSink.CacheDataSinkException) {failed=true;runCatching {sink.close()}}
    }
    override fun write(buffer: ByteArray,offset: Int,length: Int) {if(!failed) try {sink.write(buffer,offset,length)} catch(e: CacheDataSink.CacheDataSinkException) {failed=true;runCatching {sink.close()}}}
    override fun close() {try {runCatching {sink.close()}} finally {key?.let(store.evictor::releaseReservations)}}
}
/**
 * Reads an HTTPS stream with bounded range requests of [chunk] bytes instead of one open-ended request. With an
 * open-ended request the network keeps filling the socket buffers with the rest of the file while the player waits,
 * so a song skipped after a few seconds still cost most of its size in data (measured: a whole 4.5 MB file within
 * 2 s of starting). The length comes from the first response's Content-Range, so duration and seeking still work.
 * A server that ignores Range (no Content-Range) is read in one request, as before.
 */
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class ChunkedDataSource(private val upstream: DataSource,private val chunk: Long=512L*1024): DataSource {
    private val unset=androidx.media3.common.C.LENGTH_UNSET.toLong()
    private var spec: DataSpec?=null
    private var position=0L
    /** End of the requested data (absolute, exclusive), once known. */
    private var end=unset
    private var left=0L
    private var got=0L
    private var opened=false
    /** The server ignored Range: the rest comes in one response. */
    private var whole=false
    override fun addTransferListener(transferListener: TransferListener)=upstream.addTransferListener(transferListener)
    override fun open(dataSpec: DataSpec): Long {
        spec=dataSpec; position=dataSpec.position; whole=false
        end=if(dataSpec.length==unset) unset else dataSpec.position+dataSpec.length
        openRange()
        return if(end==unset) unset else end-dataSpec.position
    }
    /** Opens the next range of the stream; false when there is nothing left. */
    private fun openRange(): Boolean {
        val s=spec ?: return false
        val want=when { whole -> if(end==unset) unset else end-position; end==unset -> chunk; else -> minOf(chunk,end-position) }
        if(want!=unset && want<=0) return false
        val length=try { upstream.open(s.subrange(position-s.position,want)) } catch(e: HttpDataSource.InvalidResponseCodeException) {
            upstream.close(); if(e.responseCode==416) return false else throw e
        }
        opened=true; got=0
        val total=upstream.responseHeaders.entries.firstOrNull { it.key.equals("Content-Range",true) }?.value?.firstOrNull()?.substringAfterLast('/')?.trim()?.toLongOrNull()
        if(total!=null) { if(end==unset || total<end) end=total }
        else if(!whole) { upstream.close(); opened=false; whole=true; return openRange() }
        left=if(length==unset) Long.MAX_VALUE else length
        return true
    }
    override fun read(buffer: ByteArray,offset: Int,length: Int): Int {
        if(length==0) return 0
        while(true) {
            if(!opened) return androidx.media3.common.C.RESULT_END_OF_INPUT
            if(left>0) {
                val n=upstream.read(buffer,offset,minOf(length.toLong(),left).toInt())
                if(n!=androidx.media3.common.C.RESULT_END_OF_INPUT) { position+=n; left-=n; got+=n; return n }
            }
            // This range is done: stop at the end of the file (or an empty response), otherwise fetch the next one.
            upstream.close(); opened=false
            if(whole || got==0L || (end!=unset && position>=end) || !openRange()) return androidx.media3.common.C.RESULT_END_OF_INPUT
        }
    }
    override fun getUri(): Uri?=upstream.uri
    override fun getResponseHeaders(): Map<String,List<String>> = upstream.responseHeaders
    override fun close() { try { if(opened) upstream.close() } finally { opened=false; spec=null } }
}

/** Local audio and permanent downloads bypass streaming cache completely. */
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class RoutedDataSource(private val local: DataSource.Factory,private val remote: DataSource.Factory): DataSource {
    private var current: DataSource?=null
    private val listeners=mutableListOf<TransferListener>()
    override fun addTransferListener(transferListener: TransferListener) {listeners+=transferListener}
    override fun open(dataSpec: DataSpec): Long {
        val source=(if(dataSpec.uri.scheme=="https") remote else local).createDataSource()
        current=source;listeners.forEach(source::addTransferListener);return source.open(dataSpec)
    }
    override fun read(buffer: ByteArray,offset: Int,length: Int): Int=current?.read(buffer,offset,length) ?: -1
    override fun getUri(): Uri?=current?.uri
    override fun getResponseHeaders(): Map<String,List<String>> = current?.responseHeaders ?: emptyMap()
    override fun close() {try {current?.close()} finally {current=null}}
}
