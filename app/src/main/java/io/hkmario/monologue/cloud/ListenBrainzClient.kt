package io.hkmario.monologue.cloud

import io.hkmario.monologue.data.*
import io.hkmario.monologue.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.collections.immutable.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.*
import java.net.URLEncoder

/** Cover Art Archive thumbnail for a release, when ListenBrainz knows one. */
private fun coverUrl(release: String?, image: Long?): String? =
    if(release.isNullOrBlank() || !release.matches(Regex("[0-9a-fA-F-]{36}")) || image==null || image<=0) null else "https://coverartarchive.org/release/$release/$image-250.jpg"

class ListenBrainzClient(private val secrets: SecretStore, private val dao: MusicDao, private val settings: SettingsRepository) {
    private val client=OkHttpClient.Builder().callTimeout(45,java.util.concurrent.TimeUnit.SECONDS).build()
    private val gate=Mutex()
    private var nextRequestAt=0L
    private suspend fun request(path: String, token: String?, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val remaining=nextRequestAt-System.currentTimeMillis(); if(remaining>0) delay(remaining)
        val req=Request.Builder().url("https://api.listenbrainz.org/1/$path").header("User-Agent","monologue/${io.hkmario.monologue.BuildConfig.VERSION_NAME} (Android; https://github.com/HKmario)")
        if(token!=null) req.header("Authorization","Token $token")
        if(body!=null) req.post(body.toString().toRequestBody("application/json".toMediaType()))
        client.newCall(req.build()).execute().use { response ->
            val reset=response.header("X-RateLimit-Reset-In")?.toLongOrNull() ?: 5
            if(response.header("X-RateLimit-Remaining")=="0") nextRequestAt=System.currentTimeMillis()+reset*1000
            if(!response.isSuccessful) {
                val retry=response.header("Retry-After")?.toLongOrNull() ?: reset
                if(response.code==429) nextRequestAt=System.currentTimeMillis()+retry*1000
                throw HttpFailure(response.code,retry)
            }
            JSONObject(response.body?.string() ?: "{}")
        }
    }
    suspend fun verify(token: String): String = gate.withLock {
        val obj=request("validate-token",token)
        if(!obj.optBoolean("valid")) throw HttpFailure(401)
        val user=obj.getString("user_name")
        val previous=secrets.get("lb-user")
        if(previous!=null && previous!=user) settings.set("lbSync","false")
        secrets.put("listenbrainz",token); secrets.put("lb-user",user); user
    }
    suspend fun disconnect(discard: Boolean) = gate.withLock {
        settings.set("lbSync","false");settings.set("lbError","");settings.set("lbLastSuccess","");settings.set("lbAuthInvalid","false")
        if(discard) secrets.get("lb-user")?.let { dao.discardOutbox(it) }
        secrets.put("listenbrainz",null); secrets.put("lb-user",null)
    }
    suspend fun sync(): Int = gate.withLock {
        if(!settings.snapshot().bool("lbSync")) return@withLock 0
        val user=secrets.get("lb-user") ?: return@withLock 0
        val token=secrets.get("listenbrainz") ?: return@withLock 0
        val rows=dao.pending(user); if(rows.isEmpty()) return@withLock 0
        // Persisted payload and listened_at are reused byte-for-byte on retry, including ambiguous responses.
        val payload=JSONArray(); rows.forEach { payload.put(JSONObject(it.payload)) }
        try {
            val acknowledgement=request("submit-listens",token,JSONObject().put("listen_type","import").put("payload",payload))
            require(acknowledgement.optString("status")=="ok") {"伺服器未確認接受聆聽紀錄"}
            dao.acknowledge(rows.map { it.id }); settings.set("lbLastSuccess",System.currentTimeMillis().toString()); settings.set("lbError","")
        } catch(e: Exception) {
            if(e is CancellationException) throw e
            dao.outboxError(rows.map { it.id },e.message ?: "同步失敗")
            settings.set("lbError",e.message ?: "同步失敗")
            if(e is HttpFailure && e.code==401) settings.set("lbAuthInvalid","true")
            throw e
        }
        rows.size
    }
    suspend fun recommendations(tracks: List<Track>): DiscoverUiState = gate.withLock {
        val user=secrets.get("lb-user") ?: return@withLock DiscoverUiState()
        val data=request("user/${URLEncoder.encode(user,"UTF-8")}/playlists/recommendations",null)
        val items=data.optJSONArray("playlists") ?: JSONArray()
        val lists=(0 until items.length()).map { items.getJSONObject(it).optJSONObject("playlist") ?: items.getJSONObject(it) }
        val selected=lists.filter { it.optString("title").contains("weekly",true) }.maxByOrNull { it.optString("date") }
            ?: return@withLock DiscoverUiState(phase=Phase.Empty,error="此帳號未有每週推薦")
        val identifier=selected.optString("identifier").substringAfterLast('/')
        if(!identifier.matches(Regex("[0-9a-fA-F-]{36}"))) return@withLock DiscoverUiState(phase=Phase.Error,error="服務未提供可讀取的歌單 ID")
        val playlist=request("playlist/$identifier",null).getJSONObject("playlist")
        val songs=playlist.optJSONArray("track") ?: JSONArray()
        val parsed=(0 until songs.length()).map { i ->
            val t=songs.getJSONObject(i); val title=t.optString("title"); val artist=t.optString("creator")
            val candidates=tracks.filter { normalize(it.title)==normalize(title) && normalize(it.artist)==normalize(artist) }
            val ids=t.optJSONArray("identifier")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: listOf(t.optString("identifier"))
            val mbid=ids.firstNotNullOfOrNull { Regex("recording/([0-9a-fA-F-]{36})").find(it)?.groupValues?.get(1) }
            val extra=t.optJSONObject("extension")?.optJSONObject("https://musicbrainz.org/doc/jspf#track")?.optJSONObject("additional_metadata")
            Recommendation("$identifier:$i",title,artist,candidates.singleOrNull(),coverUrl(extra?.optString("caa_release_mbid"),extra?.optLong("caa_id")),mbid)
        }
        // Older playlists carry no cover fields; one metadata call fills them in for every recording at once.
        val missing=parsed.filter { it.artwork==null && it.recordingMbid!=null }.map { it.recordingMbid!! }.distinct()
        val covers=if(missing.isEmpty()) emptyMap() else runCatching {
            val meta=request("metadata/recording/?recording_mbids=${missing.joinToString(",")}&inc=release",null)
            missing.associateWith { id -> meta.optJSONObject(id)?.optJSONObject("release")?.let { r -> coverUrl(r.optString("caa_release_mbid"),r.optLong("caa_id")) } }
        }.getOrDefault(emptyMap())
        val result=parsed.map { r -> if(r.artwork==null) r.copy(artwork=covers[r.recordingMbid]) else r }.toPersistentList()
        DiscoverUiState(if(result.isEmpty()) Phase.Empty else Phase.Ready,playlist.optString("title","每週探索"),playlist.optString("date",selected.optString("date")),result)
    }
}
