package io.hkmario.monologue.data

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.*
import androidx.media3.common.C
import java.io.File

/** Includes in-flight reservations. Pinned keys and pending writes never become eviction victims. */
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class ProtectedLru(private val limit: Long = 1_000_000_000): CacheEvictor {
    private val spans=java.util.TreeSet<CacheSpan>(compareBy<CacheSpan> { it.lastTouchTimestamp }.thenBy { it.key }.thenBy { it.position })
    private val pinned=mutableSetOf<String>()
    private val reserved=mutableMapOf<Pair<String,Long>,Long>()
    private var used=0L
    @Synchronized fun pin(keys: Set<String>) { pinned.clear(); pinned.addAll(keys) }
    override fun requiresCacheSpanTouches() = true
    override fun onCacheInitialized() {}
    @Synchronized override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        val amount=if(length==C.LENGTH_UNSET.toLong()) 2*1024*1024L else length
        val previous=reserved.remove(key to position) ?: 0L
        while(used+reserved.values.sum()+amount>limit) {
            val victim=spans.firstOrNull { it.key !in pinned && reserved.keys.none { p -> p.first==it.key } }
                ?: throw Cache.CacheException("串流快取已滿；目前播放受保護，改用網絡讀取")
            cache.removeSpan(victim)
        }
        reserved[key to position]=amount
    }
    @Synchronized override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        reserved.remove(span.key to span.position)
        spans.add(span); used+=span.length
        while(used+reserved.values.sum()>limit) {
            val victim=spans.firstOrNull { it.key !in pinned } ?: break
            cache.removeSpan(victim)
        }
    }
    @Synchronized override fun onSpanRemoved(cache: Cache, span: CacheSpan) { if(spans.remove(span)) used-=span.length }
    @Synchronized override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) { spans.remove(oldSpan); spans.add(newSpan) }
    @Synchronized fun clearSafe(cache: Cache): Boolean {
        spans.toList().filter { it.key !in pinned && reserved.keys.none { p -> p.first==it.key } }.forEach { cache.removeSpan(it) }
        return spans.isNotEmpty()
    }
    @Synchronized fun releaseReservations(key: String) { reserved.keys.removeAll { it.first==key } }
}
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class StreamCache(private val context: Context) {
    val directory=File(context.cacheDir,"stream-v1")
    val offline=File(context.filesDir,"offline").apply { mkdirs() }
    val offlineRoots: List<File> get()=listOfNotNull(offline,context.getExternalFilesDir(null)?.let {File(it,"offline")})
    fun offlineDirectory(settings: io.hkmario.monologue.domain.AppSettingsUiState): File {
        val dir=if(settings.text("downloadLocation","internal")=="external") context.getExternalFilesDir(null)?.let {File(it,"offline")} ?: error("外置儲存空間目前無法使用") else offline
        if(!dir.exists() && !dir.mkdirs()) error("無法建立永久下載資料夾")
        return dir
    }
    val evictor=ProtectedLru()
    val cache=SimpleCache(directory,evictor,StandaloneDatabaseProvider(context))
    @Volatile var pendingClear=false
    fun pin(keys: Set<String>) {
        evictor.pin(keys)
        if(pendingClear) pendingClear=evictor.clearSafe(cache)
    }
    fun clear(): Boolean { pendingClear=evictor.clearSafe(cache); return pendingClear }
}
