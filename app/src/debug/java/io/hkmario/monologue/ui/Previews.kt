package io.hkmario.monologue.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.hkmario.monologue.domain.*
import kotlinx.collections.immutable.*

/** DESIGN FIXTURES ONLY: excluded from release, never inserted into the production database. */
object PreviewFixtures {
    val tracks=persistentListOf(Track("preview:1","Afterglow","Mira Sol","Evening","Music","",238000),Track("preview:2","Blue Hour","Northline","Quiet","Music","",204000),Track("preview:3","Slow Motion","June Park","Quiet","Music","",211000))
    val entry=QueueEntry("preview-entry",tracks[0])
    val app=AppUiState(library=LocalLibraryUiState(Phase.Ready,tracks,persistentListOf(Playlist("preview-list","晚霞時分",tracks)),tracks,search(tracks,SearchRequest(LibraryTab.Tracks,"",0))),player=NowPlayingUiState(Phase.Ready,entry,isPlaying=false,seekable=true,durationMs=238000),queue=PlaybackQueueUiState(persistentListOf(entry),entry.id))
}
@Preview(name="媒體庫 · 示範資料",widthDp=390,heightDp=844,showBackground=true)
@Composable fun LibraryPreview() {MonologueTheme {Column {Text("Monologue",style=MaterialTheme.typography.headlineLarge,modifier=Modifier.padding(24.dp));Text("設計預覽 · 示範資料",modifier=Modifier.padding(horizontal=24.dp));LibraryScreen(PreviewFixtures.app.library,AppSettingsUiState(),{},{},{},{},{})}}}
@Preview(name="暖白黑膠 · 示範資料",widthDp=390,heightDp=844,showBackground=true)
@Composable fun PlayerPreview() {MonologueTheme {NowPlayingScreen(PreviewFixtures.app,remember {mutableStateOf(PlaybackProgress(positionMs=102000))},remember {VinylClock(24.0)},false,{},{},{},{},{},{},{})}}
@Preview(name="Drive · 真實未設定狀態",widthDp=390,heightDp=844,showBackground=true)
@Composable fun DrivePreview() {MonologueTheme {DriveScreen(DriveLibraryUiState(),AppSettingsUiState(),DownloadManagerUiState(),{},{},{})}}
@Preview(name="設定",widthDp=390,heightDp=844,showBackground=true)
@Composable fun SettingsPreview() {MonologueTheme {SettingsHome {}}}
