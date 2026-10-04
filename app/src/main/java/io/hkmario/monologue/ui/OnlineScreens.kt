@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.hkmario.monologue.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.hkmario.monologue.BuildConfig
import io.hkmario.monologue.domain.*
import coil.compose.AsyncImage

/** Whether this build can sign in to Spotify; when it cannot, Spotify is hidden rather than offered as an option that fails. */
val spotifyAvailable get()=BuildConfig.SPOTIFY_CLIENT_ID.isNotBlank() && !BuildConfig.SPOTIFY_REDIRECT_URI.contains(".invalid/")

/** One search box for everything: 媒體庫 and Google Drive answer while typing; an online source is asked only when the user presses search. */
@Composable fun SearchScreen(library: LocalLibraryUiState,online: OnlineUiState,plugins: PluginUiState,settings: AppSettingsUiState,onEvent: (UiEvent)->Unit,onMore: (Track)->Unit={},playingId: String?=null,openSources: ()->Unit={},openCategory: (String)->Unit={}) {
    var playlist by rememberSaveable {mutableStateOf("")}
    var allLocal by rememberSaveable(online.query) {mutableStateOf(false)}
    var allCloud by rememberSaveable(online.query) {mutableStateOf(false)}
    fun act(a: OnlineAction)=onEvent(UiEvent.Online(a))
    val q=normalize(online.query)
    val matches=remember(q,library.tracks) { if(q.isEmpty()) emptyList() else library.tracks.filter {normalize(it.title).contains(q) || normalize(it.artist).contains(q) || normalize(it.album).contains(q)} }
    val local=matches.filter {it.source!=Source.Drive}; val cloud=matches.filter {it.source==Source.Drive}
    val sources=plugins.plugins.filter {settings.bool("plugin.${it.id}.enabled",true) && (it.id!="spotify" || online.spotifyConnected)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=24.dp,end=24.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item {
            // A plain, solid search box at the top, as in the reference: no outline, square-ish corners.
            val field=MaterialTheme.colorScheme.surfaceContainerLowest
            TextField(online.query,{act(OnlineAction.Query(it))},Modifier.fillMaxWidth().padding(top=8.dp),placeholder={Text("你想聽什麼？")},leadingIcon={Icon(Icons.Outlined.Search,null)},
                trailingIcon={if(online.query.isNotEmpty()) ActionIcon(Icons.Outlined.Close,"清除搜尋") {act(OnlineAction.Query(""))}},singleLine=true,shape=RoundedCornerShape(8.dp),
                colors=TextFieldDefaults.colors(focusedContainerColor=field,unfocusedContainerColor=field,focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent),
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={if(online.query.isNotBlank()) act(OnlineAction.Search)}))
        }
        if(q.isEmpty()) {
            item { LaunchedEffect(Unit) { act(OnlineAction.BrowseCovers) } }
            // Two tiles a row: a category from YouTube Music, its top song's cover tilted in the corner.
            items(browseCategories.chunked(2),key={row -> row.first().id}) { row ->
                Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    row.forEach { c -> BrowseTile(c,online.browse.songs[c.id]?.firstOrNull()?.artwork,Modifier.weight(1f)) { openCategory(c.id) } }
                    if(row.size==1) Spacer(Modifier.weight(1f))
                }
            }
            item { Text("分類歌曲由 YouTube Music 提供；載入時只傳送分類名稱，結果保留一日。輸入文字時，媒體庫與雲端即時顯示，線上則按搜尋才會傳送。",Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            item {SectionTitle("媒體庫 · ${local.size} 首")}
            if(local.isEmpty()) item {Text("媒體庫沒有符合的歌曲",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            items(if(allLocal) local else local.take(5),key={"local:"+it.id}) {t -> LibraryTrackRow(t,t.id==playingId,{onEvent(UiEvent.Play(t))},{onMore(t)})}
            if(local.size>5 && !allLocal) item {TextButton(onClick={allLocal=true}) {Text("顯示全部 ${local.size} 首")}}
            if(cloud.isNotEmpty()) {
                item {SectionTitle("Google Drive · ${cloud.size} 首")}
                items(if(allCloud) cloud else cloud.take(5),key={"cloud:"+it.id}) {t -> LibraryTrackRow(t,t.id==playingId,{onEvent(UiEvent.Play(t))},{onMore(t)})}
                if(cloud.size>5 && !allCloud) item {TextButton(onClick={allCloud=true}) {Text("顯示全部 ${cloud.size} 首")}}
            }
            item {SectionTitle("線上","管理音源",openSources)}
            // The source is picked right above its results, not in a settings dialog; switching re-runs a search already made.
            if(sources.size>1) item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    sources.forEachIndexed {i,p -> SegmentedButton(selected=p.id==online.provider,onClick={act(OnlineAction.Provider(p.id));if(online.searched) act(OnlineAction.Search)},shape=SegmentedButtonDefaults.itemShape(i,sources.size)) {Text(p.name,maxLines=1)}}
                }
            }
            if(online.provider=="spotify" && online.spotifyConnected) item {
                Info("Spotify · ${online.spotifyUser ?: "已連接"} · 歌曲資料來源")
                OutlinedTextField(playlist,{playlist=it},Modifier.fillMaxWidth(),label={Text("Spotify 播放清單連結")},singleLine=true)
                TextButton(onClick={act(OnlineAction.SpotifyPlaylist(playlist))},enabled=playlist.isNotBlank()) {Text("讀取播放清單")}
            }
            if(!online.searched && online.phase!=Phase.Loading && online.resolvingId==null) item {
                FilledTonalButton(onClick={act(OnlineAction.Search)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),enabled=sources.isNotEmpty()) {Icon(Icons.Outlined.TravelExplore,null);Spacer(Modifier.width(8.dp));Text("在 ${providerLabel(online.provider)} 搜尋「${online.query}」",maxLines=1)}
                Text("按搜尋才會把文字傳送至所選服務；不會上傳你的媒體庫。",Modifier.padding(top=4.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(online.phase==Phase.Loading || online.resolvingId!=null) item {LinearProgressIndicator(Modifier.fillMaxWidth());Text(if(online.resolvingId!=null) "正在確認可用音訊…" else "正在搜尋…")}
            online.error?.let {item {Text(it,color=MaterialTheme.colorScheme.error)}}
            if(online.phase==Phase.Empty && online.searched) item {EmptyPanel("線上沒有搜尋結果","試試其他歌名，或換一個音源。")}
        items(online.results,key={it.provider+":"+it.id}) {song->
            Column {
                val quality=online.quality[song.provider+":"+song.id]
                ListItem(headlineContent={Text(song.title)},supportingContent={
                    Column {
                        Text("${if(song.provider=="youtube") "YouTube 頻道" else "歌手"}：${song.artist.ifBlank {"未知"}}${if(song.album.isNotBlank()) " · ${song.album}" else ""}")
                        Text("${if(song.durationMs>0) formatTime(song.durationMs) else "長度未知"} · 平台：${providerLabel(song.provider)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if(!song.audio) "只有歌曲資料，沒有音訊；可尋找可播音源" else if(quality!=null) "音訊：$quality（來源回報）" else "音質尚未確認",style=MaterialTheme.typography.labelMedium,color=if(song.audio && quality!=null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
                    }
                },leadingContent={
                    if(song.artwork!=null) AsyncImage(song.artwork,null,Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)))
                    else Icon(Icons.Outlined.MusicNote,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.primary)
                },colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surface))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    if(song.audio) {
                        if(quality==null) TextButton(onClick={act(OnlineAction.Inspect(song))},modifier=Modifier.heightIn(min=48.dp),enabled=online.resolvingId==null) {Text("檢查音質")}
                        TextButton(onClick={act(OnlineAction.Download(song))},modifier=Modifier.heightIn(min=48.dp),enabled=online.resolvingId==null) {Text("離線下載")}
                        FilledTonalButton(onClick={act(OnlineAction.Play(song))},modifier=Modifier.heightIn(min=48.dp),enabled=online.resolvingId==null) {Text("播放")}
                    } else TextButton(onClick={act(OnlineAction.Match(song))},modifier=Modifier.heightIn(min=48.dp)) {Text("尋找可播音源")}
                }
            }
        }
            if(online.results.isNotEmpty() && online.results.none {it.audio}) item {Info("這些結果提供歌曲資料；尋找音源後由你確認演出者及版本，不會把不同錄音自動當成同一首。")}
        }
    }
}

/** A coloured category tile: bold title top-left, the cover tilted into the bottom-right corner and clipped by the tile. */
@Composable private fun BrowseTile(category: BrowseCategory,cover: String?,modifier: Modifier,open: ()->Unit) {
    Box(modifier.height(104.dp).clip(RoundedCornerShape(8.dp)).background(Color(category.color)).clickable(onClickLabel="開啟${category.title}",onClick=open)) {
        Text(category.title,Modifier.padding(12.dp).fillMaxWidth(0.68f),color=Color.White,style=MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.Bold),maxLines=2,overflow=TextOverflow.Ellipsis)
        if(cover!=null) AsyncImage(cover,null,Modifier.align(Alignment.BottomEnd).offset(x=14.dp,y=10.dp).size(72.dp).graphicsLayer { rotationZ=25f }.shadow(6.dp,RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp)),contentScale=ContentScale.Crop)
    }
}

/** A 搜尋 tile opened: the category's songs from YouTube Music. Tap a song to play it; the arrow saves it offline. */
@Composable fun BrowseCategoryScreen(id: String,online: OnlineUiState,onEvent: (UiEvent)->Unit,playingId: String?=null) {
    val category=browseCategories.find { it.id==id } ?: return
    fun act(a: OnlineAction)=onEvent(UiEvent.Online(a))
    LaunchedEffect(id) { act(OnlineAction.Browse(id)) }
    val songs=online.browse.songs[id].orEmpty()
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=24.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(132.dp).background(Color(category.color))) {
                Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                    Text(category.title,color=Color.White,style=MaterialTheme.typography.headlineLarge.copy(fontWeight=FontWeight.Bold))
                    Text(if(songs.isEmpty()) "YouTube Music" else "${songs.size} 首 · YouTube Music",color=Color.White.copy(alpha=0.8f),style=MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if(id in online.browse.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        online.browse.errors[id]?.let { message -> item {
            Column(Modifier.padding(24.dp)) {
                Text("未能載入：$message",color=MaterialTheme.colorScheme.error)
                TextButton(onClick={act(OnlineAction.Browse(id))}) { Text("再試一次") }
            }
        } }
        online.error?.let { message -> item { Text(message,Modifier.padding(horizontal=24.dp,vertical=8.dp),color=MaterialTheme.colorScheme.error) } }
        items(songs,key={it.id}) { song ->
            val resolving=online.resolvingId==song.id
            Row(Modifier.fillMaxWidth().clickable(enabled=online.resolvingId==null,onClickLabel="播放") { act(OnlineAction.Play(song)) }.padding(horizontal=24.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                if(song.artwork!=null) AsyncImage(song.artwork,null,Modifier.size(52.dp).clip(RoundedCornerShape(4.dp)),contentScale=ContentScale.Crop)
                else Icon(Icons.Outlined.MusicNote,null,Modifier.size(52.dp),tint=MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal=16.dp)) {
                    Text(song.title,style=MaterialTheme.typography.bodyLarge,maxLines=1,overflow=TextOverflow.Ellipsis,color=if(song.id==playingId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text(listOf(song.artist,if(song.durationMs>0) formatTime(song.durationMs) else "").filter { it.isNotBlank() }.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                // Play and download side by side; the progress takes the play button's place while the audio is found.
                if(resolving) Box(Modifier.size(48.dp),contentAlignment=Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp) }
                else ActionIcon(Icons.Outlined.PlayArrow,"播放 ${song.title}",online.resolvingId==null) { act(OnlineAction.Play(song)) }
                ActionIcon(Icons.Outlined.Download,"離線下載 ${song.title}",online.resolvingId==null) { act(OnlineAction.Download(song)) }
            }
        }
    }
}

@Composable fun PluginsSettings(state: AppUiState,onEvent: (UiEvent)->Unit,openAccount: ()->Unit) {
    var category by rememberSaveable {mutableStateOf("all")};var manifest by rememberSaveable {mutableStateOf("")}
    fun act(a: OnlineAction)=onEvent(UiEvent.Online(a))
    val s=state.settings
    Choice("外掛分類",category,listOf("all" to "全部","metadata" to "歌曲資料","audio" to "音訊來源")) {category=it}
    Choice("音質偏好",s.text("audioQuality","best"),listOf("best" to "最高可用音質","balanced" to "節省流量（優先 ≤192 kbps）")) {onEvent(UiEvent.Setting("audioQuality",it))}
    Choice("配對歌曲的預設音源",s.text("audioProvider","youtube"),state.plugins.plugins.filter {it.category=="audio" && s.bool("plugin.${it.id}.enabled",true)}.map {it.id to it.name}) {onEvent(UiEvent.Setting("audioProvider",it))}
    Info("音質設定用於下一次解析音源；已下載檔案保持原格式。數字以來源及解碼器回報為準，不把轉碼或升頻標為無損。")
    // A source this build cannot use (Spotify without the developer's app registration) is not shown at all.
    state.plugins.plugins.filter {(category=="all" || it.category==category) && (it.id!="spotify" || spotifyAvailable || state.online.spotifyConnected)}.forEach {p->
        SectionTitle(p.name)
        Info("${if(p.builtIn) "內建接入模組" else "已安裝 HTTP 外掛"} · ${p.version} · ${if(p.category=="audio") "音訊來源" else "歌曲資料"}")
        Toggle("啟用 ${p.name}","停用後不再向此來源搜尋或解析音訊",s.bool("plugin.${p.id}.enabled",true)) {onEvent(UiEvent.Setting("plugin.${p.id}.enabled",it.toString()))}
        when(p.id) {
            "spotify" -> {
                Info(if(state.online.spotifyConnected) "已連接 · ${state.online.spotifyUser}" else "尚未連接 Spotify")
                if(state.online.spotifyConnected) TextButton(onClick={act(OnlineAction.SpotifyDisconnect)}) {Text("斷開 Spotify")}
                else Button(onClick={act(OnlineAction.SpotifyConnect)},enabled=spotifyAvailable) {Text("登入 Spotify")}
            }
            "musicbrainz" -> SettingAction("ListenBrainz 帳號與同步",state.listenBrainz.username ?: "未連接",openAccount)
            "youtube" -> Info("NewPipe Extractor · 直接確認目前可用的音訊串流。來源變更、地區或登入限制可能令解析失敗。YouTube 音訊不標為無損。")
            else -> {
                Info("服務：${p.endpoint}\n允許音訊網域：${p.hosts.joinToString()}")
                if(p.manifestUrl.isNotBlank()) TextButton(onClick={act(OnlineAction.InspectPlugin(p.manifestUrl))}) {Text("檢查外掛更新")}
                TextButton(onClick={act(OnlineAction.RemovePlugin(p.id))}) {Text("移除此來源")}
            }
        }
    }
    SectionTitle("新增音訊來源")
    Info("貼上音源外掛的 HTTPS 網址即可加入，例如提供無損音訊的服務（格式：monologue HTTP provider v1）。Spotube 的 .smplug 外掛不能直接安裝。")
    OutlinedTextField(manifest,{manifest=it},Modifier.fillMaxWidth(),label={Text("HTTPS 外掛描述網址")},singleLine=true)
    Button(onClick={act(OnlineAction.InspectPlugin(manifest))},enabled=manifest.startsWith("https://") && state.plugins.phase!=Phase.Loading) {Text("檢查外掛")}
    if(state.plugins.phase==Phase.Loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.plugins.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    state.plugins.pending?.let {p->AlertDialog(onDismissRequest={act(OnlineAction.DismissPlugin)},title={Text("安裝／更新 ${p.name}？")},text={Text("版本 ${p.version}\n搜尋資料會傳送至 ${p.endpoint}\n音訊網域：${p.hosts.joinToString()}\n此外掛是宣告式接入設定，不執行下載程式碼。")},confirmButton={TextButton(onClick={act(OnlineAction.InstallPlugin)}) {Text("確認安裝")}},dismissButton={TextButton(onClick={act(OnlineAction.DismissPlugin)}) {Text("取消")}})}
}

@Composable fun UpdatesSettings(state: UpdateUiState,settings: AppSettingsUiState,onEvent: (UiEvent)->Unit) {
    fun act(a: OnlineAction)=onEvent(UiEvent.Online(a))
    Text("Monologue ${BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.headlineSmall)
    EditSetting("GitHub 發布專案",settings.text("updateRepository").ifBlank {BuildConfig.UPDATE_REPOSITORY},"預設為官方專案 ${BuildConfig.UPDATE_REPOSITORY}；留空即使用預設。") {onEvent(UiEvent.Setting("updateRepository",it.trim()))}
    Info("正式更新需要較高版本號、相同套件及相同簽署。APK 下載後仍由你在 Android 確認安裝。")
    Button(onClick={act(OnlineAction.CheckUpdate)},enabled=state.phase!=Phase.Loading && !state.downloading) {Text("檢查更新")}
    if(state.phase==Phase.Loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    state.version?.let {SectionTitle("版本 $it")}
    if(state.notes.isNotBlank()) Info(state.notes)
    if(state.downloading) {LinearProgressIndicator(progress={if(state.size>0) state.downloaded.toFloat()/state.size else 0f},modifier=Modifier.fillMaxWidth());Info("${formatBytes(state.downloaded)} / ${formatBytes(state.size)}")}
    if(state.apkPath!=null) Button(onClick={act(OnlineAction.InstallUpdate)}) {Text("安裝已驗證更新")}
    else if(state.assetUrl!=null) Button(onClick={act(OnlineAction.DownloadUpdate)},enabled=!state.downloading) {Text("下載更新 · ${formatBytes(state.size)}")}
}
