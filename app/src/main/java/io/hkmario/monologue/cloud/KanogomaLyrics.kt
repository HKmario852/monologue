package io.hkmario.monologue.cloud

import io.hkmario.monologue.domain.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/** One song page on Kanogoma: "ARTIST - TITLE", the singer row, and each line's Japanese and Chinese. */
data class KanogomaSong(val heading: String, val singer: String, val original: List<String>, val chinese: List<String>)

/**
 * Reads a Kanogoma song page: each lyric line is a `div[data-i]` holding the Japanese with furigana (`p.ruby`),
 * romaji (`p.rmj`) and the Chinese translation (`p.zh`). A line without Chinese (a shout, an English line) keeps its
 * own text so the translation stays line for line. Null when the page has no lyrics.
 */
fun parseKanogomaSong(html: String): KanogomaSong? {
    val doc = Jsoup.parse(html)
    val lines = doc.select("#kngm > div[data-i]").mapNotNull { line ->
        val ruby = line.selectFirst("p.ruby") ?: return@mapNotNull null
        ruby.select("rt, rp").remove()
        val original = ruby.text().replace('　', ' ').trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        original to (line.selectFirst("p.zh")?.text()?.trim()?.takeIf { it.isNotEmpty() } ?: original)
    }
    if(lines.size < 4) return null
    val singer = doc.select(".info-table tr").firstOrNull { it.selectFirst("th")?.text()?.startsWith("唱") == true }?.selectFirst("td")?.text() ?: ""
    return KanogomaSong(doc.selectFirst("h1")?.text() ?: "", singer, lines.map { it.first }, lines.map { it.second })
}

/**
 * Kanogoma 歌の胡麻 (kanogoma.com), a site of hand-made Chinese translations of Japanese songs (about 400). Songs are
 * found with the site's WordPress search API; the lyrics are read from the song page (unofficial, off by default).
 * Plain text, so they do not scroll with playback on their own; the translation also moves onto synced lyrics.
 */
class KanogomaLyrics {
    private val client = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()
    private fun get(url: HttpUrl): String = client.newCall(Request.Builder().url(url)
        .header("User-Agent", "Mozilla/5.0 (Linux; Android) Monologue").header("Accept-Language", "zh-HK,zh-TW;q=0.9,ja;q=0.8").build()).execute().use { r ->
        if(!r.isSuccessful) throw HttpFailure(r.code)
        r.body?.string() ?: ""
    }

    /** Song page addresses for [query], best match first. */
    private fun search(query: String): List<String> {
        val body = get("https://kanogoma.com/wp-json/wp/v2/search".toHttpUrl().newBuilder().addQueryParameter("search", query)
            .addQueryParameter("subtype", "song").addQueryParameter("per_page", "5").build())
        val items = JSONArray(body.ifBlank { "[]" })
        return (0 until items.length()).map { items.getJSONObject(it).optString("url") }.filter { it.startsWith("https://kanogoma.com/song/") }
    }

    suspend fun find(track: Track, artists: List<String>): FoundLyrics? = withContext(Dispatchers.IO) {
        val titles = songTitleVariants(track.title, track.album)
        val queries = titles.flatMap { title -> listOfNotNull(artists.firstOrNull()?.let { "$title $it" }, title) }.distinct()
        val tried = mutableSetOf<String>()
        for(query in queries) {
            ensureActive()
            for(url in search(query).filter { tried.add(it) }.take(3)) {
                ensureActive()
                val song = parseKanogomaSong(get(url.toHttpUrl())) ?: continue
                // The heading is "ARTIST - TITLE"; the singer row names the artist.
                val title = song.heading.substringAfter(" - ", song.heading)
                if(titles.none { sameTitle(title, it) || looseTitleKey(title) == looseTitleKey(it) }) continue
                val singer = looseTitleKey(song.singer.ifBlank { song.heading.substringBefore(" - ") })
                if(artists.isNotEmpty() && artists.none { looseTitleKey(it).let { a -> a.isNotEmpty() && (singer.contains(a) || a.contains(singer) && singer.isNotEmpty()) } }) continue
                return@withContext FoundLyrics(song.original.joinToString("\n"), song.chinese.joinToString("\n"), null, "Kanogoma 歌の胡麻 · 人手翻譯（純文字） · ${song.heading}")
            }
        }
        null
    }
}
