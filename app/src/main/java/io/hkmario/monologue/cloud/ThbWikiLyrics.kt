package io.hkmario.monologue.cloud

import io.hkmario.monologue.BuildConfig
import io.hkmario.monologue.domain.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** A THBWiki 歌词 page read into LRC: the sung lines, their Chinese translation (same timestamps) and the translator. */
data class ThbLyrics(val original: String, val translation: String?, val translator: String)

// Brackets are escaped throughout: Android's regex engine rejects a bare "}" or "]".
private val wikiRuby = Regex("""\{\{\s*(?:ruby|注音)\s*\|([^|\}]*)\|[^\}]*\}\}""", RegexOption.IGNORE_CASE)
private val wikiLink = Regex("""\[\[(?:[^|\]]*\|)?([^\]]*)\]\]""")
private val htmlTag = Regex("""<[^>]+>""")
private fun wikiText(s: String) = s.replace(wikiRuby, "$1").replace(wikiLink, "$1").replace(Regex("""<rt>.*?</rt>"""), "").replace(htmlTag, "").replace('　', ' ').trim()

/**
 * Reads the `lyrics=` part of a THBWiki 歌词 page: blocks of `time=00:05.55`, the sung line (`ja=`, or `en=` and other
 * languages) and its translation (`zh=`); `sep=` marks a break. Null when the page has fewer than four sung lines.
 */
fun parseThbLyrics(wikitext: String): ThbLyrics? {
    val body = wikitext.substringAfter("\nlyrics=", "").ifEmpty { return null }
    val original = StringBuilder(); val translation = StringBuilder(); var lines = 0; var translated = 0
    for(block in body.split(Regex("""\n\s*\n"""))) {
        val fields = block.lines().mapNotNull { line -> line.indexOf('=').takeIf { it > 0 }?.let { line.substring(0, it).trim() to wikiText(line.substring(it + 1)) } }
        if(fields.isEmpty()) continue
        val time = fields.firstOrNull { it.first == "time" || it.first == "sep" }?.second?.takeIf { Regex("""\d{1,3}:\d{2}(\.\d{1,3})?""").matches(it) }?.let { "[$it]" } ?: ""
        val sung = fields.firstOrNull { it.first !in setOf("time", "sep", "zh") && it.second.isNotEmpty() }?.second
        if(sung == null) { if(time.isNotEmpty()) original.append(time).append('\n'); continue }
        original.append(time).append(sung).append('\n'); lines++
        fields.firstOrNull { it.first == "zh" && it.second.isNotEmpty() }?.let { translation.append(time).append(it.second).append('\n'); translated++ }
    }
    if(lines < 4) return null
    val translator = Regex("""译者\s*=\s*(.*)""").find(wikitext)?.groupValues?.get(1)?.let { wikiText(it.replace(Regex("""\[\[用户:([^|\]]*)(?:\|([^\]]*))?\]\]""")) { m -> m.groupValues[2].ifEmpty { m.groupValues[1] } }) }?.trim(' ', '，', ',') ?: ""
    // A translation is kept only when it covers the lines, so it lines up.
    return ThbLyrics(original.toString().trim(), translation.toString().trim().takeIf { translated >= lines * 0.8 }, translator)
}

/**
 * THBWiki (thwiki.cc), the Chinese Touhou Project wiki, through its public MediaWiki API. Its 歌词 pages give Touhou
 * doujin songs line by line with timestamps and a Chinese translation by a named translator (CC BY-NC-SA 3.0).
 * A page counts when its title is the song's and the circle in the title, or an album page linking to it, names
 * one of the artists (or the album).
 */
class ThbWikiLyrics {
    private val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
    private val userAgent = "Monologue/${BuildConfig.VERSION_NAME} ( https://github.com/HKmario852/monologue )"

    private fun api(vararg params: Pair<String, String>): JSONObject {
        val url = "https://thwiki.cc/api.php".toHttpUrl().newBuilder().addQueryParameter("format", "json").addQueryParameter("formatversion", "2")
        params.forEach { (k, v) -> url.addQueryParameter(k, v) }
        val body = client.newCall(Request.Builder().url(url.build()).header("User-Agent", userAgent).build()).execute().use { r ->
            if(!r.isSuccessful) throw HttpFailure(r.code)
            r.body?.string() ?: ""
        }
        return JSONObject(body.ifBlank { "{}" })
    }

    /** Wikitext of each page, by title. */
    private fun contents(titles: List<String>): Map<String, String> {
        if(titles.isEmpty()) return emptyMap()
        val pages = api("action" to "query", "prop" to "revisions", "rvprop" to "content", "rvslots" to "main", "titles" to titles.joinToString("|"))
            .optJSONObject("query")?.optJSONArray("pages") ?: return emptyMap()
        return (0 until pages.length()).map { pages.getJSONObject(it) }.mapNotNull { p ->
            p.optJSONArray("revisions")?.optJSONObject(0)?.optJSONObject("slots")?.optJSONObject("main")?.optString("content")?.let { p.optString("title") to it }
        }.toMap()
    }

    suspend fun find(track: Track, artists: List<String>): FoundLyrics? = withContext(Dispatchers.IO) {
        val titles = songTitleVariants(track.title, track.album)
        val names = (artists + track.album).map(::looseTitleKey).filter { it.length >= 2 }
        fun named(text: String) = looseTitleKey(text).let { t -> names.any { t.contains(it) } }
        for(title in titles) {
            ensureActive()
            val found = api("action" to "query", "list" to "search", "srsearch" to title, "srnamespace" to "512", "srlimit" to "10")
                .optJSONObject("query")?.optJSONArray("search") ?: continue
            // "歌词:Bad Apple!!（Alstroemeria Records）": the song title, then the circle when several songs share it.
            val pages = (0 until found.length()).map { found.getJSONObject(it).optString("title") }.filter { page ->
                val name = page.removePrefix("歌词:")
                sameTitle(withoutBracketNotes(name), title) || looseTitleKey(withoutBracketNotes(name)) == looseTitleKey(title)
            }.take(3)
            for(page in pages) {
                ensureActive()
                val circle = Regex("""（([^（）]+)）$""").find(page)?.groupValues?.get(1)
                val matches = if(circle != null && named(circle)) true else {
                    // Otherwise an album page that lists the song names the artist (制作方, 演唱 …) or is the album.
                    val albums = api("action" to "query", "list" to "backlinks", "bltitle" to page, "blnamespace" to "0", "bllimit" to "5")
                        .optJSONObject("query")?.optJSONArray("backlinks")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("title") } } ?: emptyList()
                    albums.any { named(withoutBracketNotes(it)) } || contents(albums).values.any(::named)
                }
                if(!matches) continue
                val lyrics = contents(listOf(page))[page]?.let(::parseThbLyrics) ?: continue
                // The last line must come before the end of the file (a different arrangement runs longer).
                val last = Lrc.parse(lyrics.original).mapNotNull { it.timeMs }.maxOrNull() ?: 0
                if(track.durationMs > 0 && last > track.durationMs + 15_000) continue
                val credit = lyrics.translator.takeIf { it.isNotEmpty() }?.let { " · 譯者：$it" } ?: ""
                return@withContext FoundLyrics(lyrics.original, lyrics.translation, null, "THBWiki · ${page.removePrefix("歌词:")}$credit")
            }
        }
        null
    }
}
