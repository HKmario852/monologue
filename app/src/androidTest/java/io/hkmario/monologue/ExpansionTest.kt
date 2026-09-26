package io.hkmario.monologue

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.hkmario.monologue.domain.*
import io.hkmario.monologue.ui.*
import io.hkmario.monologue.cloud.*
import kotlinx.collections.immutable.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpansionTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun discoverHasStatsAndNoCredentialFields() {
        var listening=false;var support=false
        compose.setContent {MonologueTheme {DiscoverScreen(ListenBrainzUiState(),DiscoverUiState(),{},ListeningStatsUiState(),{}, {listening=true},{support=true})}}
        compose.onNodeWithText("0 分鐘").performClick();Assert.assertTrue(listening)
        compose.onNodeWithText("US$ 0.00–0.00").performScrollTo().performClick();Assert.assertTrue(support)
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }
    @Test fun statisticsIncludeEverySongAndSubThresholdTime() {
        val track=Track("fixture-a","統計測試歌曲","測試歌手",uri="")
        val rows=persistentListOf(RankedTrack(track,3,65000),RankedTrack(track.copy(id="fixture-b",title="未達門檻歌曲"),0,7000))
        compose.setContent {MonologueTheme {StatisticsScreen(ListeningStatsUiState(detail=rows),false,{})}}
        compose.onNodeWithText("2 首歌曲 · 3 次達標播放 · Asia/Hong_Kong").assertExists()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("未達門檻歌曲"))
        compose.onNodeWithText("未達門檻歌曲").assertIsDisplayed()
        compose.onNodeWithText("測試歌手\n0 次播放 · 0 分 7 秒").assertExists()
    }
    @Test fun tokenIsInsideSettingsOnly() {
        compose.setContent {MonologueTheme {ListenBrainzAccount(ListenBrainzUiState(),{})}}
        compose.onNodeWithText("ListenBrainz Token").assertExists()
        compose.onNodeWithContentDescription("顯示 Token").performClick()
        compose.onNodeWithContentDescription("隱藏 Token").assertExists()
    }
    @Test fun bottomSettingsNavigationAndStatisticsRoutes() {
        compose.setContent {MonologueTheme {AppHost(AppUiState(),remember {mutableStateOf(PlaybackProgress())},remember {VinylClock()},false,{},null,{}, {_,_->},{},{})}}
        compose.onAllNodesWithText("探索").onLast().performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("0 分鐘"))
        compose.onNodeWithText("0 分鐘").performClick()
        compose.onNodeWithText("聆聽明細").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("你的聆聽足跡"))
        compose.onNodeWithText("你的聆聽足跡").assertExists()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.waitForIdle()
        java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"navigation-qa.png").outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("外觀與導航"))
        compose.onNodeWithText("外觀與導航").assertIsDisplayed()
    }
    @Test fun updateStateDoesNotClaimSuccessWithoutRelease() {
        compose.setContent {MonologueTheme {UpdatesSettings(UpdateUiState(),AppSettingsUiState(),{})}}
        compose.onNodeWithText("檢查更新").assertExists()
        compose.onNodeWithText("安裝已驗證更新").assertDoesNotExist()
    }
}

/** Explicit network probe, separate from deterministic UI tests. No credentials or library uploads. */
@RunWith(AndroidJUnit4::class)
class LiveSourceProbe {
    @Test fun probePublicSources()=runBlocking {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MonologueApp
        val lines=mutableListOf<String>()
        for(provider in listOf("musicbrainz","youtube")) {
            try {
                val songs=app.graph.online.search(provider,"Bach cello suite")
                Assert.assertTrue("$provider real results",songs.isNotEmpty())
                lines+="$provider search: ${songs.size} actual results"
                if(provider=="youtube") {
                    val choice=app.graph.online.resolve(songs.first())
                    val request=okhttp3.Request.Builder().url(choice.url).header("Range","bytes=0-4095").build()
                    app.graph.online.http.newCall(request).execute().use {r->
                        require(r.isSuccessful);val bytes=r.body!!.byteStream().readUpTo(4096);require(bytes.isNotEmpty());lines+="youtube audio: HTTP ${r.code}, ${bytes.size} bytes, ${choice.mime}, ${choice.bitrate} bps"
                    }
                    val instrumentation=InstrumentationRegistry.getInstrumentation()
                    val original=app.graph.settings.snapshot()
                    app.graph.settings.set("statistics","false");app.graph.settings.set("lbSync","false")
                    try {
                        val scenario=androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
                        try {
                            kotlinx.coroutines.delay(1500)
                            instrumentation.runOnMainSync {app.graph.playback.play(listOf(app.graph.online.track(songs.first())))}
                            kotlinx.coroutines.withTimeout(30000) {while(!app.graph.playback.state.value.isPlaying) kotlinx.coroutines.delay(100)}
                            kotlinx.coroutines.delay(1500)
                            require(app.graph.playback.progress.value.positionMs>500)
                            lines+="youtube Media3: playing, position=${app.graph.playback.progress.value.positionMs}, format=${app.graph.playback.state.value.audioFormat}"
                        } finally {instrumentation.runOnMainSync {if(app.graph.playback.state.value.isPlaying) app.graph.playback.toggle();app.graph.playback.clearUpcoming()};scenario.close()}
                    } finally {app.graph.settings.set("statistics",original.bool("statistics",true).toString());app.graph.settings.set("lbSync",original.bool("lbSync").toString())}
                }
            } catch(e: Throwable) {lines+="$provider: UNVERIFIED/FAILED ${e.javaClass.simpleName}: ${e.message}"}
        }
        java.io.File(app.filesDir,"live-source-report.txt").writeText(lines.joinToString("\n"))
        println(lines.joinToString("\n"))
    }
}
