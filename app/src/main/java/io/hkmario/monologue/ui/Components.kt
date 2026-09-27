@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.hkmario.monologue.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import io.hkmario.monologue.domain.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable fun ActionIcon(icon: ImageVector, description: String, enabled: Boolean=true, action: ()->Unit) {
    IconButton(onClick=action,enabled=enabled,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) { Icon(icon,description) }
}
@Composable fun Art(track: Track?, size: Dp=52.dp, modifier: Modifier=Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainer),contentAlignment=Alignment.Center) {
        Icon(Icons.Outlined.MusicNote,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(size/2))
        if(track?.artwork!=null) AsyncImage(track.artwork,null,Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
    }
}
@Composable fun EmptyPanel(title: String, detail: String, action: String?=null, onClick: ()->Unit={}) {
    Column(Modifier.fillMaxWidth().padding(vertical=32.dp,horizontal=24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.Album,null,Modifier.size(44.dp),tint=MaterialTheme.colorScheme.primary)
        Text(title,style=MaterialTheme.typography.titleLarge)
        Text(detail,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
        if(action!=null) OutlinedButton(onClick=onClick) { Text(action) }
    }
}
@Composable fun SectionTitle(title: String, action: String?=null, onAction: ()->Unit={}) {
    Row(Modifier.fillMaxWidth().padding(top=20.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) { Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge); if(action!=null) TextButton(onClick=onAction) { Text(action) } }
}
@Composable fun TrackRow(track: Track, subtitle: String="${track.artist} · ${formatTime(track.durationMs)}", onPlay: ()->Unit, onMore: ()->Unit, leading: String?=null) {
    Row(Modifier.fillMaxWidth().heightIn(min=88.dp).clickable(enabled=track.uri.isNotBlank(),onClick=onPlay).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        if(leading!=null) Text(leading,Modifier.widthIn(min=24.dp),style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary)
        Art(track,60.dp)
        Column(Modifier.weight(1f)) { Text(track.title,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleMedium.copy(fontSize=18.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)); Text(subtitle,Modifier.padding(top=2.dp),maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        ActionIcon(Icons.Outlined.MoreVert,"${track.title} 更多操作",action=onMore)
    }
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.65f))
}
fun formatTime(ms: Long): String { val sec=ms.coerceAtLeast(0)/1000; return "%d:%02d".format(sec/60,sec%60) }
fun formatBytes(bytes: Long)="%.1f MB".format(bytes/1_000_000.0)

@Composable fun MiniPlayer(player: NowPlayingUiState, progress: State<PlaybackProgress>, onEvent: (UiEvent)->Unit, onExpand: ()->Unit, onQueue: ()->Unit) {
    val entry=player.entry ?: return
    Surface(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp),shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surface,tonalElevation=1.dp,shadowElevation=2.dp,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min=68.dp).padding(start=8.dp,end=4.dp),verticalAlignment=Alignment.CenterVertically) {
                Row(Modifier.weight(1f).heightIn(min=60.dp).semantics { contentDescription="${entry.track.title}，${entry.track.artist}"; customActions=listOf(CustomAccessibilityAction("上一首") { onEvent(UiEvent.Previous); true },CustomAccessibilityAction("下一首") { onEvent(UiEvent.Next); true }) }.clickable(onClick=onExpand).pointerInput(entry.id) {
                    var x=0f; var y=0f
                    detectDragGestures(onDragStart={x=0f;y=0f},onDragEnd={ when { y < -50 && abs(y)>abs(x) -> onExpand(); x < -70 -> onEvent(UiEvent.Next); x>70 -> onEvent(UiEvent.Previous) } }) { change,delta -> x+=delta.x;y+=delta.y;change.consume() }
                },verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Art(entry.track,46.dp)
                    Column(Modifier.weight(1f)) { Text(entry.track.title,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleMedium); Text(entry.track.artist,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                ActionIcon(if(player.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,if(player.isPlaying) "暫停" else "播放") { onEvent(UiEvent.TogglePlay) }
                ActionIcon(Icons.Outlined.QueueMusic,"播放隊列",action=onQueue)
            }
            MiniProgress(player,progress)
        }
    }
}
@Composable private fun MiniProgress(player: NowPlayingUiState, progress: State<PlaybackProgress>) {
    if(player.buffering) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
    else LinearProgressIndicator(progress={ if(player.durationMs>0) (progress.value.positionMs.toFloat()/player.durationMs).coerceIn(0f,1f) else 0f },modifier=Modifier.fillMaxWidth().height(2.dp),trackColor=MaterialTheme.colorScheme.surfaceContainer)
}

@Composable fun Vinyl(clock: VinylClock, playing: Boolean, visible: Boolean, allowed: Boolean, track: Track?, modifier: Modifier=Modifier) {
    val angle=remember { mutableFloatStateOf(clock.angle(System.nanoTime()).toFloat()) }
    LaunchedEffect(playing,visible,allowed) {
        clock.configure(System.nanoTime(),playing,visible,allowed)
        angle.floatValue=clock.angle(System.nanoTime()).toFloat()
        if(playing && visible && allowed) while(true) withFrameNanos { angle.floatValue=clock.angle(System.nanoTime()).toFloat() }
    }
    DisposableEffect(clock) { onDispose { clock.configure(System.nanoTime(),visible=false) } }
    Box(modifier.aspectRatio(1f).semantics { contentDescription="黑膠唱片，${track?.title ?: "沒有歌曲"}，${if(playing) "播放中" else "已暫停"}" }) {
        Box(Modifier.fillMaxSize().padding(10.dp).graphicsLayer { rotationZ=angle.floatValue }.clip(CircleShape).background(Color(0xFF171717)),contentAlignment=Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val radius=size.minDimension/2
                drawCircle(Color(0xFF363636),radius-1,style=Stroke(2f))
                for(i in 0..55) drawCircle(Color(if(i%4==0) 0xFF313131 else 0xFF232323),radius*(0.44f+i/103f),style=Stroke(if(i%5==0) 1.6f else 0.8f))
                drawArc(Color(0xFF444444).copy(alpha=.3f),220f,45f,false,Offset(radius*.14f,radius*.14f),androidx.compose.ui.geometry.Size(radius*1.72f,radius*1.72f),style=Stroke(radius*.15f))
            }
            Box(Modifier.fillMaxSize(.39f).clip(CircleShape).background(Color(0xFFA74932)),contentAlignment=Alignment.Center) {
                Text("m",color=Color(0xFFF5EFE4),style=MaterialTheme.typography.displayLarge)
                if(track?.artwork!=null) AsyncImage(track.artwork,null,Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF171717)))
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val w=size.width; val h=size.height
            drawCircle(Color(0xFF45403B),w*.058f,Offset(w*.94f,h*.1f))
            drawCircle(Color(0xFF938778),w*.035f,Offset(w*.94f,h*.1f),style=Stroke(w*.006f))
            drawLine(Color(0xFF302D29),Offset(w*.94f,h*.10f),Offset(w*.86f,h*.65f),w*.032f,StrokeCap.Round)
            drawLine(Color(0xFFB39A80),Offset(w*.94f,h*.10f),Offset(w*.86f,h*.65f),w*.012f,StrokeCap.Round)
            drawLine(Color(0xFF39332E),Offset(w*.86f,h*.65f),Offset(w*.79f,h*.75f),w*.045f,StrokeCap.Round)
            drawLine(Color(0xFFA74932),Offset(w*.80f,h*.73f),Offset(w*.78f,h*.76f),w*.018f,StrokeCap.Round)
        }
    }
}

@Composable fun QueueSheet(state: PlaybackQueueUiState, onEvent: (UiEvent)->Unit, close: ()->Unit) {
    ModalBottomSheet(onDismissRequest=close) {
        val list=rememberLazyListState(); val scope=rememberCoroutineScope(); val currentState by rememberUpdatedState(state)
        var viewport by remember {mutableStateOf(Rect.Zero)}
        var draggedId by remember {mutableStateOf<String?>(null)}
        var edge by remember {mutableIntStateOf(0)}
        val edgePixels=with(LocalDensity.current) {56.dp.toPx()}
        LaunchedEffect(draggedId,edge) {
            while(draggedId!=null && edge!=0) {
                val from=currentState.entries.indexOfFirst {it.id==draggedId}
                if(from>=0) {
                    val to=(from+edge).coerceIn(0,currentState.entries.lastIndex)
                    if(to!=from) onEvent(UiEvent.QueueMove(draggedId!!,to))
                    list.scrollBy(edge*edgePixels/2)
                }
                delay(120)
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal=24.dp).navigationBarsPadding()) {
            SectionTitle("播放隊列","清除待播") { onEvent(UiEvent.QueueClear) }
            if(state.entries.isEmpty()) EmptyPanel("隊列是空的","在媒體庫或搜尋選擇歌曲開始聆聽")
            LazyColumn(state=list,modifier=Modifier.fillMaxWidth().heightIn(max=520.dp).onGloballyPositioned {viewport=it.boundsInRoot()}) {
                itemsIndexed(state.entries,key={_,entry->entry.id}) { index,entry ->
                    var handleCoordinates by remember(entry.id) {mutableStateOf<LayoutCoordinates?>(null)}
                    Row(Modifier.fillMaxWidth().background(if(entry.id==state.currentId) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,RoundedCornerShape(12.dp)).semantics {
                        customActions=listOf(CustomAccessibilityAction("上移") { onEvent(UiEvent.QueueMove(entry.id,index-1)); true },CustomAccessibilityAction("下移") { onEvent(UiEvent.QueueMove(entry.id,index+1)); true },CustomAccessibilityAction("移除") { onEvent(UiEvent.QueueRemove(entry.id)); true })
                    }.pointerInput(entry.id) {
                        var dx=0f
                        detectHorizontalDragGestures(onDragStart={dx=0f},onDragEnd={if(dx < -100) onEvent(UiEvent.QueueRemove(entry.id))}) { change,delta -> dx+=delta; change.consume() }
                    }.padding(4.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { onEvent(UiEvent.QueuePlay(entry.id)) }.padding(8.dp)) { Text(entry.track.title,maxLines=1,overflow=TextOverflow.Ellipsis); Text(if(entry.removeAfterPlaying) "本曲結束後移除" else if(entry.id==state.currentId) "正在播放" else entry.track.artist,style=MaterialTheme.typography.bodySmall) }
                        ActionIcon(Icons.Outlined.Close,"移除 ${entry.track.title}") { onEvent(UiEvent.QueueRemove(entry.id)) }
                        Icon(Icons.Outlined.DragHandle,"長按拖曳排序",Modifier.size(48.dp).onGloballyPositioned {handleCoordinates=it}.pointerInput(entry.id) {
                            var distance=0f
                            detectDragGesturesAfterLongPress(onDragStart={distance=0f;draggedId=entry.id},onDragEnd={distance=0f;draggedId=null;edge=0},onDragCancel={distance=0f;draggedId=null;edge=0}) { change,delta ->
                                change.consume(); distance+=delta.y
                                val y=handleCoordinates?.takeIf {it.isAttached}?.localToRoot(change.position)?.y
                                edge=when {y==null->0;y<viewport.top+edgePixels->-1;y>viewport.bottom-edgePixels->1;else->0}
                                val from=currentState.entries.indexOfFirst { it.id==entry.id }
                                if(edge==0 && from>=0 && abs(distance)>60.dp.toPx()) { val to=(from+if(distance>0) 1 else -1).coerceIn(0,currentState.entries.lastIndex); if(to!=from) { onEvent(UiEvent.QueueMove(entry.id,to)); scope.launch { list.animateScrollToItem(to) } }; distance=0f }
                            }
                        }.padding(12.dp))
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
