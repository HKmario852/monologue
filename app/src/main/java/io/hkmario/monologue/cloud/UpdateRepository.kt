package io.hkmario.monologue.cloud

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.*
import io.hkmario.monologue.*
import io.hkmario.monologue.R
import io.hkmario.monologue.data.SettingsRepository
import io.hkmario.monologue.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class UpdateRepository(private val context: Context,private val settings: SettingsRepository) {
    private val http=OkHttpClient.Builder().followRedirects(true).readTimeout(60,TimeUnit.SECONDS).build()
    val state=MutableStateFlow(UpdateUiState())
    private var release: JSONObject?=null
    suspend fun restore() {
        val s=settings.snapshot();release=runCatching {JSONObject(s.text("update.release"))}.getOrNull()
        release?.let {j->state.value=from(j).copy(apkPath=s.text("update.apk").takeIf {File(it).isFile})}
    }
    private fun from(j: JSONObject)=UpdateUiState(phase=Phase.Ready,version=j.getString("version"),notes=j.optString("notes"),assetUrl=j.getString("url"),digest=j.optString("digest").takeIf {it.startsWith("sha256:")},size=j.getLong("size"))
    suspend fun check()=withContext(Dispatchers.IO) {
        if(state.value.downloading) return@withContext
        val repo=settings.snapshot().text("updateRepository").trim().ifBlank {BuildConfig.UPDATE_REPOSITORY}
        if(repo.isBlank()) {state.value=UpdateUiState(error="尚未設定 GitHub 發布位置；目前仍在開發階段");return@withContext}
        require(repo.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) {"請輸入 owner/repository"}
        state.value=UpdateUiState(phase=Phase.Loading)
        try {
            val r=http.newCall(Request.Builder().url("https://api.github.com/repos/$repo/releases/latest").header("Accept","application/vnd.github+json").header("User-Agent","monologue/${BuildConfig.VERSION_NAME}").build()).execute()
            val json=r.use {if(it.code==404) error("此專案尚未有公開正式版本");require(it.isSuccessful) {"GitHub 回應 HTTP ${it.code}"};JSONObject(it.body!!.string())}
            val tag=json.getString("tag_name").removePrefix("v")
            if(!newerVersion(tag,BuildConfig.VERSION_NAME)) {release=null;settings.set("update.release","");settings.set("update.apk","");state.value=UpdateUiState(phase=Phase.Ready,version=BuildConfig.VERSION_NAME,notes="目前已是最新正式版本");return@withContext}
            val assets=json.getJSONArray("assets");val apks=(0 until assets.length()).map {assets.getJSONObject(it)}.filter {it.getString("name").endsWith(".apk")}
            val asset=apks.singleOrNull() ?: apks.singleOrNull {it.getString("name").contains("universal")} ?: error("Release 必須提供單一 APK 或明確的 universal APK")
            val url=asset.getString("browser_download_url");require(url.startsWith("https://github.com/$repo/releases/download/"))
            release=JSONObject().put("version",tag).put("notes",json.optString("body")).put("url",url).put("digest",asset.optString("digest")).put("size",asset.getLong("size"))
            settings.set("update.release",release.toString());settings.set("update.apk","");state.value=from(release!!)
        } catch(e: CancellationException) {throw e} catch(e: Exception) {state.value=UpdateUiState(phase=Phase.Error,error=e.message)}
    }
    suspend fun enqueue() {
        require(state.value.assetUrl!=null && !state.value.downloading)
        WorkManager.getInstance(context).enqueueUniqueWork("monologue-update",ExistingWorkPolicy.KEEP,OneTimeWorkRequestBuilder<UpdateDownloadWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresStorageNotLow(true).build()).build())
    }
    suspend fun download()=withContext(Dispatchers.IO) {
        if(release==null) restore()
        val data=release ?: error("請先檢查更新");val info=from(data)
        val folder=File(context.cacheDir,"updates").apply {mkdirs()};val temp=File(folder,"update.part");val target=File(folder,"monologue.apk")
        state.value=info.copy(downloading=true)
        try {
            require(info.size in 1..500_000_000 && folder.usableSpace>info.size+64_000_000) {"更新檔大小無效或儲存空間不足"}
            val sha=MessageDigest.getInstance("SHA-256");var count=0L;var last=0L
            http.newCall(Request.Builder().url(info.assetUrl!!).build()).execute().use {r->
                require(r.isSuccessful) {"更新下載回應 HTTP ${r.code}"}
                r.body!!.byteStream().use {input->java.io.FileOutputStream(temp).use {output->val buf=ByteArray(65536);while(true) {ensureActive();val n=input.read(buf);if(n<0) break;count+=n;require(count<=info.size) {"更新檔長度不符"};output.write(buf,0,n);sha.update(buf,0,n);if(System.currentTimeMillis()-last>500) {state.value=info.copy(downloading=true,downloaded=count);last=System.currentTimeMillis()}};output.fd.sync()}}
            }
            require(count==info.size) {"更新未完整下載"}
            val actual=sha.digest().joinToString("") {"%02x".format(it)};info.digest?.let {require(it.removePrefix("sha256:").equals(actual,true)) {"更新檔 SHA-256 不符"}}
            validateApk(temp)
            java.nio.file.Files.move(temp.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            settings.set("update.apk",target.absolutePath);state.value=info.copy(downloaded=count,apkPath=target.absolutePath)
        } catch(e: CancellationException) {temp.delete();state.value=info;throw e} catch(e: Exception) {temp.delete();state.value=info.copy(phase=Phase.Error,error=e.message);throw e}
    }
    @Suppress("DEPRECATION") fun validateApk(file: File) {
        require(file.canonicalFile.parentFile==File(context.cacheDir,"updates").canonicalFile)
        val pm=context.packageManager
        val flags=if(Build.VERSION.SDK_INT>=28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val candidate=pm.getPackageArchiveInfo(file.absolutePath,flags) ?: error("無法驗證 APK")
        val installed=pm.getPackageInfo(context.packageName,flags)
        require(candidate.packageName==context.packageName) {"更新套件名稱不符"}
        val cv=if(Build.VERSION.SDK_INT>=28) candidate.longVersionCode else candidate.versionCode.toLong()
        require(cv>BuildConfig.VERSION_CODE) {"拒絕相同或較舊版本"}
        fun signatures(p: android.content.pm.PackageInfo)=if(Build.VERSION.SDK_INT>=28) p.signingInfo?.apkContentsSigners?.map {it.toCharsString()}?.toSet() else p.signatures?.map {it.toCharsString()}?.toSet()
        require(!signatures(installed).isNullOrEmpty() && signatures(candidate)==signatures(installed)) {"更新簽署與目前 App 不同，拒絕安裝"}
    }
}
fun newerVersion(remote: String,current: String): Boolean {
    val pattern=Regex("[0-9]+\\.[0-9]+\\.[0-9]+")
    if(!remote.matches(pattern) || !current.matches(pattern)) return false
    val a=remote.split('.').map {it.toLongOrNull() ?: return false};val b=current.split('.').map {it.toLong()}
    for(i in 0..2) {if(a[i]!=b[i]) return a[i]>b[i]};return false
}
class UpdateDownloadWorker(context: Context,parameters: WorkerParameters): CoroutineWorker(context,parameters) {
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val manager=applicationContext.getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(android.app.NotificationChannel("updates","Monologue 更新",android.app.NotificationManager.IMPORTANCE_LOW))
        val n=androidx.core.app.NotificationCompat.Builder(applicationContext,"updates").setSmallIcon(R.drawable.ic_monologue).setContentTitle("Monologue").setContentText("正在下載 App 更新").setOngoing(true).build()
        return if(Build.VERSION.SDK_INT>=29) ForegroundInfo(47,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(47,n)
    }
    override suspend fun doWork(): Result {
        return try {setForeground(getForegroundInfo());(applicationContext as MonologueApp).graph.updates.download();Result.success()} catch(e: CancellationException) {throw e} catch(e: Exception) {Result.failure()}
    }
}
