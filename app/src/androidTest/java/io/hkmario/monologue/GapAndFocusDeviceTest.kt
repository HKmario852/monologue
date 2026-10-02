package io.hkmario.monologue

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.hkmario.monologue.data.*
import io.hkmario.monologue.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 歌曲之間的靜音 and carrying on after another app's media, with real audio. */
@RunWith(AndroidJUnit4::class)
class GapAndFocusDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MonologueApp

    /** A quiet test tone of [seconds], under the file name QaFixtureCleanup knows. */
    private fun wav(seconds: Int): File {
        val file = File(app.filesDir, "test-audio.wav"); val rate = 8000; val samples = rate * seconds
        val buffer = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()); buffer.putInt(36 + samples * 2); buffer.put("WAVEfmt ".toByteArray()); buffer.putInt(16); buffer.putShort(1); buffer.putShort(1)
        buffer.putInt(rate); buffer.putInt(rate * 2); buffer.putShort(2); buffer.putShort(16); buffer.put("data".toByteArray()); buffer.putInt(samples * 2)
        repeat(samples) { n -> buffer.putShort((kotlin.math.sin(n * 2 * Math.PI * 220 / rate) * 500).toInt().toShort()) }
        file.writeBytes(buffer.array()); return file
    }
    private fun tracks(seconds: Int): List<Track> {
        val file = wav(seconds)
        val first = Track("device-test", "裝置測試音訊", "monologue QA", "測試", "QA", file.toURI().toString(), seconds * 1000L)
        val second = first.copy(id = "device-test-2", title = "第二首測試音訊")
        runBlocking { app.graph.db.dao().putTrack(first.row()); app.graph.db.dao().putTrack(second.row()) }
        return listOf(first, second)
    }

    /** Remove the test songs and the plays they left, once the player's pending writes have landed. */
    @After fun removeFixtures() {
        compose.runOnUiThread { app.graph.playback.state.value.takeIf { it.isPlaying }?.let { app.graph.playback.toggle() } }
        Thread.sleep(800)
        QaFixtureCleanup().removeOnlyKnownInstrumentationFixtures()
    }

    @Test fun silenceBetweenSongsWaitsBeforeTheNextOne() {
        val graph = app.graph
        runBlocking { graph.settings.set("gapSeconds", "2") }
        try {
            val songs = tracks(3)
            compose.runOnUiThread { graph.playback.play(songs) }
            compose.waitUntil(15000) { graph.playback.state.value.isPlaying }
            val started = SystemClock.elapsedRealtime()
            val firstId = graph.playback.queue.value.currentId
            // During the gap the first song is still current and the app still shows it as playing.
            compose.waitUntil(8000) { SystemClock.elapsedRealtime() - started > 3600 }
            Assert.assertEquals(firstId, graph.playback.queue.value.currentId)
            Assert.assertTrue(graph.playback.state.value.isPlaying)
            compose.waitUntil(10000) { graph.playback.queue.value.currentId != firstId }
            val elapsed = SystemClock.elapsedRealtime() - started
            Assert.assertTrue("next song after ${elapsed} ms", elapsed in 4600L..8000L)
            compose.runOnUiThread { graph.playback.toggle() }
        } finally { runBlocking { graph.settings.set("gapSeconds", "0") } }
    }

    @Test fun carriesOnAfterAnotherAppsMediaStops() {
        val graph = app.graph
        runBlocking { graph.settings.set("resumeInterruption", "true") }
        compose.runOnUiThread { graph.playback.play(tracks(20)) }
        compose.waitUntil(15000) { graph.playback.state.value.isPlaying }

        // Another "app": takes the audio away for good and plays a tone for 3 s.
        val audio = app.getSystemService(AudioManager::class.java)
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes).setOnAudioFocusChangeListener { }.build()
        Assert.assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audio.requestAudioFocus(request))
        val rate = 44100
        val other = AudioTrack.Builder().setAudioAttributes(attributes)
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STREAM).build()
        val tone = ShortArray(rate / 10) { n -> (kotlin.math.sin(n * 2 * Math.PI * 330 / rate) * 300).toInt().toShort() }
        other.play()
        val until = SystemClock.elapsedRealtime() + 3000
        while(SystemClock.elapsedRealtime() < until) other.write(tone, 0, tone.size)
        Assert.assertFalse("paused while the other app plays", graph.playback.state.value.isPlaying)

        // The other app stops: Monologue carries on by itself.
        other.stop(); other.release(); audio.abandonAudioFocusRequest(request)
        compose.waitUntil(8000) { graph.playback.state.value.isPlaying }
        compose.runOnUiThread { graph.playback.toggle() }
    }
}
