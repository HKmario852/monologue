package io.hkmario.monologue.cloud

import io.hkmario.monologue.data.LyricsRow
import io.hkmario.monologue.domain.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

/** Optional explicit-consent integration with the documented LRCLIB exact metadata endpoint. */
class LyricsClient {
    private val client=OkHttpClient.Builder().callTimeout(15,java.util.concurrent.TimeUnit.SECONDS).build()
    suspend fun find(track: Track,base: String="https://lrclib.net"): LyricsRow?=withContext(Dispatchers.IO) {
        val origin=base.toHttpUrl();require(origin.isHttps) {"歌詞服務必須使用 HTTPS"}
        val url=origin.newBuilder().addPathSegments("api/get").addQueryParameter("track_name",track.title).addQueryParameter("artist_name",track.artist).addQueryParameter("album_name",track.album).apply {if(track.durationMs>0) addQueryParameter("duration",(track.durationMs/1000).toString())}.build()
        val req=Request.Builder().url(url).header("User-Agent","monologue/0.1.0 (https://github.com/HKmario)").build()
        client.newCall(req).execute().use {response ->
            if(response.code==404) return@withContext null
            if(!response.isSuccessful) throw HttpFailure(response.code)
            val obj=JSONObject(response.body?.string() ?: "{}")
            if(normalize(obj.optString("trackName"))!=normalize(track.title) || normalize(obj.optString("artistName"))!=normalize(track.artist)) return@withContext null
            if(track.durationMs>0 && kotlin.math.abs(obj.optDouble("duration",0.0)*1000-track.durationMs)>2000) return@withContext null
            val synced=obj.optString("syncedLyrics").takeUnless {it.isBlank() || it=="null"}
            val plain=obj.optString("plainLyrics").takeUnless {it.isBlank() || it=="null"}
            (synced ?: plain)?.let {LyricsRow(track.id,it,source="LRCLIB · ${origin.host} · ${obj.optLong("id")}")}
        }
    }
}
