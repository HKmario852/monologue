package io.hkmario.monologue.cloud

import io.hkmario.monologue.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.IOException
import java.util.concurrent.TimeUnit

private val kana = Regex("[\\u3040-\\u30ff]")

/**
 * The lyrics and translation in a web page the user found (a 巴哈姆特 創作, a Pixnet or other blog post, a Kanogoma song
 * page): the part of the page with the most Japanese is taken as the post, read line by line, and split into the
 * original and its Chinese translation. Null when the page has no lyrics with a translation.
 */
fun bilingualFromHtml(html: String, url: String): BilingualLyrics? {
    val doc = Jsoup.parse(html, url)
    parseKanogomaSong(html)?.let { return BilingualLyrics(it.original.joinToString("\n"), it.chinese.joinToString("\n")) }
    doc.select("script, style, noscript, nav, header, footer, aside, form, iframe").remove()
    fun kanaIn(e: Element) = kana.findAll(e.text()).count()
    // The post body: a known container with Japanese in it, else the smallest element holding nearly all the page's
    // Japanese (the post itself rather than the whole page around it).
    val known = listOf("#article_content", ".article-content-inner", ".article-content", ".post-content", ".entry-content", "article")
        .firstNotNullOfOrNull { selector -> doc.select(selector).firstOrNull { kanaIn(it) >= 20 } }
    val body = known ?: run {
        val candidates = doc.select("div, section, main, article, td").map { it to kanaIn(it) }
        val most = candidates.maxOfOrNull { it.second } ?: 0
        candidates.filter { it.second >= most * 0.9 && most > 0 }.minByOrNull { it.first.text().length }?.first
    } ?: doc.body() ?: return null
    return splitBilingualLyrics(lineText(body))
}

/** An element's text with its line breaks: <br>, paragraphs and blocks each end a line. */
private fun lineText(element: Element): String {
    val copy = element.clone()
    copy.select("br").forEach { it.after("\n") }
    copy.select("p, div, li, h1, h2, h3, h4, tr").forEach { it.appendText("\n") }
    return copy.wholeText().lines().joinToString("\n") { it.trim().replace('　', ' ') }
}

/** Reads a translation page the user pasted (https only). Pages behind a bot check (巴哈姆特's forum) cannot be read. */
class TranslationLinks {
    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    suspend fun read(link: String): BilingualLyrics = withContext(Dispatchers.IO) {
        val url = link.trim().toHttpUrlOrNull()?.takeIf { it.isHttps } ?: throw IOException("請貼上以 https:// 開頭的網址")
        val html = client.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android) Monologue").header("Accept-Language", "zh-TW,zh;q=0.9,ja;q=0.8").build()).execute().use { r ->
            if(r.code == 403 || r.code == 429) throw IOException("這個網站拒絕讀取（可能需要人機驗證）；請試另一個網頁")
            if(!r.isSuccessful) throw IOException("網頁回應 HTTP ${r.code}")
            r.body?.source()?.let { s -> s.request(4L * 1024 * 1024); s.buffer.readUtf8(minOf(s.buffer.size, 4L * 1024 * 1024)) } ?: ""
        }
        bilingualFromHtml(html, url.toString())?.takeIf { it.translation != null } ?: throw IOException("這個網頁找不到可以逐行配對的歌詞翻譯")
    }
}
