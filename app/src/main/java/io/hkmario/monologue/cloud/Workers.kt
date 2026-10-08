package io.hkmario.monologue.cloud

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import androidx.room.withTransaction
import io.hkmario.monologue.MonologueApp
import io.hkmario.monologue.R
import io.hkmario.monologue.data.*
import io.hkmario.monologue.domain.*
import kotlinx.coroutines.*
import java.io.File
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** The same client with its connections and DNS kept on [network], whatever the phone's default network becomes. */
fun okhttp3.OkHttpClient.on(network: android.net.Network): okhttp3.OkHttpClient = newBuilder().socketFactory(network.socketFactory)
    .dns(object: okhttp3.Dns { override fun lookup(hostname: String) = network.getAllByName(hostname).toList() }).build()

class DownloadWorker(context: Context, parameters: WorkerParameters): CoroutineWorker(context,parameters) {
    private val graph=(context.applicationContext as MonologueApp).graph
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val nm=applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("downloads","Monologue 下載",NotificationManager.IMPORTANCE_LOW))
        val notification=NotificationCompat.Builder(applicationContext,"downloads").setSmallIcon(R.drawable.ic_monologue).setContentTitle("Monologue").setContentText("正在下載音樂").setOngoing(true).build()
        return if(Build.VERSION.SDK_INT>=29) ForegroundInfo(42,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(42,notification)
    }
    private suspend fun notifyCompletion() {
        if(!graph.settings.snapshot().bool("downloadNotifications",true)) return
        val rows=graph.db.dao().downloads();val failed=rows.count {it.status=="Failed"};val success=rows.count {it.status=="Complete"}
        val nm=applicationContext.getSystemService(NotificationManager::class.java)
        if(!nm.areNotificationsEnabled()) return
        nm.notify(43,NotificationCompat.Builder(applicationContext,"downloads").setSmallIcon(R.drawable.ic_monologue).setContentTitle("Monologue").setContentText("下載完成：$success 首成功，$failed 首失敗").setAutoCancel(true).build())
    }
    /**
     * The network to download on: the one WorkManager started this work for, still unmetered when 只用 Wi-Fi 下載 is on.
     * Downloads are bound to it. Unbound, a phone that moves traffic to mobile data when Wi-Fi is weak (Samsung's
     * switch to mobile data) sent 1.4 GB of a "Wi-Fi only" library download over mobile data while Wi-Fi stayed the
     * default network. Null: wait for Wi-Fi.
     */
    private fun downloadNetwork(wifiOnly: Boolean): android.net.Network? {
        val connectivity=applicationContext.getSystemService(android.net.ConnectivityManager::class.java)
        val chosen=(if(Build.VERSION.SDK_INT>=28) network else null) ?: connectivity.activeNetwork ?: return null
        val unmetered=connectivity.getNetworkCapabilities(chosen)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)==true
        return if(wifiOnly && !unmetered) null else chosen
    }
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val dao=graph.db.dao()
        val directory=try {graph.cache.offlineDirectory(graph.settings.snapshot())} catch(e: Exception) {
            dao.downloads().filter {it.status=="Queued"}.forEach {dao.putDownload(it.copy(status="Failed",error=e.message ?: "永久下載位置無法使用"))}
            dao.control((dao.control() ?: DownloadControl()).copy(phase="Complete"))
            return@withContext Result.failure()
        }
        val control=dao.control() ?: DownloadControl()
        if(control.cancelled || control.phase==DownloadPhase.Waiting.name) return@withContext Result.success()
        try { setForeground(getForegroundInfo()) } catch(e: Exception) { return@withContext Result.retry() }
        dao.recoverDownloads()
        dao.control(control.copy(phase=DownloadPhase.Running.name))
        while(!isStopped) {
            ensureActive()
            if(dao.control()?.cancelled==true) return@withContext Result.success()
            val item=dao.downloads().firstOrNull { it.status==DownloadStatus.Queued.name } ?: break
            // Checked before every song: if Wi-Fi is gone (or now metered) stop, and WorkManager runs again on Wi-Fi.
            val net=downloadNetwork(graph.settings.snapshot().bool("wifiOnly",true)) ?: return@withContext Result.retry()
            dao.putDownload(item.copy(status=DownloadStatus.Downloading.name))
            val temporary=File(directory,"${item.id}.part")
            try {
                if(directory.usableSpace < maxOf(item.size,32*1024*1024)+64*1024*1024) error("儲存空間不足；請清理空間後重試")
                val digest=MessageDigest.getInstance("MD5")
                var bytes=0L; var lastProgress=0L
                val track=dao.track(item.trackId)?.model() ?: error("找不到待下載歌曲")
                (if(track.source==Source.Online) graph.online.http.on(net).newCall(okhttp3.Request.Builder().url(graph.online.resolveUri(android.net.Uri.parse(track.uri)).toString()).build()).execute().also {if(!it.isSuccessful) {it.close();error("音源下載失敗 HTTP ${it.code}")}} else graph.drive.download(item.trackId,net)).use { response ->
                    val body=response.body ?: error("下載回應沒有內容")
                    body.byteStream().use { input ->
                        java.io.FileOutputStream(temporary).use { output ->
                            val buffer=ByteArray(64*1024)
                            while(true) {
                                ensureActive(); if(isStopped || dao.control()?.cancelled==true) throw CancellationException()
                                val count=input.read(buffer); if(count<0) break
                                if(directory.usableSpace < 32*1024*1024) error("儲存空間不足")
                                output.write(buffer,0,count); digest.update(buffer,0,count); bytes+=count
                                if(System.currentTimeMillis()-lastProgress>500) {
                                    dao.putDownload(item.copy(status=DownloadStatus.Downloading.name,received=bytes)); lastProgress=System.currentTimeMillis()
                                }
                            }
                            output.fd.sync()
                        }
                    }
                }
                require(bytes>0 && (item.size==0L || bytes==item.size)) { "下載長度驗證失敗" }
                val checksum=digest.digest().joinToString("") { "%02x".format(it) }
                require(item.checksum==null || checksum.equals(item.checksum,true)) { "下載 checksum 驗證失敗" }
                require(track.source!=Source.Drive || graph.drive.version(item.trackId)==item.version) {"下載期間遠端檔案已更新；請重新整理後重試"}
                val target=File(directory,"${item.id}.audio")
                Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE)
                graph.db.withTransaction {
                    dao.offline(item.trackId,target.absolutePath,item.version)
                    val row=dao.track(item.trackId)
                    if(row!=null) {
                        val metadata=android.media.MediaMetadataRetriever()
                        try {
                            metadata.setDataSource(target.absolutePath)
                            dao.putTrack(row.copy(artwork=embeddedArtwork(android.net.Uri.fromFile(target).toString()),title=metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE) ?: row.title,artist=metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: row.artist,album=metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: row.album,durationMs=metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: row.durationMs))
                        } catch(e: RuntimeException) { /* The player reports unsupported formats; verified bytes stay available. */ } finally {metadata.release()}
                    }
                    dao.putDownload(item.copy(status=DownloadStatus.Complete.name,received=bytes))
                }
            } catch(e: CancellationException) { temporary.delete(); throw e }
            catch(e: Exception) { temporary.delete(); dao.putDownload(item.copy(status=DownloadStatus.Failed.name,error=e.message ?: "下載失敗")) }
            val latest=dao.control() ?: control
            val next=DownloadMachine.afterItem(dao.downloads().count { it.status==DownloadStatus.Queued.name },latest.pauseBetween,latest.cancelled)
            dao.control(latest.copy(phase=next.name))
            if(next!=DownloadPhase.Running) { if(next==DownloadPhase.Complete) notifyCompletion(); return@withContext Result.success() }
        }
        dao.control((dao.control() ?: control).copy(phase=DownloadPhase.Complete.name))
        Result.success()
    }
}
class ListenSyncWorker(context: Context, parameters: WorkerParameters): CoroutineWorker(context,parameters) {
    override suspend fun doWork(): Result {
        val graph=(applicationContext as MonologueApp).graph
        return try { while(graph.listenBrainz.sync()>0) {currentCoroutineContext().ensureActive()}; Result.success() }
        catch(e: CancellationException) { throw e }
        catch(e: HttpFailure) { if(e.code==401 || e.code==400) Result.failure() else Result.retry() }
        catch(e: Exception) { Result.retry() }
    }
}
/**
 * Did the old 每日檢查新歌曲, which downloaded every song in the Drive music folder not yet on the phone. Songs are now
 * downloaded only when the user asks (下載未儲存的… or a song's download button); this class stays only so work
 * scheduled by an older version ends without doing anything, and that work is cancelled at launch.
 */
class IncrementalWorker(context: Context,parameters: WorkerParameters): CoroutineWorker(context,parameters) {
    override suspend fun doWork(): Result = Result.success()
}
object WorkScheduler {
    /** Cancels the daily download of older versions (see [IncrementalWorker]). */
    fun cancelIncremental(context: Context) { WorkManager.getInstance(context).cancelUniqueWork("monologue-incremental") }
    fun download(context: Context, wifi: Boolean) {
        val request=OneTimeWorkRequestBuilder<DownloadWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(if(wifi) NetworkType.UNMETERED else NetworkType.CONNECTED).setRequiresStorageNotLow(true).build()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,java.util.concurrent.TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("monologue-downloads",ExistingWorkPolicy.APPEND_OR_REPLACE,request)
    }
    fun sync(context: Context) {
        val request=OneTimeWorkRequestBuilder<ListenSyncWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,java.util.concurrent.TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("monologue-sync",ExistingWorkPolicy.APPEND_OR_REPLACE,request)
    }
    fun periodicSync(context: Context, enabled: Boolean) {
        val manager=WorkManager.getInstance(context)
        if(!enabled) { manager.cancelUniqueWork("monologue-sync-periodic"); manager.cancelUniqueWork("monologue-sync"); return }
        manager.enqueueUniquePeriodicWork("monologue-sync-periodic",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<ListenSyncWorker>(15,java.util.concurrent.TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
}
