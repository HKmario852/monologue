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
    var fullLyrics by rememberSaveable { mutableStateOf(false) }
    var askOnlineLyrics by rememberSaveable { mutableStateOf(false) }; var askNetEase by rememberSaveable { mutableStateOf(false) }
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
                    if(lyrics) LyricsPanel(state.lyrics,progress,state.settings,onEvent,onFullScreen={fullLyrics=true})
                    else Box(Modifier.size(discSide).then(dismissGesture).clickable { lyrics=true }.padding(vertical=8.dp)) {
                        Vinyl(clock,player.isPlaying,visible && !lyrics,state.settings.bool("vinyl",true) && !state.settings.bool("reduceMotion"),track,Modifier.fillMaxWidth())
                    }
                }
            }
            val controls: @Composable (Modifier)->Unit = { modifier ->
                Column(modifier.padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        // The lyrics replace the record, so keep the cover in sight next to the title.
                        if(lyrics) Art(track,64.dp,Modifier.padding(end=12.dp))
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
                    // No lyrics: offer the online lookup right here instead of only a file import; it still asks before sending anything.
                    if(lyrics && state.lyrics.lines.isEmpty()) Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        if(!state.settings.bool("onlineLyrics")) Button(onClick={askOnlineLyrics=true},modifier=Modifier.fillMaxWidth()) {Text("搜尋線上歌詞")}
                        else if(state.lyrics.phase!=Phase.Loading) Button(onClick={onEvent(UiEvent.RetryLyrics)},modifier=Modifier.fillMaxWidth()) {Text("再搜尋一次")}
                        if(state.settings.bool("onlineLyrics") && lyricsProviders(state.settings).none { it.info.id=="netease" && it.enabled } && state.lyrics.phase!=Phase.Loading) OutlinedButton(onClick={askNetEase=true},modifier=Modifier.fillMaxWidth()) {Text("也搜尋網易雲音樂（非官方）")}
                        OutlinedButton(onClick={importLyrics(false)},modifier=Modifier.fillMaxWidth()) {Text("匯入 LRC 歌詞檔")}
                    }
                }
            }
            if(landscape) Row(Modifier.weight(1f)) { cover(Modifier.weight(1f).fillMaxHeight().padding(16.dp)); controls(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) }
            else Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { cover(Modifier.fillMaxWidth().heightIn(max=380.dp).padding(horizontal=16.dp)); controls(Modifier.fillMaxWidth()) }
        }
    }
    if(fullLyrics) FullScreenLyrics(state,progress,onEvent) {fullLyrics=false}
    if(askNetEase) AlertDialog(onDismissRequest={askNetEase=false},title={Text("開啟網易雲音樂歌詞？")},
        text={Text("網易雲音樂沒有公開的官方 API，這裡用的是它網頁播放器使用的非官方介面：可能隨時失效，也不符合網易雲的服務條款。開啟後會把目前歌曲的歌名和歌手傳送到網易雲音樂（中國大陸的服務）。它通常有中文翻譯和羅馬拼音。要開啟嗎？")},
        confirmButton={TextButton(onClick={askNetEase=false;onEvent(UiEvent.SetLyricsProvider("netease",true))}) {Text("開啟並搜尋")}},
        dismissButton={TextButton(onClick={askNetEase=false}) {Text("取消")}})
    if(askOnlineLyrics) AlertDialog(onDismissRequest={askOnlineLyrics=false},title={Text("搜尋線上歌詞？")},
        text={Text("會把目前歌曲的歌名和歌手傳送到歌詞服務（LRCLIB）；歌手名稱寫法不同時，也會向 MusicBrainz 查詢歌手的其他寫法。不會上傳音訊或整個媒體庫。之後可在「設定 › 歌詞」關閉。")},
        confirmButton={TextButton(onClick={askOnlineLyrics=false;onEvent(UiEvent.Setting("onlineLyrics","true"))}) {Text("開始搜尋")}},
        dismissButton={TextButton(onClick={askOnlineLyrics=false}) {Text("取消")}})
}
/** Lyrics over the whole screen with larger text, the cover and title on top and play controls below; the screen stays on. */
@Composable private fun FullScreenLyrics(state: AppUiState, progress: State<PlaybackProgress>, onEvent: (UiEvent)->Unit, close: ()->Unit) {
    val track=state.player.entry?.track
    androidx.compose.ui.window.Dialog(onDismissRequest=close,properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val view=androidx.compose.ui.platform.LocalView.current
        DisposableEffect(view) {
            view.keepScreenOn=true
            // No dimmed strip over the status bar: this dialog is a screen of its own, not a popup.
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setDimAmount(0f)
            onDispose { view.keepScreenOn=false }
        }
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(start=24.dp,end=12.dp,top=12.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Art(track,56.dp)
                    Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
                        Text(track?.title ?: "未有播放歌曲",style=MaterialTheme.typography.titleLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(track?.artist ?: "",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                    ActionIcon(Icons.Outlined.FullscreenExit,"結束全螢幕歌詞",action=close)
                }
                LyricsPanel(state.lyrics,progress,state.settings,onEvent,modifier=Modifier.fillMaxWidth().weight(1f),textScale=1.3f)
                Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(24.dp,Alignment.CenterHorizontally),verticalAlignment=Alignment.CenterVertically) {
                    ActionIcon(Icons.Outlined.SkipPrevious,"上一首",track!=null) {onEvent(UiEvent.Previous)}
                    FilledIconButton(onClick={onEvent(UiEvent.TogglePlay)},enabled=track!=null,modifier=Modifier.size(64.dp),shape=CircleShape) { Icon(if(state.player.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,if(state.player.isPlaying) "暫停" else "播放",Modifier.size(32.dp)) }
                    ActionIcon(Icons.Outlined.SkipNext,"下一首",track!=null) {onEvent(UiEvent.Next)}
                }
            }
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
@Composable fun LyricsPanel(state: LyricsUiState, progress: State<PlaybackProgress>, settings: AppSettingsUiState, onEvent: (UiEvent)->Unit,
                             modifier: Modifier=Modifier.fillMaxWidth().height(338.dp), textScale: Float=1f, onFullScreen: (()->Unit)?=null) {
    val list=rememberLazyListState(); var manual by remember(state.trackId) { mutableStateOf(false) }; var autoScrolling by remember {mutableStateOf(false)}
    val position=progress.value.positionMs-settings.number("lyricOffset",0f).toLong()-settings.number(lyricOffsetKey(state.trackId),0f).toLong()
    // With 隱藏括號內的和聲 on, bracketed backing vocals such as "(In this night)" are left out, and lines that are only that disappear.
    val hideBracketed=settings.bool("hideBracketedVocals")
    val shownLines=remember(state.lines,hideBracketed) { if(!hideBracketed) state.lines else state.lines.mapNotNull { l -> withoutBracketedVocals(l.text).takeIf { it.isNotBlank() }?.let { l.copy(text=it,romaji=l.romaji?.let(::withoutBracketedVocals),translation=l.translation?.let(::withoutBracketedVocals)) } } }
    val active=shownLines.indexOfLast { it.timeMs!=null && it.timeMs<=position }
    LaunchedEffect(list) { snapshotFlow { list.isScrollInProgress }.collect { if(it && !autoScrolling) manual=true } }
    LaunchedEffect(active,manual,settings.bool("autoLyrics",true)) { if(active>=0 && !manual && settings.bool("autoLyrics",true)) { autoScrolling=true; try { list.animateScrollToItem(active) } finally {autoScrolling=false} } }
    val translations=settings.bool("translations"); val display=settings.text("lyricsDisplay","both")
    // 原文＋羅馬拼音 stays selected for songs without romaji: they show the original and say why no romaji appears.
    val mode=when { display=="romaji" -> "romaji"; !translations -> "original"; else -> display }
    // Romaji above each line in the other views, when turned on in 設定 › 歌詞.
    val romajiAbove=settings.bool("showRomaji") && state.romajiAvailable && mode!="romaji"
    Column(modifier.padding(horizontal=24.dp)) {
        if(state.lines.isNotEmpty()) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
          Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("original" to "原文","romaji" to "原文＋羅馬拼音","both" to "原文＋翻譯","translation" to "翻譯").forEach { (id,label) ->
                FilterChip(mode==id,{ when(id) {
                    "original" -> { if(display=="romaji") onEvent(UiEvent.Setting("lyricsDisplay","both")); onEvent(UiEvent.Setting("translations","false")) }
                    // Translations are not shown with romaji, so the on-device translator need not run.
                    "romaji" -> { onEvent(UiEvent.Setting("lyricsDisplay","romaji")); onEvent(UiEvent.Setting("translations","false")) }
                    else -> { onEvent(UiEvent.Setting("lyricsDisplay",id)); onEvent(UiEvent.Setting("translations","true")) }
                } },label={Text(label)})
            }
          }
          onFullScreen?.let { ActionIcon(Icons.Outlined.Fullscreen,"全螢幕歌詞",action=it) }
        }
        if(state.lines.isNotEmpty()) {
            val romajiNote=when {
                mode=="romaji" && state.romajiLoading -> "正在產生羅馬拼音…"
                mode=="romaji" && !state.romajiAvailable ->
                    if(!hasJapaneseScript(state.lines.joinToString("\n") { it.text })) "羅馬拼音只適用於日文歌詞"
                    else if(!settings.bool("generateRomaji",true)) "這首歌的歌詞來源沒有提供羅馬拼音；可在 設定 › 歌詞 開啟「自動產生羅馬拼音」"
                    else "未能產生羅馬拼音"
                state.romajiGenerated && (mode=="romaji" || romajiAbove) -> "羅馬拼音由裝置自動產生，漢字讀音可能有誤"
                else -> null
            }
            romajiNote?.let { Text(it,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.tertiary,modifier=Modifier.padding(top=6.dp)) }
        }
        state.translationSource?.takeIf { translations && (it.startsWith("正在翻譯") || it.startsWith("翻譯未完成")) }?.let { Text(it,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.tertiary,modifier=Modifier.padding(top=6.dp)) }
        if(state.lines.isEmpty() && state.phase==Phase.Loading) EmptyPanel("正在搜尋歌詞…",state.source.removePrefix("正在查詢"))
        else if(state.lines.isEmpty()) EmptyPanel("未有歌詞",state.error ?: "這首歌沒有本機或已儲存的歌詞")
        else LazyColumn(state=list,modifier=Modifier.weight(1f),contentPadding=PaddingValues(vertical=24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            itemsIndexed(shownLines,key={_,line->line.id}) { index,line ->
                Column(Modifier.fillMaxWidth().clickable(enabled=line.timeMs!=null) { onEvent(UiEvent.PreviewSeek(line.timeMs));onEvent(UiEvent.CommitSeek) }.padding(vertical=6.dp)) {
                    val size=settings.number("lyricSize",22f)*textScale
                    if(romajiAbove) line.romaji?.let { Text(it,fontSize=(size*.6f).sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=4.dp)) }
                    val main=if(mode=="translation") line.translation ?: line.text else line.text
                    Text(main,fontSize=size.sp,color=if(index==active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=if(index==active) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal)
                    if(mode=="romaji") line.romaji?.let { Text(it,fontSize=(size*.72f).sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=6.dp)) }
                    if(mode=="both") line.translation?.let { Text(it,fontSize=(size*.72f).sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=6.dp)) }
                }
            }
        }
        if(manual) TextButton(onClick={manual=false}) {Text("返回目前歌詞")}
        // The source line names where shown lyrics came from; with none shown the empty panel already says so.
        if(state.lines.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top=6.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(state.source,Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2)
            // Lyrics found online can be searched again, e.g. after turning on another source.
            if(!state.source.startsWith("使用者") && !state.source.startsWith("本機")) TextButton(onClick={onEvent(UiEvent.RefetchLyrics)}) {Text("重新搜尋")}
        }
    }
}

@Composable fun DownloadSheet(state: DownloadManagerUiState,onEvent: (UiEvent)->Unit,close: ()->Unit) {
    ModalBottomSheet(onDismissRequest=close) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=600.dp).navigationBarsPadding(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { Text("下載佇列",style=MaterialTheme.typography.headlineSmall);Text("已完成 ${state.success}／${state.items.size} 首 · 失敗 ${state.failed} · 等候 ${state.pending}",style=MaterialTheme.typography.bodySmall) }
            item { if(state.items.isNotEmpty()) LinearProgressIndicator(progress={(state.success+state.failed).toFloat()/state.items.size},modifier=Modifier.fillMaxWidth()) }
            state.current?.let { current -> item { Text(current.title); if(current.total>0) { LinearProgressIndicator(progress={(current.bytes.toFloat()/current.total).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth());Text("${current.bytes*100/current.total}%",style=TimeStyle) } else LinearProgressIndicator(Modifier.fillMaxWidth()) } }
            item { Row(verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)) {Text("每首完成後暫停");Text(if(state.pauseRequested) "目前這首完成後會暫停" else "每下載完一首先停下，等你按繼續",style=MaterialTheme.typography.bodySmall)};Switch(state.pauseBetween,{onEvent(UiEvent.PauseBetween(it))})} }
            if(state.phase==DownloadPhase.Waiting) item { Text("已暫停，等待你繼續",color=MaterialTheme.colorScheme.primary);Button(onClick={onEvent(UiEvent.ContinueDownloads)}) {Text("繼續下載")} }
            if(state.phase==DownloadPhase.Complete) item {Text(if(state.failed>0) "下載已結束，${state.failed} 首失敗" else "全部項目已處理")}
            if(state.failed>0) item {OutlinedButton(onClick={onEvent(UiEvent.RetryDownloads)}) {Text("重試所有失敗項目（${state.failed}）")}}
            items(state.items.filter {it.status==DownloadStatus.Failed},key={it.id}) { item -> Text("${item.title}\n${item.error}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.error) }
            if(state.items.isEmpty()) item {EmptyPanel("未有下載工作","在 Google Drive 或搜尋結果選擇歌曲下載")}
            if(state.pending>0 || state.current!=null || state.phase==DownloadPhase.Waiting) item {TextButton(onClick={onEvent(UiEvent.CancelDownloads)}) {Text("取消全部未完成工作")}}
        }
    }
}

/** Shared by every period switcher, so 聆聽回顧 and 支持金額 always use the same words. */
val periodLabels=listOf("本週","本月","全部")

/** 聆聽回顧: ranking, listening totals and the support estimate under one period switcher. */
@Composable fun RecapScreen(state: LeaderboardUiState,onEvent: (UiEvent)->Unit,onMore: (Track)->Unit,openSupport: ()->Unit={}) {
    // One switcher drives both the ranking and the statistics behind 支持金額, so they never show different periods.
    fun period(p: Period,offset: Int=0,byTime: Boolean=state.sortByTime) { onEvent(UiEvent.Leaderboard(p,offset,byTime)); onEvent(UiEvent.Statistics(p,offset)) }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp)) {
        item { TabRow(state.period.ordinal,containerColor=Color.Transparent) { Period.entries.forEachIndexed { i,p -> Tab(p==state.period,{period(p)},text={Text(periodLabels[i])}) } } }
        if(state.period!=Period.All) item {
            val zone=ZoneId.of(state.zone);val format=DateTimeFormatter.ofPattern("yyyy/MM/dd")
            Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {ActionIcon(Icons.Outlined.ChevronLeft,"上一期") {period(state.period,state.offset-1)};Text("${Instant.ofEpochMilli(state.startMs).atZone(zone).format(format)}\n— ${Instant.ofEpochMilli(state.endExclusiveMs-1).atZone(zone).format(format)}",style=MaterialTheme.typography.bodyMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center);ActionIcon(Icons.Outlined.ChevronRight,"下一期",state.offset<0) {period(state.period,state.offset+1)}}
        }
        item {Row(Modifier.fillMaxWidth().padding(vertical=24.dp),horizontalArrangement=Arrangement.SpaceBetween) {Column {Text("%.1f 小時".format(state.hours),style=MaterialTheme.typography.headlineLarge,color=MaterialTheme.colorScheme.primary);Text("聆聽時數",style=MaterialTheme.typography.bodySmall)};Column {Text("${state.count} 次",style=MaterialTheme.typography.headlineLarge,color=MaterialTheme.colorScheme.primary);Text("播放次數",style=MaterialTheme.typography.bodySmall)}}}
        item {Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {FilterChip(!state.sortByTime,{onEvent(UiEvent.Leaderboard(state.period,state.offset,false))},label={Text("按播放次數")});FilterChip(state.sortByTime,{onEvent(UiEvent.Leaderboard(state.period,state.offset,true))},label={Text("按聆聽時間")})}}
        if(state.rows.isEmpty()) item {EmptyPanel("這段期間未有紀錄","一首歌聽滿 30 秒（短歌則一半長度）才計一次播放；暫停與緩衝不計算在內。")}
        itemsIndexed(state.rows,key={_,it->it.track.id}) { i,row ->
            Surface(Modifier.fillMaxWidth().padding(top=8.dp),shape=RoundedCornerShape(12.dp),color=if(i==0) MaterialTheme.colorScheme.surface else Color.Transparent,border=if(i==0) BorderStroke(1.dp,MaterialTheme.colorScheme.primary) else null) {Box(Modifier.padding(horizontal=if(i==0) 12.dp else 0.dp)) {TrackRow(row.track,"${row.count} 次播放 · ${row.listenedMs/60000} 分鐘",{onEvent(UiEvent.Play(row.track))},{onMore(row.track)},"${i+1}${if(i<3) " ·" else ""}")}}
        }
        item {SectionTitle("支持歌手")}
        item {StatCard(supportRange(state.count),"按這段期間的播放次數估算 · 查看歌手明細",openSupport,badge="假設估算，非實際收益")}
        item {Text("統計時區：${state.zone}",style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(top=20.dp))}
    }
}

@Composable fun DiscoverScreen(account: ListenBrainzUiState,state: DiscoverUiState,onEvent: (UiEvent)->Unit,stats: ListeningStatsUiState = ListeningStatsUiState(),openAccount: ()->Unit = {},openRecap: ()->Unit = {},chooseVersion: (Recommendation)->Unit = {}) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=24.dp,end=24.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {SectionTitle("你的聆聽足跡","聆聽回顧",openRecap)}
        item {StatCard("${stats.all.sumOf {it.listenedMs}/60000} 分鐘","累計聆聽 · ${stats.all.sumOf {it.count}} 次播放 · 排行與明細",openRecap)}
        item {SectionTitle("每週推薦","更新") {onEvent(UiEvent.Recommendations)}}
        if(account.connection!=Connection.Connected) item {SettingAction("連接 ListenBrainz","到設定管理帳號，取得個人推薦",openAccount)}
        if(state.phase==Phase.Loading) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
        state.generated?.let {item {Text("生成日期：$it",style=MaterialTheme.typography.bodySmall)}}
        if(state.tracks.isEmpty()) item {EmptyPanel("等候新的發現",state.error ?: "尚未有推薦時不會加入示範歌曲。")}
        items(state.tracks,key={it.id}) { r -> RecommendationRow(r,state.resolving==r.id,state.resolving!=null,{onEvent(UiEvent.PlayRecommendation(r))}) { chooseVersion(r) } }
    }
}
