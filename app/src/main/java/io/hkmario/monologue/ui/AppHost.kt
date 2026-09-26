@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.hkmario.monologue.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import androidx.navigation.NavGraph.Companion.findStartDestination
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
    LaunchedEffect(state.downloads.phase,state.downloads.failed,sheet,lifecycleState) {
        if(state.downloads.phase==DownloadPhase.Running) shownFailureSummary=""
        if(state.downloads.phase==DownloadPhase.Complete && state.downloads.failed>0 && sheet==null && lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            val signature=state.downloads.items.filter {it.status==DownloadStatus.Failed}.joinToString {it.id}
            if(signature!=shownFailureSummary) {shownFailureSummary=signature;sheet="downloads"}
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
    val destinations=listOf("library" to "媒體庫","drive" to "雲端","rank" to "排行榜","discover" to "探索","settings" to "設定")
    val icons=listOf(Icons.Outlined.Home,Icons.Outlined.Cloud,Icons.Outlined.BarChart,Icons.Outlined.Explore,Icons.Outlined.Settings)
    fun navigate(target: String) { nav.navigate(target) {launchSingleTop=true;restoreState=true;popUpTo(nav.graph.findStartDestination().id) {saveState=true}} }
    fun more(track: Track) {selected=track;sheet="more"}
    LaunchedEffect(message) {
        when(val m=message) {
            is UiEffect.Message -> snackbar.showSnackbar(m.text)
            is UiEffect.UndoQueue -> if(snackbar.showSnackbar(if(m.entry.id==state.queue.currentId) "本曲結束後移除" else "已移除歌曲","復原",duration=SnackbarDuration.Short)==SnackbarResult.ActionPerformed) undo(m.entry,m.index)
            else -> Unit
        };if(message!=null) messageConsumed()
    }
    BackHandler(drawer.isOpen) {scope.launch {drawer.close()}}
    BackHandler(sheet!=null) {sheet=null}
    BackHandler(route=="drive" && state.drive.breadcrumbs.size>1 && sheet==null && !drawer.isOpen) {onEvent(UiEvent.DriveBreadcrumb(state.drive.breadcrumbs.lastIndex-1))}
    ModalNavigationDrawer(drawerState=drawer,gesturesEnabled=drawerMode && !now,drawerContent={ModalDrawerSheet {
        Text("monologue",style=MaterialTheme.typography.headlineLarge,modifier=Modifier.padding(24.dp))
        destinations.forEachIndexed {i,(id,label)->NavigationDrawerItem(label={Text(label)},selected=route==id || (id=="settings" && route.startsWith("settings/")),icon={Icon(icons[i],null)},onClick={scope.launch {drawer.close()};navigate(id)},modifier=Modifier.padding(horizontal=12.dp))}
    }}) {
        Scaffold(containerColor=MaterialTheme.colorScheme.background,contentWindowInsets=WindowInsets(0,0,0,0),snackbarHost={SnackbarHost(snackbar)},topBar={
            // The Drive tab draws its own compact wordmark header.
            if(!now && !(route in setOf("library","drive") && !drawerMode)) TopAppBar(title={Text(when {route=="library"->"monologue";route=="drive"->"Google Drive";route=="rank"->"聆聽排行";route=="discover"->"探索";route=="online"->"搜尋音樂";route=="settings"->"設定";route=="listening"->"聆聽明細";route=="support"->"支持金額估算";route=="settings/{group}"->settingsGroups.getOrElse(stack?.arguments?.getString("group")?.toIntOrNull() ?: 0) {"設定"};route=="offline"->"離線下載";route=="group"->groupTracks?.title?.substringAfterLast('/') ?: "歌曲";else->"播放清單"},style=MaterialTheme.typography.headlineMedium)},navigationIcon={if(route !in destinations.map {it.first}) ActionIcon(Icons.Outlined.ArrowBack,"返回") {nav.popBackStack()} else if(drawerMode) ActionIcon(Icons.Outlined.Menu,"開啟選單") {scope.launch {drawer.open()}}},colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background))
        },bottomBar={
            if(!now) Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                MiniPlayer(state.player,progress,onEvent,{nav.navigate("playing") {launchSingleTop=true}},{sheet="queue"})
                if(!drawerMode) NavigationBar(modifier=Modifier.heightIn(min=80.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)),containerColor=MaterialTheme.colorScheme.background,tonalElevation=0.dp) {destinations.forEachIndexed {i,(id,label)->NavigationBarItem(modifier=Modifier.testTag("nav-$id"),selected=route==id || (id=="settings" && route.startsWith("settings/")),onClick={navigate(id)},icon={Icon(icons[i],label)},label={Text(label)},colors=NavigationBarItemDefaults.colors(indicatorColor=MaterialTheme.colorScheme.primaryContainer))}}
                else Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }) {padding->
            NavHost(nav,"library",modifier=Modifier.fillMaxSize().padding(padding)) {
                composable("library") {Box(if(drawerMode) Modifier else Modifier.statusBarsPadding()) {LibraryScreen(state.library,state.settings,onEvent,{groupId=it.id;nav.navigate("group")},{nav.navigate("playlist/${Uri.encode(it)}")},::more,{navigate("discover")},
                    openDrive={navigate("drive")},
                    searchOnline={query->if(query.isNotBlank()) onEvent(UiEvent.Online(OnlineAction.SearchFor(query)));nav.navigate("online")},
                    playingId=state.player.entry?.track?.id,openSettings={nav.navigate("settings/2")})}}
                composable("drive") {Box(if(drawerMode) Modifier else Modifier.statusBarsPadding()) {DriveScreen(state.drive,state.settings,state.downloads,onEvent,{sheet="downloads"},::more,{nav.navigate("settings/3")},{navigate("library")})}}
                composable("rank") {RankScreen(state.leaderboard,onEvent,::more)}
                composable("discover") {DiscoverScreen(state.listenBrainz,state.discover,onEvent,state.stats,{nav.navigate("settings/7")},{onEvent(UiEvent.Statistics(Period.All));nav.navigate("listening")},{onEvent(UiEvent.Statistics(Period.Month));nav.navigate("support")},{nav.navigate("online")},{navigate("rank")})}
                composable("online") {OnlineScreen(state.online,state.plugins,state.settings,onEvent,{nav.navigate("settings/10")})}
                composable("listening") {StatisticsScreen(state.stats,false,onEvent)}
                composable("support") {StatisticsScreen(state.stats,true,onEvent)}
                composable("playing") {NowPlayingScreen(state,progress,clock,foreground,onEvent,{nav.popBackStack()},{sheet="queue"},{sheet="equalizer"},{sheet="sleep"},{state.player.entry?.track?.let(::more)},importLyrics)}
                composable("settings") {SettingsHome {nav.navigate("settings/$it")}}
                composable("settings/{group}") {backStack->SettingsDetail(backStack.arguments?.getString("group")?.toIntOrNull() ?: 0,state,onEvent,{sheet="equalizer"},{sheet="sleep"},{sheet="downloads"},{if(backStack.arguments?.getString("group")=="10") nav.navigate("settings/7") else navigate("discover")},{nav.navigate("offline")},systemSettings,importLyrics)}
                composable("group") {DetailScreen(groupTracks?.title ?: "歌曲",groupTracks?.tracks ?: persistentListOf(),null,onEvent,::more,{})}
                composable("playlist/{id}") {b -> val id=b.arguments?.getString("id");val playlist=state.library.playlists.find {it.id==id};val tracks=if(id=="favorites") state.library.tracks.filter {it.favorite}.toPersistentList() else playlist?.tracks ?: persistentListOf();DetailScreen(if(id=="favorites") "收藏歌曲" else playlist?.name ?: "播放清單",tracks,playlist,onEvent,::more,{nav.popBackStack()})}
                composable("offline") {OfflineScreen(state.library.tracks.filter {it.offlinePath!=null}.toPersistentList(),onEvent)}
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
        }}}
        "add-playlist" -> ModalBottomSheet(onDismissRequest={sheet=null}) {Column(Modifier.padding(24.dp).navigationBarsPadding()) {Text("加入播放清單",style=MaterialTheme.typography.titleLarge);if(state.library.playlists.isEmpty()) Info("請先在媒體庫建立播放清單。") else state.library.playlists.forEach {p->SettingAction(p.name,"${p.tracks.size} 首歌曲") {selected?.let {onEvent(UiEvent.PlaylistAdd(p.id,it))};sheet=null}}}}
    }
}

@Composable private fun DetailScreen(title: String,tracks: PersistentList<Track>,playlist: Playlist?,onEvent: (UiEvent)->Unit,onMore: (Track)->Unit,deleted: ()->Unit) {
    val listState=rememberLazyListState();val scope=rememberCoroutineScope();val latestPlaylist by rememberUpdatedState(playlist)
    var rename by remember {mutableStateOf(false)};var name by remember(title) {mutableStateOf(title)};var delete by remember {mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize(),state=listState,contentPadding=PaddingValues(24.dp)) {
        item {Text(title,style=MaterialTheme.typography.headlineMedium);Text("${tracks.size} 首歌曲",style=MaterialTheme.typography.bodyMedium);Row {Button(onClick={onEvent(UiEvent.PlayList(tracks))},enabled=tracks.isNotEmpty()) {Text("播放全部")};if(playlist!=null) {TextButton(onClick={rename=true}) {Text("改名")};TextButton(onClick={delete=true}) {Text("刪除")}}}}
        itemsIndexed(tracks,key={i,t->playlist?.entryIds?.getOrNull(i) ?: "$i:${t.id}"}) {i,track->
            Column {TrackRow(track,onPlay={onEvent(UiEvent.Play(track))},onMore={onMore(track)});if(playlist!=null) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                val entryId=playlist.entryIds.getOrNull(i)
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
                })
                ActionIcon(Icons.Outlined.ArrowUpward,"上移 ${track.title}",i>0) {onEvent(UiEvent.PlaylistMove(playlist.id,i,i-1))}
                ActionIcon(Icons.Outlined.ArrowDownward,"下移 ${track.title}",i<tracks.lastIndex) {onEvent(UiEvent.PlaylistMove(playlist.id,i,i+1))}
                ActionIcon(Icons.Outlined.RemoveCircleOutline,"從播放清單移除 ${track.title}") {onEvent(UiEvent.PlaylistRemove(playlist.id,i))}
            }}
        }
        if(tracks.isEmpty()) item {EmptyPanel("未有歌曲","在歌曲的更多選單加入播放清單或收藏")}
    }
    if(rename && playlist!=null) AlertDialog(onDismissRequest={rename=false},title={Text("改名")},text={OutlinedTextField(name,{name=it})},confirmButton={TextButton(onClick={onEvent(UiEvent.PlaylistRename(playlist.id,name));rename=false}) {Text("儲存")}},dismissButton={TextButton(onClick={rename=false}) {Text("取消")}})
    if(delete && playlist!=null) AlertDialog(onDismissRequest={delete=false},title={Text("刪除播放清單？")},text={Text("只刪除清單，不刪除音樂檔案。")},confirmButton={TextButton(onClick={onEvent(UiEvent.PlaylistDelete(playlist.id));delete=false;deleted()}) {Text("刪除")}},dismissButton={TextButton(onClick={delete=false}) {Text("取消")}})
}
@Composable private fun OfflineScreen(tracks: PersistentList<Track>,onEvent: (UiEvent)->Unit) {
    var selected by remember {mutableStateOf<Track?>(null)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp)) {if(tracks.isEmpty()) item {EmptyPanel("未有永久下載","串流暫存不會當成完整下載")};items(tracks,key={it.id}) {track->SettingAction(track.title,formatBytes(track.bytes)) {selected=track}}}
    selected?.let {t->AlertDialog(onDismissRequest={selected=null},title={Text("刪除永久下載？")},text={Text("將刪除「${t.title}」的本機離線檔案。雲端原檔不受影響。")},confirmButton={TextButton(onClick={onEvent(UiEvent.DeleteOffline(t.id));selected=null}) {Text("刪除")}},dismissButton={TextButton(onClick={selected=null}) {Text("取消")}})}
}