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

class ListenBrainzClient(private val context: android.content.Context, private val secrets: SecretStore, private val dao: MusicDao, private val settings: SettingsRepository) {
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
    private val cacheFile get()=java.io.File(context.filesDir,"lb-recommendations.json")
    private val uuid=Regex("[0-9a-fA-F-]{36}")
    /** "weekly-jams" or "weekly-exploration" for ListenBrainz's weekly playlists, from their source or title. */
    private fun kindOf(p: JSONObject): String? {
        val source=p.optJSONObject("extension")?.optJSONObject("https://musicbrainz.org/doc/jspf#playlist")?.optJSONObject("additional_metadata")?.optJSONObject("algorithm_metadata")?.optString("source_patch").orEmpty()
        val title=p.optString("title")
        return when { source=="weekly-jams" || title.contains("Weekly Jams",true) -> "weekly-jams"; source=="weekly-exploration" || title.contains("Weekly Exploration",true) -> "weekly-exploration"; else -> null }
    }
    /** The newest Weekly Jams and Weekly Exploration, each with its tracks and a cover for every recording that has one. */
    private suspend fun fetchLists(user: String): JSONObject {
        val items=request("user/${URLEncoder.encode(user,"UTF-8")}/playlists/recommendations",null).optJSONArray("playlists") ?: JSONArray()
        val all=(0 until items.length()).map { items.getJSONObject(it).optJSONObject("playlist") ?: items.getJSONObject(it) }
        val lists=JSONArray()
        for(kind in listOf("weekly-jams","weekly-exploration")) {
            val newest=all.filter { kindOf(it)==kind }.maxByOrNull { it.optString("date") } ?: continue
            val id=newest.optString("identifier").substringAfterLast('/').takeIf { it.matches(uuid) } ?: continue
            val playlist=request("playlist/$id",null).getJSONObject("playlist")
            // Older playlists carry no cover fields; one metadata call fills them in for every recording at once.
            val songs=playlist.optJSONArray("track") ?: JSONArray()
            val missing=(0 until songs.length()).mapNotNull { i -> val t=songs.getJSONObject(i)
                if(t.optJSONObject("extension")?.optJSONObject("https://musicbrainz.org/doc/jspf#track")?.optJSONObject("additional_metadata")?.has("caa_release_mbid")==true) null else recordingMbid(t) }.distinct()
            val covers=JSONObject()
            if(missing.isNotEmpty()) runCatching {
                val meta=request("metadata/recording/?recording_mbids=${missing.joinToString(",")}&inc=release",null)
                missing.forEach { m -> meta.optJSONObject(m)?.optJSONObject("release")?.let { r -> coverUrl(r.optString("caa_release_mbid"),r.optLong("caa_id"))?.let { covers.put(m,it) } } }
            }
            lists.put(JSONObject().put("kind",kind).put("id",id).put("playlist",playlist).put("covers",covers))
        }
        return JSONObject().put("user",user).put("fetchedAt",System.currentTimeMillis()).put("lists",lists)
    }
    private fun recordingMbid(t: JSONObject): String? {
        val ids=t.optJSONArray("identifier")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: listOf(t.optString("identifier"))
        return ids.firstNotNullOfOrNull { Regex("recording/([0-9a-fA-F-]{36})").find(it)?.groupValues?.get(1) }
    }
    /** "2026-09-14T00:14:45…" → "9 月 14 日那週". */
    private fun weekLabel(date: String)=runCatching { java.time.LocalDate.parse(date.take(10)).let { "${it.monthValue} 月 ${it.dayOfMonth} 日那週" } }.getOrNull()
    private fun parse(data: JSONObject,tracks: List<Track>): DiscoverUiState {
        val lists=data.optJSONArray("lists") ?: JSONArray()
        val parsed=(0 until lists.length()).map { lists.getJSONObject(it) }.map { entry ->
            val playlist=entry.getJSONObject("playlist"); val id=entry.optString("id"); val covers=entry.optJSONObject("covers") ?: JSONObject()
            val songs=playlist.optJSONArray("track") ?: JSONArray()
            val items=(0 until songs.length()).map { i ->
                val t=songs.getJSONObject(i); val title=t.optString("title"); val artist=t.optString("creator")
                val candidates=tracks.filter { normalize(it.title)==normalize(title) && normalize(it.artist)==normalize(artist) }
                val mbid=recordingMbid(t)
                val extra=t.optJSONObject("extension")?.optJSONObject("https://musicbrainz.org/doc/jspf#track")?.optJSONObject("additional_metadata")
                Recommendation("$id:$i",title,artist,candidates.singleOrNull(),coverUrl(extra?.optString("caa_release_mbid"),extra?.optLong("caa_id")) ?: mbid?.let { covers.optString(it).ifBlank { null } },mbid)
            }.toPersistentList()
            RecommendationList(id,if(entry.optString("kind")=="weekly-jams") "每週精選" else "每週探索",weekLabel(playlist.optString("date")),items)
        }.filter { it.tracks.isNotEmpty() }.toPersistentList()
        if(parsed.isEmpty()) return DiscoverUiState(phase=Phase.Empty,error="此帳號未有每週推薦")
        return DiscoverUiState(Phase.Ready,lists=parsed).showing(0)
    }
    /**
     * The newest Weekly Jams and Weekly Exploration ListenBrainz made for the user. They are kept on the phone so 探索
     * shows them at once (also offline) and fetched again when older than 12 hours, or when [force]d by 更新.
     * The library is matched on every call, so songs added since play from the phone.
     */
    suspend fun recommendations(tracks: List<Track>,force: Boolean=true): DiscoverUiState = gate.withLock {
        val user=secrets.get("lb-user") ?: return@withLock DiscoverUiState()
        val cached=withContext(Dispatchers.IO) { runCatching { JSONObject(cacheFile.readText()) }.getOrNull() }?.takeIf { it.optString("user")==user }
        val fresh=cached!=null && System.currentTimeMillis()-cached.optLong("fetchedAt")<12*3_600_000L
        val data=if(!force && fresh) cached!! else try { fetchLists(user).also { d -> withContext(Dispatchers.IO) { cacheFile.writeText(d.toString()) } } }
            catch(e: CancellationException) { throw e } catch(e: Exception) { cached ?: throw e }
        parse(data,tracks)
    }
}
