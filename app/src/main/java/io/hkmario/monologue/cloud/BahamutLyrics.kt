package io.hkmario.monologue.cloud

import io.hkmario.monologue.domain.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

/**
 * Fan translations posted as 創作 on 巴哈姆特 (home.gamer.com.tw), read from the web pages (unofficial, off by default).
 * Posts are found through the site's own tag search: first the artist's tag (translators tag posts with the artist),
 * then the song title's tag. A post counts only when its title names the song. The lyrics are plain text, with the
 * translator's Chinese translation paired line by line when the post lays it out that way.
 */
class BahamutLyrics(private val romaji: RomajiGenerator) {
    private val client=OkHttpClient.Builder().callTimeout(8,TimeUnit.SECONDS).build()
    private fun page(url: HttpUrl): Document = client.newCall(Request.Builder().url(url)
        .header("User-Agent","Mozilla/5.0 (Linux; Android) Monologue").header("Accept-Language","zh-TW,zh;q=0.9,ja;q=0.8").build()).execute().use { r ->
        if(!r.isSuccessful) throw HttpFailure(r.code)
        Jsoup.parse(r.body?.string() ?: "",url.toString())
    }

    private data class Post(val url: String,val title: String,val author: String)

    private fun tagSearch(tag: String,page: Int): List<Post> {
        val doc=page("https://home.gamer.com.tw/search.php".toHttpUrl().newBuilder().addQueryParameter("keyword",tag).addQueryParameter("o","tag").addQueryParameter("page","$page").build())
        return doc.select("a.TS1[href*=creationDetail.php]").map { a ->
            val author=a.closest(".HOME-mainbox1b")?.selectFirst(".ST1 a")?.text() ?: ""
            Post(a.absUrl("href"),a.text(),author)
        }
    }

    /** The site's keyword search, which matches every word of [query] in post titles and text. */
    private fun keywordSearch(query: String): List<Post> {
        val doc=page("https://home.gamer.com.tw/search.php".toHttpUrl().newBuilder().addQueryParameter("keyword",query).build())
        return doc.select("a.TS1[href*=creationDetail.php]").map { a ->
            Post(a.absUrl("href"),a.text(),a.closest(".HOME-mainbox1b")?.selectFirst(".ST1 a")?.text() ?: "")
        }
    }

    /** The post's lyrics; with [artists], only when its text or tags name one of them (for titles without the artist). */
    private fun read(post: Post,artists: List<String>?=null): FoundLyrics? {
        val doc=page(post.url.toHttpUrl())
        val body=doc.selectFirst("#article_content") ?: return null
        if(artists!=null) {
            val tags=doc.select("a[href*=o=tag]").joinToString(" ") { it.text() }
            if(artists.none { body.text().contains(it,ignoreCase=true) || tags.contains(it,ignoreCase=true) }) return null
        }
        body.select("script, style").remove()
        body.select("br").forEach { it.after("\n") }
        body.select("p, div").forEach { it.appendText("\n") }
        val text=body.wholeText().lines().joinToString("\n") { it.trim().replace('　',' ') }
        val lyrics=splitBilingualLyrics(text) ?: return null
        val kind=if(isSyncedLyrics(lyrics.original)) "網友翻譯" else "網友翻譯（純文字）"
        return FoundLyrics(lyrics.original,lyrics.translation,null,"巴哈姆特 · $kind · ${post.author} · ${post.title}")
    }

    /** Reading of a title in romaji, so 二人の唇 and ふたりの唇 compare equal. */
    private suspend fun readingKey(text: String)=try { looseTitleKey(romaji.generate(text)) } catch(e: CancellationException) { throw e } catch(e: Exception) { looseTitleKey(text) }

    suspend fun find(track: Track,artists: List<String>): FoundLyrics? = withContext(Dispatchers.IO) {
        val titles=songTitleVariants(track.title,track.album)
        val readings=titles.map { readingKey(it) }.filter { it.length>=4 }.toSet()
        suspend fun aboutSong(post: Post): Boolean {
            if(titles.any { postTitleMentionsSong(post.title,it) }) return true
            // Kanji in one title and kana in the other: compare how they read.
            return readings.isNotEmpty() && postTitleParts(post.title).any { readingKey(it) in readings }
        }
        fun namesArtist(post: Post)=artists.isEmpty() || artists.any { post.title.contains(it,ignoreCase=true) }
        val tried=mutableSetOf<String>()
        suspend fun firstReadable(posts: List<Post>,needArtist: Boolean): FoundLyrics? {
            for(post in posts) {
                ensureActive()
                if(post.url in tried || !aboutSong(post)) continue
                tried+=post.url
                // A post titled with the song alone ("agony（神無月巫女）") counts when its text or tags name the artist.
                read(post,if(needArtist && !namesArtist(post)) artists else null)?.let { return it }
            }
            return null
        }
        // 1. The main artist's tag, newest first; a few pages cover most artists' translated songs.
        artists.firstOrNull()?.let { artist ->
            for(page in 1..5) {
                ensureActive()
                val posts=tagSearch(artist,page)
                if(posts.isEmpty()) break
                firstReadable(posts,needArtist=false)?.let { return@withContext it }
            }
        }
        // 2. The song title's tag, when the post also names one of the artists.
        for(title in titles) { ensureActive(); firstReadable(tagSearch(title,1),needArtist=true)?.let { return@withContext it } }
        // 3. Keyword search for posts not tagged with the artist: artist and title, artist and the title's Latin words
        //    (kanji may be written in kana in the post), and all credited artists together.
        val song=titles.last()
        val latin=Regex("""[A-Za-z][A-Za-z']*(?:\s+[A-Za-z][A-Za-z']*)*""").findAll(song).map { it.value }.filter { it.length>=4 }.joinToString(" ")
        val queries=listOfNotNull(
            artists.firstOrNull()?.let { "$it $song" },
            artists.firstOrNull()?.takeIf { latin.isNotEmpty() }?.let { "$it $latin" },
            artists.takeIf { it.size>=2 }?.joinToString(" "),
        ).distinct()
        for(query in queries) { ensureActive(); firstReadable(keywordSearch(query),needArtist=true)?.let { return@withContext it } }
        null
    }
}
