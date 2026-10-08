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
import com.google.mlkit.nl.translate.TranslateLanguage

/** The language most lines are written in, each line judged on its own, so a credit such as "Nhạc: ヒグチアイ" cannot decide it. */
fun mainLanguage(lines: List<String>): String? = lines.mapNotNull { lyricsLanguage(listOf(it)) }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

/** Lyrics to show, with the Chinese translation and romaji that came in the same upload. */
data class LyricLayers(val original: String, val translation: String?, val romaji: String?)

/**
 * Uploads with a second line under each timestamp: a Chinese second line is a translation, a Japanese one under romaji
 * means the lines are swapped. A Latin second line under Japanese, Chinese or Korean is romaji only when it reads as
 * romaji; otherwise it is a translation into another language (Vietnamese, English …) and is left out, never mixed
 * into the original.
 */
fun embeddedLayers(text: String): LyricLayers {
    val split=splitEmbeddedTranslation(text) ?: return LyricLayers(text,null,null)
    val first=mainLanguage(Lrc.parse(split.first).map { it.text })
    return when(mainLanguage(Lrc.parse(split.second).map { it.text })) {
        TranslateLanguage.CHINESE -> LyricLayers(split.first,split.second,null)
        TranslateLanguage.JAPANESE -> if(looksLikeRomaji(split.first)) LyricLayers(split.second,null,split.first) else LyricLayers(text,null,null)
        TranslateLanguage.ENGLISH -> when(first) {
            TranslateLanguage.JAPANESE -> LyricLayers(split.first,null,split.second.takeIf(::looksLikeRomaji))
            TranslateLanguage.CHINESE,TranslateLanguage.KOREAN -> LyricLayers(split.first,null,null)
            else -> LyricLayers(text,null,null)
        }
        else -> LyricLayers(text,null,null)
    }
}

/**
 * Optional explicit-consent lookup on LRCLIB, searched by title and artist.
 * When LRCLIB has the title under a differently written artist (水瀬いのり ↔ Inori Minase),
 * the artist's aliases are looked up on MusicBrainz and matching is retried with them.
 * Every request gives up after 8 seconds so a slow service never stalls the lyrics view.
 */
class LyricsClient(context: android.content.Context) {
    private val client=OkHttpClient.Builder().callTimeout(8,TimeUnit.SECONDS).build()
    private val userAgent="Monologue/${BuildConfig.VERSION_NAME} ( https://github.com/HKmario852/monologue )"
    /** Aliases survive restarts (empty lists too), so each artist is asked on MusicBrainz at most once. */
    private val aliasStore=context.getSharedPreferences("lyrics-aliases",android.content.Context.MODE_PRIVATE)
    private val aliases=java.util.concurrent.ConcurrentHashMap<String,List<String>>(aliasStore.all.mapNotNull { (k,v) -> (v as? String)?.let { k to it.split('\u001f').filter { s -> s.isNotBlank() } } }.toMap())
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
        aliasStore.edit().putString(name,found.joinToString("\u001f")).apply()
        return found
    }

    suspend fun find(track: Track,base: String="https://lrclib.net"): FoundLyrics?=withContext(Dispatchers.IO) {
        val origin=base.toHttpUrl();require(origin.isHttps) {"歌詞服務必須使用 HTTPS"}
        val seconds=(track.durationMs/1000).toInt()
        val artists=creditedArtists(track.artist)
        fun found(pick: LyricsPick,via: String=""): FoundLyrics {
            val source="LRCLIB · ${pick.candidate.artist} · ${pick.candidate.track}$via"+if(pick.offsetSec>3) " · 長度相差 ${pick.offsetSec} 秒，時間可能略有偏差" else ""
            val layers=embeddedLayers(pick.text)
            return FoundLyrics(layers.original,layers.translation,layers.romaji,source)
        }
        // Search with the title as tagged, then without bracketed notes such as (Single Ver.) or (feat. X).
        val titles=listOfNotNull(track.title,searchTitleWithoutNotes(track.title))
        val all=mutableListOf<LyricsCandidate>()
        var titleOnlySearched=false
        suspend fun expandedArtists()=artists+artists.take(3).flatMap { name -> try { aliasesOf(name) } catch(e: CancellationException) { throw e } catch(e: Exception) { emptyList() } }
        /**
         * Some catalogues list the romaji transcription under the romanised artist and the Japanese original under the
         * Japanese name (BABYMETAL / ベビーメタル). When the match is romaji, look for a Japanese version of the same song:
         * same title and an alias-matched artist, or same title and a length within 3 s. The romaji stays as its own layer.
         */
        suspend fun preferJapanese(pick: LyricsPick,via: String=""): FoundLyrics {
            if(!looksLikeRomaji(pick.text)) return found(pick,via)
            // The Japanese version is often under another artist, so it only shows up in a title-only search.
            if(!titleOnlySearched) { titleOnlySearched=true; all+=try { search(origin,mapOf("track_name" to track.title)) } catch(e: java.io.IOException) { emptyList() } }
            val japanese=all.distinctBy { it.id }.filter { sameTitle(it.track,track.title) && hasJapaneseScript(it.synced ?: it.plain ?: "") }
            if(japanese.isEmpty()) return found(pick,via)
            val byArtist=pickLyrics(track.title,expandedArtists(),seconds,japanese)
            val byLength=if(seconds>0) pickLyrics(track.title,emptyList(),seconds,japanese.filter { kotlin.math.abs(it.durationSec-seconds)<=3 }) else null
            // An artist match whose length is off loses to a same-length version, whose timestamps fit the recording.
            val nativePick=(if(byArtist!=null && (byArtist.offsetSec<=3 || byLength==null)) byArtist else byLength) ?: return found(pick,via)
            val native=found(nativePick," · 日文版")
            return native.copy(romaji=native.romaji ?: pick.text)
        }
        for(title in titles) {
            ensureActive()
            // 1. Title + main artist. 2. Title only (catalogues credit artists differently).
            val first=artists.firstOrNull()?.let { search(origin,mapOf("track_name" to title,"artist_name" to it)) } ?: emptyList()
            all+=first
            pickLyrics(track.title,artists,seconds,all.distinctBy { it.id })?.let { return@withContext preferJapanese(it) }
            // A failed fallback after a real answer means "not found" rather than "service down".
            all+=try { search(origin,mapOf("track_name" to title)) } catch(e: java.io.IOException) { if(artists.isEmpty()) throw e else emptyList() }
            if(title==track.title) titleOnlySearched=true
            pickLyrics(track.title,artists,seconds,all.distinctBy { it.id })?.let { return@withContext preferJapanese(it) }
        }
        // 3. The title exists under another spelling of the artist: ask MusicBrainz for aliases, only when it could help.
        val candidates=all.distinctBy { it.id }
        if(artists.isEmpty() || candidates.none { sameTitle(it.track,track.title) }) return@withContext null
        pickLyrics(track.title,expandedArtists(),seconds,candidates)?.let { return@withContext preferJapanese(it," · 以 MusicBrainz 別名配對") }
        null
    }
}
