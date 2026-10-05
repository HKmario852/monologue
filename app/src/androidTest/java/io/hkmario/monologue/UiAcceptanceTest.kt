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
        // Replace this screenshot from earlier runs ("name.png" and MediaStore's "name (n).png" copies) so repeated runs
        // never run out of unique file names. Only this suite's QA folder is touched.
        context.contentResolver.delete(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "${android.provider.MediaStore.Images.Media.RELATIVE_PATH}=? AND (${android.provider.MediaStore.Images.Media.DISPLAY_NAME}=? OR ${android.provider.MediaStore.Images.Media.DISPLAY_NAME} LIKE ?)",
            arrayOf("Pictures/monologue-qa/","$name.png","$name (%).png"))
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
        // No app-name bar: the 媒體庫 title row carries the gear and ⋮ menu.
        // (The closed drawer still composes its "Monologue" heading, so check visibility rather than existence.)
        Assert.assertFalse(compose.onAllNodesWithText("Monologue").fetchSemanticsNodes().indices.any {compose.onAllNodesWithText("Monologue")[it].isDisplayed()})
        compose.onNodeWithContentDescription("媒體庫選項").assertIsDisplayed()
        compose.onAllNodesWithText("Afterglow").onFirst().assertExists()
        screenshot("01-library-demo")
        compose.onNodeWithContentDescription("設定").performClick()
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
    @Test fun recapEmptyStateAndSupportLink() {
        var support=false
        compose.setContent {MonologueTheme {RecapScreen(LeaderboardUiState(),{},{}) {support=true}}}
        compose.onNodeWithText("這段期間未有紀錄").assertIsDisplayed()
        compose.onNodeWithText("本週").assertIsDisplayed()
        compose.onNodeWithText("US$ 0.00–0.00").performScrollTo().performClick();Assert.assertTrue(support)
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
        compose.onNodeWithTag("nav-discover").performClick()
        compose.onNodeWithText("你的聆聽足跡").assertIsDisplayed()
        compose.runOnIdle {state.value=state.value.copy(settings=AppSettingsUiState(persistentMapOf("navigation" to "drawer")))}
        compose.onNodeWithText("你的聆聽足跡").assertIsDisplayed()
        compose.onNodeWithContentDescription("開啟選單").assertIsDisplayed()
        compose.runOnIdle {state.value=state.value.copy(settings=AppSettingsUiState())}
        compose.onNodeWithText("你的聆聽足跡").assertIsDisplayed()
    }
    @Test fun switchingTabsAlwaysOpensTheTabFirstPage() {
        compose.setContent {MonologueTheme {AppHost(PreviewFixtures.app,remember {mutableStateOf(PlaybackProgress())},remember {VinylClock()},false,{},null,{}, {_,_->},{},{})}}
        // 設定 opens from the 媒體庫 header; it is no longer a bottom tab.
        compose.onNodeWithTag("nav-settings").assertDoesNotExist()
        compose.onNodeWithContentDescription("設定").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("歌詞"))
        compose.onNodeWithText("歌詞").performClick()
        compose.onNodeWithText("歌詞文字大小").assertExists()
        compose.onNodeWithTag("nav-discover").performClick()
        compose.onNodeWithText("你的聆聽足跡").assertIsDisplayed()
        compose.onNodeWithTag("nav-library").performClick()
        compose.onNodeWithContentDescription("設定").performClick()
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
        compose.onNodeWithText("找不到「Afterglow」").assertIsDisplayed()
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
        compose.setContent {MonologueTheme {SearchScreen(LocalLibraryUiState(),OnlineUiState(query="Test",results=persistentListOf(song,meta),phase=Phase.Ready,searched=true),PluginUiState(),AppSettingsUiState(),{})}}
        compose.onNodeWithText("媒體庫沒有符合的歌曲").assertExists()
        compose.onNodeWithText("音質尚未確認").assertExists()
        compose.onNodeWithText("3:05 · 平台：YouTube").assertExists()
        compose.onNodeWithText("只有歌曲資料，沒有音訊；可尋找可播音源").assertExists()
    }
    @Test fun searchShowsLibraryAndCloudBeforeAskingOnline() {
        val local=Track("l","Afterglow","Local Artist",uri="content://l")
        val cloud=Track("d","Afterglow (Live)","Drive Artist",uri="drive://d",source=Source.Drive)
        var last: UiEvent?=null
        compose.setContent {MonologueTheme {SearchScreen(LocalLibraryUiState(tracks=persistentListOf(local,cloud)),OnlineUiState(query="afterglow"),PluginUiState(persistentListOf(SourcePlugin("youtube","YouTube","1","audio",builtIn=true))),AppSettingsUiState(),{last=it})}}
        compose.onNodeWithText("媒體庫 · 1 首").assertIsDisplayed()
        compose.onNodeWithText("Google Drive · 1 首").assertIsDisplayed()
        Assert.assertNull(last)
        compose.onNodeWithText("在 YouTube 搜尋「afterglow」").performScrollTo().performClick()
        Assert.assertEquals(UiEvent.Online(OnlineAction.Search),last)
    }
    @Test fun settingsSearchFindsTokenGroup() {
        compose.setContent {MonologueTheme {SettingsHome {}}}
        compose.onNode(hasSetTextAction()).performTextInput("token")
        compose.onNodeWithText("ListenBrainz").assertIsDisplayed()
        compose.onNodeWithText("播放").assertDoesNotExist()
    }
    @Test fun settingsShowGroupIconsAndRowIcons() {
        // (dark theme, group or -1 for the home)
        var screen by mutableStateOf(false to -1)
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f,1f)) {
                Box(Modifier.requiredSize(400.dp,870.dp)) {
                    MonologueTheme(AppSettingsUiState(persistentMapOf("theme" to if(screen.first) "dark" else "paper"))) {
                        Surface(Modifier.fillMaxSize()) {
                            if(screen.second<0) SettingsHome {} else SettingsDetail(screen.second,PreviewFixtures.app,{},{},{},{},{},{},{},{})
                        }
                    }
                }
            }
        }
        // Each group says what it holds instead of a number.
        compose.onNodeWithText("主題、導航樣式、黑膠與開啟動畫").assertIsDisplayed()
        compose.onNodeWithText("01").assertDoesNotExist()
        screenshot("11-settings-home")
        screen=false to 0; compose.waitForIdle(); compose.onNodeWithText("黑膠旋轉").assertIsDisplayed(); screenshot("12-settings-appearance")
        screen=true to -1; compose.waitForIdle(); screenshot("13-settings-home-dark")
        screen=true to 5; compose.waitForIdle(); screenshot("14-settings-lyrics-dark")
    }
    @Test fun downloadWaitingDoesNotResumeOnToggle() {
        var last: UiEvent?=null
        val state=DownloadManagerUiState(DownloadPhase.Waiting,persistentListOf(DownloadItem("a","a","Example",DownloadStatus.Queued)),true)
        compose.setContent {MonologueTheme {DownloadSheet(state,{last=it},{})}}
        compose.onNodeWithText("已暫停，等待你繼續").assertIsDisplayed()
        compose.onNodeWithText("繼續下載").performClick()
        Assert.assertEquals(UiEvent.ContinueDownloads,last)
    }
    @Test fun uprightLyricsFillTheScreenAboveCompactControls() {
        val lines=(1..20).map { LyricLine("$it",it*4000L,"歌詞の行 $it") }.toPersistentList()
        val app=PreviewFixtures.app.copy(lyrics=LyricsUiState(Phase.Ready,PreviewFixtures.app.player.entry?.track?.id,lines,"測試歌詞"))
        compose.setContent {
            // A phone held upright (about 400 × 870 dp), whatever the test device's own screen is.
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f,1f)) {
                Box(Modifier.requiredSize(400.dp,870.dp)) {
                    MonologueTheme {NowPlayingScreen(app,remember {mutableStateOf(PlaybackProgress(positionMs=0))},remember {VinylClock()},false,{},{},{},{},{},{},{})}
                }
            }
        }
        compose.onNodeWithText("歌詞").performClick()
        compose.waitForIdle()
        // Several lines show, with the full-screen button below them and the tool row still on screen.
        (1..6).forEach { compose.onNodeWithText("歌詞の行 $it").assertIsDisplayed() }
        compose.onNodeWithContentDescription("全螢幕歌詞").assertIsDisplayed()
        compose.onNodeWithText("隊列").assertIsDisplayed()
        screenshot("09-upright-lyrics")
    }
    @Test fun aTranslationFoundElsewhereNamesItsTranslator() {
        val lines=(1..6).map { LyricLine("$it",it*4000L,"歌詞の行 $it",translation="歌詞第 $it 行") }.toPersistentList()
        val settings=AppSettingsUiState(persistentMapOf("translations" to "true"))
        var state by mutableStateOf(LyricsUiState(Phase.Ready,"t",lines,"LRCLIB · 歌手 · 歌名","巴哈姆特 · 網友翻譯（純文字） · 譯者 · 歌名 · 中文翻譯：繁體中文"))
        compose.setContent { MonologueTheme { Surface { LyricsPanel(state,remember {mutableStateOf(PlaybackProgress(positionMs=4000))},settings,{}) } } }
        compose.onNodeWithText("歌詞第 1 行").assertIsDisplayed()
        compose.onNodeWithText("LRCLIB · 歌手 · 歌名\n翻譯：巴哈姆特 · 網友翻譯（純文字） · 譯者 · 歌名").assertIsDisplayed()
        screenshot("10-translation-credit")
        // A translation that came with the lyrics is already named by the source line.
        state=state.copy(source="網易雲音樂 · 歌手 · 歌名",translationSource="網易雲音樂中文翻譯：繁體中文")
        compose.waitForIdle()
        compose.onNodeWithText("翻譯：",substring=true).assertDoesNotExist()
    }
    @Test fun searchShowsCategoryTilesTwoAcross() {
        var opened: String?=null
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f,1f)) {
                Box(Modifier.requiredSize(400.dp,870.dp)) {
                    MonologueTheme {SearchScreen(PreviewFixtures.app.library,OnlineUiState(),PluginUiState(),AppSettingsUiState(),{},openCategory={opened=it})}
                }
            }
        }
        // Two tiles share a row, as in the reference layout.
        val left=compose.onNodeWithText("熱門新歌").fetchSemanticsNode().boundsInRoot
        val right=compose.onNodeWithText("日本樂曲").fetchSemanticsNode().boundsInRoot
        Assert.assertEquals(left.top,right.top,1f)
        Assert.assertTrue(right.left>left.right)
        compose.onNodeWithText("你想聽什麼？").assertIsDisplayed()
        screenshot("10-search-tiles")
        compose.onNodeWithText("日本樂曲").performClick()
        Assert.assertEquals("jpop",opened)
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
