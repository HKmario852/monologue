@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.hkmario.monologue.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.hkmario.monologue.BuildConfig
import io.hkmario.monologue.domain.*
import kotlinx.collections.immutable.toPersistentList

internal val Ink @Composable get()=MaterialTheme.colorScheme.onBackground
internal val Muted @Composable get()=MaterialTheme.colorScheme.onSurfaceVariant
internal val Accent @Composable get()=MaterialTheme.colorScheme.primary
internal val SerifItalic=androidx.compose.ui.text.TextStyle(fontFamily=androidx.compose.ui.text.font.FontFamily.Serif,fontStyle=FontStyle.Italic)

@Composable fun Eyebrow(text: String)=Text(text,style=MaterialTheme.typography.labelMedium.copy(letterSpacing=1.5.sp),color=Muted)
@Composable fun InkButton(text: String,icon: androidx.compose.ui.graphics.vector.ImageVector?,modifier: Modifier=Modifier,enabled: Boolean=true,compact: Boolean=false,click: ()->Unit) {
    Button(onClick=click,modifier=modifier.heightIn(min=if(compact) 48.dp else 52.dp),enabled=enabled,shape=RoundedCornerShape(2.dp),colors=ButtonDefaults.buttonColors(containerColor=Ink,contentColor=MaterialTheme.colorScheme.background),contentPadding=PaddingValues(horizontal=if(compact) 14.dp else 20.dp)) {
        if(icon!=null) {Icon(icon,null,Modifier.size(18.dp));Spacer(Modifier.width(if(compact) 8.dp else 10.dp))}
        Text(text,style=MaterialTheme.typography.titleSmall.copy(fontFamily=androidx.compose.ui.text.font.FontFamily.Serif,fontWeight=FontWeight.Bold,fontSize=if(compact) 13.sp else MaterialTheme.typography.titleSmall.fontSize))
    }
}

@Composable fun DriveScreen(state: DriveLibraryUiState,settings: AppSettingsUiState,downloads: DownloadManagerUiState,onEvent: (UiEvent)->Unit,onDownloads: ()->Unit,onMore: (Track)->Unit,openSettings: ()->Unit={},openLibrary: ()->Unit={}) {
    val known=state.connection==Connection.Connected || state.everywhere.isNotEmpty()
    Box(Modifier.fillMaxSize()) {
        when {
            !known && state.phase==Phase.Loading -> Column(Modifier.fillMaxSize().padding(horizontal=24.dp)) { Spacer(Modifier.height(56.dp)); LinearProgressIndicator(Modifier.fillMaxWidth(),color=Accent); Text("正在讀取雲端硬碟…",Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodyMedium,color=Muted) }
            !known -> DriveIntro(state.error,onEvent,openLibrary)
            state.connection==Connection.Connected && !settings.bool("driveRootChosen") -> DriveRootPicker(state,onEvent)
            else -> DriveLibrary(state,settings,downloads,onEvent,onDownloads,onMore,openSettings)
        }
        state.connectStep?.let { DriveConnecting(it) { onEvent(UiEvent.DriveCancelConnect) } }
    }
}

/** i-a: what connecting means, before any Google screen appears. */
@Composable private fun DriveIntro(error: String?,onEvent: (UiEvent)->Unit,openLibrary: ()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp)) {
        Spacer(Modifier.height(24.dp)); Eyebrow("雲端")
        Text("把你的唱片櫃\n接上雲端。",Modifier.padding(top=8.dp),style=MaterialTheme.typography.displaySmall.copy(fontWeight=FontWeight.Bold,lineHeight=44.sp),color=Ink)
        Text("登入 Google 帳戶後，Monologue 會讀取你雲端硬碟中的音樂資料夾，可即時串流，也可逐首下載離線收聽。",Modifier.padding(top=16.dp),style=MaterialTheme.typography.bodyLarge.copy(fontFamily=androidx.compose.ui.text.font.FontFamily.Serif),color=Muted)
        HorizontalDivider(Modifier.padding(top=24.dp),color=Ink)
        listOf("只讀取音訊檔案" to "MP3、FLAC、M4A、OGG、OPUS、WAV 等格式","絕不修改或刪除" to "你的雲端檔案維持原狀","隨時可中斷連接" to "已下載的歌曲仍保留在本機").forEachIndexed { i,(title,detail) ->
            Row(Modifier.fillMaxWidth().padding(vertical=14.dp)) {
                Text("${i+1}",Modifier.width(36.dp),style=SerifItalic.copy(fontSize=18.sp),color=Accent)
                Column { Text(title,style=MaterialTheme.typography.titleSmall.copy(fontFamily=androidx.compose.ui.text.font.FontFamily.Serif),color=Ink); Text(detail,style=MaterialTheme.typography.bodySmall,color=Muted) }
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        }
        Spacer(Modifier.height(24.dp))
        InkButton(if(BuildConfig.GOOGLE_AUTH_CONFIGURED) "以 Google 帳戶登入" else "此版本暫未開放雲端登入",Icons.Outlined.Cloud,Modifier.fillMaxWidth(),enabled=BuildConfig.GOOGLE_AUTH_CONFIGURED) { onEvent(UiEvent.ConnectDrive) }
        if(!BuildConfig.GOOGLE_AUTH_CONFIGURED) Text("正式版會開放 Google Drive；現在可先聽本機音樂或搜尋線上音樂。",Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodySmall,color=Muted)
        error?.let { Text(it,Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error) }
        TextButton(onClick=openLibrary,modifier=Modifier.align(Alignment.CenterHorizontally).padding(vertical=8.dp)) { Text("稍後再說，先聽本機音樂",style=MaterialTheme.typography.bodyMedium.copy(textDecoration=TextDecoration.Underline),color=Muted) }
    }
}

/** i-b: progress card while Google's own sign-in window is open. */
@Composable private fun DriveConnecting(step: Int,cancel: ()->Unit) {
    Box(Modifier.fillMaxSize().background(Ink.copy(alpha=.45f)).clickable(interactionSource=remember {androidx.compose.foundation.interaction.MutableInteractionSource()},indication=null) {},contentAlignment=Alignment.Center) {
        Surface(Modifier.padding(24.dp).widthIn(max=420.dp).fillMaxWidth(),shape=RoundedCornerShape(2.dp),color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).border(1.dp,Accent.copy(alpha=.4f),CircleShape),contentAlignment=Alignment.Center) { Box(Modifier.size(20.dp).clip(CircleShape).background(Accent)) }
                Text("正在連接雲端硬碟",Modifier.padding(top=20.dp),style=MaterialTheme.typography.titleLarge.copy(fontWeight=FontWeight.Bold),color=Ink)
                Text("請在 Google 的登入視窗中選擇帳戶，並允許 Monologue 讀取你的音樂檔案。",Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodyMedium,color=Muted,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                HorizontalDivider(Modifier.padding(vertical=16.dp),color=MaterialTheme.colorScheme.outlineVariant)
                listOf("帳戶選擇","權限確認","讀取資料夾").forEachIndexed { i,label ->
                    val (status,color)=when { i<step -> "完成" to Muted; i==step -> "進行中…" to Accent; else -> "等待" to Muted.copy(alpha=.6f) }
                    Row(Modifier.fillMaxWidth().padding(vertical=4.dp)) { Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=Muted); Text(status,style=SerifItalic.copy(fontSize=12.sp),color=color) }
                }
                TextButton(onClick=cancel,modifier=Modifier.padding(top=8.dp)) { Text("取消",color=Accent,style=MaterialTheme.typography.bodyMedium.copy(textDecoration=TextDecoration.Underline)) }
            }
        }
    }
}

/** i-c: pick the folder that becomes the cloud library's starting point. */
@Composable private fun DriveRootPicker(state: DriveLibraryUiState,onEvent: (UiEvent)->Unit) {
    val index=state.index
    // Open where the music actually is: follow single-folder chains such as 電腦 › 我的電腦 › song.
    fun suggested(): List<String> {
        val p=mutableListOf("root")
        while(p.size<12) {
            val kids=index.children(p.last()).filter {index.stat(it.id).tracks>0}
            val only=kids.singleOrNull() ?: break
            if(index.stat(p.last()).tracks!=index.stat(only.id).tracks || index.children(only.id).none {index.stat(it.id).tracks>0}) break
            p+=only.id
        }
        return p
    }
    var path by rememberSaveable(index.nodes.size) { mutableStateOf(suggested()) }
    val here=path.last()
    val children=index.children(here)
    var selected by rememberSaveable(here) { mutableStateOf(children.maxByOrNull { index.stat(it.id).tracks }?.takeIf { index.stat(it.id).tracks>0 }?.id ?: here) }
    Column(Modifier.fillMaxSize().padding(horizontal=24.dp)) {
        Row(Modifier.fillMaxWidth().padding(top=8.dp,bottom=16.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Accent),contentAlignment=Alignment.Center) { Text((state.account ?: "M").take(1).uppercase(),style=SerifItalic.copy(fontSize=20.sp,fontWeight=FontWeight.Bold),color=MaterialTheme.colorScheme.onPrimary) }
            Text(state.account ?: "Google 帳戶",Modifier.weight(1f).padding(horizontal=12.dp),style=MaterialTheme.typography.bodyMedium,color=Ink,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text("● 已連接",style=MaterialTheme.typography.labelSmall,color=Muted)
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        Text("音樂放在哪裡？",Modifier.padding(top=20.dp),style=MaterialTheme.typography.headlineMedium.copy(fontWeight=FontWeight.Bold),color=Ink)
        Text("選一個資料夾作為雲端音樂庫的起點，子資料夾會一併讀取。",Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodyMedium,color=Muted)
        Row(Modifier.fillMaxWidth().padding(top=16.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically) {
            if(path.size>1) Text("‹ 上一層",Modifier.clickable { path=path.dropLast(1) }.padding(end=12.dp),style=MaterialTheme.typography.labelMedium,color=Accent)
            Text(path.joinToString(" / ",postfix=" /") { index.name(it) },style=MaterialTheme.typography.labelSmall,color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        HorizontalDivider(color=Ink)
        LazyColumn(Modifier.weight(1f)) {
            if(state.phase==Phase.Loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical=16.dp),color=Accent) }
            items(children,key={it.id}) { folder ->
                val stat=index.stat(folder.id)
                PickerRow(folder.name,if(stat.tracks==0) "未發現音訊檔案" else "${stat.folders} 個資料夾 · ${stat.tracks} 首",selected==folder.id,stat.folders>0,{selected=folder.id}) { path=path+folder.id }
            }
            if(here=="root") item { PickerRow("整個雲端硬碟","掃描較慢 · ${index.stat("root").tracks} 首",selected=="root",false,{selected="root"},italic=true) {} }
            else if(children.isEmpty()) item { Text("此資料夾沒有子資料夾；可直接選用上一層。",Modifier.padding(vertical=16.dp),style=MaterialTheme.typography.bodySmall,color=Muted) }
        }
        HorizontalDivider(color=Ink)
        val name=index.name(selected)
        InkButton("以「$name」為音樂庫 →",null,Modifier.fillMaxWidth().padding(top=16.dp),enabled=state.phase!=Phase.Loading) { onEvent(UiEvent.DriveChooseRoot(selected,name)) }
        Text("之後可在「設定 › Google Drive 與下載」更改或中斷連接",Modifier.align(Alignment.CenterHorizontally).padding(vertical=10.dp),style=SerifItalic.copy(fontSize=12.sp),color=Muted)
    }
}
@Composable private fun PickerRow(name: String,detail: String,selected: Boolean,canOpen: Boolean,select: ()->Unit,italic: Boolean=false,open: ()->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp).clickable(onClick=select).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
        RadioButton(selected,select,colors=RadioButtonDefaults.colors(selectedColor=Accent))
        Column(Modifier.weight(1f).padding(start=4.dp)) {
            Text(name,style=MaterialTheme.typography.titleMedium.copy(fontFamily=androidx.compose.ui.text.font.FontFamily.Serif,fontStyle=if(italic) FontStyle.Italic else FontStyle.Normal),color=Ink)
            Text(detail,style=SerifItalic.copy(fontSize=12.sp),color=Muted)
        }
        if(canOpen) ActionIcon(Icons.Outlined.ChevronRight,"打開 $name",action=open)
    }
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
}

private fun formatLabel(mime: String)=when(mime.substringAfter('/').substringBefore(';').lowercase()) {
    "mpeg","mp3"->"MP3";"flac","x-flac"->"FLAC";"mp4","x-m4a","m4a","aac"->"AAC";"ogg","vorbis"->"OGG";"opus"->"OPUS";"wav","x-wav"->"WAV";else->"音訊"
}
private fun sizeLabel(bytes: Long)=if(bytes>=1_000_000_000) "%.1f GB".format(bytes/1_000_000_000.0) else "%.0f MB".format(bytes/1_000_000.0)

/** Connected (and i-d disconnected) library: compact header, search with sort beside it, album-style rows. */
@Composable private fun DriveLibrary(state: DriveLibraryUiState,settings: AppSettingsUiState,downloads: DownloadManagerUiState,onEvent: (UiEvent)->Unit,onDownloads: ()->Unit,onMore: (Track)->Unit,openSettings: ()->Unit) {
    val index=state.index; val current=state.breadcrumbs.last().id
    val offline=state.connection==Connection.AuthorizationRequired
    val under=if(index.nodes.isEmpty() && index.stats.isEmpty()) state.everywhere else index.tracksUnder(current,state.everywhere)
    val direct=under.filter {it.folder==current}
    val q=normalize(state.query)
    val folders=if(q.isEmpty()) index.children(current).filter {index.stat(it.id).tracks>0} else emptyList()
    val listed=(if(q.isNotEmpty()) under.filter {normalize(it.title) .contains(q) || normalize(it.artist).contains(q) || normalize(it.album).contains(q)} else direct.ifEmpty {under}).let { t ->
        when(state.sort) {"size"->t.sortedByDescending {it.bytes};"nameDesc"->t.sortedByDescending {normalize(it.title)};"artist"->t.sortedBy {normalize(it.artist)+normalize(it.title)};else->t.sortedBy {normalize(it.title)}}
    }
    val album=listed.map {it.album}.distinct().singleOrNull()?.takeIf {it!="未知專輯" && listed.size>1}
    val unsaved=under.count {it.offlinePath==null}
    var menu by remember { mutableStateOf(false) }; var sortMenu by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=24.dp,end=24.dp,bottom=24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top=8.dp).heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
                    (listOf(DriveFolder("",  "雲端硬碟"))+state.breadcrumbs).forEachIndexed { i,crumb ->
                        if(i>0) Text("  —  ",style=MaterialTheme.typography.labelMedium,color=Muted)
                        val target=i-1
                        Text(crumb.name.uppercase(),Modifier.clickable(enabled=target>=0 && target<state.breadcrumbs.lastIndex) { onEvent(UiEvent.DriveBreadcrumb(target)) },style=MaterialTheme.typography.labelMedium.copy(letterSpacing=1.5.sp),color=if(target>=0 && target<state.breadcrumbs.lastIndex) Accent else Muted)
                    }
                }
                if(state.phase==Phase.Loading) CircularProgressIndicator(Modifier.padding(start=8.dp).size(18.dp),strokeWidth=2.dp,color=Accent)
                Box {
                    ActionIcon(Icons.Outlined.MoreVert,"雲端選項") { menu=true }
                    DropdownMenu(menu,{menu=false}) {
                        state.account?.let { DropdownMenuItem(text={Text(it,style=MaterialTheme.typography.bodySmall,color=Muted)},onClick={},enabled=false) }
                        DropdownMenuItem(text={Text("重新整理")},onClick={menu=false;onEvent(UiEvent.DriveRefresh)})
                        DropdownMenuItem(text={Text("下載中心")},onClick={menu=false;onDownloads()})
                        DropdownMenuItem(text={Text("更換音樂資料夾")},onClick={menu=false;onEvent(UiEvent.Setting("driveRootChosen","false"))})
                        DropdownMenuItem(text={Text("Drive 設定")},onClick={menu=false;openSettings()})
                    }
                }
            }
        }
        if(offline) item {
            Column(Modifier.fillMaxWidth().padding(top=16.dp)) {
                HorizontalDivider(color=Accent,thickness=2.dp)
                Row(Modifier.padding(top=14.dp),verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Outlined.ErrorOutline,null,Modifier.size(18.dp),tint=Accent); Text("雲端連線已中斷",Modifier.padding(start=8.dp),style=MaterialTheme.typography.titleSmall.copy(fontWeight=FontWeight.Bold),color=Accent) }
                Text("Google 登入已逾期或權限被撤銷。已下載的 ${state.everywhere.count {it.offlinePath!=null}} 首仍可離線播放；串流與新下載需重新登入。",Modifier.padding(vertical=8.dp),style=MaterialTheme.typography.bodySmall,color=Muted)
                OutlinedButton(onClick={onEvent(UiEvent.ConnectDrive)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(2.dp),border=BorderStroke(1.dp,Accent)) { Text("重新登入 Google 帳戶",color=Accent) }
            }
        } else if(state.phase==Phase.Error) item {
            Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically) { Text(state.error ?: "無法連線到 Google Drive",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error); TextButton(onClick={onEvent(UiEvent.DriveRefresh)}) {Text("重試",color=Accent)} }
        }
        item {
            // The download button sits beside the folder name; a long name pushes it onto the next line instead of overlapping.
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(state.breadcrumbs.last().name,Modifier.align(Alignment.CenterVertically).padding(end=12.dp),style=MaterialTheme.typography.displaySmall.copy(fontWeight=FontWeight.Bold),color=Ink,maxLines=2,overflow=TextOverflow.Ellipsis)
                InkButton(if(unsaved>0) "下載未儲存的 $unsaved 首" else "已全部下載",if(unsaved>0) Icons.Outlined.Download else Icons.Outlined.DownloadDone,Modifier.align(Alignment.CenterVertically),enabled=!offline && unsaved>0,compact=true) { onEvent(UiEvent.DownloadFolder) }
            }
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("${under.size} 首${if(folders.isNotEmpty()) " · ${folders.size} 個資料夾" else ""} · ${sizeLabel(under.sumOf {it.bytes})}",Modifier.weight(1f),style=SerifItalic.copy(fontSize=15.sp),color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
                // While signed out only downloaded songs can play, so shuffle just those.
                val shufflable=if(offline) under.filter {it.offlinePath!=null} else under
                Row(Modifier.clickable(enabled=shufflable.isNotEmpty()) { onEvent(UiEvent.PlayList(shufflable.shuffled().toPersistentList())) }.heightIn(min=48.dp).padding(start=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("隨機播放全部",style=MaterialTheme.typography.bodyMedium,color=if(shufflable.isNotEmpty()) Ink else Muted)
                    Icon(Icons.Outlined.Shuffle,null,Modifier.padding(start=6.dp).size(18.dp),tint=Accent)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top=4.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically) {
                Row(Modifier.weight(1f).heightIn(min=44.dp).border(1.dp,MaterialTheme.colorScheme.outlineVariant,RoundedCornerShape(2.dp)).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search,null,Modifier.size(18.dp),tint=Muted)
                    Box(Modifier.weight(1f).padding(start=8.dp)) {
                        if(state.query.isEmpty()) Text("搜尋此資料夾・歌名、歌手或專輯",style=MaterialTheme.typography.bodyMedium,color=Muted,maxLines=1)
                        BasicTextField(state.query,{onEvent(UiEvent.DriveQuery(it))},Modifier.fillMaxWidth(),singleLine=true,textStyle=MaterialTheme.typography.bodyMedium.copy(color=Ink),cursorBrush=SolidColor(Accent))
                    }
                    if(state.query.isNotEmpty()) ActionIcon(Icons.Outlined.Close,"清除搜尋") { onEvent(UiEvent.DriveQuery("")) }
                }
                Box {
                    ActionIcon(Icons.Outlined.SwapVert,"排序：${sortLabels[state.sort] ?: "名稱 A–Z"}") { sortMenu=true }
                    DropdownMenu(sortMenu,{sortMenu=false}) { sortLabels.forEach { (id,label) -> DropdownMenuItem(text={Text(label,color=if(id==state.sort) Accent else Ink)},onClick={sortMenu=false;onEvent(UiEvent.Setting("driveSort",id))}) } }
                }
            }
        }
        downloads.current?.let { cur -> item {
            val pct=if(cur.total>0) (cur.bytes*100/cur.total).toInt() else 0
            HorizontalDivider(Modifier.padding(top=8.dp),color=MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().clickable(onClick=onDownloads).heightIn(min=48.dp).padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("下載中",style=MaterialTheme.typography.labelLarge,color=Accent)
                Text("${cur.title} — $pct% · 第 ${downloads.success+downloads.failed+1}／${downloads.items.size} 首",Modifier.weight(1f).padding(start=14.dp),style=SerifItalic.copy(fontSize=14.sp),color=Ink,maxLines=1,overflow=TextOverflow.Ellipsis)
                Icon(Icons.Outlined.ChevronRight,null,tint=Ink)
            }
        } }
        if(folders.isNotEmpty()) item { HorizontalDivider(Modifier.padding(top=4.dp),color=MaterialTheme.colorScheme.outlineVariant) }
        items(folders,key={"dir:${it.id}"}) { folder ->
            Row(Modifier.fillMaxWidth().heightIn(min=52.dp).clickable { onEvent(UiEvent.DriveOpen(DriveFolder(folder.id,folder.name))) },verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.width(44.dp)) { Icon(Icons.Outlined.Folder,"資料夾",Modifier.size(20.dp),tint=Muted) }
                Text(folder.name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium.copy(fontFamily=androidx.compose.ui.text.font.FontFamily.Serif),color=Ink,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text("${index.stat(folder.id).tracks} 首 →",style=MaterialTheme.typography.bodySmall,color=Muted)
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        }
        if(listed.isNotEmpty()) item {
            HorizontalDivider(Modifier.padding(top=if(folders.isEmpty()) 8.dp else 16.dp),color=Ink)
            if(q.isEmpty() && direct.isEmpty() && folders.isNotEmpty()) Text("所有子資料夾的歌曲",Modifier.padding(top=12.dp),style=MaterialTheme.typography.labelMedium,color=Muted)
        }
        itemsIndexed(listed,key={_,t->t.id}) { i,t -> DriveTrackRow(i+1,t,album!=null,state.downloads[t.id],offline,onEvent,onMore) }
        if(listed.isEmpty() && state.phase!=Phase.Loading) item {
            EmptyPanel(if(q.isNotEmpty()) "找不到「${state.query}」" else "這裡沒有音訊檔",if(q.isNotEmpty()) "只搜尋目前資料夾及其子資料夾" else "可在右上角選單「更換音樂資料夾」，或把音樂放入此資料夾後重新整理。")
        }
    }
}
private val sortLabels=linkedMapOf("name" to "名稱 A–Z","nameDesc" to "名稱 Z–A","artist" to "歌手","size" to "大小，由大至小")

@Composable private fun DriveTrackRow(number: Int,t: Track,albumView: Boolean,job: DownloadItem?,offline: Boolean,onEvent: (UiEvent)->Unit,onMore: (Track)->Unit) {
    val saved=t.offlinePath!=null
    val playable=saved || !offline
    val downloading=job?.status==DownloadStatus.Downloading
    val pct=if(job!=null && job.total>0) (job.bytes*100/job.total).toInt() else 0
    Row(Modifier.fillMaxWidth().heightIn(min=88.dp).alpha(if(playable) 1f else .45f).combinedClickable(enabled=playable,onClick={onEvent(UiEvent.Play(t))},onLongClick={onMore(t)},onLongClickLabel="更多操作").padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
        if(albumView) Text("$number",Modifier.width(44.dp),style=SerifItalic.copy(fontSize=20.sp),color=Muted)
        else { Art(t,60.dp); Spacer(Modifier.width(16.dp)) }
        Column(Modifier.weight(1f)) {
            Text(t.title,style=MaterialTheme.typography.titleMedium.copy(fontFamily=androidx.compose.ui.text.font.FontFamily.Serif,fontSize=19.sp,fontWeight=FontWeight.SemiBold),color=Ink,maxLines=1,overflow=TextOverflow.Ellipsis)
            if(downloading) LinearProgressIndicator(progress={pct/100f},modifier=Modifier.fillMaxWidth(.85f).padding(top=10.dp).height(3.dp),color=Accent,trackColor=MaterialTheme.colorScheme.outlineVariant,drawStopIndicator={})
            else Text(listOfNotNull(if(albumView) null else t.artist,if(t.durationMs>0) formatTime(t.durationMs) else null,formatLabel(t.mime),if(!playable) "需重新登入" else null).joinToString(" · "),Modifier.padding(top=2.dp),style=MaterialTheme.typography.bodyMedium,color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        val (label,color)=when {
            downloading -> "$pct%" to Accent
            job?.status==DownloadStatus.Queued -> "◌ 等候" to Muted
            job?.status==DownloadStatus.Failed -> "✕ 失敗" to MaterialTheme.colorScheme.error
            saved && t.remoteVersion!=t.downloadedVersion -> "● 有更新" to Accent
            saved -> "● 已存" to Ink
            offline -> "— 離線" to Muted
            else -> "○ 串流" to MaterialTheme.colorScheme.tertiary
        }
        Text(label,Modifier.padding(start=12.dp),style=MaterialTheme.typography.labelLarge,color=color)
        ActionIcon(Icons.Outlined.MoreVert,"${t.title} 更多操作") { onMore(t) }
    }
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
}
