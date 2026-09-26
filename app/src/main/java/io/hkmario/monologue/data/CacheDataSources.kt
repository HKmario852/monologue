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
