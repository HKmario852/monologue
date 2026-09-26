package io.hkmario.monologue

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.hkmario.monologue.ui.*
import io.hkmario.monologue.domain.*
import kotlinx.collections.immutable.*
import org.junit.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UiAcceptanceTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val values=android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"$name.png")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/monologue-qa")
        }
        val uri=context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        context.contentResolver.openOutputStream(uri)!!.use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun libraryAndSettingsRender() {
        compose.setContent {MonologueTheme {AppHost(PreviewFixtures.app,remember {mutableStateOf(PlaybackProgress(positionMs=102000))},remember {VinylClock()},false,{},null,{}, {_,_->},{},{})}}
        Assert.assertTrue(compose.onAllNodesWithText("Monologue").fetchSemanticsNodes().indices.any {compose.onAllNodesWithText("Monologue")[it].isDisplayed()})
        compose.onAllNodesWithText("Afterglow").onFirst().assertExists()
        screenshot("01-library-demo")
        compose.onAllNodesWithText("設定").onLast().performClick()
        compose.onNodeWithText("外觀與導航").assertIsDisplayed()
        screenshot("05-settings")
    }
    @Test fun playerHasVinylAndCollapsibleControls() {
        var collapse=false
        compose.setContent {MonologueTheme {NowPlayingScreen(PreviewFixtures.app,remember {mutableStateOf(PlaybackProgress(positionMs=102000))},remember {VinylClock(37.0)},false,{}, {collapse=true},{},{},{},{},{})}}
        compose.onNodeWithContentDescription("收合正在播放").assertIsDisplayed()
        compose.onNodeWithContentDescription("播放",useUnmergedTree=true).performScrollTo().assertIsDisplayed()
        screenshot("02-player-demo")
        compose.onNodeWithContentDescription("收合正在播放").performClick()
        Assert.assertTrue(collapse)
    }
    @Test fun driveNeverClaimsConnectedWhenUnconfigured() {
        compose.setContent {MonologueTheme {DriveScreen(DriveLibraryUiState(),AppSettingsUiState(),DownloadManagerUiState(),{},{},{})}}
        compose.onNodeWithText("以 Google 帳戶登入").assertExists()
        compose.onNodeWithText("絕不修改或刪除").assertExists()
        screenshot("03-drive-unconfigured")
    }
    @Test fun rankingEmptyStateAndSettingsDetail() {
        compose.setContent {MonologueTheme {RankScreen(LeaderboardUiState(),{},{})}}
        compose.onNodeWithText("呢個週期未有紀錄").assertIsDisplayed()
        screenshot("04-rank-empty")
    }
    @Test fun miniPlayerButtonsDoNotExpand() {
        var expanded=false;var toggled=false;var queue=false
        compose.setContent {MonologueTheme {MiniPlayer(PreviewFixtures.app.player,remember {mutableStateOf(PlaybackProgress())},{if(it==UiEvent.TogglePlay) toggled=true},{expanded=true},{queue=true})}}
        compose.onNodeWithContentDescription("播放").performClick()
        Assert.assertTrue(toggled);Assert.assertFalse(expanded)
        compose.onNodeWithContentDescription("播放隊列").performClick()
        Assert.assertTrue(queue);Assert.assertFalse(expanded)
    }
    @Test fun navigationStyleSwitchKeepsDestination() {
        val state=mutableStateOf(PreviewFixtures.app)
        compose.setContent {MonologueTheme {AppHost(state.value,remember {mutableStateOf(PlaybackProgress())},remember {VinylClock()},false,{},null,{}, {_,_->},{},{})}}
        compose.onAllNodesWithText("排行榜").onLast().performClick()
        compose.onNodeWithText("聆聽排行").assertIsDisplayed()
        compose.runOnIdle {state.value=state.value.copy(settings=AppSettingsUiState(persistentMapOf("navigation" to "drawer")))}
        compose.onNodeWithText("聆聽排行").assertIsDisplayed()
        compose.onNodeWithContentDescription("開啟選單").assertIsDisplayed()
        compose.runOnIdle {state.value=state.value.copy(settings=AppSettingsUiState())}
        compose.onNodeWithText("聆聽排行").assertIsDisplayed()
    }
    @Test fun switchingTabsAlwaysOpensTheTabFirstPage() {
        compose.setContent {MonologueTheme {AppHost(PreviewFixtures.app,remember {mutableStateOf(PlaybackProgress())},remember {VinylClock()},false,{},null,{}, {_,_->},{},{})}}
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("歌詞"))
        compose.onNodeWithText("歌詞").performClick()
        compose.onNodeWithText("歌詞文字大小").assertExists()
        compose.onNodeWithTag("nav-rank").performClick()
        compose.onNodeWithText("聆聽排行").assertIsDisplayed()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithText("歌詞文字大小").assertDoesNotExist()
        compose.onNodeWithText("把 Monologue 調成你的節奏。").assertExists()
        // Same for 媒體庫: an opened album is not shown again after visiting another tab.
        compose.onNodeWithTag("nav-library").performClick()
        compose.onNodeWithText("你的歌單").assertExists()
    }
    @Test fun darkThemeRetainsVinylStructure() {
        var foreground=androidx.compose.ui.graphics.Color.Unspecified
        compose.setContent {MonologueTheme(AppSettingsUiState(persistentMapOf("theme" to "dark"))) {
            foreground=LocalContentColor.current
            NowPlayingScreen(PreviewFixtures.app,remember {mutableStateOf(PlaybackProgress())},remember {VinylClock()},false,{},{},{},{},{},{},{})
        }}
        compose.onNodeWithContentDescription("黑膠唱片",substring=true).assertExists()
        Assert.assertEquals(NightColors.onBackground,foreground)
        screenshot("08-dark-demo")
    }
    @Test fun staleTabResultsAreNeverRenderedInUiState() {
        val event=mutableStateOf<UiEvent?>(null)
        val state=PreviewFixtures.app.library.copy(search=search(PreviewFixtures.tracks,SearchRequest(LibraryTab.Artists,"Afterglow",8)))
        compose.setContent {MonologueTheme {LibraryScreen(state,AppSettingsUiState(),{event.value=it},{},{},{},{})}}
        compose.onNodeWithText("搵唔到「Afterglow」").assertIsDisplayed()
        compose.onNodeWithText("目前只搜尋媒體庫歌手").assertIsDisplayed()
        compose.onNodeWithText("改為線上搜尋「Afterglow」").assertExists()
    }
    @Test fun firstRunOffersThreeSources() {
        var event: UiEvent?=null;var drive=false;var online=false
        compose.setContent {MonologueTheme {LibraryScreen(LocalLibraryUiState(phase=Phase.PermissionRequired),AppSettingsUiState(),{event=it},{},{},{},{},{drive=true},{online=true})}}
        compose.onNodeWithText("掃描本機音樂").performClick();Assert.assertEquals(UiEvent.RequestAudioPermission,event)
        compose.onNodeWithText("連接 Google Drive").performClick();Assert.assertTrue(drive)
        compose.onNodeWithText("搜尋線上音樂").performClick();Assert.assertTrue(online)
    }
    @Test fun onlineResultsNeverClaimQualityBeforeResolving() {
        val song=OnlineSong("youtube:x","Test Song","Test Channel",durationMs=185000,provider="youtube",audio=true)
        val meta=OnlineSong("mb","Meta Song","Artist",provider="musicbrainz")
        compose.setContent {MonologueTheme {OnlineScreen(OnlineUiState(results=persistentListOf(song,meta),phase=Phase.Ready,searched=true),PluginUiState(),AppSettingsUiState(),{},{})}}
        compose.onNodeWithText("音質尚未確認").assertExists()
        compose.onNodeWithText("3:05 · 平台：YouTube").assertExists()
        compose.onNodeWithText("只有歌曲資料，沒有音訊；可尋找可播音源").assertExists()
    }
    @Test fun settingsSearchFindsTokenGroup() {
        compose.setContent {MonologueTheme {SettingsHome {}}}
        compose.onNode(hasSetTextAction()).performTextInput("token")
        compose.onNodeWithText("ListenBrainz").assertIsDisplayed()
        compose.onNodeWithText("播放").assertDoesNotExist()
    }
    @Test fun downloadWaitingDoesNotResumeOnToggle() {
        var last: UiEvent?=null
        val state=DownloadManagerUiState(DownloadPhase.Waiting,persistentListOf(DownloadItem("a","a","Example",DownloadStatus.Queued)),true)
        compose.setContent {MonologueTheme {DownloadSheet(state,{last=it},{})}}
        compose.onNodeWithText("已暫停，等待你繼續").assertIsDisplayed()
        compose.onNodeWithText("繼續下載").performClick()
        Assert.assertEquals(UiEvent.ContinueDownloads,last)
    }
    @Test fun largeTextPlayerKeepsControlsReachable() {
        compose.setContent {
            val density=androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,1.6f)) {
                MonologueTheme {NowPlayingScreen(PreviewFixtures.app,remember {mutableStateOf(PlaybackProgress())},remember {VinylClock()},false,{},{},{},{},{},{},{})}
            }
        }
        compose.onNodeWithContentDescription("播放",useUnmergedTree=true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("收合正在播放").assertIsDisplayed()
        screenshot("06-large-text-demo")
    }
    @Test fun downwardCoverGestureCollapsesButCancelledGestureDoesNot() {
        var collapsed=false
        compose.setContent {MonologueTheme {NowPlayingScreen(PreviewFixtures.app,remember {mutableStateOf(PlaybackProgress())},remember {VinylClock()},false,{}, {collapsed=true},{},{},{},{},{})}}
        compose.onNodeWithContentDescription("黑膠唱片",substring=true).performTouchInput {
            down(center);moveBy(androidx.compose.ui.geometry.Offset(0f,40f),300);cancel()
        }
        compose.waitForIdle()
        Assert.assertFalse(collapsed)
        compose.onNodeWithContentDescription("黑膠唱片",substring=true).performTouchInput {swipeDown(durationMillis=250)}
        Assert.assertTrue(collapsed)
    }
    @Test fun scrubberDragNeverCollapsesPlayer() {
        var collapsed=false;var committed=false;var changed=0
        compose.setContent {
            val progress=remember {mutableStateOf(PlaybackProgress())}
            MonologueTheme {NowPlayingScreen(PreviewFixtures.app,progress,remember {VinylClock()},false,{when(it) {is UiEvent.PreviewSeek->{changed++;progress.value=progress.value.copy(seekPreview=it.position)};UiEvent.CommitSeek->committed=true;else->Unit}}, {collapsed=true},{},{},{},{},{})}
        }
        compose.onNodeWithContentDescription("播放進度").performScrollTo().performTouchInput {swipe(androidx.compose.ui.geometry.Offset(width*.15f,centerY),androidx.compose.ui.geometry.Offset(width*.8f,centerY),500)}
        compose.waitForIdle()
        screenshot("07-scrubber-test")
        Assert.assertFalse(collapsed)
        Assert.assertTrue("seek changed=$changed; bounds=${compose.onNodeWithContentDescription("播放進度").fetchSemanticsNode().boundsInRoot}",committed)
    }
}
