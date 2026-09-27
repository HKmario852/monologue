package io.hkmario.monologue.cloud

import io.hkmario.monologue.domain.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

/**
 * Plain-text lyrics read from Japanese lyric web pages (unofficial, off by default).
 * These pages have no timestamps, so the lyrics show without line highlighting.
 * Pages change without notice; a layout change makes a provider return nothing rather than wrong text.
 */
abstract class WebLyricsProvider {
    protected val client=OkHttpClient.Builder().callTimeout(8,TimeUnit.SECONDS).build()
    protected fun page(url: HttpUrl) = client.newCall(Request.Builder().url(url)
        .header("User-Agent","Mozilla/5.0 (Linux; Android) Monologue").header("Accept-Language","ja,en;q=0.8").build()).execute().use { r ->
        if(!r.isSuccessful) throw HttpFailure(r.code)
        Jsoup.parse(r.body?.string() ?: "",url.toString())
    }
    /** Text of a lyrics block: furigana (rt/rp) removed, <br> kept as line breaks. */
    protected fun lyricsText(block: Element): String {
        block.select("rt, rp, .rt, script, style").remove()
        block.select("br").forEach { it.after("\n") }
        return block.wholeText().lines().map { it.trim().replace('　',' ') }.joinToString("\n").replace(Regex("\n{3,}"),"\n\n").trim()
    }
    /** Candidates found by title; each carries the page URL in [LyricsCandidate.plain] until chosen. */
    protected abstract fun search(title: String,artist: String?): List<LyricsCandidate>
    protected abstract fun read(url: String): FoundLyrics?
    protected abstract val label: String

    suspend fun find(track: Track, artists: List<String>): FoundLyrics? = withContext(Dispatchers.IO) {
        // Title + main artist narrows common titles (カラフル) to one page; title alone catches differently credited songs.
        for(title in listOfNotNull(track.title,searchTitleWithoutNotes(track.title))) for(artist in listOfNotNull(artists.firstOrNull(),null).distinct()) {
            ensureActive()
            val pick=pickLyrics(track.title,artists,0,search(title,artist).map { it.copy(synced=null) }) ?: continue
            // Name the source from the matched search result, which is cleaner than the page heading.
            return@withContext read(pick.candidate.plain!!)?.copy(source="$label（純文字）· ${pick.candidate.artist} · ${pick.candidate.track}")
        }
        null
    }
}

/** J-Lyric (j-lyric.net): search by title, lyrics in <p id="Lyric">. */
class JLyricProvider: WebLyricsProvider() {
    override val label="J-Lyric"
    override fun search(title: String,artist: String?): List<LyricsCandidate> {
        val doc=page("https://j-lyric.net/search.php".toHttpUrl().newBuilder().addQueryParameter("kt",title).addQueryParameter("ct","2")
            .apply { if(artist!=null) { addQueryParameter("ka",artist); addQueryParameter("ca","2") } }.build())
        return doc.select("p.mid > a[href*=/artist/]").mapNotNull { a ->
            val artist=a.parent()?.nextElementSibling()?.takeIf { it.hasClass("sml") }?.selectFirst("a")?.text() ?: return@mapNotNull null
            LyricsCandidate(0,a.text(),artist,0.0,null,a.absUrl("href"))
        }
    }
    override fun read(url: String): FoundLyrics? {
        val doc=page(url.toHttpUrl())
        val block=doc.selectFirst("p#Lyric") ?: return null
        return lyricsText(block).takeIf { it.isNotBlank() }?.let { FoundLyrics(it,null,null,label) }
    }
}

/** うたてん (utaten.com): search by title; lyrics page has a hiragana block and a romaji block. */
class UtaTenProvider: WebLyricsProvider() {
    override val label="うたてん"
    override fun search(title: String,artist: String?): List<LyricsCandidate> {
        val doc=page("https://utaten.com/lyric/search".toHttpUrl().newBuilder().addQueryParameter("title",title).addQueryParameter("sort","popular_sort_asc")
            .apply { if(artist!=null) addQueryParameter("artist_name",artist) }.build())
        return doc.select("article.list__item").mapNotNull { item ->
            val link=item.selectFirst("h2.list__title a[href*=/lyric/]") ?: return@mapNotNull null
            val artist=item.selectFirst(".list__name__artist a")?.text() ?: return@mapNotNull null
            LyricsCandidate(0,link.text(),artist,0.0,null,link.absUrl("href"))
        }
    }
    override fun read(url: String): FoundLyrics? {
        val doc=page(url.toHttpUrl())
        val body=doc.selectFirst(".lyricBody") ?: return null
        val original=body.selectFirst(".hiragana")?.let { lyricsText(it.clone()) }?.takeIf { it.isNotBlank() } ?: return null
        // The romaji block pairs each segment with its reading in <rt>; the readings joined form the romaji line.
        val romaji=body.selectFirst(".romaji")?.let { r ->
            r.select(".ruby").forEach { ruby -> ruby.replaceWith(org.jsoup.nodes.TextNode(" "+ruby.select(".rt").text()+" ")) }
            r.select("br").forEach { it.after("\n") }
            r.wholeText().lines().map { it.trim().replace(Regex("\\s+")," ") }.joinToString("\n").replace(Regex("\n{3,}"),"\n\n").trim()
        }?.takeIf { it.lines().size==original.lines().size }
        return FoundLyrics(original,null,romaji,label)
    }
}
