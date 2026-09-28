package io.hkmario.monologue.cloud

import io.hkmario.monologue.BuildConfig
import io.hkmario.monologue.domain.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * VocaDB (vocadb.net), the public database of Vocaloid and doujin music, through its documented public API.
 * Many songs carry the Japanese lyrics (credited to where they were transcribed, such as MikuWiki), and some a
 * romanisation and translations. The lyrics are plain text, so they do not scroll with playback.
 */
class VocaDbLyrics {
    // VocaDB answers a fuzzy search slowly (several seconds), so it gets longer than the other sources.
    private val client=OkHttpClient.Builder().callTimeout(15,TimeUnit.SECONDS).build()
    private val userAgent="Monologue/${BuildConfig.VERSION_NAME} ( https://github.com/HKmario852/monologue )"

    private fun search(title: String,exact: Boolean): List<Pair<LyricsCandidate,JSONObject>> {
        val url="https://vocadb.net/api/songs".toHttpUrl().newBuilder().addQueryParameter("query",title).addQueryParameter("fields","Lyrics")
            .addQueryParameter("maxResults","5").addQueryParameter("nameMatchMode",if(exact) "Exact" else "Auto").addQueryParameter("sort","RatingScore").addQueryParameter("lang","Default").build()
        val body=client.newCall(Request.Builder().url(url).header("User-Agent",userAgent).header("Accept","application/json").build()).execute().use { r ->
            if(!r.isSuccessful) throw HttpFailure(r.code)
            r.body?.string() ?: ""
        }
        val items=JSONObject(body.ifBlank { "{}" }).optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).map { items.getJSONObject(it) }.filter { (it.optJSONArray("lyrics")?.length() ?: 0)>0 }.map { song ->
            LyricsCandidate(song.optLong("id"),song.optString("defaultName").ifBlank { song.optString("name") },song.optString("artistString"),song.optDouble("lengthSeconds",0.0),null,"lyrics") to song
        }
    }

    /** Lines that show, ignoring blank ones: a romanisation or translation is used only when it lines up one to one. */
    private fun lineCount(text: String)=text.lines().count { it.isNotBlank() }

    suspend fun find(track: Track,artists: List<String>): FoundLyrics? = withContext(Dispatchers.IO) {
        val seconds=(track.durationMs/1000).toInt()
        // The exact title first (fast); a fuzzy search only when that finds nothing usable.
        for((title,exact) in songTitleVariants(track.title,track.album).flatMap { listOf(it to true,it to false) }) {
            ensureActive()
            val found=search(title,exact)
            val candidates=found.map { it.first }
            // Same title and artist; or, for a differently credited upload, the same title and length within 3 s.
            val pick=pickLyrics(title,artists,seconds,candidates)
                ?: if(seconds>0) pickLyrics(title,emptyList(),seconds,candidates.filter { it.durationSec>0 && kotlin.math.abs(it.durationSec-seconds)<=3 }) else null
            val song=pick?.let { p -> found.first { it.first.id==p.candidate.id }.second } ?: continue
            val lyrics=song.getJSONArray("lyrics").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            fun text(o: JSONObject)=o.optString("value").replace("\r\n","\n").lines().joinToString("\n") { it.trim().replace('　',' ') }.trim()
            val original=lyrics.firstOrNull { it.optString("translationType")=="Original" } ?: continue
            val originalText=text(original)
            val romaji=lyrics.firstOrNull { it.optString("translationType")=="Romanized" }?.let(::text)?.takeIf { lineCount(it)==lineCount(originalText) }
            val chinese=lyrics.firstOrNull { o -> o.optString("translationType")=="Translation" && (o.optJSONArray("cultureCodes")?.let { c -> (0 until c.length()).any { c.getString(it).startsWith("zh") } } ?: false) }
                ?.let(::text)?.takeIf { lineCount(it)==lineCount(originalText) }
            val credit=original.optString("source").takeIf { it.isNotBlank() }?.let { " · 轉錄：$it" } ?: ""
            return@withContext FoundLyrics(originalText,chinese,romaji,"VocaDB（純文字）· ${song.optString("artistString")} · ${pick.candidate.track}$credit")
        }
        null
    }
}
