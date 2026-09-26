package io.hkmario.monologue.cloud

import io.hkmario.monologue.AppGraph
import io.hkmario.monologue.data.*
import io.hkmario.monologue.domain.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

class DownloadCoordinator(private val graph: AppGraph) {
    private val mutex=Mutex()
    suspend fun enqueue(tracks: List<Track>): Int = mutex.withLock {
        val dao=graph.db.dao();var added=0
        for(t in tracks) {
            val row=dao.track(t.id)?.model() ?: t
            if(row.offlinePath?.let {File(it).isFile && File(it).length()>0}==true && row.remoteVersion==row.downloadedVersion) continue
            val version=t.remoteVersion ?: error("遠端未提供版本資訊")
            val id=MessageDigest.getInstance("SHA-256").digest("${t.id}:$version".toByteArray()).joinToString("") {"%02x".format(it)}
            val exists=dao.downloads().find {it.id==id}
            if(exists?.status in setOf("Queued","Downloading")) continue
            dao.putDownload(DownloadRow(id,t.id,t.title,version,t.checksum,t.bytes,sequence=System.currentTimeMillis()+added));added++
        }
        if(added>0) {
            val settings=graph.settings.snapshot()
            val control=dao.control() ?: DownloadControl(pauseBetween=settings.bool("pauseBetween"))
            if(control.phase!="Waiting") {dao.control(control.copy(phase="Running",cancelled=false));WorkScheduler.download(graph.context,settings.bool("wifiOnly",true))}
        }
        added
    }
}
