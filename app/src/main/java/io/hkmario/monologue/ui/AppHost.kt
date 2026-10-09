@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.hkmario.monologue.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import io.hkmario.monologue.domain.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.launch

@Composable fun AppHost(state: AppUiState,progress: State<PlaybackProgress>,clock: VinylClock,foreground: Boolean,onEvent: (UiEvent)->Unit,message: UiEffect?,messageConsumed: ()->Unit,undo: (QueueEntry,Int)->Unit,importLyrics: (Boolean)->Unit,systemSettings: ()->Unit) {
    val nav=rememberNavController();val stack by nav.currentBackStackEntryAsState();val route=stack?.destination?.route ?: "library"
    val drawer=rememberDrawerState(DrawerValue.Closed);val scope=rememberCoroutineScope();val snackbar=remember {SnackbarHostState()}
    val drawerMode=state.settings.text("navigation","bottom")=="drawer";val now=route=="playing"
    var sheet by rememberSaveable {mutableStateOf<String?>(null)};var selected by remember {mutableStateOf<Track?>(null)}
    var shownFailureSummary by rememberSaveable {mutableStateOf("")}
    val lifecycleState by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    // Failed downloads are announced with a message the user can open, instead of the download sheet opening by itself.
    LaunchedEffect(state.downloads.phase,state.downloads.failed,lifecycleState) {
        if(state.downloads.phase==DownloadPhase.Running) shownFailureSummary=""
        if(state.downloads.phase==DownloadPhase.Complete && state.downloads.failed>0 && lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            val signature=state.downloads.items.filter {it.status==DownloadStatus.Failed}.joinToString {it.id}
            if(signature!=shownFailureSummary) {
                shownFailureSummary=signature
                if(sheet!="downloads") scope.launch { if(snackbar.showSnackbar("${state.downloads.failed} 首下載失敗","查看",withDismissAction=true,duration=SnackbarDuration.Long)==SnackbarResult.ActionPerformed) sheet="downloads" }
            }
        }
    }
    var groupId by rememberSaveable {mutableStateOf<String?>(null)}
    val groupTracks=remember(groupId,state.library.tracks) {
        groupId?.let {id->
            val tab=runCatching {LibraryTab.valueOf(id.substringBefore(':'))}.getOrNull()
            val title=id.substringAfter(':')
            if(id.startsWith("Offline:")) GroupItem(id,"離線下載",state.library.tracks.filter {it.offlinePath!=null}.toPersistentList())
            else GroupItem(id,title,state.library.tracks.filter {when(tab) {LibraryTab.Artists->it.artist==title;LibraryTab.Albums->it.album==title;LibraryTab.Folders->it.folder==title;else->false}}.toPersistentList())
        }
    }
    // Four everyday destinations; 設定 is opened from the 媒體庫 header (or the drawer) instead of taking a tab.
    val destinations=listOf("library" to "媒體庫","search" to "搜尋","discover" to "探索","drive" to "雲端")
    val icons=listOf(Icons.Outlined.Home,Icons.Outlined.Search,Icons.Outlined.Explore,Icons.Outlined.Cloud)
    // Each bottom tab is its own nested graph. Switching tabs always opens the tab's first page (設定 › 歌詞 → 探索 → 設定 shows 設定),
    // so nothing opened inside a tab is saved or restored.
    fun inTab(id: String)=stack?.destination?.hierarchy?.any {it.route=="tab/$id"}==true
    fun navigate(tab: String) {
        if(tab=="library") nav.popBackStack("library",false)
        else nav.navigate("tab/$tab") {launchSingleTop=true;popUpTo("library")}
    }
    fun onTab(tab: String)=navigate(tab)
    /** Links that lead into another tab switch to it first, so the page lands on that tab's own stack. */
    fun openIn(tab: String,route: String) { if(!inTab(tab)) navigate(tab); nav.navigate(route) {launchSingleTop=true} }
    fun more(track: Track) {selected=track;sheet="more"}
    fun searchFor(query: String) { if(query.isNotBlank()) onEvent(UiEvent.Online(OnlineAction.SearchFor(query))); navigate("search") }
    LaunchedEffect(message) {
        when(val m=message) {
            is UiEffect.Message -> snackbar.showSnackbar(m.text)
            is UiEffect.UndoQueue -> if(snackbar.showSnackbar(if(m.entry.id==state.queue.currentId) "本曲結束後移除" else "已移除歌曲","復原",duration=SnackbarDuration.Short)==SnackbarResult.ActionPerformed) undo(m.entry,m.index)
            is UiEffect.Undo -> if(snackbar.showSnackbar(m.text,"復原",duration=SnackbarDuration.Short)==SnackbarResult.ActionPerformed) onEvent(m.undo)
            else -> Unit
        };if(message!=null) messageConsumed()
    }
    BackHandler(drawer.isOpen) {scope.launch {drawer.close()}}
    BackHandler(sheet!=null) {sheet=null}
    BackHandler(route=="drive" && state.drive.breadcrumbs.size>1 && sheet==null && !drawer.isOpen) {onEvent(UiEvent.DriveBreadcrumb(state.drive.breadcrumbs.lastIndex-1))}
    ModalNavigationDrawer(drawerState=drawer,gesturesEnabled=drawerMode && !now,drawerContent={ModalDrawerSheet {
        Text("Monologue",style=MaterialTheme.typography.headlineLarge,modifier=Modifier.padding(24.dp))
        destinations.forEachIndexed {i,(id,label)->NavigationDrawerItem(label={Text(label)},selected=inTab(id),icon={Icon(icons[i],null)},onClick={scope.launch {drawer.close()};onTab(id)},modifier=Modifier.padding(horizontal=12.dp))}
        HorizontalDivider(Modifier.padding(vertical=8.dp,horizontal=24.dp))
        NavigationDrawerItem(label={Text("設定")},selected=inTab("settings"),icon={Icon(Icons.Outlined.Settings,null)},onClick={scope.launch {drawer.close()};onTab("settings")},modifier=Modifier.padding(horizontal=12.dp))
    }}) {
        Scaffold(containerColor=MaterialTheme.colorScheme.background,contentWindowInsets=WindowInsets(0,0,0,0),snackbarHost={SnackbarHost(snackbar)},topBar={
            // 媒體庫 and 雲端 draw their own compact wordmark header.
            if(!now && !(route in setOf("library","drive") && !drawerMode)) TopAppBar(title={Text(when {route=="library"->"monologue";route=="drive"->"Google Drive";route=="search"->"搜尋";route=="discover"->"探索";route=="recap"->"聆聽回顧";route=="settings"->"設定";route=="support"->"支持金額估算";route=="settings/{group}"->settingsGroups.getOrElse(stack?.arguments?.getString("group")?.toIntOrNull() ?: 0) {"設定"};route=="offline"->"離線下載";route=="browse/{id}"->"";route=="group"->groupTracks?.title?.substringAfterLast('/') ?: "歌曲";else->"播放清單"},style=MaterialTheme.typography.headlineMedium)},navigationIcon={if(route !in destinations.map {it.first}) ActionIcon(Icons.Outlined.ArrowBack,"返回") {nav.popBackStack()} else if(drawerMode) ActionIcon(Icons.Outlined.Menu,"開啟選單") {scope.launch {drawer.open()}}},colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background))
        },bottomBar={
            if(!now) Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                MiniPlayer(state.player,progress,onEvent,{nav.navigate("playing") {launchSingleTop=true}},{sheet="queue"})
                if(!drawerMode) NavigationBar(modifier=Modifier.heightIn(min=80.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)),containerColor=MaterialTheme.colorScheme.background,tonalElevation=0.dp) {destinations.forEachIndexed {i,(id,label)->NavigationBarItem(modifier=Modifier.testTag("nav-$id"),selected=inTab(id),onClick={onTab(id)},icon={Icon(icons[i],label)},label={Text(label)},colors=NavigationBarItemDefaults.colors(indicatorColor=MaterialTheme.colorScheme.primaryContainer))}}
                else Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }) {padding->
            NavHost(nav,"tab/library",modifier=Modifier.fillMaxSize().padding(padding)) {
                navigation(startDestination="library",route="tab/library") {
                    composable("library") {Box(if(drawerMode) Modifier else Modifier.statusBarsPadding()) {LibraryScreen(state.library,state.settings,onEvent,{groupId=it.id;nav.navigate("group")},{nav.navigate("playlist/${Uri.encode(it)}")},::more,{navigate("discover")},
                        openDrive={navigate("drive")},
                        searchOnline=::searchFor,
                        playingId=state.player.entry?.track?.id,openSettings={openIn("settings","settings/2")},openAppSettings={navigate("settings")})}}
                    composable("group") {DetailScreen(groupTracks?.title ?: "歌曲",groupTracks?.tracks ?: persistentListOf(),null,onEvent,::more,{})}
                    composable("playlist/{id}") {b -> val id=b.arguments?.getString("id");val playlist=state.library.playlists.find {it.id==id};val tracks=if(id=="favorites") state.library.tracks.filter {it.favorite}.toPersistentList() else playlist?.tracks ?: persistentListOf();DetailScreen(if(id=="favorites") "收藏歌曲" else playlist?.name ?: "播放清單",tracks,playlist,onEvent,::more,{nav.popBackStack()})}
                }
                navigation(startDestination="search",route="tab/search") {
                    composable("search") {SearchScreen(state.library,state.online,state.plugins,state.settings,onEvent,::more,state.player.entry?.track?.id,{openIn("settings","settings/10")}) {nav.navigate("browse/$it")}}
                    composable("browse/{id}") {b -> BrowseCategoryScreen(b.arguments?.getString("id") ?: "",state.online,onEvent,state.player.entry?.track?.id)}
                }
                navigation(startDestination="discover",route="tab/discover") {
                    composable("discover") {DiscoverScreen(state.listenBrainz,state.discover,onEvent,state.stats,{openIn("settings","settings/7")},
                        openRecap={onEvent(UiEvent.Statistics(state.leaderboard.period,state.leaderboard.offset));nav.navigate("recap")},
                        chooseVersion={r->searchFor("${r.title} ${r.artist}")})}
                    composable("recap") {RecapScreen(state.leaderboard,onEvent,::more) {nav.navigate("support")}}
                    composable("support") {StatisticsScreen(state.stats,true,onEvent)}
                }
                navigation(startDestination="drive",route="tab/drive") {
                    composable("drive") {Box(if(drawerMode) Modifier else Modifier.statusBarsPadding()) {DriveScreen(state.drive,state.settings,state.downloads,onEvent,{sheet="downloads"},::more,{openIn("settings","settings/3")},{navigate("library")})}}
                }
                navigation(startDestination="settings",route="tab/settings") {
                    composable("settings") {SettingsHome {nav.navigate("settings/$it")}}
                    composable("settings/{group}") {backStack->SettingsDetail(backStack.arguments?.getString("group")?.toIntOrNull() ?: 0,state,onEvent,{sheet="equalizer"},{sheet="sleep"},{sheet="downloads"},{if(backStack.arguments?.getString("group")=="10") nav.navigate("settings/7") else navigate("discover")},{nav.navigate("offline")},systemSettings,importLyrics,openDrive={navigate("drive")})}
                    composable("offline") {OfflineScreen(state.library.tracks.filter {it.offlinePath!=null}.toPersistentList(),onEvent)}
                }
                composable("playing") {NowPlayingScreen(state,progress,clock,foreground,onEvent,{nav.popBackStack()},{sheet="queue"},{sheet="equalizer"},{sheet="sleep"},{state.player.entry?.track?.let(::more)},importLyrics)}
            }
        }
    }
    when(sheet) {
        "queue" -> QueueSheet(state.queue,onEvent) {sheet=null}
        "downloads" -> DownloadSheet(state.downloads,onEvent) {sheet=null}
        "equalizer" -> EqualizerSheet(state.equalizer,onEvent) {sheet=null}
        "sleep" -> SleepSheet(state.sleep,onEvent) {sheet=null}
        "more" -> selected?.let {track -> ModalBottomSheet(onDismissRequest={sheet=null}) {Column(Modifier.padding(24.dp).navigationBarsPadding()) {
            Text(track.title,style=MaterialTheme.typography.titleLarge)
            SettingAction("接著播放","") {onEvent(UiEvent.Enqueue(track,true));sheet=null}
            SettingAction("加入隊列","") {onEvent(UiEvent.Enqueue(track));sheet=null}
            SettingAction(if(track.favorite) "取消收藏" else "收藏歌曲","") {onEvent(UiEvent.Favorite(track));sheet=null}
            SettingAction("加入播放清單","") {sheet="add-playlist"}
            if(track.source!=Source.Local) SettingAction("下載供離線播放","") {onEvent(UiEvent.DownloadTrack(track));sheet="downloads"}
            // Only for the song playing now: its lyrics are what the translation is lined up with.
            if(track.id==state.player.entry?.track?.id) SettingAction("貼上翻譯連結","巴哈姆特、Pixnet、網誌等有這首歌翻譯的網頁") {sheet="translation-link"}
        }}}
        "translation-link" -> ModalBottomSheet(onDismissRequest={sheet=null}) {Column(Modifier.padding(24.dp).navigationBarsPadding().imePadding()) {
            val clipboard=androidx.compose.ui.platform.LocalClipboardManager.current
            // A link just copied from the browser is filled in.
            var link by rememberSaveable {mutableStateOf(clipboard.getText()?.text?.trim()?.takeIf { it.startsWith("https://") } ?: "")}
            Text("貼上翻譯連結",style=MaterialTheme.typography.titleLarge)
            Text("在網上找到這首歌的中文翻譯？貼上網頁連結，翻譯會逐行配對到目前的歌詞。需要人機驗證的網頁（例如巴哈姆特哈啦區）無法讀取。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=8.dp))
            OutlinedTextField(link,{link=it},Modifier.fillMaxWidth(),placeholder={Text("https://")},singleLine=true)
            Button(onClick={onEvent(UiEvent.TranslationLink(link));sheet=null},enabled=link.trim().startsWith("https://"),modifier=Modifier.fillMaxWidth().padding(top=12.dp)) {Text("讀取翻譯")}
        }}
        "add-playlist" -> ModalBottomSheet(onDismissRequest={sheet=null}) {Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp).navigationBarsPadding()) {
            var name by rememberSaveable {mutableStateOf("")}
            Text("加入播放清單",style=MaterialTheme.typography.titleLarge)
            // A new playlist can be made right here, so adding a song never sends the user elsewhere first.
            Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                OutlinedTextField(name,{name=it},Modifier.weight(1f),placeholder={Text("新播放清單名稱")},singleLine=true)
                TextButton(onClick={selected?.let {onEvent(UiEvent.PlaylistCreate(name,it))};sheet=null},enabled=name.isNotBlank()) {Text("建立並加入")}
            }
            state.library.playlists.forEach {p->SettingAction(p.name,"${p.tracks.size} 首歌曲") {selected?.let {onEvent(UiEvent.PlaylistAdd(p.id,it))};sheet=null}}
        }}
    }
}

@Composable private fun DetailScreen(title: String,tracks: PersistentList<Track>,playlist: Playlist?,onEvent: (UiEvent)->Unit,onMore: (Track)->Unit,deleted: ()->Unit) {
    val listState=rememberLazyListState();val scope=rememberCoroutineScope();val latestPlaylist by rememberUpdatedState(playlist)
    var rename by remember {mutableStateOf(false)};var name by remember(title) {mutableStateOf(title)};var delete by remember {mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize(),state=listState,contentPadding=PaddingValues(24.dp)) {
        item {
            // 播放全部 sits beside the title (as on 雲端); a long title pushes it onto the next line.
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(title,Modifier.align(Alignment.CenterVertically).padding(end=12.dp),style=MaterialTheme.typography.headlineMedium,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Button(onClick={onEvent(UiEvent.PlayList(tracks))},modifier=Modifier.align(Alignment.CenterVertically),enabled=tracks.isNotEmpty()) {Icon(Icons.Outlined.PlayArrow,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("播放全部")}
            }
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("${tracks.size} 首歌曲",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                if(playlist!=null) {TextButton(onClick={rename=true}) {Text("改名")};TextButton(onClick={delete=true}) {Text("刪除")}}
            }
            if(playlist!=null && tracks.isNotEmpty()) Text("向左滑動移除歌曲；長按右側把手拖曳排序",Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        itemsIndexed(tracks,key={i,t->playlist?.entryIds?.getOrNull(i) ?: "$i:${t.id}"}) {i,track->
            if(playlist==null) TrackRow(track,onPlay={onEvent(UiEvent.Play(track))},onMore={onMore(track)})
            else {
                val entryId=playlist.entryIds.getOrNull(i)
                var dx by remember(entryId) {mutableFloatStateOf(0f)}
                // Same gestures as the play queue: swipe left removes (with 復原), the handle reorders. Move/remove stay available to accessibility services.
                Row(Modifier.fillMaxWidth().graphicsLayer {translationX=dx}.semantics {
                    customActions=listOf(
                        CustomAccessibilityAction("上移") {if(i>0) onEvent(UiEvent.PlaylistMove(playlist.id,i,i-1)); true},
                        CustomAccessibilityAction("下移") {if(i<tracks.lastIndex) onEvent(UiEvent.PlaylistMove(playlist.id,i,i+1)); true},
                        CustomAccessibilityAction("從播放清單移除") {onEvent(UiEvent.PlaylistRemove(playlist.id,i)); true})
                }.pointerInput(entryId) {
                    detectHorizontalDragGestures(onDragStart={dx=0f},onDragCancel={dx=0f},onDragEnd={
                        val current=latestPlaylist
                        val at=current?.entryIds?.indexOf(entryId) ?: -1
                        if(dx < -120.dp.toPx() && current!=null && at>=0) onEvent(UiEvent.PlaylistRemove(current.id,at))
                        dx=0f
                    }) {change,delta -> change.consume();dx=(dx+delta).coerceAtMost(0f)}
                },verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {TrackRow(track,onPlay={onEvent(UiEvent.Play(track))},onMore={onMore(track)})}
                    Icon(Icons.Outlined.DragHandle,"長按拖曳排序 ${track.title}",Modifier.size(48.dp).padding(12.dp).pointerInput(entryId) {
                        var dy=0f
                        detectDragGesturesAfterLongPress(onDragStart={dy=0f},onDragEnd={dy=0f}) {change,amount ->
                            change.consume();dy+=amount.y
                            if(kotlin.math.abs(dy)>60.dp.toPx()) {
                                val current=latestPlaylist ?: return@detectDragGesturesAfterLongPress
                                val from=current.entryIds.indexOf(entryId)
                                if(from>=0) {val to=(from+if(dy>0) 1 else -1).coerceIn(0,current.tracks.lastIndex);if(to!=from) {onEvent(UiEvent.PlaylistMove(current.id,from,to));scope.launch {listState.animateScrollToItem(to)}}}
                                dy=0f
                            }
                        }
                    },tint=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if(tracks.isEmpty()) item {EmptyPanel("未有歌曲","在歌曲的更多選單加入播放清單或收藏")}
    }
    if(rename && playlist!=null) AlertDialog(onDismissRequest={rename=false},title={Text("改名")},text={OutlinedTextField(name,{name=it})},confirmButton={TextButton(onClick={onEvent(UiEvent.PlaylistRename(playlist.id,name));rename=false}) {Text("儲存")}},dismissButton={TextButton(onClick={rename=false}) {Text("取消")}})
    if(delete && playlist!=null) AlertDialog(onDismissRequest={delete=false},title={Text("刪除播放清單？")},text={Text("只刪除清單，不刪除音樂檔案。")},confirmButton={TextButton(onClick={onEvent(UiEvent.PlaylistDelete(playlist.id));delete=false;deleted()}) {Text("刪除")}},dismissButton={TextButton(onClick={delete=false}) {Text("取消")}})
}
@Composable private fun OfflineScreen(tracks: PersistentList<Track>,onEvent: (UiEvent)->Unit) {
    var selected by remember {mutableStateOf<Track?>(null)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp)) {if(tracks.isEmpty()) item {EmptyPanel("未有離線下載","只有下載完成的歌曲會在這裡；播放時的暫存不計算在內")};items(tracks,key={it.id}) {track->SettingAction(track.title,formatBytes(track.bytes)) {selected=track}}}
    selected?.let {t->AlertDialog(onDismissRequest={selected=null},title={Text("刪除離線下載？")},text={Text("將刪除「${t.title}」存在手機上的檔案。雲端原檔不受影響。")},confirmButton={TextButton(onClick={onEvent(UiEvent.DeleteOffline(t.id));selected=null}) {Text("刪除")}},dismissButton={TextButton(onClick={selected=null}) {Text("取消")}})}
}
