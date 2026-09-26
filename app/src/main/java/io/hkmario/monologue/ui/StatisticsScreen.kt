package io.hkmario.monologue.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import io.hkmario.monologue.domain.*
import java.time.*
import io.hkmario.monologue.domain.Period
import java.time.format.DateTimeFormatter
import java.util.Locale

// An explicit hypothetical range, never a claim about a platform's royalty rate.
fun supportRange(count: Int) = "US$ %.2f–%.2f".format(Locale.US,count * 0.003,count * 0.005)

@Composable fun StatCard(value: String,label: String,open: ()->Unit,badge: String?=null) {
    Card(onClick=open,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(24.dp).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(badge!=null) Surface(shape=RoundedCornerShape(8.dp),color=MaterialTheme.colorScheme.surface) {Text(badge,Modifier.padding(horizontal=10.dp,vertical=4.dp),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurface)}
            Text(value,style=MaterialTheme.typography.headlineLarge,color=MaterialTheme.colorScheme.onPrimaryContainer)
            Row(verticalAlignment=Alignment.CenterVertically) {Text(label,Modifier.weight(1f),color=MaterialTheme.colorScheme.onPrimaryContainer);Icon(Icons.Outlined.ChevronRight,null,tint=MaterialTheme.colorScheme.onPrimaryContainer)}
        }
    }
}

@Composable fun StatisticsScreen(state: ListeningStatsUiState,support: Boolean,onEvent: (UiEvent)->Unit) {
    val labels=listOf("本週","本月","全部")
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Row {Period.entries.forEachIndexed {i,p->FilterChip(selected=state.period==p,onClick={onEvent(UiEvent.Statistics(p))},label={Text(labels[i])},modifier=Modifier.padding(end=8.dp))}}}
        if(state.period!=Period.All) item {
            val zone=ZoneId.of(state.zone);val format=DateTimeFormatter.ofPattern("yyyy/MM/dd")
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                ActionIcon(Icons.Outlined.ChevronLeft,"上一期") {onEvent(UiEvent.Statistics(state.period,state.offset-1))}
                Text("${Instant.ofEpochMilli(state.startMs).atZone(zone).format(format)} – ${Instant.ofEpochMilli(state.endMs-1).atZone(zone).format(format)}",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                ActionIcon(Icons.Outlined.ChevronRight,"下一期",state.offset<0) {onEvent(UiEvent.Statistics(state.period,state.offset+1))}
            }
        }
        item {
            val count=state.detail.sumOf {it.count};val ms=state.detail.sumOf {it.listenedMs}
            if(support) Text("假設估算，非實際收益",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.tertiary)
            Text(if(support) supportRange(count) else "${ms/3600000} 小時 ${(ms/60000)%60} 分鐘",style=MaterialTheme.typography.headlineLarge,color=MaterialTheme.colorScheme.primary)
            Info("${state.detail.size} 首歌曲 · $count 次達標播放 · ${state.zone}")
            if(support) Info("假設每次達標播放對應 US$ 0.003–0.005，估算支持金額範圍。這是自訂情境計算，並非 Spotify 固定付款率、實際欠款或藝人已獲收益。未達本機計次門檻的片段不計金額；不會產生付款。")
            else Info("按實際聆聽時間排列，包含未達計次門檻的片段。暫停、緩衝及 seek 跳過的部分不計入。")
        }
        if(state.detail.isEmpty()) item {EmptyPanel("尚未有聆聽紀錄","播放音樂後，這裡會顯示真實統計。")}
        if(support) {
            val artists=state.detail.groupBy {it.track.artist}.entries.sortedByDescending {e->e.value.sumOf {it.count}}
            items(artists,key={it.key}) {entry->
                ListItem(headlineContent={Text(entry.key)},supportingContent={Text("${entry.value.size} 首歌曲 · ${entry.value.sumOf {it.count}} 次播放")},trailingContent={Text(supportRange(entry.value.sumOf {it.count}),style=MaterialTheme.typography.labelLarge)},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surface))
            }
        } else items(state.detail,key={it.track.id}) {r ->
            ListItem(headlineContent={Text(r.track.title)},supportingContent={Text("${r.track.artist}\n${r.count} 次播放 · ${r.listenedMs/60000} 分 ${(r.listenedMs/1000)%60} 秒")},leadingContent={Art(r.track,52.dp)},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surface))
        }
    }
}

@Composable fun ListenBrainzAccount(account: ListenBrainzUiState,onEvent: (UiEvent)->Unit) {
    var token by remember {mutableStateOf("")};var show by remember {mutableStateOf(false)}
    Text("ListenBrainz",style=MaterialTheme.typography.headlineSmall)
    Info(when(account.connection) {Connection.Connected->"已連接 · ${account.username}";Connection.Verifying->"驗證中…";Connection.InvalidToken->"Token 無效，請重新連接";Connection.NetworkError->"網絡錯誤；未判定 Token 無效";else->"未連接"})
    OutlinedTextField(token,{token=it},Modifier.fillMaxWidth(),label={Text(if(account.connection==Connection.Connected) "更換 Token" else "ListenBrainz Token")},singleLine=true,visualTransformation=if(show) VisualTransformation.None else PasswordVisualTransformation(),trailingIcon={ActionIcon(if(show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,if(show) "隱藏 Token" else "顯示 Token") {show=!show}})
    Button(onClick={onEvent(UiEvent.VerifyToken(token));token=""},enabled=token.isNotBlank() && account.connection!=Connection.Verifying) {Text("驗證並連接")}
    account.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    account.lastSuccess?.let {Info("最後成功同步：${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))}")}
}
