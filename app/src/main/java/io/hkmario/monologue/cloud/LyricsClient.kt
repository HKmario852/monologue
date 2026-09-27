package io.hkmario.monologue.cloud

import io.hkmario.monologue.BuildConfig
import io.hkmario.monologue.data.LyricsRow
import io.hkmario.monologue.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Optional explicit-consent lookup on LRCLIB, searched by title and artist.
 * When LRCLIB has the title under a differently written artist (水瀬いのり ↔ Inori Minase),
 * the artist's aliases are looked up on MusicBrainz and matching is retried with them.
 * Every request gives up after 8 seconds so a slow service never stalls the lyrics view.
 */
class LyricsClient {
    private val client=OkHttpClient.Builder().callTimeout(8,TimeUnit.SECONDS).build()
    private val userAgent="Monologue/${BuildConfig.VERSION_NAME} ( https://github.com/HKmario852/monologue )"
    private val aliases=java.util.concurrent.ConcurrentHashMap<String,List<String>>()
    private val musicBrainzGate=Mutex(); private var musicBrainzLast=0L

    private fun get(url: HttpUrl): String = client.newCall(Request.Builder().url(url).header("User-Agent",userAgent).build()).execute().use { response ->
        if(!response.isSuccessful) throw HttpFailure(response.code)
        response.body?.string() ?: ""
    }
    /** LRCLIB and MusicBrainz answer 5xx now and then under load: retry twice, waiting 1 s then 2 s. */
    private suspend fun <T> retryOnServerError(block: () -> T): T {
        for(wait in listOf(1000L,2000L)) { try { return block() } catch(e: HttpFailure) { if(e.code<500) throw e; delay(wait) } }
        return block()
    }

    private suspend fun search(origin: HttpUrl,params: Map<String,String>): List<LyricsCandidate> = retryOnServerError {
        val items=JSONArray(get(origin.newBuilder().addPathSegments("api/search").apply { params.forEach { (k,v) -> addQueryParameter(k,v) } }.build()).ifBlank { "[]" })
        (0 until minOf(items.length(),50)).map { i -> val o=items.getJSONObject(i)
            LyricsCandidate(o.optLong("id"),o.optString("trackName"),o.optString("artistName"),o.optDouble("duration",0.0),
                o.optString("syncedLyrics").takeUnless { it.isBlank() || it=="null" },o.optString("plainLyrics").takeUnless { it.isBlank() || it=="null" },o.optBoolean("instrumental"))
        }
    }

    /** Other spellings MusicBrainz records for an artist: aliases plus the romanised sort name. Cached per name. */
    private suspend fun aliasesOf(name: String): List<String> {
        aliases[name]?.let { return it }
        val found=musicBrainzGate.withLock {
            // MusicBrainz allows one request per second per client.
            val wait=1100-(System.currentTimeMillis()-musicBrainzLast); if(wait>0) delay(wait)
            try {
                val quoted=name.replace("\"","")
                val url="https://musicbrainz.org/ws/2/artist/".toHttpUrl().newBuilder().addQueryParameter("query","artist:\"$quoted\" OR alias:\"$quoted\"").addQueryParameter("fmt","json").addQueryParameter("limit","3").build()
                val artists=JSONObject(retryOnServerError { get(url) }).optJSONArray("artists") ?: JSONArray()
                (0 until artists.length()).map { artists.getJSONObject(it) }
                    .filter { a -> a.optInt("score")>=90 && (sameName(a.optString("name"),name) || (a.optJSONArray("aliases")?.let { l -> (0 until l.length()).any { sameName(l.getJSONObject(it).optString("name"),name) } } ?: false)) }
                    .flatMap { a -> listOf(a.optString("name"))+sortNameVariants(a.optString("sort-name"))+(a.optJSONArray("aliases")?.let { l -> (0 until l.length()).map { l.getJSONObject(it).optString("name") } } ?: emptyList()) }
                    .filter { it.isNotBlank() }.distinct()
            } finally { musicBrainzLast=System.currentTimeMillis() }
        }
        aliases[name]=found
        return found
    }

    suspend fun find(track: Track,base: String="https://lrclib.net"): LyricsRow?=withContext(Dispatchers.IO) {
        val origin=base.toHttpUrl();require(origin.isHttps) {"歌詞服務必須使用 HTTPS"}
        val seconds=(track.durationMs/1000).toInt()
        val artists=creditedArtists(track.artist)
        fun row(pick: LyricsPick,via: String="") = LyricsRow(track.id,pick.text,source="LRCLIB · ${pick.candidate.artist} · ${pick.candidate.track}$via"+
            if(pick.offsetSec>3) " · 長度相差 ${pick.offsetSec} 秒，時間可能略有偏差" else "")

        // 1. Title + main artist. 2. Title only (catalogues credit artists differently).
        val first=artists.firstOrNull()?.let { search(origin,mapOf("track_name" to track.title,"artist_name" to it)) } ?: emptyList()
        pickLyrics(track.title,artists,seconds,first)?.let { return@withContext row(it) }
        ensureActive()
        // A failed fallback after a real answer means "not found" rather than "service down".
        val byTitle=try { search(origin,mapOf("track_name" to track.title)) } catch(e: java.io.IOException) { if(artists.isEmpty()) throw e else emptyList() }
        val all=(first+byTitle).distinctBy { it.id }
        pickLyrics(track.title,artists,seconds,all)?.let { return@withContext row(it) }
        // 3. The title exists under another spelling of the artist: ask MusicBrainz for aliases, only when it could help.
        if(artists.isEmpty() || all.none { sameTitle(it.track,track.title) }) return@withContext null
        val expanded=artists+artists.take(3).flatMap { name -> try { aliasesOf(name) } catch(e: CancellationException) { throw e } catch(e: Exception) { emptyList() } }
        pickLyrics(track.title,expanded,seconds,all)?.let { return@withContext row(it," · 以 MusicBrainz 別名配對") }
        null
    }
}
