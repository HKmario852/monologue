@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.hkmario.monologue.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import io.hkmario.monologue.BuildConfig
import io.hkmario.monologue.domain.*

val settingsGroups=listOf("外觀與導航","播放","媒體庫","Google Drive 與下載","儲存空間","歌詞","聆聽統計","ListenBrainz","通知與背景行為","關於、隱私及維護","音源外掛與音質","App 更新")
/** What each group contains, so settings search finds items by name rather than only group titles. */
val settingsKeywords=listOf(
    "主題 深色 暖白 動態 導航 底部 側邊 bottom drawer banner 黑膠 旋轉 動態效果 語言",
    "恢復 隊列 自動播放 中斷 耳機 速度 等化器 睡眠 淡出 下載優先 行動網絡 串流",
    "權限 資料夾 掃描 索引 隱藏 長度 tab 排序 搜尋歷史",
    "google drive 雲端 連接 授權 斷開 根資料夾 下載位置 wi-fi 增量 暫停 下載中心",
    "快取 暫存 cache 儲存 空間 離線 永久下載 歌詞快取 封面",
    "歌詞 字體 翻譯 偏移 lrc lrclib",
    "統計 時區 排行榜 回顧 匯出 csv 清除",
    "listenbrainz token 同步 推薦 帳號",
    "通知 背景 電池",
    "版本 私隱 授權 開源 診斷 匯出 匯入 還原",
    "音源 外掛 插件 音質 youtube spotify musicbrainz 無損 lossless plugin",
    "更新 github release apk 版本")
/** Common statistics time zones; the device zone and any previously saved zone are added when shown. */
val timeZones=listOf("Asia/Hong_Kong" to "香港","Asia/Taipei" to "台北","Asia/Macau" to "澳門","Asia/Shanghai" to "北京／上海","Asia/Singapore" to "新加坡","Asia/Tokyo" to "東京","Asia/Seoul" to "首爾","Australia/Sydney" to "悉尼","Europe/London" to "倫敦","Europe/Paris" to "巴黎","America/New_York" to "紐約","America/Los_Angeles" to "洛杉磯","America/Vancouver" to "溫哥華","America/Toronto" to "多倫多","UTC" to "UTC")
@Composable fun SettingsHome(open: (Int)->Unit) {
    var query by rememberSaveable {mutableStateOf("")}
    val q=query.trim().lowercase()
    val matches=settingsGroups.indices.filter {q.isEmpty() || settingsGroups[it].lowercase().contains(q) || q.split(' ').filter {w->w.isNotBlank()}.all {w->settingsKeywords[it].lowercase().contains(w) || settingsGroups[it].lowercase().contains(w)}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp)) {
        item {Text("把 Monologue 調成你的節奏。",style=MaterialTheme.typography.bodyLarge,modifier=Modifier.padding(bottom=12.dp))}
        item {OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(bottom=12.dp),placeholder={Text("搜尋設定，例如：Token、快取、音質")},leadingIcon={Icon(Icons.Outlined.Search,null)},trailingIcon={if(query.isNotEmpty()) ActionIcon(Icons.Outlined.Close,"清除設定搜尋") {query=""}},singleLine=true,shape=androidx.compose.foundation.shape.RoundedCornerShape(28.dp))}
        items(matches,key={it}) { i -> ListItem(headlineContent={Text(settingsGroups[i])},leadingContent={Text("%02d".format(i+1),color=MaterialTheme.colorScheme.primary)},trailingContent={Icon(Icons.Outlined.ChevronRight,null)},modifier=Modifier.clickable {open(i)},colors=ListItemDefaults.colors(containerColor=Color.Transparent));HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)}
        if(matches.isEmpty()) item {Info("找不到「$query」相關設定。")}
    }
}
@Composable fun SettingsDetail(group: Int,state: AppUiState,onEvent: (UiEvent)->Unit,openEq: ()->Unit,openSleep: ()->Unit,openDownloads: ()->Unit,openDiscover: ()->Unit,openOffline: ()->Unit,openSystemSettings: ()->Unit,importLyrics: (Boolean)->Unit,openDrive: ()->Unit={}) {
    val s=state.settings
    var confirm by remember {mutableStateOf<Pair<String,UiEvent>?>(null)}
    fun setting(key: String,value: String) {onEvent(UiEvent.Setting(key,value))}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=12.dp)) {
        when(group) {
            10 -> PluginsSettings(state,onEvent,openDiscover)
            11 -> UpdatesSettings(state.updates,state.settings,onEvent)
            0 -> {
                Choice("主題",s.text("theme","paper"),listOf("paper" to "品牌暖白","dark" to "深色","system" to "跟隨系統")) {setting("theme",it)}
                Toggle("動態主題色","Android 12 或以上；保留黑膠結構與排版",s.bool("dynamic")) {setting("dynamic",it.toString())}
                Choice("導航樣式",s.text("navigation","bottom"),listOf("bottom" to "底部導覽列","drawer" to "側邊選單")) {setting("navigation",it)}
                Toggle("黑膠旋轉","24 秒一圈；暫停時定格",s.bool("vinyl",true)) {setting("vinyl",it.toString())}
                Toggle("減少動態效果","關閉旋轉；亦尊重系統動畫設定",s.bool("reduceMotion")) {setting("reduceMotion",it.toString())}
                Choice("顯示語言",s.text("language","zh-Hant"),listOf("zh-Hant" to "繁體中文","system" to "跟隨系統（未支援語言用繁體中文）")) {setting("language",it)}
                Info("文字大小會跟隨 Android 系統字體設定。")
            }
            1 -> {
                Toggle("恢復上次播放隊列","啟動時還原歌曲與位置",s.bool("restoreQueue",true)) {setting("restoreQueue",it.toString())}
                Toggle("啟動時自動播放","預設關閉",s.bool("autoplay")) {setting("autoplay",it.toString())}
                Toggle("中斷後恢復播放","仍須遵循 Android 音訊焦點規則",s.bool("resumeInterruption",true)) {setting("resumeInterruption",it.toString())}
                Toggle("耳機拔除時暫停","避免聲音突然由揚聲器播放",s.bool("noisyPause",true)) {setting("noisyPause",it.toString())}
                Choice("播放速度",s.text("speed","1.0"),listOf("0.5" to "0.5×","0.75" to "0.75×","1.0" to "1.0×","1.25" to "1.25×","1.5" to "1.5×","2.0" to "2.0×")) {setting("speed",it)}
                SettingAction("等化器","可調頻段視乎裝置支援",openEq)
                SettingAction("睡眠計時器",if(state.sleep.remainingMs>0) "剩餘 ${formatTime(state.sleep.remainingMs)}" else if(state.sleep.endOfTrack) "本曲結束後停止" else "關閉",openSleep)
                Toggle("睡眠結束前淡出","最後 5 秒降低音量",s.bool("sleepFade")) {setting("sleepFade",it.toString())}
                Toggle("完整下載優先","已驗證的離線檔案優先於串流",s.bool("offlineFirst",true)) {setting("offlineFirst",it.toString())}
                Toggle("允許行動網絡串流","可能產生流動數據用量",s.bool("mobileStreaming")) {setting("mobileStreaming",it.toString())}
            }
            2 -> {
                SettingAction("音樂權限","開啟系統權限設定",openSystemSettings)
                SettingAction("授權音樂資料夾","選取本機音樂資料夾") {onEvent(UiEvent.PickFolder)}
                state.authorizedFolders.forEach {uri -> SettingAction("取消資料夾授權",android.net.Uri.decode(uri.substringAfterLast('/'))) {confirm="取消此資料夾的讀取授權？檔案不會被刪除。" to UiEvent.RevokeFolder(uri)} }
                SettingAction("重新掃描","更新索引；不會移除音訊檔案") {onEvent(UiEvent.Scan)}
                Toggle("自動更新索引","媒體資料變動時重新掃描",s.bool("autoScan",true)) {setting("autoScan",it.toString())}
                EditSetting("隱藏資料夾",s.text("hiddenFolders"),"每行一個路徑或資料夾名稱；重新掃描後套用") {setting("hiddenFolders",it)}
                EditSetting("最短音訊長度（秒）",s.text("minDuration","0"),"0 表示不排除；重新掃描後套用",numeric=true) {if(it.toFloatOrNull()?.let { n -> n>=0 }==true) setting("minDuration",it)}
                Choice("預設 Tab",s.text("defaultTab","Tracks"),LibraryTab.entries.map {it.name to it.label}) {setting("defaultTab",it)}
                // Same options and names as the sort menus in 媒體庫.
                Choice("歌曲排序",s.text("sort","title"),songSorts.toList()) {setting("sort",it)}
                Choice("專輯排序",s.text("groupSort.Albums","name"),albumSorts.toList()) {setting("groupSort.Albums",it)}
                Toggle("記錄搜尋歷史","按分類保存最近 10 個已提交搜尋",s.bool("searchHistory",true)) {setting("searchHistory",it.toString())}
                SettingAction("清空目前 Tab 搜尋紀錄","") {onEvent(UiEvent.ClearSearchHistory)}
                SettingAction("清除本機索引","只移除索引，不刪除音訊檔案") {confirm="清除本機索引？音樂檔仍然保留，可重新掃描。" to UiEvent.ClearIndex}
            }
            3 -> {
                Info(state.drive.account ?: "Google Drive 尚未連接")
                SettingAction("連接／重新驗證","唯讀權限用於瀏覽你既有的 Drive 音樂") {onEvent(UiEvent.ConnectDrive)}
                SettingAction("斷開 Google Drive","停止雲端操作及未完成下載；保留永久下載") {confirm="斷開 Drive？目前下載會停止，永久下載會保留。" to UiEvent.DisconnectDrive}
                SettingAction("更換音樂資料夾",s.text("driveRootName").ifBlank {"尚未選擇"}) {setting("driveRootChosen","false");openDrive()}
                Choice("永久下載位置",s.text("downloadLocation","internal"),listOf("internal" to "App 內部私人空間","external" to "App 外置私人空間")) {setting("downloadLocation",it)}
                Info("位置變更對下一個下載工作生效；舊檔仍可播放。兩種位置均與串流快取分開，解除安裝會移除。")
                Toggle("只用 Wi-Fi 下載","對新排程工作生效",s.bool("wifiOnly",true)) {setting("wifiOnly",it.toString())}
                Toggle("每日檢查新歌曲","每 24 小時檢查雲端音樂資料夾有沒有新歌；需已連接",s.bool("autoIncremental")) {setting("autoIncremental",it.toString())}
                Toggle("歌曲間暫停","正在下載時會先完成本曲",state.downloads.pauseBetween) {onEvent(UiEvent.PauseBetween(it))}
                SettingAction("下載中心","成功 ${state.downloads.success}，失敗 ${state.downloads.failed}",openDownloads)
                Info("下載失敗會保留原因，可一鍵重試；已下載的歌曲不會重複下載。手機空間不足時會暫停新的下載。")
            }
            4 -> {
                SectionTitle("播放暫存")
                Text("${formatBytes(state.storage.cacheBytes)} / 1,000 MB",style=MaterialTheme.typography.headlineSmall)
                Info("串流播放時暫存的音訊。空間用滿時會自動清除最久沒播放的部分；正在播放的歌曲不受影響。")
                SettingAction("清除播放暫存",if(state.storage.deferredClear) "正在使用的部分會在播放完畢後清除" else "不影響離線下載") {onEvent(UiEvent.ClearStreamCache)}
                SectionTitle("離線下載")
                Text(formatBytes(state.storage.offlineBytes),style=MaterialTheme.typography.headlineSmall)
                SettingAction("管理離線下載","逐首選擇刪除",openOffline)
                Info("裝置剩餘空間：${formatBytes(state.storage.freeBytes)}")
                Info("歌詞快取文字用量：${formatBytes(state.storage.lyricsBytes)}")
                SettingAction("清除歌詞快取","包括你已匯入的原文及翻譯") {confirm="清除已匯入的歌詞及翻譯快取？原始 LRC 檔案不受影響。" to UiEvent.ClearLyricsCache}
                Info("封面快取：${formatBytes(state.storage.artBytes)}")
                SettingAction("清除封面快取","不影響音訊檔內嵌封面或永久下載") {onEvent(UiEvent.ClearArtworkCache)}
                SettingAction("重新整理用量","") {onEvent(UiEvent.RefreshStorage)}
            }
            5 -> {
                Choice("歌詞文字大小",s.text("lyricSize","22"),listOf("18" to "18 sp","22" to "22 sp","26" to "26 sp","30" to "30 sp")) {setting("lyricSize",it)}
                Toggle("顯示翻譯歌詞","只有時間戳可可靠配對才顯示",s.bool("translations")) {setting("translations",it.toString())}
                Choice("翻譯顯示方式",s.text("lyricsDisplay","both"),listOf("both" to "原文＋翻譯","translation" to "只顯示翻譯","romaji" to "原文＋羅馬拼音")) {setting("lyricsDisplay",it)}
                Choice("翻譯目標語言",s.text("translationLanguage","繁體中文"),listOf("繁體中文" to "繁體中文","English" to "English","日本語" to "日本語")) {setting("translationLanguage",it)}
                Toggle("沒有翻譯時在裝置上翻譯","使用 ML Kit 離線翻譯；第一次使用會下載約 30 MB 的語言模型，歌詞不會傳送到翻譯伺服器",s.bool("autoTranslate",true)) {setting("autoTranslate",it.toString())}
                Toggle("在其他顯示方式也顯示羅馬拼音","歌詞來源有提供時（例如網易雲音樂、うたてん的日文歌），在「原文」「原文＋翻譯」「翻譯」的每行上方顯示；「原文＋羅馬拼音」總是顯示",s.bool("showRomaji")) {setting("showRomaji",it.toString())}
                Toggle("自動捲動歌詞","手動捲動後可按返回目前歌詞",s.bool("autoLyrics",true)) {setting("autoLyrics",it.toString())}
                EditSetting("全域時間偏移（毫秒）",s.text("lyricOffset","0"),"正數延後高亮；負數提早",true) {if(it.toLongOrNull()!=null) setting("lyricOffset",it)}
                state.player.entry?.track?.let { t ->
                    EditSetting("本曲時間偏移（毫秒）",s.text(lyricOffsetKey(t.id),"0"),t.title,true) {if(it.toLongOrNull()!=null) setting(lyricOffsetKey(t.id),it)}
                    SettingAction("匯入本曲原文歌詞","LRC 或純文字；本機來源優先") {importLyrics(false)}
                    SettingAction("匯入本曲翻譯歌詞","以時間戳配對，不按列表順序") {importLyrics(true)}
                }
                Info("目前來源：${state.lyrics.source}")
                Toggle("線上歌詞搜尋（LRCLIB）","本機歌詞優先，然後快取；只查詢目前歌曲",s.bool("onlineLyrics")) { enabled -> if(enabled) confirm="啟用 LRCLIB 查詢？會傳送目前歌曲的歌名和歌手到歌詞服務，必要時向 MusicBrainz 查詢歌手的其他寫法，不上傳音訊或完整媒體庫。歌詞版權屬原權利人。" to UiEvent.Setting("onlineLyrics","true") else setting("onlineLyrics","false") }
                EditSetting("歌詞服務網址",s.text("lyricsBase","https://lrclib.net"),"LRCLIB 或相容的 HTTPS 自行託管服務。") {if(it.startsWith("https://")) setting("lyricsBase",it)}
                SectionTitle("歌詞來源")
                Info("依序搜尋已開啟的來源；用箭頭調整順序。非官方來源預設關閉。")
                Toggle("優先同步歌詞","找到純文字歌詞時，繼續向後面的來源查詢會跟著播放捲動的同步版本；都沒有才用純文字",s.bool("preferSyncedLyrics",true)) {setting("preferSyncedLyrics",it.toString())}
                val providers=lyricsProviders(s)
                providers.forEachIndexed { index,p ->
                    Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("${index+1}. ${p.info.name}",style=MaterialTheme.typography.titleMedium); Text(p.info.detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                        ActionIcon(Icons.Outlined.ArrowUpward,"${p.info.name} 往前",index>0) {onEvent(UiEvent.MoveLyricsProvider(p.info.id,-1))}
                        ActionIcon(Icons.Outlined.ArrowDownward,"${p.info.name} 往後",index<providers.lastIndex) {onEvent(UiEvent.MoveLyricsProvider(p.info.id,1))}
                        Switch(p.enabled,{ on -> if(on && p.info.warning!=null) confirm="${p.info.warning}要開啟嗎？" to UiEvent.SetLyricsProvider(p.info.id,true) else onEvent(UiEvent.SetLyricsProvider(p.info.id,on)) })
                    }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                }
                Info("已儲存的歌詞不會自動更換來源（只有羅馬拼音或純文字歌詞會自動再找一次日文原文或同步版本）；在歌詞畫面按「重新搜尋」即可改用目前的來源設定。翻譯也可獨立匯入帶時間戳的 LRC。")
            }
            6 -> {
                Toggle("記錄本機聆聽資料","預設開啟；與 ListenBrainz 同步分開",s.bool("statistics",true)) {setting("statistics",it.toString())}
                // A list instead of free text: nobody should need to know IANA zone IDs.
                val zone=s.text("timezone","Asia/Hong_Kong"); val device=java.time.ZoneId.systemDefault().id
                val zones=(listOf(device to "跟隨手機（$device）")+timeZones.filter {it.first!=device}).let { z -> if(z.none {it.first==zone}) z+(zone to zone) else z }
                Choice("統計時區",zone,zones) {setting("timezone",it)}
                Info("一首歌聽滿 30 秒（短歌則一半長度）才計一次播放；暫停、緩衝及跳過的部分不計時間。每週由星期一開始，每月由 1 號開始；更改時區後會按新時區重新計算，歷史紀錄會保留。")
                Choice("預設回顧期間",s.text("rankPeriod","Week"),listOf("Week" to periodLabels[0],"Month" to periodLabels[1],"All" to periodLabels[2])) {setting("rankPeriod",it)}
                Toggle("預設按聆聽時間排序","關閉則按播放次數",s.bool("rankTime")) {setting("rankTime",it.toString())}
                SettingAction("匯出個人統計","CSV 原始事件") {onEvent(UiEvent.Export("statistics"))}
                SettingAction("清除目前回顧期間","不影響其他期間或 ListenBrainz 歷史") {confirm="刪除目前回顧期間的本機統計？此操作無法復原。" to UiEvent.ClearStatistics(state.leaderboard.startMs,state.leaderboard.endExclusiveMs)}
                SettingAction("清除全部本機統計","不刪除歌曲或伺服器紀錄") {confirm="永久刪除全部本機聆聽紀錄？此操作無法復原。" to UiEvent.ClearStatistics(0,Long.MAX_VALUE)}
            }
            7 -> {
                ListenBrainzAccount(state.listenBrainz,onEvent)
                Toggle("同步聆聽紀錄","會傳送歌名、歌手、專輯、播放時間及時長。停用不等於刪除伺服器歷史。",s.bool("lbSync")) { enabled -> if(enabled) confirm="啟用後會把歌曲、歌手、專輯、聆聽時間及時長傳送至 ListenBrainz；不傳送音訊檔。是否啟用？" to UiEvent.Setting("lbSync","true") else setting("lbSync","false") }
                Info("${state.listenBrainz.pending} 筆聆聽紀錄待同步")
                SettingAction("立即同步","只會移除伺服器已確認成功的紀錄") {onEvent(UiEvent.SyncNow)}
                SettingAction("更新推薦","推薦並非音訊供應服務") {onEvent(UiEvent.Recommendations);openDiscover()}
                SettingAction("斷開帳號，保留待同步資料","重新連接相同使用者後可繼續") {confirm="斷開 ListenBrainz 並保留未同步紀錄？上傳會停止。" to UiEvent.DisconnectListenBrainz(false)}
                SettingAction("斷開帳號，刪除待同步資料","不刪除本機統計或伺服器歷史") {confirm="斷開並刪除這個帳號的待同步紀錄？此操作無法復原。" to UiEvent.DisconnectListenBrainz(true)}
            }
            8 -> {
                Info("播放控制由 Android 媒體通知、鎖屏及藍牙提供。背景播放由 MediaSessionService 管理。")
                Toggle("下載完成／失敗通知","不影響背景下載進度通知",s.bool("downloadNotifications",true)) {setting("downloadNotifications",it.toString())}
                Info("通知權限：${if(state.notificationAllowed) "已允許" else "未允許"}")
                SettingAction("通知權限及系統設定","在 Android 設定中管理",openSystemSettings)
                Info("若裝置實際限制背景工作，可在系統設定檢查。App 不會主動要求忽略電池最佳化。")
            }
            9 -> {
                Text("Monologue",style=MaterialTheme.typography.displaySmall)
                Info("版本 ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}")
                SectionTitle("私隱")
                Info("本機掃描、播放清單與統計保存在裝置。Drive 僅在授權後存取；ListenBrainz 上傳預設關閉。憑證用 Android Keystore 加密，排除備份、設定匯出與診斷。")
                SectionTitle("開源及素材")
                Info("Monologue：GPL-3.0-or-later\nNewPipe Extractor：GPL-3.0-or-later\nAndroidX／Media3／Room／WorkManager：Apache-2.0\nKotlin／Coroutines／Immutable collections：Apache-2.0\nOkHttp／Coil：Apache-2.0\n字體：Android 系統 Serif／Sans Serif fallback。\n黑膠與唱臂：程式繪製。正式 App 不附帶示範音樂或參考圖封面。完整授權見專案 THIRD_PARTY_NOTICES.md。")
                SettingAction("匯出診斷資料","預覽：版本、Android API、曲目數、播放狀態與下載計數；已排除 Token、帳號、曲名及私人路徑。") {confirm="診斷只包括 App／Android 版本、曲目總數、播放狀態、下載成功及失敗數。沒有 Token、帳號、歌曲名稱、URI 或私人路徑。是否匯出？" to UiEvent.Export("diagnostics")}
                SettingAction("匯出設定及播放清單","不包含憑證；播放清單含本機媒體 ID，請妥善保管") {onEvent(UiEvent.Export("settings"))}
                SettingAction("匯入設定及播放清單","只匯入可配對的曲目；不匯入憑證或啟用同步") {onEvent(UiEvent.ImportSettings)}
                SettingAction("還原設定","不清除統計、播放清單或永久下載") {confirm="還原所有偏好設定？統計、播放清單與永久下載會保留。" to UiEvent.ResetSettings}
            }
        }
        Spacer(Modifier.height(32.dp))
    }
    confirm?.let { (message,event) -> AlertDialog(onDismissRequest={confirm=null},title={Text("確認操作")},text={Text(message)},confirmButton={TextButton(onClick={confirm=null;onEvent(event)}) {Text("確認")}},dismissButton={TextButton(onClick={confirm=null}) {Text("取消")}}) }
}
@Composable fun Info(text: String) {Text(text,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=12.dp))}
@Composable fun SettingAction(title: String,subtitle: String,click: ()->Unit) {ListItem(headlineContent={Text(title)},supportingContent={if(subtitle.isNotBlank()) Text(subtitle)},trailingContent={Icon(Icons.Outlined.ChevronRight,null)},modifier=Modifier.clickable(onClick=click),colors=ListItemDefaults.colors(containerColor=Color.Transparent));HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)}
@Composable fun Toggle(title: String,subtitle: String,value: Boolean,change: (Boolean)->Unit) {Row(Modifier.fillMaxWidth().clickable {change(!value)}.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f).padding(end=12.dp)) {Text(title,style=MaterialTheme.typography.titleMedium);if(subtitle.isNotBlank()) Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(value,change)};HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)}
@Composable fun Choice(title: String,value: String,options: List<Pair<String,String>>,change: (String)->Unit) {
    var open by remember {mutableStateOf(false)}
    SettingAction(title,options.find {it.first==value}?.second ?: value) {open=true}
    if(open) AlertDialog(onDismissRequest={open=false},title={Text(title)},text={Column(Modifier.verticalScroll(rememberScrollState())) {options.forEach { (id,label) -> Row(Modifier.fillMaxWidth().clickable {change(id);open=false}.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {RadioButton(id==value,{change(id);open=false});Text(label)}}}},confirmButton={TextButton(onClick={open=false}) {Text("關閉")}})
}
@Composable fun EditSetting(title: String,value: String,hint: String,numeric: Boolean=false,save: (String)->Unit) {
    var open by remember {mutableStateOf(false)};var draft by remember(value) {mutableStateOf(value)}
    SettingAction(title,value.ifBlank {"未設定"}) {draft=value;open=true}
    if(open) AlertDialog(onDismissRequest={open=false},title={Text(title)},text={Column {Info(hint);OutlinedTextField(draft,{draft=it},keyboardOptions=KeyboardOptions(keyboardType=if(numeric) KeyboardType.Number else KeyboardType.Text))}},confirmButton={TextButton(onClick={save(draft);open=false}) {Text("儲存")}},dismissButton={TextButton(onClick={open=false}) {Text("取消")}})
}
@Composable fun EqualizerSheet(state: EqualizerUiState,onEvent: (UiEvent)->Unit,close: ()->Unit) {
    ModalBottomSheet(onDismissRequest=close) {
        Column(Modifier.fillMaxWidth().heightIn(max=650.dp).verticalScroll(rememberScrollState()).padding(24.dp).navigationBarsPadding()) {
            Text("等化器",style=MaterialTheme.typography.headlineSmall)
            if(!state.supported) Info(state.reason)
            else {
                Toggle("啟用等化器","",state.enabled) {onEvent(UiEvent.EqEnabled(it))}
                state.bands.forEach {band ->
                    Text("${band.hz} Hz · ${band.level/100f} dB",style=TimeStyle)
                    var value by remember(band.level) {mutableFloatStateOf(band.level.toFloat())}
                    Slider(value,{value=it},enabled=state.enabled,onValueChangeFinished={onEvent(UiEvent.EqBand(band.index,value.toInt()))},valueRange=state.min.toFloat()..state.max.toFloat())
                }
                state.presets.forEachIndexed {i,name -> TextButton(onClick={onEvent(UiEvent.EqPreset(i))}) {Text(name)} }
            }
            TextButton(onClick={onEvent(UiEvent.EqReset)},enabled=state.supported) {Text("重設所有頻段")}
        }
    }
}
@Composable fun SleepSheet(state: SleepTimerUiState,onEvent: (UiEvent)->Unit,close: ()->Unit) {
    var custom by remember {mutableStateOf("90")}
    ModalBottomSheet(onDismissRequest=close) {
        Column(Modifier.padding(24.dp).navigationBarsPadding()) {
            Text("睡眠計時器",style=MaterialTheme.typography.headlineSmall)
            Info(if(state.endOfTrack) "本曲結束後停止" else if(state.remainingMs>0) "剩餘 ${formatTime(state.remainingMs)}" else "未啟用")
            Row { listOf(15,30,45,60).forEach {m -> TextButton(onClick={onEvent(UiEvent.Sleep(m))}) {Text("$m 分")} } }
            Row(verticalAlignment=Alignment.CenterVertically) {
                OutlinedTextField(custom,{custom=it},Modifier.weight(1f),label={Text("自訂分鐘")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
                TextButton(onClick={custom.toIntOrNull()?.takeIf {it in 1..1440}?.let {onEvent(UiEvent.Sleep(it))}},enabled=(custom.toIntOrNull() ?: 0) in 1..1440) {Text("設定")}
            }
            TextButton(onClick={onEvent(UiEvent.Sleep(0,true))}) {Text("本曲結束後停止")}
            TextButton(onClick={onEvent(UiEvent.Sleep(0))}) {Text("取消計時器")}
        }
    }
}
