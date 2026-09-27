package io.hkmario.monologue.cloud

import io.hkmario.monologue.BuildConfig
import io.hkmario.monologue.data.LyricsRow
import io.hkmario.monologue.domain.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray

/**
 * Optional explicit-consent lookup on LRCLIB. Uses the search endpoint rather than the exact-match one:
 * exact lookups need the album name and a near-identical length, which Drive files rarely match.
 */
class LyricsClient {
    private val client=OkHttpClient.Builder().callTimeout(15,java.util.concurrent.TimeUnit.SECONDS).build()
    private fun search(origin: HttpUrl,params: Map<String,String>): List<LyricsCandidate> {
        val url=origin.newBuilder().addPathSegments("api/search").apply { params.forEach { (k,v) -> addQueryParameter(k,v) } }.build()
        val req=Request.Builder().url(url).header("User-Agent","Monologue/${BuildConfig.VERSION_NAME} (https://github.com/HKmario852/monologue)").build()
        return client.newCall(req).execute().use { response ->
            if(!response.isSuccessful) throw HttpFailure(response.code)
            val items=JSONArray(response.body?.string() ?: "[]")
            (0 until minOf(items.length(),50)).map { i -> val o=items.getJSONObject(i)
                LyricsCandidate(o.optLong("id"),o.optString("trackName"),o.optString("artistName"),o.optDouble("duration",0.0),
                    o.optString("syncedLyrics").takeUnless { it.isBlank() || it=="null" },o.optString("plainLyrics").takeUnless { it.isBlank() || it=="null" },o.optBoolean("instrumental"))
            }
        }
    }
    private suspend fun searchWithRetry(origin: HttpUrl,params: Map<String,String>): List<LyricsCandidate> =
        try { search(origin,params) } catch(e: HttpFailure) { if(e.code<500) throw e; delay(1000); search(origin,params) }
    suspend fun find(track: Track,base: String="https://lrclib.net"): LyricsRow?=withContext(Dispatchers.IO) {
        val origin=base.toHttpUrl();require(origin.isHttps) {"歌詞服務必須使用 HTTPS"}
        val seconds=(track.durationMs/1000).toInt()
        val known=track.artist.takeUnless { isUnknownArtist(it) } ?: ""
        val artist=primaryArtist(known).takeUnless { it.isBlank() }
        // Title + main artist first; title alone catches catalogues that credit the artist differently.
        val attempts=listOfNotNull(artist?.let { mapOf("track_name" to track.title,"artist_name" to it) },mapOf("track_name" to track.title))
        var answered=false
        for(params in attempts) {
            ensureActive()
            // LRCLIB answers 5xx now and then under load: one short retry, and a failed fallback after a real answer means "not found".
            val results=try { searchWithRetry(origin,params) } catch(e: java.io.IOException) { if(answered) break else throw e }
            answered=true
            val pick=pickLyrics(track.title,known,seconds,results) ?: continue
            val note=if(pick.offsetSec>3) " · 長度相差 ${pick.offsetSec} 秒，時間可能略有偏差" else ""
            return@withContext LyricsRow(track.id,pick.text,source="LRCLIB · ${pick.candidate.artist} · ${pick.candidate.track}$note")
        }
        null
    }
}
