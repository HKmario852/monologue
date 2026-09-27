package io.hkmario.monologue.cloud

import io.hkmario.monologue.data.LyricsRow
import io.hkmario.monologue.domain.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject

/**
 * Optional explicit-consent integration with LRCLIB's search endpoint. Only the title and artist are sent,
 * because tags on Drive and local files often lack an album or carry a slightly different length.
 */
class LyricsClient {
    private val client=OkHttpClient.Builder().callTimeout(15,java.util.concurrent.TimeUnit.SECONDS).build()
    suspend fun find(track: Track,base: String="https://lrclib.net"): LyricsRow?=withContext(Dispatchers.IO) {
        val origin=base.toHttpUrl();require(origin.isHttps) {"歌詞服務必須使用 HTTPS"}
        if(track.title.isBlank()) return@withContext null
        val url=origin.newBuilder().addPathSegments("api/search").addQueryParameter("track_name",track.title).apply {if(track.artist.isNotBlank() && track.artist!="未知歌手") addQueryParameter("artist_name",track.artist)}.build()
        val req=Request.Builder().url(url).header("User-Agent","monologue/0.4.0 (https://github.com/HKmario852/monologue)").build()
        val results=client.newCall(req).execute().use {response ->
            if(response.code==404) return@withContext null
            if(!response.isSuccessful) throw HttpFailure(response.code)
            JSONArray(response.body?.string() ?: "[]")
        }
        val best=pick((0 until results.length()).map {results.getJSONObject(it)},track) ?: return@withContext null
        val synced=best.optString("syncedLyrics").takeUnless {it.isBlank() || it=="null"}
        val plain=best.optString("plainLyrics").takeUnless {it.isBlank() || it=="null"}
        (synced ?: plain)?.let {LyricsRow(track.id,it,source="LRCLIB · ${origin.host} · ${best.optLong("id")}")}
    }

    /**
     * The title must match; the artist must match or contain the other (so "A feat. B" still finds "A").
     * Among matches, time-synced lyrics win, then the closest length when the song's length is known.
     */
    private fun pick(results: List<JSONObject>,track: Track): JSONObject? {
        val title=normalize(track.title); val artist=normalize(track.artist).takeUnless {track.artist=="未知歌手"}.orEmpty()
        fun artistMatches(other: String)=artist.isEmpty() || normalize(other).let {it==artist || it.contains(artist) || artist.contains(it) && it.isNotEmpty()}
        return results.filter {normalize(it.optString("trackName"))==title && artistMatches(it.optString("artistName")) && !it.optBoolean("instrumental")}
            .filter {it.optString("syncedLyrics").let {s->s.isNotBlank() && s!="null"} || it.optString("plainLyrics").let {s->s.isNotBlank() && s!="null"}}
            .sortedWith(compareBy<JSONObject> {if(it.optString("syncedLyrics").let {s->s.isNotBlank() && s!="null"}) 0 else 1}
                .thenBy {if(track.durationMs>0) kotlin.math.abs(it.optDouble("duration",0.0)*1000-track.durationMs) else 0.0})
            .firstOrNull()
    }
}
