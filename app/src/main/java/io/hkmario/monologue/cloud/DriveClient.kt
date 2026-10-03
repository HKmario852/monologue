package io.hkmario.monologue.cloud

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import io.hkmario.monologue.domain.*
import io.hkmario.monologue.data.MusicDao
import io.hkmario.monologue.data.row
import io.hkmario.monologue.BuildConfig
import kotlinx.collections.immutable.*
import java.io.IOException

class AuthorizationNeeded: IOException("Google Drive 需要重新授權；下載佇列已保留")
class HttpFailure(val code: Int, val retryAfterSeconds: Long = 0, detail: String? = null): IOException(detail ?: when(code) { 401 -> "授權已過期"; 403 -> "檔案失去存取權或配額不足"; 404 -> "遠端檔案不存在"; 429 -> "服務暫時限速，稍後重試"; else -> "服務回應 HTTP $code" })
/** Turns Google's JSON error body into an actionable message instead of a bare status code. */
internal fun driveFailure(code: Int, body: String?): HttpFailure {
    val error=runCatching { JSONObject(body ?: "").getJSONObject("error") }.getOrNull()
    val reason=error?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty()
    val status=error?.optString("status").orEmpty()
    val detail=when {
        reason=="accessNotConfigured" || reason=="SERVICE_DISABLED" -> "Google Cloud 專案尚未啟用 Google Drive API；請在 Cloud Console 啟用後重試"
        reason=="insufficientPermissions" || status=="PERMISSION_DENIED" && code==403 -> "授權未包含 Drive 讀取權限；請重新連接，並在 Google 同意畫面勾選「查看及下載你所有的 Google 雲端硬碟檔案」"
        code==403 && error?.optString("message")?.isNotBlank()==true -> "Google Drive 拒絕存取：${error.optString("message")}"
        else -> null
    }
    return HttpFailure(code,detail=detail)
}
internal val audioExtensions=setOf("mp3","flac","m4a","ogg","opus","wav","aac","wma","aif","aiff","alac")
internal fun isAudioFile(name: String, mime: String)=mime.startsWith("audio/") || name.substringAfterLast('.',"").lowercase() in audioExtensions
class DriveClient(private val context: Context, private val dao: MusicDao) {
    private val consent=context.getSharedPreferences("drive-consent",Context.MODE_PRIVATE)
    @Volatile private var token: String? = null
    private var expiresAt=0L
    private val mutex=Mutex()
    private val plain=OkHttpClient.Builder().connectTimeout(20,java.util.concurrent.TimeUnit.SECONDS).readTimeout(60,java.util.concurrent.TimeUnit.SECONDS).build()
    val streamingClient = plain.newBuilder().addInterceptor { chain ->
        val request=chain.request()
        if(request.url.host=="www.googleapis.com" && request.url.encodedPath.startsWith("/drive/v3/files/")) {
            val access=runBlocking { accessToken() }
            val response=chain.proceed(request.newBuilder().header("Authorization","Bearer $access").build())
            if(response.code==401) { response.close(); token=null; throw AuthorizationNeeded() }
            response
        } else chain.proceed(request)
    }.build()
    fun request() = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.readonly"))).build()
    fun accept(value: String) { consent.edit().putBoolean("enabled",true).apply(); token=value; expiresAt=android.os.SystemClock.elapsedRealtime()+45*60_000 }
    fun disconnect() { consent.edit().putBoolean("enabled",false).apply(); token=null; expiresAt=0 }
    val enabled get()=consent.getBoolean("enabled",false)
    suspend fun accessToken(): String = mutex.withLock {
        if(!consent.getBoolean("enabled",false)) throw AuthorizationNeeded()
        if(!BuildConfig.GOOGLE_AUTH_CONFIGURED) throw IOException("尚未設定 Google Cloud Android OAuth 憑證；請按建置說明登記套件名稱及簽署 SHA-1")
        token?.takeIf { android.os.SystemClock.elapsedRealtime()<expiresAt }?.let { return@withLock it }
        val result=Identity.getAuthorizationClient(context).authorize(request()).await()
        if(result.hasResolution()) throw AuthorizationNeeded()
        val access=result.accessToken ?: throw AuthorizationNeeded()
        accept(access); access
    }
    private suspend fun get(path: String, params: Map<String,String> = emptyMap()): JSONObject = withContext(Dispatchers.IO) {
        val url="https://www.googleapis.com/drive/v3/$path".toHttpUrl().newBuilder().apply { params.forEach { (k,v) -> addQueryParameter(k,v) } }.build()
        val req=Request.Builder().url(url).header("Authorization","Bearer ${accessToken()}").build()
        plain.newCall(req).execute().use { response ->
            if(response.code==401) { token=null; throw AuthorizationNeeded() }
            if(!response.isSuccessful) throw driveFailure(response.code,response.body?.string())
            JSONObject(response.body?.string() ?: "{}")
        }
    }
    suspend fun account(): String { val u=get("about",mapOf("fields" to "user(displayName,emailAddress)")).getJSONObject("user"); return u.optString("emailAddress",u.optString("displayName")) }
    private val fileFields="nextPageToken,files(id,name,mimeType,size,createdTime,modifiedTime,version,md5Checksum,parents,ownedByMe,capabilities(canDownload))"
    private val tagged=context.getSharedPreferences("drive-metadata",Context.MODE_PRIVATE)
    private suspend fun track(f: JSONObject, folder: String): Track {
        val id=f.getString("id"); val name=f.getString("name"); val old=dao.track("drive:$id")
        val version=f.optString("version",f.optString("modifiedTime"))
        // Keep tags read earlier unless the file changed; otherwise every listing would reset them to the file name.
        val keep=old!=null && tagged.getString("drive:$id",null)==version
        val t=Track("drive:$id",if(keep) old!!.title else name.substringBeforeLast('.'),old?.artist ?: "未知歌手",old?.album ?: "未知專輯",folder,"https://www.googleapis.com/drive/v3/files/$id?alt=media",old?.durationMs ?: 0,artwork=if(keep) old!!.artwork else null,source=Source.Drive,favorite=old?.favorite ?: false,offlinePath=old?.offlinePath,downloadedVersion=old?.downloadedVersion,remoteVersion=version,bytes=f.optString("size").toLongOrNull() ?: 0,checksum=f.optString("md5Checksum").ifBlank { null },mime=f.getString("mimeType"),addedMs=driveTimeMs(f.optString("createdTime")).takeIf { it>0 } ?: old?.addedMs ?: 0)
        return t
    }
    private val library=context.getSharedPreferences("drive-library",Context.MODE_PRIVATE)
    /** Track IDs under the chosen music folder, remembered so they appear in 媒體庫 before Drive is reached on the next launch. */
    @Volatile var libraryIds: Set<String> = library.getStringSet("ids",emptySet())!!.toSet(); private set
    fun saveLibrary(ids: Set<String>) { libraryIds=ids; library.edit().putStringSet("ids",ids).apply() }
    fun needsTags(track: Track)=track.source==Source.Drive && tagged.getString(track.id,null)!=track.remoteVersion
    /** Reads embedded tags once per file version; files without tags are remembered too, so they are not re-read every visit. */
    suspend fun readTags(track: Track): Track? = withContext(Dispatchers.IO) {
        if(!needsTags(track)) return@withContext null
        val found=readDriveTags(context,plain,accessToken(),track)
        val latest=dao.track(track.id)
        val result=if(found!=null && latest!=null) latest.copy(title=found.title,artist=found.artist,album=found.album,durationMs=found.durationMs,artwork=found.artwork).also { dao.putTrack(it) }.model() else null
        tagged.edit().putString(track.id,track.remoteVersion).apply()
        result
    }
    private suspend fun pages(q: String, orderBy: String, each: suspend (JSONObject)->Unit) {
        var page=""
        do {
            // An empty pageToken is not "first page" for every Google frontend, so only send it when continuing.
            val params=mutableMapOf("q" to q,"pageSize" to "1000","fields" to fileFields,"orderBy" to orderBy,"supportsAllDrives" to "true","includeItemsFromAllDrives" to "true")
            if(page.isNotBlank()) params["pageToken"]=page
            val obj=get("files",params)
            val items=obj.optJSONArray("files")
            if(items!=null) for(i in 0 until items.length()) each(items.getJSONObject(i))
            page=obj.optString("nextPageToken")
        } while(page.isNotBlank())
    }
    suspend fun list(folder: String): Pair<PersistentList<DriveFolder>,PersistentList<Track>> {
        val folders=mutableListOf<DriveFolder>(); val tracks=mutableListOf<Track>()
        pages("'${folder.replace("'","\\'")}' in parents and trashed = false","folder,name") { f ->
            val mime=f.getString("mimeType")
            if(mime=="application/vnd.google-apps.folder") folders+=DriveFolder(f.getString("id"),f.getString("name"))
            else if(isAudioFile(f.getString("name"),mime)) tracks+=track(f,folder)
        }
        dao.putTracks(tracks.map {it.row()})
        return folders.toPersistentList() to tracks.toPersistentList()
    }
    /** The real ID behind the "root" alias; files report this ID in `parents`. */
    suspend fun rootId(): String=get("files/root",mapOf("fields" to "id")).getString("id")
    /** All readable folders in one listing, so folder navigation and song counts need no further requests. */
    suspend fun folders(): List<DriveNode> {
        val nodes=mutableListOf<DriveNode>()
        pages("mimeType = 'application/vnd.google-apps.folder' and trashed = false","name") { f -> nodes+=DriveNode(f.getString("id"),f.getString("name"),f.optJSONArray("parents")?.optString(0),f.optBoolean("ownedByMe",true)) }
        return nodes
    }
    /** Every audio file the account can read: nested folders and "Shared with me" included, one query instead of a folder walk. */
    suspend fun everywhere(limit: Int = 5000): PersistentList<Track> {
        val tracks=mutableListOf<Track>()
        // Drive's `name contains` only matches word prefixes, so extensions are checked locally; octet-stream covers MP3s uploaded without a type.
        pages("trashed = false and (mimeType contains 'audio/' or mimeType = 'application/octet-stream')","name") { f ->
            if(tracks.size<limit && isAudioFile(f.getString("name"),f.getString("mimeType"))) tracks+=track(f,f.optJSONArray("parents")?.optString(0).orEmpty())
        }
        // One write for the whole listing: per-row writes made every screen re-sort hundreds of times.
        dao.putTracks(tracks.map {it.row()})
        return tracks.toPersistentList()
    }
    suspend fun recursive(folder: String): List<Track> {
        val visited=mutableSetOf<String>(); val result=mutableListOf<Track>()
        suspend fun walk(id: String) { if(!visited.add(id)) return; val (folders,tracks)=list(id); result+=tracks; folders.forEach { walk(it.id) } }
        walk(folder); return result.distinctBy { it.id }
    }
    suspend fun version(trackId: String): String = get("files/${trackId.removePrefix("drive:")}",mapOf("fields" to "version")).getString("version")
    suspend fun download(trackId: String): Response = withContext(Dispatchers.IO) {
        val id=trackId.removePrefix("drive:")
        val response=plain.newCall(Request.Builder().url("https://www.googleapis.com/drive/v3/files/$id?alt=media").header("Authorization","Bearer ${accessToken()}").build()).execute()
        if(!response.isSuccessful) { val code=response.code; val body=runCatching { response.body?.string() }.getOrNull(); response.close(); if(code==401) { token=null; throw AuthorizationNeeded() }; throw driveFailure(code,body) }
        response
    }
}
