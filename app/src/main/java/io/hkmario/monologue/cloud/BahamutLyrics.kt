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
class BahamutLyrics {
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

    private fun read(post: Post): FoundLyrics? {
        val doc=page(post.url.toHttpUrl())
        val body=doc.selectFirst("#article_content") ?: return null
        body.select("script, style").remove()
        body.select("br").forEach { it.after("\n") }
        body.select("p, div").forEach { it.appendText("\n") }
        val text=body.wholeText().lines().joinToString("\n") { it.trim().replace('　',' ') }
        val lyrics=splitBilingualLyrics(text) ?: return null
        return FoundLyrics(lyrics.original,lyrics.translation,null,"巴哈姆特 · 網友翻譯（純文字）· ${post.author} · ${post.title}")
    }

    suspend fun find(track: Track,artists: List<String>): FoundLyrics? = withContext(Dispatchers.IO) {
        val titles=listOfNotNull(track.title,searchTitleWithoutNotes(track.title))
        fun aboutSong(post: Post)=titles.any { postTitleMentionsSong(post.title,it) }
        val tried=mutableSetOf<String>()
        suspend fun firstReadable(posts: List<Post>): FoundLyrics? {
            for(post in posts) { ensureActive(); if(tried.add(post.url)) read(post)?.let { return it } }
            return null
        }
        // 1. The main artist's tag, newest first; a few pages cover most artists' translated songs.
        artists.firstOrNull()?.let { artist ->
            for(page in 1..5) {
                ensureActive()
                val posts=tagSearch(artist,page)
                if(posts.isEmpty()) break
                firstReadable(posts.filter(::aboutSong))?.let { return@withContext it }
            }
        }
        // 2. The song title's tag, when the post also names one of the artists.
        for(title in titles) {
            ensureActive()
            val posts=tagSearch(title,1).filter { post -> aboutSong(post) && (artists.isEmpty() || artists.any { post.title.contains(it,ignoreCase=true) }) }
            firstReadable(posts)?.let { return@withContext it }
        }
        null
    }
}
