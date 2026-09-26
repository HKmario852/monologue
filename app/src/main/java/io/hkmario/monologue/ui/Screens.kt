@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.hkmario.monologue.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import io.hkmario.monologue.domain.Period
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import io.hkmario.monologue.domain.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter

@Composable private fun SourceOption(icon: androidx.compose.ui.graphics.vector.ImageVector,title: String,detail: String,click: ()->Unit) {
    Surface(onClick=click,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.heightIn(min=72.dp).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Icon(icon,null,Modifier.size(28.dp),tint=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) { Text(title,style=MaterialTheme.typography.titleMedium); Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Outlined.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
/** First-run entry: the three places music can come from, instead of a single permission prompt. */
@Composable fun StartPanel(permissionRequired: Boolean,onEvent: (UiEvent)->Unit,openDrive: ()->Unit,searchOnline: (String)->Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("你的音樂在哪裡？",style=MaterialTheme.typography.headlineSmall)
        Text("選一個開始；之後隨時可以再加入其他來源。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        SourceOption(Icons.Outlined.PhoneAndroid,"掃描本機音樂",if(permissionRequired) "允許讀取音訊檔；本機音樂不會上傳" else "未找到歌曲？選擇存放音樂的資料夾") { onEvent(if(permissionRequired) UiEvent.RequestAudioPermission else UiEvent.PickFolder) }
        SourceOption(Icons.Outlined.Cloud,"連接 Google Drive","串流或下載雲端硬碟內的音樂",openDrive)
        SourceOption(Icons.Outlined.TravelExplore,"搜尋線上音樂","YouTube 音訊・MusicBrainz 歌曲資料") { searchOnline("") }
    }
}
@Composable fun NowPlayingScreen(state: AppUiState, progress: State<PlaybackProgress>, clock: VinylClock, visible: Boolean, onEvent: (UiEvent)->Unit, collapse: ()->Unit, queue: ()->Unit, equalizer: ()->Unit, sleep: ()->Unit, more: ()->Unit, importLyrics: (Boolean)->Unit) {
    val player=state.player; val track=player.entry?.track
    var lyrics by rememberSaveable { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }; var dragging by remember { mutableStateOf(false) }
    val displayDrag by animateFloatAsState(if(dragging) drag else 0f,spring(),label="collapse")
    val threshold=with(LocalDensity.current) { 120.dp.toPx() }
    val dismissGesture=Modifier.pointerInput(Unit) {
        val velocity=VelocityTracker()
        detectVerticalDragGestures(onDragStart={dragging=true;drag=0f;velocity.resetTracking()},onDragCancel={dragging=false;drag=0f},onDragEnd={val speed=velocity.calculateVelocity().y; if(drag>threshold || drag>threshold/3 && speed>1200) collapse(); dragging=false;drag=0f}) { change,amount -> if(amount>0 || drag>0) { drag=(drag+amount).coerceAtLeast(0f); velocity.addPosition(change.uptimeMillis,change.position); change.consume() } }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().graphicsLayer { translationY=displayDrag }) {
        val landscape=maxWidth>maxHeight
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().then(dismissGesture).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                ActionIcon(Icons.Outlined.KeyboardArrowDown,"收合正在播放",action=collapse)
                Text("正在播放",Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center,style=MaterialTheme.typography.titleMedium)
                ActionIcon(Icons.Outlined.MoreVert,"歌曲更多操作",enabled=track!=null,action=more)
            }
            val cover: @Composable (Modifier)->Unit = { modifier ->
                BoxWithConstraints(modifier,contentAlignment=Alignment.Center) {
                    val discSide=minOf(maxWidth,maxHeight)
                    if(lyrics) LyricsPanel(state.lyrics,progress,state.settings,onEvent)
                    else Box(Modifier.size(discSide).then(dismissGesture).clickable { lyrics=true }.padding(vertical=8.dp)) {
                        Vinyl(clock,player.isPlaying,visible && !lyrics,state.settings.bool("vinyl",true) && !state.settings.bool("reduceMotion"),track,Modifier.fillMaxWidth())
                    }
                }
            }
            val controls: @Composable (Modifier)->Unit = { modifier ->
                Column(modifier.padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(track?.title ?: "未有播放歌曲",style=MaterialTheme.typography.headlineMedium,maxLines=2,overflow=TextOverflow.Ellipsis); Text(track?.artist ?: "從媒體庫開始聆聽",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                        ActionIcon(if(track?.favorite==true) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,"收藏歌曲",track!=null) { track?.let { onEvent(UiEvent.Favorite(it)) } }
                    }
                    state.player.audioFormat?.let {Text(it,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    track?.let { Text(when { it.offlinePath!=null -> "✓ 已下載"; it.source==Source.Online -> "音訊來源：${providerLabel(it.folder)}"; it.source==Source.Drive -> "☁ 線上串流"; else -> "本機音樂" },style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.tertiary,modifier=Modifier.padding(vertical=4.dp)) }
                    if(player.buffering) Text("正在緩衝…",style=MaterialTheme.typography.bodySmall)
                    player.error?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
                    Scrubber(player,progress,onEvent)
                    Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                        IconToggleButton(player.shuffle,{onEvent(UiEvent.Shuffle)}) { Icon(Icons.Outlined.Shuffle,"隨機播放",tint=if(player.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                        ActionIcon(Icons.Outlined.SkipPrevious,"上一首",track!=null) {onEvent(UiEvent.Previous)}
                        FilledIconButton(onClick={onEvent(UiEvent.TogglePlay)},enabled=track!=null,modifier=Modifier.size(76.dp),shape=CircleShape) { Icon(if(player.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,if(player.isPlaying) "暫停" else "播放",Modifier.size(36.dp)) }
                        ActionIcon(Icons.Outlined.SkipNext,"下一首",track!=null) {onEvent(UiEvent.Next)}
                        IconButton({onEvent(UiEvent.Repeat)}) { Icon(if(player.repeat==1) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat,"循環：${when(player.repeat){1->"單曲";2->"全部";else->"關閉"}}",tint=if(player.repeat!=0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top=8.dp,bottom=12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                        ToolButton(Icons.Outlined.Equalizer,"音效",equalizer)
                        ToolButton(Icons.Outlined.Bedtime,"睡眠",sleep)
                        ToolButton(if(lyrics) Icons.Outlined.Album else Icons.Outlined.Lyrics,if(lyrics) "封面" else "歌詞") {lyrics=!lyrics}
                        ToolButton(Icons.Outlined.QueueMusic,"隊列",queue)
                    }
                    if(lyrics && state.lyrics.lines.isEmpty()) OutlinedButton(onClick={importLyrics(false)},modifier=Modifier.fillMaxWidth()) {Text("匯入本機 LRC")}
                }
            }
            if(landscape) Row(Modifier.weight(1f)) { cover(Modifier.weight(1f).fillMaxHeight().padding(16.dp)); controls(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) }
            else Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { cover(Modifier.fillMaxWidth().heightIn(max=380.dp).padding(horizontal=16.dp)); controls(Modifier.fillMaxWidth()) }
        }
    }
}
@Composable private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector,text: String,click: ()->Unit) { Column(Modifier.widthIn(min=56.dp).clickable(onClick=click).padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally) {Icon(icon,text); Text(text,style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(top=4.dp))} }
@Composable private fun Scrubber(player: NowPlayingUiState, progress: State<PlaybackProgress>, onEvent: (UiEvent)->Unit) {
    val current=progress.value; val shown=current.seekPreview ?: current.positionMs
    Column {
        Slider(shown.toFloat().coerceIn(0f,player.durationMs.coerceAtLeast(1).toFloat()),{onEvent(UiEvent.PreviewSeek(it.toLong()))},enabled=player.seekable && player.durationMs>0,onValueChangeFinished={onEvent(UiEvent.CommitSeek)},valueRange=0f..player.durationMs.coerceAtLeast(1).toFloat(),thumb={Box(Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))},track={SliderDefaults.Track(sliderState=it,modifier=Modifier.height(3.dp),thumbTrackGapSize=0.dp,drawStopIndicator=null)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).semantics { contentDescription="播放進度" })
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text(formatTime(shown),style=TimeStyle);Text(if(player.durationMs>0) "−${formatTime(player.durationMs-shown)}" else "未知長度",style=TimeStyle)}
    }
}
@Composable fun LyricsPanel(state: LyricsUiState, progress: State<PlaybackProgress>, settings: AppSettingsUiState, onEvent: (UiEvent)->Unit) {
    val list=rememberLazyListState(); var manual by remember(state.trackId) { mutableStateOf(false) }; var autoScrolling by remember {mutableStateOf(false)}
    val position=progress.value.positionMs-settings.number("lyricOffset",0f).toLong()-settings.number(lyricOffsetKey(state.trackId),0f).toLong()
    val active=state.lines.indexOfLast { it.timeMs!=null && it.timeMs<=position }
    LaunchedEffect(list) { snapshotFlow { list.isScrollInProgress }.collect { if(it && !autoScrolling) manual=true } }
    LaunchedEffect(active,manual,settings.bool("autoLyrics",true)) { if(active>=0 && !manual && settings.bool("autoLyrics",true)) { autoScrolling=true; try { list.animateScrollToItem(active) } finally {autoScrolling=false} } }
    Column(Modifier.fillMaxWidth().height(338.dp).padding(horizontal=24.dp)) {
        if(state.lines.isEmpty()) EmptyPanel("未有歌詞",state.error ?: state.source)
        else LazyColumn(state=list,modifier=Modifier.weight(1f),contentPadding=PaddingValues(vertical=24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            itemsIndexed(state.lines,key={_,line->line.id}) { index,line ->
                Column(Modifier.fillMaxWidth().clickable(enabled=line.timeMs!=null) { onEvent(UiEvent.PreviewSeek(line.timeMs));onEvent(UiEvent.CommitSeek) }.padding(vertical=6.dp)) {
                    Text(line.text,fontSize=settings.number("lyricSize",22f).sp,color=if(index==active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=if(index==active) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal)
                    line.translation?.let { Text(it,fontSize=(settings.number("lyricSize",22f)*.72f).sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=6.dp)) }
                }
            }
        }
        if(manual) TextButton(onClick={manual=false}) {Text("返回目前歌詞")}
        Text(state.source,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun DownloadSheet(state: DownloadManagerUiState,onEvent: (UiEvent)->Unit,close: ()->Unit) {
    ModalBottomSheet(onDismissRequest=close) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=600.dp).navigationBarsPadding(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { Text("下載佇列",style=MaterialTheme.typography.headlineSmall);Text("成功 ${state.success} / ${state.items.size} 首 · 已處理 ${state.success+state.failed} · 失敗 ${state.failed} · 待下載 ${state.pending}",style=MaterialTheme.typography.bodySmall) }
            item { if(state.items.isNotEmpty()) LinearProgressIndicator(progress={(state.success+state.failed).toFloat()/state.items.size},modifier=Modifier.fillMaxWidth()) }
            state.current?.let { current -> item { Text(current.title); if(current.total>0) { LinearProgressIndicator(progress={(current.bytes.toFloat()/current.total).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth());Text("${current.bytes*100/current.total}%",style=TimeStyle) } else LinearProgressIndicator(Modifier.fillMaxWidth()) } }
            item { Row(verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)) {Text("歌曲完成後暫停");Text(if(state.pauseRequested) "完成目前歌曲後暫停" else "每處理一首後等待手動繼續",style=MaterialTheme.typography.bodySmall)};Switch(state.pauseBetween,{onEvent(UiEvent.PauseBetween(it))})} }
            if(state.phase==DownloadPhase.Waiting) item { Text("已暫停，等待你繼續",color=MaterialTheme.colorScheme.primary);Button(onClick={onEvent(UiEvent.ContinueDownloads)}) {Text("繼續下載")} }
            if(state.phase==DownloadPhase.Complete) item {Text(if(state.failed>0) "下載已結束，${state.failed} 首失敗" else "全部項目已處理")}
            if(state.failed>0) item {OutlinedButton(onClick={onEvent(UiEvent.RetryDownloads)}) {Text("重試所有失敗項目（${state.failed}）")}}
            items(state.items.filter {it.status==DownloadStatus.Failed},key={it.id}) { item -> Text("${item.title}\n${item.error}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.error) }
            if(state.items.isEmpty()) item {EmptyPanel("未有下載工作","從 Google Drive 選擇歌曲或資料夾")}
            if(state.pending>0 || state.current!=null || state.phase==DownloadPhase.Waiting) item {TextButton(onClick={onEvent(UiEvent.CancelDownloads)}) {Text("取消全部未完成工作")}}
        }
    }
}

@Composable fun RankScreen(state: LeaderboardUiState,onEvent: (UiEvent)->Unit,onMore: (Track)->Unit) {
    val labels=listOf("週榜","月榜","總榜")
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp)) {
        item { TabRow(state.period.ordinal,containerColor=Color.Transparent) { Period.entries.forEachIndexed { i,p -> Tab(p==state.period,{onEvent(UiEvent.Leaderboard(p,byTime=state.sortByTime))},text={Text(labels[i])}) } } }
        if(state.period!=Period.All) item {
            val zone=ZoneId.of(state.zone);val format=DateTimeFormatter.ofPattern("yyyy/MM/dd")
            Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {ActionIcon(Icons.Outlined.ChevronLeft,"上一期") {onEvent(UiEvent.Leaderboard(state.period,state.offset-1,state.sortByTime))};Text("${Instant.ofEpochMilli(state.startMs).atZone(zone).format(format)}\n— ${Instant.ofEpochMilli(state.endExclusiveMs-1).atZone(zone).format(format)}",style=MaterialTheme.typography.bodyMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center);ActionIcon(Icons.Outlined.ChevronRight,"下一期",state.offset<0) {onEvent(UiEvent.Leaderboard(state.period,state.offset+1,state.sortByTime))}}
        }
        item {Row(Modifier.fillMaxWidth().padding(vertical=24.dp),horizontalArrangement=Arrangement.SpaceBetween) {Column {Text("%.1f 小時".format(state.hours),style=MaterialTheme.typography.headlineLarge,color=MaterialTheme.colorScheme.primary);Text("總聆聽時數",style=MaterialTheme.typography.bodySmall)};Column {Text("${state.count} 次",style=MaterialTheme.typography.headlineLarge,color=MaterialTheme.colorScheme.primary);Text("總播放次數",style=MaterialTheme.typography.bodySmall)}}}
        item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {FilterChip(!state.sortByTime,{onEvent(UiEvent.Leaderboard(state.period,state.offset,false))},label={Text("播放次數")});FilterChip(state.sortByTime,{onEvent(UiEvent.Leaderboard(state.period,state.offset,true))},label={Text("聆聽時間")})}}
        if(state.rows.isEmpty()) item {EmptyPanel("呢個週期未有紀錄","聆聽達 30 秒或曲長一半後計一次；暫停與緩衝唔會計入。")}
        itemsIndexed(state.rows,key={_,it->it.track.id}) { i,row ->
            Surface(Modifier.fillMaxWidth().padding(top=8.dp),shape=RoundedCornerShape(12.dp),color=if(i==0) MaterialTheme.colorScheme.surface else Color.Transparent,border=if(i==0) BorderStroke(1.dp,MaterialTheme.colorScheme.primary) else null) {Box(Modifier.padding(horizontal=if(i==0) 12.dp else 0.dp)) {TrackRow(row.track,"${row.count} 次播放 · ${row.listenedMs/60000} 分鐘",{onEvent(UiEvent.Play(row.track))},{onMore(row.track)},"${i+1}${if(i<3) " ·" else ""}")}}
        }
        item {Text("統計時區：${state.zone}",style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(top=20.dp))}
    }
}

@Composable fun DiscoverScreen(account: ListenBrainzUiState,state: DiscoverUiState,onEvent: (UiEvent)->Unit,stats: ListeningStatsUiState = ListeningStatsUiState(),openAccount: ()->Unit = {},openListening: ()->Unit = {},openSupport: ()->Unit = {},openOnline: ()->Unit = {},openRank: ()->Unit = {}) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {
            Surface(onClick=openOnline,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.heightIn(min=56.dp).padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.TravelExplore,null,tint=MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {Text("搜尋線上音樂",style=MaterialTheme.typography.titleMedium);Text("YouTube・MusicBrainz，不搜尋本機",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                }
            }
        }
        item {SectionTitle("你的聆聽足跡","完整排行",openRank)}
        item {StatCard("${stats.all.sumOf {it.listenedMs}/60000} 分鐘","累計聆聽 · ${stats.all.sumOf {it.count}} 次播放",openListening)}
        item {StatCard(supportRange(stats.month.sumOf {it.count}),"本月支持金額 · 查看歌手明細",openSupport,badge="假設估算，非實際收益")}
        item {SectionTitle("每週推薦","更新") {onEvent(UiEvent.Recommendations)}}
        if(account.connection!=Connection.Connected) item {SettingAction("連接 ListenBrainz","到設定管理帳號，取得個人推薦",openAccount)}
        if(state.phase==Phase.Loading) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
        state.generated?.let {item {Text("生成日期：$it",style=MaterialTheme.typography.bodySmall)}}
        if(state.tracks.isEmpty()) item {EmptyPanel("等候新的發現",state.error ?: "尚未有推薦時不會加入示範歌曲。")}
        items(state.tracks,key={it.id}) { r -> RecommendationRow(r,state.resolving==r.id,state.resolving!=null,{onEvent(UiEvent.PlayRecommendation(r))}) { onEvent(UiEvent.Online(OnlineAction.SearchFor("${r.title} ${r.artist}")));openOnline() } }
    }
}
