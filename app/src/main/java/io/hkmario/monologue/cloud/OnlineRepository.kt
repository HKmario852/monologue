package io.hkmario.monologue.cloud

import android.net.Uri
import io.hkmario.monologue.BuildConfig
import io.hkmario.monologue.data.SettingsRepository
import io.hkmario.monologue.domain.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.*
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.stream.*
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Native adapters plus declarative HTTP providers. No downloaded executable code. */
class OnlineRepository(private val settings: SettingsRepository,val spotify: SpotifyClient) {
    val http=OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(35,TimeUnit.SECONDS).build()
    private val mbLock=Any();private var mbLast=0L
    private val resolved=java.util.concurrent.ConcurrentHashMap<String,Pair<Long,AudioChoice>>()
    val builtIns=persistentListOf(
        SourcePlugin("youtube","YouTube Audio","0.26.5","audio",builtIn=true),
        SourcePlugin("musicbrainz","MusicBrainz + ListenBrainz","1.0","metadata",builtIn=true))
    // Spotify is not offered: development-mode apps need the owner to hold Premium, so MusicBrainz is the song-data source.
    init {
        NewPipe.init(object: Downloader() {
            override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): org.schabi.newpipe.extractor.downloader.Response {
                val b=Request.Builder().url(request.url()).header("User-Agent","Mozilla/5.0")
                request.headers().forEach {(k,values)->values.forEach {b.addHeader(k,it)}}
                b.method(request.httpMethod(),request.dataToSend()?.toRequestBody(null))
                return http.newCall(b.build()).execute().use {r->org.schabi.newpipe.extractor.downloader.Response(r.code,r.message,r.headers.toMultimap(),r.body?.string(),r.request.url.toString())}
            }
        })
    }
    fun json(url: String): JSONObject {
        val request=Request.Builder().url(url).header("User-Agent","monologue/${BuildConfig.VERSION_NAME} (Android music library)").build()
        return http.newCall(request).execute().use {r->if(!r.isSuccessful) throw IOException("服務回應 HTTP ${r.code}");val b=r.body ?: error("服務沒有回傳內容");require(b.contentLength()<=5_000_000) {"回應過大"};val bytes=b.byteStream().readUpTo(5_000_001);require(bytes.size<=5_000_000);JSONObject(String(bytes,Charsets.UTF_8))}
    }
    suspend fun plugins(): PersistentList<SourcePlugin> {
        val raw=settings.snapshot().text("plugins","[]")
        return builtIns.addAll((0 until JSONArray(raw).length()).map {parsePlugin(JSONArray(raw).getJSONObject(it))})
    }
    private fun parsePlugin(j: JSONObject): SourcePlugin {
        require(j.getInt("schema")==1) {"不支援此外掛格式；需要 monologue HTTP provider v1"}
        val id=j.getString("id");require(id.matches(Regex("[a-z][a-z0-9.-]{2,63}")) && builtIns.none {it.id==id}) {"外掛 ID 無效或與內建來源衝突"}
        val endpoint=j.getString("endpoint").toHttpUrl();require(endpoint.isHttps && endpoint.username.isEmpty() && endpoint.password.isEmpty())
        val hosts=j.getJSONArray("audioHosts");val domains=(0 until hosts.length()).map {hosts.getString(it).lowercase()};require(domains.all {it.matches(Regex("[a-z0-9.-]+")) && !it.contains("..")})
        return SourcePlugin(id,j.getString("name").take(100),j.getString("version").take(30),"audio",endpoint.toString().trimEnd('/'),j.optString("manifestUrl"),domains.toPersistentList())
    }
    suspend fun inspectPlugin(url: String): SourcePlugin=withContext(Dispatchers.IO) {
        val u=url.toHttpUrl();require(u.isHttps && u.username.isEmpty() && u.password.isEmpty()) {"請使用 HTTPS 外掛描述網址"}
        parsePlugin(json(u.toString()).put("manifestUrl",u.toString()))
    }
    suspend fun installPlugin(plugin: SourcePlugin) {
        val all=plugins().filter {!it.builtIn && it.id!=plugin.id}+plugin
        val array=JSONArray();all.forEach {p->array.put(JSONObject().put("schema",1).put("id",p.id).put("name",p.name).put("version",p.version).put("endpoint",p.endpoint).put("audioHosts",JSONArray(p.hosts)).put("manifestUrl",p.manifestUrl))}
        settings.set("plugins",array.toString())
    }
    suspend fun removePlugin(id: String) {
        val a=JSONArray(settings.snapshot().text("plugins","[]"));val out=JSONArray();for(i in 0 until a.length()) if(a.getJSONObject(i).getString("id")!=id) out.put(a.getJSONObject(i))
        settings.set("plugins",out.toString());settings.set("plugin.$id.enabled","false")
        if(settings.snapshot().text("audioProvider","youtube")==id) settings.set("audioProvider","youtube")
    }
    suspend fun search(provider: String,query: String): PersistentList<OnlineSong> = withContext(Dispatchers.IO) {
        require(query.isNotBlank())
        require(settings.snapshot().bool("plugin.$provider.enabled",true)) {"此來源已停用，請到外掛設定啟用"}
        when(provider) {
            "youtube" -> {
                val extractor=ServiceList.YouTube.getSearchExtractor(query,listOf("videos"),"");extractor.fetchPage()
                extractor.initialPage.items.filterIsInstance<StreamInfoItem>().take(30).map {s->OnlineSong("youtube:"+Uri.parse(s.url).getQueryParameter("v"),s.name,s.uploaderName ?: "YouTube",durationMs=s.duration.coerceAtLeast(0)*1000,artwork=s.thumbnails.firstOrNull()?.url,provider="youtube",url=s.url,audio=true)}.toPersistentList()
            }
            "spotify" -> spotify.search(query)
            "musicbrainz" -> {
                val url="https://musicbrainz.org/ws/2/recording/".toHttpUrl().newBuilder().addQueryParameter("query",query).addQueryParameter("fmt","json").addQueryParameter("limit","25").build()
                val data=synchronized(mbLock) {val gap=1100-(android.os.SystemClock.elapsedRealtime()-mbLast);if(gap>0) Thread.sleep(gap);try {json(url.toString())} finally {mbLast=android.os.SystemClock.elapsedRealtime()}}
                val items=data.optJSONArray("recordings") ?: JSONArray()
                (0 until items.length()).map {i->val r=items.getJSONObject(i);val artists=r.optJSONArray("artist-credit") ?: JSONArray();OnlineSong(r.getString("id"),r.getString("title"),(0 until artists.length()).joinToString("") {val a=artists.getJSONObject(it);a.optString("name")+a.optString("joinphrase")},r.optJSONArray("releases")?.optJSONObject(0)?.optString("title") ?: "",r.optLong("length"),provider="musicbrainz",url="https://musicbrainz.org/recording/${r.getString("id")}")}.toPersistentList()
            }
            else -> {
                val plugin=plugins().first {it.id==provider}
                val data=json((plugin.endpoint+"/search").toHttpUrl().newBuilder().addQueryParameter("q",query).build().toString()).getJSONArray("tracks")
                (0 until minOf(data.length(),100)).map {i->val t=data.getJSONObject(i);OnlineSong(t.getString("id"),t.getString("title"),t.optString("artist"),t.optString("album"),t.optLong("durationMs"),t.optString("artwork").takeIf {it.startsWith("https://")},plugin.id,audio=true)}.toPersistentList()
            }
        }
    }
    suspend fun resolve(song: OnlineSong): AudioChoice = resolve(song.provider,song.id.removePrefix("youtube:"),song.url)
    suspend fun resolve(provider: String,id: String,url: String="",quality: String?=null): AudioChoice=withContext(Dispatchers.IO) {
        val prefs=settings.snapshot();require(prefs.bool("plugin.$provider.enabled",true)) {"音訊來源已停用"}
        val selectedQuality=quality ?: prefs.text("audioQuality","best")
        val cacheKey="$provider:$id:$selectedQuality"
        resolved[cacheKey]?.takeIf {android.os.SystemClock.elapsedRealtime()-it.first<120_000}?.let {return@withContext it.second}
        val choices=if(provider=="youtube") {
            val info=StreamInfo.getInfo(ServiceList.YouTube,url.ifBlank {"https://www.youtube.com/watch?v=$id"})
            info.audioStreams.filter {it.isUrl && it.deliveryMethod==DeliveryMethod.PROGRESSIVE_HTTP}.map {s->AudioChoice(s.content,s.format?.mimeType ?: "audio/webm",maxOf(s.bitrate,s.averageBitrate*1000))}
        } else {
            val plugin=plugins().first {it.id==provider}
            val data=json((plugin.endpoint+"/resolve").toHttpUrl().newBuilder().addQueryParameter("id",id).build().toString()).getJSONArray("streams")
            (0 until data.length()).map {i->val s=data.getJSONObject(i);val u=s.getString("url").toHttpUrl();require(u.isHttps && u.host in plugin.hosts) {"音訊網域不在外掛已批准範圍"};AudioChoice(u.toString(),s.getString("mime"),s.optInt("bitrate"),s.optInt("sampleRate"),s.optInt("bitDepth"),s.optBoolean("lossless"))}
        }
        val eligible=if(selectedQuality=="balanced") choices.filter {it.bitrate in 1..192000}.ifEmpty {choices} else choices
        val best=eligible.maxWithOrNull(compareBy<AudioChoice> {it.lossless}.thenBy {if(it.lossless) it.bitDepth else 0}.thenBy {it.bitrate}.thenBy {it.sampleRate}) ?: throw IOException("來源未提供可播放音訊；可能需要登入、地區支援或來源更新")
        require(best.url.startsWith("https://")) {"拒絕不安全音訊連線"}
        resolved[cacheKey]=android.os.SystemClock.elapsedRealtime() to best;best
    }
    fun track(song: OnlineSong): Track {
        require(song.audio)
        val id=song.id.removePrefix("youtube:")
        return Track("online:${song.provider}:$id",song.title,song.artist,song.album,folder=song.provider,uri="https://audio.monologue.invalid/${Uri.encode(song.provider)}/${Uri.encode(id)}",durationMs=song.durationMs,artwork=song.artwork,source=Source.Online,remoteVersion="1")
    }
    fun resolveUri(uri: Uri): Uri {
        if(uri.host!="audio.monologue.invalid") return uri
        val p=uri.pathSegments;require(p.size==2)
        return Uri.parse(runBlocking {resolve(p[0],p[1],quality=uri.getQueryParameter("quality"))}.url)
    }
}
fun java.io.InputStream.readUpTo(limit: Int): ByteArray {
    val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
    while(output.size()<limit) {val n=read(buffer,0,minOf(buffer.size,limit-output.size()));if(n<0) break;output.write(buffer,0,n)}
    return output.toByteArray()
}
