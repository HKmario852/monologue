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

    /** A name compared without case, spaces or apostrophes: tags write Ariabl’eyeS as Ariabl'eyeS or AriableyeS. */
    private fun looseName(s: String)=normalize(s).replace(Regex("""[\s'’‘`´＇]"""),"")
    private fun mentions(text: String,name: String): Boolean {
        val loose=looseName(name)
        if(loose.isEmpty()) return false
        if(loose.any { it.code>0x7F }) return looseName(text).contains(loose)
        // A name in Latin letters must stand as words: FLOW is not in "overflow" or "flowers".
        val words=normalize(name).replace(apostrophes,"").split(' ').filter { it.isNotEmpty() }.joinToString("""\s*""") { Regex.escape(it) }
        return Regex("""(?<![a-z0-9])$words(?![a-z0-9])""").containsMatchIn(normalize(text).replace(apostrophes,""))
    }
    private val apostrophes=Regex("""['’‘`´＇]""")
    /** The name as the site's search expects it: a curly apostrophe finds nothing, a straight one does. */
    private fun searchName(s: String)=s.replace('’','\'').replace('‘','\'')

    private fun tagSearch(tag: String,page: Int): List<Post> {
        val doc=page("https://home.gamer.com.tw/search.php".toHttpUrl().newBuilder().addQueryParameter("keyword",tag).addQueryParameter("o","tag").addQueryParameter("page","$page").build())
        return doc.select("a.TS1[href*=creationDetail.php]").map { a ->
            val author=a.closest(".HOME-mainbox1b")?.selectFirst(".ST1 a")?.text() ?: ""
            Post(a.absUrl("href"),a.text(),author)
        }
    }

    /** The site's keyword search, which matches every word of [query] in post titles and text. */
    private fun keywordSearch(query: String,page: Int=1): List<Post> {
        val doc=page("https://home.gamer.com.tw/search.php".toHttpUrl().newBuilder().addQueryParameter("keyword",query).addQueryParameter("page","$page").build())
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
            if(artists.none { mentions(body.text(),it) || mentions(tags,it) }) return null
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
        fun namesArtist(post: Post)=artists.isEmpty() || artists.any { mentions(post.title,it) }
        val song=titles.last().trimEnd('。','.','！','!','？','?')
        // A long title in Japanese or Chinese names one song, so a post titled with it needs no singer in its text,
        // and searching the title alone is worth it; a short one ("Colors") matches many songs' posts.
        val distinctive=looseTitleKey(song).let { key -> key.count { it.code>0x2E80 }>=6 || key.length>=16 }
        val tried=mutableSetOf<String>()
        // A post with the lyrics but no translation (a pronunciation guide, say) is kept only in case no post
        // translates the song. Reading stops after a few posts, as each one is a page load.
        var untranslated: FoundLyrics?=null
        fun done()=tried.size>=MAX_READS
        suspend fun firstTranslated(posts: List<Post>,needArtist: Boolean): FoundLyrics? {
            for(post in posts) {
                ensureActive()
                if(done()) return null
                if(post.url in tried || !aboutSong(post)) continue
                // For a short title, a post not naming the singer in its title is read only when the title says it
                // carries lyrics or a translation; figure reviews and comics called "Colors" are not.
                if(needArtist && !distinctive && !namesArtist(post) && !lyricsPostTitle.containsMatchIn(post.title)) continue
                tried+=post.url
                // A post titled with the song alone ("agony（神無月巫女）") counts when its text or tags name the artist.
                val found=read(post,if(needArtist && !namesArtist(post)) artists else null) ?: continue
                if(found.translation!=null) return found
                if(untranslated==null) untranslated=found
            }
            return null
        }
        // 1. The main artist's tag, newest first; a few pages cover most artists' translated songs.
        artists.firstOrNull()?.let { artist ->
            for(tag in listOf(searchName(artist),artist.filterNot { it in "'’‘" }).distinct()) for(page in 1..5) {
                ensureActive()
                if(done()) return@withContext untranslated
                val posts=tagSearch(tag,page)
                if(posts.isEmpty()) break
                firstTranslated(posts,needArtist=false)?.let { return@withContext it }
            }
        }
        // 2. The song title's tag, when the post also names one of the artists.
        for(title in (titles+titles.map(::toTraditional)).distinct()) {
            ensureActive()
            if(done()) return@withContext untranslated
            firstTranslated(tagSearch(title,1),needArtist=true)?.let { return@withContext it }
        }
        // 3. Keyword search, as a person would search the site: title and singer, title and 中文翻譯, then the title alone over a few
        //    pages. Translators in Taiwan type 黃泉 and 皇國 where the title has 黄泉 and 皇国, and the site matches
        //    only the form written, so both are tried. Then the singer with the title's Latin words (kanji may be
        //    written in kana in the post) and all credited singers together.
        val artist=artists.firstOrNull()?.let(::searchName)
        val forms=listOf(song,toTraditional(song)).distinct()
        val latin=Regex("""[A-Za-z][A-Za-z']*(?:\s+[A-Za-z][A-Za-z']*)*""").findAll(song).map { it.value }.filter { it.length>=4 }.joinToString(" ")
        // Many posts name the anime rather than the singer in their title ("【歌詞中文翻譯】反逆のルルーシュ Colors"),
        // and the site searches titles: the title with 中文翻譯 finds those even for a short title.
        val queries=forms.mapNotNull { form -> artist?.let { "$form $it" to 2 } } + forms.map { "$it 中文翻譯" to 2 } +
            (if(distinctive || artist==null) forms.map { it to 3 } else emptyList()) + listOfNotNull(
            artist?.takeIf { latin.isNotEmpty() }?.let { "$it $latin" to 1 },
            artists.takeIf { it.size>=2 }?.joinToString(" ") { searchName(it) }?.let { it to 1 },
        )
        for((query,pages) in queries.distinctBy { it.first }) for(page in 1..pages) {
            ensureActive()
            if(done()) return@withContext untranslated
            val posts=keywordSearch(query,page)
            if(posts.isEmpty()) break
            firstTranslated(posts,needArtist=!distinctive)?.let { return@withContext it }
        }
        untranslated
    }

    private companion object {
        const val MAX_READS=15
        val lyricsPostTitle=Regex("""(?i)歌詞|歌词|翻譯|翻译|中譯|中日|日中|lyrics""")
    }
}
