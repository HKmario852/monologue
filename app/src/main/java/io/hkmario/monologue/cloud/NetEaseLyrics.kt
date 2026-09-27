package io.hkmario.monologue.cloud

import io.hkmario.monologue.domain.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Lyrics from one provider: original LRC plus, when available, a translation and a romanised LRC. */
data class FoundLyrics(val original: String, val translation: String?, val romaji: String?, val source: String)

/**
 * NetEase Cloud Music (網易雲音樂) lyrics through the unofficial public web endpoints its own web player uses.
 * Off by default: it is not an official API, may stop working at any time, and sends the song title and
 * artist to a service in mainland China. Only plain, unencrypted endpoints are used.
 */
class NetEaseLyrics {
    private val client=OkHttpClient.Builder().callTimeout(8,TimeUnit.SECONDS).build()
    private fun get(url: HttpUrl): JSONObject = client.newCall(Request.Builder().url(url)
        .header("User-Agent","Mozilla/5.0 (Linux; Android) Monologue").header("Referer","https://music.163.com/").build()).execute().use { r ->
        if(!r.isSuccessful) throw HttpFailure(r.code)
        JSONObject(r.body?.string() ?: "{}")
    }
    private fun lrc(obj: JSONObject, key: String) = obj.optJSONObject(key)?.optString("lyric")?.takeIf { it.isNotBlank() && it!="null" && Lrc.parse(it).isNotEmpty() }

    suspend fun find(track: Track, artists: List<String>): FoundLyrics? = withContext(Dispatchers.IO) {
        val seconds=(track.durationMs/1000).toInt()
        val query=listOfNotNull(track.title,artists.firstOrNull()).joinToString(" ")
        val search=get("https://music.163.com/api/cloudsearch/pc".toHttpUrl().newBuilder().addQueryParameter("s",query).addQueryParameter("type","1").addQueryParameter("limit","20").build())
        val songs=search.optJSONObject("result")?.optJSONArray("songs") ?: return@withContext null
        // Treat each result as a candidate and reuse the LRCLIB matching rules: same title, a matching credited artist.
        val candidates=(0 until songs.length()).map { songs.getJSONObject(it) }.flatMap { s ->
            val names=s.optJSONArray("ar")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("name") } }.orEmpty()
            // NetEase also lists translated or alternate titles ("tns", "alia"); any of them may be the title on the file.
            val titles=listOf(s.optString("name"))+(s.optJSONArray("tns")?.let { t -> (0 until t.length()).map { t.getString(it) } }.orEmpty())+(s.optJSONArray("alia")?.let { t -> (0 until t.length()).map { t.getString(it) } }.orEmpty())
            titles.filter { it.isNotBlank() }.map { title -> LyricsCandidate(s.optLong("id"),title,names.joinToString(" / "),s.optLong("dt")/1000.0,"[00:00.00]",null) }
        }
        val pick=pickLyrics(track.title,artists,seconds,candidates) ?: return@withContext null
        ensureActive()
        val body=get("https://music.163.com/api/song/lyric".toHttpUrl().newBuilder().addQueryParameter("id",pick.candidate.id.toString())
            .addQueryParameter("lv","1").addQueryParameter("tv","-1").addQueryParameter("rv","-1").build())
        val original=lrc(body,"lrc")?.let(::stripCreditLines) ?: return@withContext null
        val note=if(pick.offsetSec>3) " · 長度相差 ${pick.offsetSec} 秒，時間可能略有偏差" else ""
        FoundLyrics(original,lrc(body,"tlyric")?.let(::stripCreditLines),lrc(body,"romalrc")?.let(::stripCreditLines),"網易雲音樂 · ${pick.candidate.artist} · ${pick.candidate.track}$note")
    }
}
