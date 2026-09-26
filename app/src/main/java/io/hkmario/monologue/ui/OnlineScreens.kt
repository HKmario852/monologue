package io.hkmario.monologue.ui

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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.hkmario.monologue.BuildConfig
import io.hkmario.monologue.domain.*
import coil.compose.AsyncImage

@Composable fun OnlineScreen(state: OnlineUiState,plugins: PluginUiState,settings: AppSettingsUiState,onEvent: (UiEvent)->Unit,openPlugins: ()->Unit) {
    var playlist by rememberSaveable {mutableStateOf("")}
    fun act(a: OnlineAction)=onEvent(UiEvent.Online(a))
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Choice("搜尋來源",state.provider,plugins.plugins.filter {settings.bool("plugin.${it.id}.enabled",true)}.map {it.id to it.name}) {act(OnlineAction.Provider(it))}
            OutlinedTextField(state.query,{act(OnlineAction.Query(it))},Modifier.fillMaxWidth(),label={Text("搜尋 ${providerLabel(state.provider)}・歌曲、歌手或專輯")},singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={act(OnlineAction.Search)}),trailingIcon={ActionIcon(Icons.Outlined.Search,"搜尋線上音樂",state.query.isNotBlank()) {act(OnlineAction.Search)}})
            Info("按搜尋才會把查詢文字傳送至所選服務；不會上傳本機媒體庫。")
            TextButton(onClick=openPlugins) {Text("管理音源與音質")}
        }
        if(state.provider=="spotify") item {
            Info(if(state.spotifyConnected) "Spotify · ${state.spotifyUser ?: "已連接"} · 歌曲資料來源" else "請先在外掛設定連接 Spotify")
            OutlinedTextField(playlist,{playlist=it},Modifier.fillMaxWidth(),label={Text("Spotify 播放清單連結")},singleLine=true)
            TextButton(onClick={act(OnlineAction.SpotifyPlaylist(playlist))},enabled=state.spotifyConnected && playlist.isNotBlank()) {Text("讀取播放清單")}
        }
        if(state.phase==Phase.Loading || state.resolvingId!=null) item {LinearProgressIndicator(Modifier.fillMaxWidth());Text(if(state.resolvingId!=null) "正在確認可用音訊…" else "正在搜尋…")}
        state.error?.let {item {Text(it,color=MaterialTheme.colorScheme.error)}}
        if(state.phase==Phase.Empty && state.searched) item {EmptyPanel("沒有搜尋結果","試試其他歌名或音源。")}
        items(state.results,key={it.provider+":"+it.id}) {song->
            Column {
                val quality=state.quality[song.provider+":"+song.id]
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
                        if(quality==null) TextButton(onClick={act(OnlineAction.Inspect(song))},modifier=Modifier.heightIn(min=48.dp),enabled=state.resolvingId==null) {Text("檢查音質")}
                        TextButton(onClick={act(OnlineAction.Download(song))},modifier=Modifier.heightIn(min=48.dp),enabled=state.resolvingId==null) {Text("離線下載")}
                        FilledTonalButton(onClick={act(OnlineAction.Play(song))},modifier=Modifier.heightIn(min=48.dp),enabled=state.resolvingId==null) {Text("播放")}
                    } else TextButton(onClick={act(OnlineAction.Match(song))},modifier=Modifier.heightIn(min=48.dp)) {Text("尋找可播音源")}
                }
            }
        }
        if(state.results.isNotEmpty() && state.results.none {it.audio}) item {Info("這些結果提供歌曲資料；尋找音源後由你確認演出者及版本，不會把不同錄音自動當成同一首。")}
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
    state.plugins.plugins.filter {category=="all" || it.category==category}.forEach {p->
        SectionTitle(p.name)
        Info("${if(p.builtIn) "內建接入模組" else "已安裝 HTTP 外掛"} · ${p.version} · ${if(p.category=="audio") "音訊來源" else "歌曲資料"}")
        Toggle("啟用 ${p.name}","停用後不再向此來源搜尋或解析音訊",s.bool("plugin.${p.id}.enabled",true)) {onEvent(UiEvent.Setting("plugin.${p.id}.enabled",it.toString()))}
        when(p.id) {
            "spotify" -> {
                Info(if(state.online.spotifyConnected) "已連接 · ${state.online.spotifyUser}" else if(BuildConfig.SPOTIFY_CLIENT_ID.isBlank() || BuildConfig.SPOTIFY_REDIRECT_URI.contains(".invalid/")) "此測試版本尚未完成開發方 Spotify 配置" else "尚未連接 Spotify")
                if(state.online.spotifyConnected) TextButton(onClick={act(OnlineAction.SpotifyDisconnect)}) {Text("斷開 Spotify")}
                else Button(onClick={act(OnlineAction.SpotifyConnect)},enabled=BuildConfig.SPOTIFY_CLIENT_ID.isNotBlank() && !BuildConfig.SPOTIFY_REDIRECT_URI.contains(".invalid/")) {Text("登入 Spotify")}
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
    Info("支援 monologue HTTP provider v1 描述檔。可接入提供無損音訊的服務；Spotube 的 .smplug 不能直接安裝。")
    OutlinedTextField(manifest,{manifest=it},Modifier.fillMaxWidth(),label={Text("HTTPS 外掛描述網址")},singleLine=true)
    Button(onClick={act(OnlineAction.InspectPlugin(manifest))},enabled=manifest.startsWith("https://") && state.plugins.phase!=Phase.Loading) {Text("檢查外掛")}
    if(state.plugins.phase==Phase.Loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.plugins.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    state.plugins.pending?.let {p->AlertDialog(onDismissRequest={act(OnlineAction.DismissPlugin)},title={Text("安裝／更新 ${p.name}？")},text={Text("版本 ${p.version}\n搜尋資料會傳送至 ${p.endpoint}\n音訊網域：${p.hosts.joinToString()}\n此外掛是宣告式接入設定，不執行下載程式碼。")},confirmButton={TextButton(onClick={act(OnlineAction.InstallPlugin)}) {Text("確認安裝")}},dismissButton={TextButton(onClick={act(OnlineAction.DismissPlugin)}) {Text("取消")}})}
}

@Composable fun UpdatesSettings(state: UpdateUiState,settings: AppSettingsUiState,onEvent: (UiEvent)->Unit) {
    fun act(a: OnlineAction)=onEvent(UiEvent.Online(a))
    Text("monologue ${BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.headlineSmall)
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
