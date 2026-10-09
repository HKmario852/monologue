package io.hkmario.monologue

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.hkmario.monologue.data.EmbeddedArtworkBitmapLoader
import io.hkmario.monologue.data.embeddedArtwork
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

/** The media notification gets covers that live inside downloaded audio files. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class ArtworkLoaderDeviceTest {
    /** A short MP3 whose ID3v2.3 tag carries a 64 × 64 red PNG cover, like a downloaded song. */
    private fun mp3WithCover(file: File) {
        val png = ByteArrayOutputStream().also { Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val apic = byteArrayOf(0) + "image/png".toByteArray() + byteArrayOf(0, 3, 0) + png   // encoding, MIME, type: front cover, empty description
        fun size(n: Int, bits: Int) = (3 downTo 0).map { ((n shr (it * bits)) and ((1 shl bits) - 1)).toByte() }.toByteArray()
        val frame = "APIC".toByteArray() + size(apic.size, 8) + byteArrayOf(0, 0) + apic
        val tag = "ID3".toByteArray() + byteArrayOf(3, 0, 0) + size(frame.size, 7) + frame
        // 40 silent MPEG-1 Layer III frames, 128 kbps, 44.1 kHz (417 bytes each).
        val audio = ByteArray(417 * 40).also { a -> for(i in 0 until 40) { a[i * 417] = 0xFF.toByte(); a[i * 417 + 1] = 0xFB.toByte(); a[i * 417 + 2] = 0x90.toByte(); a[i * 417 + 3] = 0x64 } }
        file.writeBytes(tag + audio)
    }

    @Test fun coversInsideAudioFilesReachTheMediaControls() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "artwork-test.mp3").also(::mp3WithCover)
        val uri = Uri.parse(embeddedArtwork(Uri.fromFile(file).toString()))
        // Media3's own loader cannot read these, which left the notification without a cover.
        assertTrue(runCatching { DataSourceBitmapLoader(context).loadBitmap(uri).get(10, TimeUnit.SECONDS) }.isFailure)
        val bitmap = EmbeddedArtworkBitmapLoader(context, DataSourceBitmapLoader(context)).loadBitmap(uri).get(10, TimeUnit.SECONDS)
        assertEquals(64, bitmap.width)
        assertEquals(Color.RED, bitmap.getPixel(32, 32))
        file.delete()
    }
}
