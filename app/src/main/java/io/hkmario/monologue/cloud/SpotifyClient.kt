package io.hkmario.monologue.cloud

import android.net.Uri
import android.util.Base64
import io.hkmario.monologue.BuildConfig
import io.hkmario.monologue.data.SecretStore
import io.hkmario.monologue.domain.OnlineSong
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.*
import java.security.*
import java.io.IOException

class SpotifyClient(private val secrets: SecretStore) {
    private val http=OkHttpClient();private val mutex=Mutex()
    val configured get()=BuildConfig.SPOTIFY_CLIENT_ID.isNotBlank() && !redirect.contains(".invalid/")
    val connected get()=secrets.get("spotify-token")!=null
    private val redirect=BuildConfig.SPOTIFY_REDIRECT_URI
    fun accepts(uri: Uri): Boolean {
        val expected=Uri.parse(redirect)
        return uri.scheme==expected.scheme && uri.host==expected.host && uri.port==expected.port && uri.path==expected.path
    }
    private fun random()=Base64.encodeToString(ByteArray(32).also {SecureRandom().nextBytes(it)},Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    fun authorizeUrl(): String {
        require(configured) {"此版本尚未完成開發方 Spotify 登入配置"}
        val verifier=random();val state=random()
        secrets.put("spotify-pkce",JSONObject().put("verifier",verifier).put("state",state).put("created",System.currentTimeMillis()).toString())
        val challenge=Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return "https://accounts.spotify.com/authorize".toHttpUrl().newBuilder().addQueryParameter("client_id",BuildConfig.SPOTIFY_CLIENT_ID).addQueryParameter("response_type","code").addQueryParameter("redirect_uri",redirect).addQueryParameter("code_challenge_method","S256").addQueryParameter("code_challenge",challenge).addQueryParameter("state",state).addQueryParameter("scope","playlist-read-private playlist-read-collaborative user-library-read").build().toString()
    }
    suspend fun callback(uri: Uri): String=withContext(Dispatchers.IO) {
        require(accepts(uri))
        val pending=secrets.get("spotify-pkce")?.let(::JSONObject) ?: error("登入已過期，請重新連接")
        require(uri.getQueryParameter("state")==pending.getString("state") && System.currentTimeMillis()-pending.getLong("created") in 0..600_000) {"登入狀態驗證失敗"}
        secrets.put("spotify-pkce",null)
        require(uri.getQueryParameter("error")==null) {"Spotify 授權已取消"}
        val code=uri.getQueryParameter("code") ?: error("Spotify 沒有提供授權碼")
        exchange(FormBody.Builder().add("grant_type","authorization_code").add("code",code).add("redirect_uri",redirect).add("client_id",BuildConfig.SPOTIFY_CLIENT_ID).add("code_verifier",pending.getString("verifier")).build())
        val me=get("https://api.spotify.com/v1/me")
        (me.optString("display_name").takeIf {it.isNotBlank() && it!="null"} ?: me.getString("id")).also {secrets.put("spotify-user",it)}
    }
    private fun exchange(body: RequestBody): JSONObject {
        return http.newCall(Request.Builder().url("https://accounts.spotify.com/api/token").post(body).build()).execute().use {r->
            if(!r.isSuccessful) {
                val detail=runCatching {JSONObject(r.body!!.string()).optString("error_description")}.getOrDefault("")
                throw IOException("Spotify 登入回應 HTTP ${r.code}${if(detail.isNotBlank()) "：$detail" else ""}")
            }
            val token=JSONObject(r.body!!.string());val old=secrets.get("spotify-token")?.let(::JSONObject)
            if(!token.has("refresh_token")) old?.optString("refresh_token")?.let {token.put("refresh_token",it)}
            token.put("expiresAt",System.currentTimeMillis()+token.getLong("expires_in")*1000)
            secrets.put("spotify-token",token.toString());token
        }
    }
    private suspend fun access(): String=mutex.withLock {
        var t=secrets.get("spotify-token")?.let(::JSONObject) ?: error("請先到外掛設定連接 Spotify")
        if(t.optLong("expiresAt")<System.currentTimeMillis()+60_000) t=exchange(FormBody.Builder().add("grant_type","refresh_token").add("refresh_token",t.getString("refresh_token")).add("client_id",BuildConfig.SPOTIFY_CLIENT_ID).build())
        t.getString("access_token")
    }
    private suspend fun get(url: String): JSONObject {
        val u=url.toHttpUrl();require(u.isHttps && u.host=="api.spotify.com")
        return http.newCall(Request.Builder().url(u).header("Authorization","Bearer ${access()}").build()).execute().use {r->
            if(r.code==401) {secrets.put("spotify-token",null);throw IOException("Spotify 登入已失效，請重新連接")}
            if(r.code==429) throw IOException("Spotify 要求稍後重試（${r.header("Retry-After") ?: "稍後"} 秒）")
            if(r.code==403) {
                val detail=runCatching {JSONObject(r.body!!.string()).getJSONObject("error").optString("message")}.getOrDefault("")
                // Development-mode apps stop working entirely when the app owner has no Premium subscription.
                throw IOException(if(detail.contains("premium",true)) "Spotify 拒絕存取：開發模式 App 的擁有者需要 Spotify Premium，Web API 才能使用" else "Spotify 拒絕存取（HTTP 403）${if(detail.isNotBlank()) "：$detail" else ""}；請確認帳號已加入 App 的 User Management")
            }
            if(!r.isSuccessful) throw IOException("Spotify 回應 HTTP ${r.code}；請檢查帳號及開發方 API 存取資格")
            JSONObject(r.body!!.string())
        }
    }
    private fun song(t: JSONObject): OnlineSong {
        val a=t.optJSONArray("artists") ?: JSONArray();val album=t.optJSONObject("album")
        return OnlineSong(t.getString("id"),t.getString("name"),(0 until a.length()).joinToString(", ") {a.getJSONObject(it).getString("name")},album?.optString("name") ?: "",t.optLong("duration_ms"),album?.optJSONArray("images")?.optJSONObject(0)?.optString("url"),"spotify",t.optJSONObject("external_urls")?.optString("spotify") ?: "")
    }
    suspend fun search(query: String): PersistentList<OnlineSong> = withContext(Dispatchers.IO) {
        val url="https://api.spotify.com/v1/search".toHttpUrl().newBuilder().addQueryParameter("q",query).addQueryParameter("type","track").addQueryParameter("limit","10").build()
        val a=get(url.toString()).getJSONObject("tracks").getJSONArray("items");(0 until a.length()).map {song(a.getJSONObject(it))}.toPersistentList()
    }
    suspend fun playlist(url: String): PersistentList<OnlineSong> = withContext(Dispatchers.IO) {
        val uri=Uri.parse(url);require(uri.host=="open.spotify.com" && uri.pathSegments.firstOrNull()=="playlist") {"請貼上 Spotify 播放清單分享連結"}
        val id=uri.pathSegments.getOrNull(1) ?: error("播放清單 ID 不完整");require(id.matches(Regex("[a-zA-Z0-9]+")))
        var next: String?="https://api.spotify.com/v1/playlists/$id/items?limit=50";val result=mutableListOf<OnlineSong>()
        while(next!=null && result.size<5000) {
            currentCoroutineContext().ensureActive();val page=get(next);val items=page.getJSONArray("items")
            for(i in 0 until items.length()) {val entry=items.getJSONObject(i);val t=entry.optJSONObject("item") ?: entry.optJSONObject("track");if(t!=null && t.optString("type")=="track" && !t.isNull("id")) result+=song(t)}
            next=page.optString("next").takeIf {it.isNotBlank() && it!="null"}
        };result.toPersistentList()
    }
    fun user()=secrets.get("spotify-user")
    fun disconnect() {listOf("spotify-token","spotify-pkce","spotify-user").forEach {secrets.put(it,null)}}
}
