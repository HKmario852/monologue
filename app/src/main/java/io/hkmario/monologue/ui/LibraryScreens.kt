@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package io.hkmario.monologue.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.hkmario.monologue.domain.*
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Oxblood=Color(0xFF8B2E1F); private val Navy=Color(0xFF26324A); private val Mustard=Color(0xFFE0A43A)
private val Sand=Color(0xFFE8C88E); private val PaperArt=Color(0xFFFBF7EF); private val InkArt=Color(0xFF1F1B18); private val Mist=Color(0xFF9DB6C8)
private val SerifTitle @Composable get()=MaterialTheme.typography.titleMedium.copy(fontFamily=FontFamily.Serif,fontWeight=FontWeight.SemiBold)

/** Printed-sleeve style artwork for playlists and albums that carry no cover of their own. */
@Composable fun GeneratedCover(seed: String,label: String,modifier: Modifier=Modifier) {
    val variant=(seed.hashCode() and 0x7fffffff)%4
    Box(modifier.aspectRatio(1f).clip(RoundedCornerShape(2.dp)).background(when(variant) {0->Oxblood;1->Sand;2->Navy;else->PaperArt}).then(if(variant==3) Modifier.border(1.dp,InkArt,RoundedCornerShape(2.dp)) else Modifier)) {
        when(variant) {
            0 -> { Box(Modifier.fillMaxSize(.46f).align(Alignment.TopCenter).offset(y=14.dp).clip(CircleShape).background(PaperArt)); Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom=18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) { repeat(3) { HorizontalDivider(color=PaperArt.copy(alpha=.8f)) } } }
            1 -> Row(Modifier.fillMaxSize().padding(16.dp),horizontalArrangement=Arrangement.SpaceEvenly) { listOf(Oxblood to 0.dp,Oxblood to 22.dp,InkArt to (-8).dp).forEach { (c,y) -> Box(Modifier.width(22.dp).height(40.dp).offset(y=y+10.dp).clip(RoundedCornerShape(12.dp)).background(c)) } }
            2 -> { Text(label.take(2),Modifier.padding(14.dp),style=MaterialTheme.typography.headlineLarge.copy(fontWeight=FontWeight.Bold),color=PaperArt,maxLines=1); Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom=36.dp).height(5.dp).background(Mustard)) }
            else -> { Text(label,Modifier.padding(14.dp),style=MaterialTheme.typography.titleLarge.copy(fontStyle=FontStyle.Italic),color=InkArt,maxLines=3,overflow=TextOverflow.Ellipsis); Box(Modifier.align(Alignment.BottomEnd).padding(14.dp).size(28.dp).clip(CircleShape).background(Mist)) }
        }
    }
}
@Composable private fun AlbumCover(tracks: List<Track>,title: String,modifier: Modifier=Modifier) {
    Box(modifier.aspectRatio(1f)) {
        GeneratedCover(title,title,Modifier.fillMaxSize())
        tracks.firstOrNull {it.artwork!=null}?.artwork?.let { AsyncImage(it,null,Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)),contentScale=ContentScale.Crop) }
    }
}

/** A–Z for Latin, 日 for kana, 中 for Han characters, # for the rest. */
fun indexKey(name: String): String {
    val first=java.text.Normalizer.normalize(name.trim(),java.text.Normalizer.Form.NFKC).firstOrNull() ?: return "#"
    val base=java.text.Normalizer.normalize(first.toString(),java.text.Normalizer.Form.NFD).first().uppercaseChar()
    val script=runCatching {Character.UnicodeScript.of(first.code)}.getOrNull()
    return when {
        base in 'A'..'Z' -> base.toString()
        script==Character.UnicodeScript.HIRAGANA || script==Character.UnicodeScript.KATAKANA -> "日"
        script==Character.UnicodeScript.HAN -> "中"
        else -> "#"
    }
}
private val sectionOrder=('A'..'Z').map {it.toString()}+listOf("#","日","中")
private val strokeCollator by lazy { android.icu.text.Collator.getInstance(android.icu.util.ULocale("zh_Hant_TW@collation=stroke")) }
private val kanaCollator by lazy { android.icu.text.Collator.getInstance(android.icu.util.ULocale.JAPANESE) }
private fun sectionSort(section: String): Comparator<String> = when(section) { "中" -> Comparator {a,b->strokeCollator.compare(a,b)}; "日" -> Comparator {a,b->kanaCollator.compare(a,b)}; else -> compareBy {normalize(it)} }

private fun displayRoot(folder: String): Pair<String,String> = when {
    folder.startsWith("/storage/emulated/0") -> "內部儲存空間" to "/storage/emulated/0"
    folder.startsWith("/storage/") -> "SD 卡" to "/storage/"+folder.removePrefix("/storage/").substringBefore('/')
    else -> folder.substringBefore('/').ifBlank {"其他"} to folder.substringBefore('/')
}
/** "內部儲存空間/Music/Album" style path used for browsing local folders. */
private fun displayPath(folder: String): String { val (label,prefix)=displayRoot(folder); return (label+"/"+folder.removePrefix(prefix).trim('/')).trimEnd('/') }

@Composable fun LibraryScreen(state: LocalLibraryUiState,settings: AppSettingsUiState,onEvent: (UiEvent)->Unit,onGroup: (GroupItem)->Unit,onPlaylist: (String)->Unit,onMore: (Track)->Unit,discover: ()->Unit,openDrive: ()->Unit={},searchOnline: (String)->Unit={},playingId: String?=null,openSettings: ()->Unit={},openAppSettings: ()->Unit={}) {
    val search=state.search; val request=search.request; val searching=request.query.isNotBlank()
    val grid=rememberLazyGridState(); val scope=rememberCoroutineScope()
    val sectionIndex=remember { HashMap<String,Int>() }
    var create by rememberSaveable { mutableStateOf(false) }; var name by rememberSaveable { mutableStateOf("") }
    val local=state.tracks.count {it.source==Source.Local}; val cloud=state.tracks.size-local
    val artistSections=remember(search.groups,request.tab) {
        if(request.tab!=LibraryTab.Artists) emptyList() else search.groups.groupBy {indexKey(it.title)}.toList().sortedBy {sectionOrder.indexOf(it.first)}.map { (key,groups) -> key to groups.sortedWith(compareBy(sectionSort(key)) {it.title}) }
    }
    val showStrip=request.tab==LibraryTab.Artists && !searching && artistSections.size>1
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(GridCells.Adaptive(150.dp),Modifier.fillMaxSize(),state=grid,contentPadding=PaddingValues(start=24.dp,end=if(showStrip) 40.dp else 24.dp,bottom=24.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            var n=0
            fun full(key: Any,content: @Composable ()->Unit) { item(key=key,span={GridItemSpan(maxLineSpan)}) {content()}; n++ }
            full("head") { LibraryHeader(state,local,cloud,request,onEvent,openSettings,openAppSettings) }
            if(state.phase==Phase.Loading) full("scan") { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=8.dp),color=Accent) }
            if(searching && search.phase==Phase.Empty) {
                full("empty") {
                    Column {
                        EmptyPanel("找不到「${request.query}」","目前只搜尋媒體庫${request.tab.label}","清除搜尋") { onEvent(UiEvent.Query("")) }
                        FilledTonalButton(onClick={searchOnline(request.query)},modifier=Modifier.fillMaxWidth()) { Icon(Icons.Outlined.TravelExplore,null); Spacer(Modifier.width(8.dp)); Text("改為線上搜尋「${request.query}」") }
                    }
                }
            } else when(request.tab) {
                LibraryTab.Tracks -> {
                    if(state.tracks.isEmpty() && !searching && state.phase!=Phase.Loading) full("start") { StartPanel(state.phase==Phase.PermissionRequired,onEvent,openDrive,searchOnline) }
                    else {
                        if(!searching) {
                            full("playlists") { PlaylistShelf(state,{create=true},onPlaylist) }
                            full("order") { SongOrderBar(settings,state.tracks,onEvent) }
                        }
                        val rows=if(searching) search.tracks else state.tracks
                        items(rows,key={it.id},span={GridItemSpan(maxLineSpan)}) { t -> LibraryTrackRow(t,t.id==playingId,{onEvent(UiEvent.Play(t))},{onMore(t)}) }
                        n+=rows.size
                    }
                }
                LibraryTab.Artists -> {
                    if(searching) { items(search.groups,key={it.id},span={GridItemSpan(maxLineSpan)}) { g -> ArtistRow(g) {onGroup(g)} }; n+=search.groups.size }
                    else {
                        sectionIndex.clear()
                        for((key,groups) in artistSections) {
                            sectionIndex[key]=n
                            full("section:$key") { SectionLetter(key) }
                            items(groups,key={it.id},span={GridItemSpan(maxLineSpan)}) { g -> ArtistRow(g) {onGroup(g)} }; n+=groups.size
                        }
                        if(artistSections.isEmpty() && state.phase!=Phase.Loading && search.phase!=Phase.Loading) full("none") { EmptyPanel("未有歌手","加入本機或雲端音樂後會在這裡顯示") }
                    }
                }
                LibraryTab.Albums -> {
                    val sort=settings.text("groupSort.Albums","name")
                    val albums=search.groups.let { g -> when(sort) {"count"->g.sortedByDescending {it.tracks.size};"artist"->g.sortedBy {normalize(it.tracks.firstOrNull()?.artist ?: "")};else->g.sortedBy {normalize(it.title)}} }
                    full("albumbar") { AlbumBar(albums.size,sort,onEvent) }
                    items(albums,key={it.id}) { g -> AlbumCard(g) {onGroup(g)} }
                    if(albums.isEmpty() && state.phase!=Phase.Loading && search.phase!=Phase.Loading) full("none") { EmptyPanel("未有專輯","加入本機或雲端音樂後會在這裡顯示") }
                }
                LibraryTab.Folders -> {
                    if(searching) items(search.groups.filter {!it.title.startsWith("drive") && (it.title.startsWith("/") || it.title.startsWith("授權"))},key={it.id},span={GridItemSpan(maxLineSpan)}) { g ->
                        FolderRow(Icons.Outlined.Folder,g.title.substringAfterLast('/'),"${displayPath(g.title)} · ${g.tracks.size} 首") {onGroup(g)}
                    } else full("folders") { FolderBrowser(state,settings,onEvent,onGroup,onMore,openDrive,playingId) }
                }
            }
        }
        if(showStrip) LetterStrip(artistSections.map {it.first},Modifier.align(Alignment.CenterEnd).padding(end=6.dp)) { key -> sectionIndex[key]?.let { scope.launch { grid.scrollToItem(it) } } }
    }
    if(create) AlertDialog(onDismissRequest={create=false},title={Text("新增歌單")},text={OutlinedTextField(name,{name=it},label={Text("名稱")},singleLine=true)},confirmButton={TextButton(onClick={onEvent(UiEvent.PlaylistCreate(name));name="";create=false},enabled=name.isNotBlank()) {Text("建立")}},dismissButton={TextButton(onClick={create=false}) {Text("取消")}})
}

@Composable private fun LibraryHeader(state: LocalLibraryUiState,local: Int,cloud: Int,request: SearchRequest,onEvent: (UiEvent)->Unit,openSettings: ()->Unit,openAppSettings: ()->Unit) {
    var menu by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Row(Modifier.weight(1f),verticalAlignment=Alignment.Bottom) {
                Text("媒體庫",style=MaterialTheme.typography.displaySmall.copy(fontWeight=FontWeight.Bold),color=Ink)
                Text(when { local>0 && cloud>0 -> "本機 $local · 雲端 $cloud 首"; cloud>0 -> "雲端 · $cloud 首"; else -> "本機 · $local 首" },Modifier.padding(start=12.dp,bottom=6.dp),style=SerifItalic.copy(fontSize=15.sp),color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            ActionIcon(Icons.Outlined.Settings,"設定",action=openAppSettings)
            Box {
                ActionIcon(Icons.Outlined.MoreVert,"媒體庫選項") { menu=true }
                DropdownMenu(menu,{menu=false}) {
                    DropdownMenuItem(text={Text("重新掃描本機音樂")},onClick={menu=false;onEvent(UiEvent.Scan)})
                    DropdownMenuItem(text={Text("授權音樂資料夾")},onClick={menu=false;onEvent(UiEvent.PickFolder)})
                    DropdownMenuItem(text={Text("媒體庫設定")},onClick={menu=false;openSettings()})
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top=16.dp).heightIn(min=48.dp).drawBehind { drawLine(Color(0xFF2E2722).copy(alpha=.9f),Offset(0f,size.height),Offset(size.width,size.height),1.dp.toPx()) },verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search,null,Modifier.size(20.dp),tint=Ink)
            Box(Modifier.weight(1f).padding(start=10.dp)) {
                if(request.query.isEmpty()) Text("在${request.tab.label}中搜尋歌曲、歌手、專輯",style=SerifItalic.copy(fontSize=15.sp),color=Muted,maxLines=1)
                BasicTextField(request.query,{onEvent(UiEvent.Query(it))},Modifier.fillMaxWidth(),singleLine=true,textStyle=MaterialTheme.typography.bodyLarge.copy(color=Ink),cursorBrush=SolidColor(Accent),keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={onEvent(UiEvent.SubmitSearch)}))
            }
            if(request.query.isNotEmpty()) ActionIcon(Icons.Outlined.Close,"清除搜尋") { onEvent(UiEvent.Query("")) }
        }
        Row(Modifier.fillMaxWidth().padding(top=16.dp),horizontalArrangement=Arrangement.spacedBy(28.dp)) {
            LibraryTab.entries.forEach { tab ->
                val active=tab==request.tab
                Column(Modifier.clickable {onEvent(UiEvent.Tab(tab))}.heightIn(min=44.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Bottom) {
                    Text(tab.label,style=MaterialTheme.typography.titleMedium.copy(fontFamily=FontFamily.Serif,fontWeight=if(active) FontWeight.Bold else FontWeight.Normal),color=if(active) Ink else Muted)
                    Box(Modifier.padding(top=8.dp).width(32.dp).height(2.dp).background(if(active) Accent else Color.Transparent))
                }
            }
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable private fun PlaylistShelf(state: LocalLibraryUiState,create: ()->Unit,open: (String)->Unit) {
    Column(Modifier.padding(top=16.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("你的歌單",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,color=Muted)
            Text("＋ 新增歌單",Modifier.clickable(onClick=create).padding(vertical=8.dp),style=SerifItalic.copy(fontSize=13.sp),color=Accent)
        }
        LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.padding(top=4.dp)) {
            item {
                PlaylistTile("最愛",state.tracks.count {it.favorite},{open("favorites")}) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)).background(Oxblood)) { Icon(Icons.Outlined.Favorite,null,Modifier.align(Alignment.BottomStart).padding(12.dp).size(28.dp),tint=PaperArt) }
                }
            }
            items(state.playlists,key={it.id}) { p -> PlaylistTile(p.name,p.tracks.size,{open(p.id)}) { GeneratedCover(p.id+p.name,p.name,Modifier.fillMaxSize()) } }
        }
        HorizontalDivider(Modifier.padding(top=16.dp),color=Ink)
    }
}
@Composable private fun PlaylistTile(name: String,count: Int,click: ()->Unit,cover: @Composable ()->Unit) {
    Column(Modifier.width(120.dp).clickable(onClick=click)) {
        Box(Modifier.size(120.dp)) { cover() }
        Text(name,Modifier.padding(top=8.dp),style=MaterialTheme.typography.titleSmall.copy(fontFamily=FontFamily.Serif),color=Ink,maxLines=1,overflow=TextOverflow.Ellipsis)
        Text("$count 首",style=SerifItalic.copy(fontSize=12.sp),color=Muted)
    }
}
/** Sort options shared by the 媒體庫 menus and 設定 › 媒體庫, so both always offer the same choices under the same names. */
val songSorts=linkedMapOf("title" to "依標題排列","artist" to "依歌手排列","duration" to "依長度排列（由長至短）")
val albumSorts=linkedMapOf("name" to "依專輯名稱","artist" to "依歌手","count" to "依歌曲數")
@Composable private fun SongOrderBar(settings: AppSettingsUiState,tracks: List<Track>,onEvent: (UiEvent)->Unit) {
    var menu by remember { mutableStateOf(false) }
    val sorts=songSorts
    Row(Modifier.fillMaxWidth().padding(top=12.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Text("${sorts[settings.text("sort","title")] ?: "依標題排列"} ⌄",Modifier.clickable {menu=true}.padding(vertical=8.dp),style=SerifItalic.copy(fontSize=13.sp),color=Muted)
            DropdownMenu(menu,{menu=false}) { sorts.forEach { (id,label) -> DropdownMenuItem(text={Text(label)},onClick={menu=false;onEvent(UiEvent.Setting("sort",id))}) } }
        }
        Row(Modifier.clickable(enabled=tracks.isNotEmpty()) {onEvent(UiEvent.PlayList(tracks.shuffled().toPersistentList()))}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("隨機播放全部",style=MaterialTheme.typography.bodyMedium,color=Ink)
            Icon(Icons.Outlined.Shuffle,null,Modifier.padding(start=6.dp).size(18.dp),tint=Accent)
        }
    }
}
@Composable fun LibraryTrackRow(t: Track,playing: Boolean,play: ()->Unit,more: ()->Unit) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min=88.dp).clickable(enabled=t.uri.isNotBlank(),onClick=play).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(60.dp)) {
                GeneratedCover(t.album+t.artist,t.album,Modifier.fillMaxSize())
                t.artwork?.let { AsyncImage(it,null,Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)),contentScale=ContentScale.Crop) }
            }
            Column(Modifier.weight(1f).padding(start=16.dp)) {
                Text(t.title,style=SerifTitle.copy(fontSize=19.sp),color=if(playing) Accent else Ink,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(listOfNotNull(t.artist,if(t.durationMs>0) formatTime(t.durationMs) else null,if(t.source==Source.Drive && t.offlinePath==null) "雲端" else null).joinToString(" · "),Modifier.padding(top=2.dp),style=SerifItalic.copy(fontSize=14.sp),color=if(playing) Accent else Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            ActionIcon(Icons.Outlined.MoreVert,"${t.title} 更多操作",action=more)
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
    }
}
@Composable private fun SectionLetter(key: String) {
    Column(Modifier.padding(top=20.dp)) {
        Row(verticalAlignment=Alignment.Bottom) {
            Text(key,style=MaterialTheme.typography.displaySmall.copy(fontStyle=FontStyle.Italic,fontWeight=FontWeight.Bold),color=Accent)
            if(key=="中") Text("中文 · 依筆畫",Modifier.padding(start=12.dp,bottom=6.dp),style=SerifItalic.copy(fontSize=12.sp),color=Muted)
            if(key=="日") Text("日文假名",Modifier.padding(start=12.dp,bottom=6.dp),style=SerifItalic.copy(fontSize=12.sp),color=Muted)
        }
        HorizontalDivider(color=Ink)
    }
}
@Composable private fun ArtistRow(g: GroupItem,click: ()->Unit) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min=64.dp).clickable(onClick=click).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape)) {
                GeneratedCover(g.title,g.title,Modifier.fillMaxSize())
                g.tracks.firstOrNull {it.artwork!=null}?.artwork?.let { AsyncImage(it,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop) }
            }
            Column(Modifier.weight(1f).padding(start=14.dp)) {
                Text(g.title,style=SerifTitle,color=Ink,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text("${g.tracks.map {it.album}.distinct().size} 張專輯 · ${g.tracks.size} 首",style=SerifItalic.copy(fontSize=12.sp),color=Muted)
            }
            Icon(Icons.Outlined.ChevronRight,null,tint=Muted)
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
    }
}
@Composable private fun LetterStrip(keys: List<String>,modifier: Modifier,jump: (String)->Unit) {
    // Every key must fit the visible height, so 日 and 中 at the end are never cut off in landscape.
    BoxWithConstraints(modifier.fillMaxHeight().padding(top=8.dp,bottom=20.dp),contentAlignment=Alignment.Center) {
        val slot=(maxHeight/keys.size.coerceAtLeast(1)).coerceAtMost(22.dp)
        val size=with(androidx.compose.ui.platform.LocalDensity.current) { (slot*.72f).toSp() }
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            keys.forEach { k -> Box(Modifier.height(slot).clickable {jump(k)}.padding(horizontal=6.dp),contentAlignment=Alignment.Center) { Text(k,style=MaterialTheme.typography.labelSmall.copy(fontFamily=FontFamily.Serif,fontSize=size,lineHeight=size),color=if(k=="中" || k=="日") Accent else Muted) } }
        }
    }
}
@Composable private fun AlbumBar(count: Int,sort: String,onEvent: (UiEvent)->Unit) {
    var menu by remember { mutableStateOf(false) }
    val sorts=albumSorts
    Row(Modifier.fillMaxWidth().padding(top=12.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
        Text("$count 張 · ${sorts[sort] ?: "依專輯名稱"}",Modifier.weight(1f),style=SerifItalic.copy(fontSize=13.sp),color=Muted)
        Box {
            Text("排序 ⌄",Modifier.clickable {menu=true}.padding(8.dp),style=MaterialTheme.typography.bodyMedium,color=Ink)
            DropdownMenu(menu,{menu=false}) { sorts.forEach { (id,label) -> DropdownMenuItem(text={Text(label)},onClick={menu=false;onEvent(UiEvent.Setting("groupSort.Albums",id))}) } }
        }
    }
}
@Composable private fun AlbumCard(g: GroupItem,click: ()->Unit) {
    Column(Modifier.padding(bottom=20.dp).clickable(onClick=click)) {
        AlbumCover(g.tracks,g.title,Modifier.fillMaxWidth())
        Text(g.title,Modifier.padding(top=8.dp),style=MaterialTheme.typography.titleSmall.copy(fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold),color=Ink,maxLines=2,overflow=TextOverflow.Ellipsis)
        Text("${g.tracks.map {it.artist}.distinct().let { if(it.size==1) it[0] else "多位歌手" }} · ${g.tracks.size} 首",style=SerifItalic.copy(fontSize=12.sp),color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}
@Composable private fun FolderRow(icon: androidx.compose.ui.graphics.vector.ImageVector,title: String,detail: String,highlight: Boolean=false,dim: Boolean=false,trailing: (@Composable ()->Unit)?=null,onLongClick: (()->Unit)?=null,click: ()->Unit) {
    Column(Modifier.background(if(highlight) MaterialTheme.colorScheme.surface else Color.Transparent)) {
        Row(Modifier.fillMaxWidth().heightIn(min=64.dp).alpha(if(dim) .5f else 1f).combinedClickable(enabled=!dim,onClick=click,onLongClick=onLongClick).padding(horizontal=if(highlight) 8.dp else 0.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(icon,null,Modifier.size(22.dp),tint=if(highlight) Accent else Muted)
            Column(Modifier.weight(1f).padding(start=16.dp)) {
                Text(title,style=SerifTitle,color=Ink,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(detail,style=SerifItalic.copy(fontSize=12.sp),color=if(highlight) Accent else Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            if(trailing!=null) trailing() else if(!dim) Icon(Icons.Outlined.ChevronRight,null,tint=Muted)
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
    }
}

/** 資料夾: phone storage from the music folder down, with the cloud library and downloads pinned on top. */
@Composable private fun FolderBrowser(state: LocalLibraryUiState,settings: AppSettingsUiState,onEvent: (UiEvent)->Unit,onGroup: (GroupItem)->Unit,onMore: (Track)->Unit,openDrive: ()->Unit,playingId: String?) {
    val local=remember(state.tracks) { state.tracks.filter {it.source==Source.Local}.map { displayPath(it.folder) to it } }
    val roots=remember(state.tracks) { state.tracks.filter {it.source==Source.Local}.associate { val (label,prefix)=displayRoot(it.folder); label to prefix } }
    val start=remember(local) {
        local.map {it.first.split('/')}.reduceOrNull { a,b -> a.zip(b).takeWhile {(x,y)->x==y}.map {it.first} }?.joinToString("/").orEmpty()
    }
    var here by rememberSaveable(start) { mutableStateOf(start) }
    var exclude by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val hidden=settings.text("hiddenFolders").split('\n').filter {it.isNotBlank()}
    val below=local.filter {it.first==here || it.first.startsWith("$here/")}
    val children=below.mapNotNull { (p,_) -> p.removePrefix(here).trim('/').substringBefore('/').takeIf {it.isNotEmpty() && p!=here} }.distinct().sortedBy {normalize(it)}
    fun absolute(display: String): String { val label=display.substringBefore('/'); return (roots[label] ?: "")+"/"+display.substringAfter('/',"") }
    Column {
        if(state.phase==Phase.PermissionRequired) FolderRow(Icons.Outlined.PhoneAndroid,"允許讀取本機音樂","授權後掃描手機內的音訊檔",highlight=true) { onEvent(UiEvent.RequestAudioPermission) }
        if(here.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top=16.dp,bottom=8.dp).horizontalScroll(rememberScrollState())) {
            val parts=here.split('/')
            parts.forEachIndexed { i,part ->
                if(i>0) Text(" / ",style=MaterialTheme.typography.labelMedium,color=Muted)
                val target=parts.take(i+1).joinToString("/")
                Text(part,Modifier.clickable(enabled=target!=here) {here=target},style=MaterialTheme.typography.labelMedium.copy(fontFamily=FontFamily.Serif,letterSpacing=1.sp),color=if(target==here) Ink else Accent)
            }
        }
        HorizontalDivider(color=Ink)
        if(here==start) {
            val cloud=state.tracks.filter {it.source==Source.Drive}
            if(cloud.isNotEmpty()) FolderRow(Icons.Outlined.Cloud,"雲端硬碟 · ${settings.text("driveRootName").ifBlank {"音樂"}}","${cloud.size} 首 · 串流或已下載",highlight=true,click=openDrive)
            val saved=state.tracks.filter {it.offlinePath!=null}
            if(saved.isNotEmpty()) FolderRow(Icons.Outlined.DownloadDone,"離線下載","已下載到手機的歌曲 · ${saved.size} 首",highlight=true) { onGroup(GroupItem("Offline:","離線下載",saved.toPersistentList())) }
        }
        children.forEach { child ->
            val path=if(here.isEmpty()) child else "$here/$child"
            val inside=local.filter {it.first==path || it.first.startsWith("$path/")}
            val subfolders=inside.mapNotNull {(p,_)->p.removePrefix(path).trim('/').substringBefore('/').takeIf {it.isNotEmpty()}}.distinct().size
            FolderRow(Icons.Outlined.Folder,child,if(subfolders>0) "$subfolders 個資料夾 · ${inside.size} 首" else "${inside.size} 首",trailing={
                Box {
                    ActionIcon(Icons.Outlined.MoreVert,"$child 更多操作") { menuFor=path }
                    DropdownMenu(menuFor==path,{menuFor=null}) { DropdownMenuItem(text={Text("不計入媒體庫")},leadingIcon={Icon(Icons.Outlined.FolderOff,null)},onClick={menuFor=null;exclude=path}) }
                }
            },onLongClick={exclude=path}) { here=path }
        }
        local.filter {it.first==here}.map {it.second}.sortedBy {normalize(it.title)}.forEach { t -> LibraryTrackRow(t,t.id==playingId,{onEvent(UiEvent.Play(t))},{onMore(t)}) }
        hidden.forEach { h ->
            FolderRow(Icons.Outlined.FolderOff,h.trimEnd('/').substringAfterLast('/').ifBlank {h},"已排除，不計入媒體庫",dim=true,trailing={ Text("加回",Modifier.clickable { onEvent(UiEvent.Setting("hiddenFolders",hidden.filter {it!=h}.joinToString("\n")));onEvent(UiEvent.Scan) }.padding(8.dp),style=MaterialTheme.typography.bodyMedium.copy(textDecoration=androidx.compose.ui.text.style.TextDecoration.Underline),color=Accent) }) {}
        }
        if(local.isEmpty() && state.phase!=Phase.PermissionRequired) Text("手機內未找到音樂。可用上方 ⋮ 選單「授權音樂資料夾」加入其他位置。",Modifier.padding(vertical=16.dp),style=MaterialTheme.typography.bodySmall,color=Muted)
        Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically) {
            val last=settings.text("lastScan").toLongOrNull()?.let { ms -> val t=Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()); if(t.toLocalDate()==LocalDate.now()) "今日 "+t.format(DateTimeFormatter.ofPattern("HH:mm")) else t.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")) } ?: "尚未掃描"
            Text("上次掃描 · $last",Modifier.weight(1f),style=SerifItalic.copy(fontSize=12.sp),color=Muted)
            Row(Modifier.clickable {onEvent(UiEvent.Scan)}.padding(8.dp),verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Outlined.Refresh,null,Modifier.size(16.dp),tint=Ink); Text("重新掃描",Modifier.padding(start=6.dp),style=MaterialTheme.typography.bodyMedium,color=Ink) }
        }
    }
    exclude?.let { path -> AlertDialog(onDismissRequest={exclude=null},title={Text("不計入媒體庫？")},text={Text("「${path.substringAfterLast('/')}」內的歌曲會從媒體庫移除，檔案本身不會被刪除。之後可在此頁「加回」。")},confirmButton={TextButton(onClick={ onEvent(UiEvent.Setting("hiddenFolders",(hidden+absolute(path)).joinToString("\n"))); onEvent(UiEvent.Scan); exclude=null }) {Text("排除")}},dismissButton={TextButton(onClick={exclude=null}) {Text("取消")}}) }
}

/** A weekly recommendation: release cover, what will actually play, and a way to pick another version. */
@Composable fun RecommendationRow(r: Recommendation,resolving: Boolean,busy: Boolean,play: ()->Unit,chooseVersion: ()->Unit) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min=88.dp).clickable(enabled=!busy,onClick=play).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(60.dp)) {
                GeneratedCover(r.title+r.artist,r.title,Modifier.fillMaxSize())
                (r.artwork ?: r.match?.artwork)?.let { AsyncImage(it,null,Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)),contentScale=ContentScale.Crop) }
            }
            Column(Modifier.weight(1f).padding(horizontal=16.dp)) {
                Text(r.title,style=SerifTitle.copy(fontSize=19.sp),color=Ink,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(r.artist,style=SerifItalic.copy(fontSize=14.sp),color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(if(r.match!=null) "● 媒體庫已有此歌曲" else "○ 由 YouTube 播放・自動配對，版本可能不同",style=MaterialTheme.typography.labelSmall,color=if(r.match!=null) Ink else MaterialTheme.colorScheme.tertiary,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            if(r.match==null) Text("選版本",Modifier.clickable(onClick=chooseVersion).padding(8.dp),style=MaterialTheme.typography.labelMedium,color=Accent)
            if(resolving) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp),strokeWidth=2.dp,color=Accent)
            else ActionIcon(Icons.Outlined.PlayArrow,"播放 ${r.title}",!busy,play)
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
    }
}
