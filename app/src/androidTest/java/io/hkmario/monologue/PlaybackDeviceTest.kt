package io.hkmario.monologue

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.hkmario.monologue.domain.*
import io.hkmario.monologue.data.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class PlaybackDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MonologueApp
    private fun wav(): File {
        val file=File(app.filesDir,"test-audio.wav");val rate=8000;val samples=rate*20
        val buffer=ByteBuffer.allocate(44+samples*2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray());buffer.putInt(36+samples*2);buffer.put("WAVEfmt ".toByteArray());buffer.putInt(16);buffer.putShort(1);buffer.putShort(1);buffer.putInt(rate);buffer.putInt(rate*2);buffer.putShort(2);buffer.putShort(16);buffer.put("data".toByteArray());buffer.putInt(samples*2)
        repeat(samples) {n->buffer.putShort((kotlin.math.sin(n*2*Math.PI*220/rate)*500).toInt().toShort())}
        file.writeBytes(buffer.array());return file
    }
    @Test fun realAudioPauseQueueNavigationAndPersistence() {
        val graph=app.graph;val file=wav();val track=Track("device-test","裝置測試音訊","monologue QA","測試","QA",file.toURI().toString(),20000)
        runBlocking {graph.db.dao().putTrack(track.row());graph.settings.set("statistics","true")}
        compose.waitUntil(10000) {graph.playback.state.value.phase!=Phase.Loading}
        compose.runOnUiThread {graph.playback.play(listOf(track,track.copy(id="device-test-2",title="第二首測試音訊")))}
        compose.waitUntil(15000) {graph.playback.state.value.isPlaying}
        Thread.sleep(1500)
        val before=graph.playback.progress.value.positionMs
        Assert.assertTrue(before>0)
        compose.runOnUiThread {graph.playback.toggle()}
        compose.waitUntil(5000) {!graph.playback.state.value.isPlaying}
        val paused=graph.playback.progress.value.positionMs
        Thread.sleep(1200)
        Assert.assertTrue(kotlin.math.abs(graph.playback.progress.value.positionMs-paused)<150)
        val ids=graph.playback.queue.value.entries.map {it.id}
        Assert.assertEquals(2,ids.distinct().size)
        compose.runOnUiThread {graph.playback.move(ids[0],1)}
        Assert.assertEquals(ids[0],graph.playback.queue.value.currentId)
        runBlocking {graph.settings.set("navigation","drawer")}
        Assert.assertEquals(ids[0],graph.playback.queue.value.currentId)
        compose.runOnUiThread {graph.playback.toggle()}
        compose.waitUntil(5000) {graph.playback.state.value.isPlaying}
        compose.runOnUiThread {graph.playback.remove(ids[0])}
        Assert.assertTrue(graph.playback.queue.value.entries.first {it.id==ids[0]}.removeAfterPlaying)
        Assert.assertTrue(graph.playback.state.value.isPlaying)
        Thread.sleep(1200)
        val foregroundPosition=graph.playback.progress.value.positionMs
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
        Thread.sleep(1500)
        Assert.assertTrue(graph.playback.state.value.isPlaying)
        Assert.assertTrue(graph.playback.progress.value.positionMs>foregroundPosition+700)
        compose.runOnUiThread {graph.playback.toggle()}
        runBlocking {graph.settings.set("navigation","bottom");Assert.assertEquals("bottom",SettingsRepository(app).snapshot().text("navigation"))}
        Thread.sleep(1000)
        val events=runBlocking {graph.db.dao().events().filter {it.trackId==track.id}}
        Assert.assertTrue(events.sumOf {it.listenedMs} in 2000L..8000L)
        Assert.assertEquals(0,events.count {it.counted})
        // Two stretches of playing (paused in between) are two rows, not one row per half-second tick.
        Assert.assertEquals(2,events.size)
    }
}
