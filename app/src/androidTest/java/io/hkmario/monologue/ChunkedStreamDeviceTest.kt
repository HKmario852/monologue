package io.hkmario.monologue

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.hkmario.monologue.data.ChunkedDataSource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Streams are fetched in bounded ranges (runs on the device: DataSpec needs a real Uri). */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class ChunkedStreamDeviceTest {
    /** A file served over "HTTP": answers ranges with Content-Range, or the whole file when [ranges] is off. */
    private class FakeServer(val file: ByteArray, val ranges: Boolean = true): DataSource {
        val requests = mutableListOf<Pair<Long, Long>>()
        private var data = ByteArray(0); private var at = 0; private var headers = emptyMap<String, List<String>>()
        override fun addTransferListener(transferListener: TransferListener) {}
        override fun open(dataSpec: DataSpec): Long {
            requests += dataSpec.position to dataSpec.length
            val start = if(ranges) dataSpec.position.toInt() else 0
            if(start >= file.size) throw androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException(416, null, null, emptyMap(), dataSpec, ByteArray(0))
            val end = if(ranges && dataSpec.length != C.LENGTH_UNSET.toLong()) minOf(file.size, start + dataSpec.length.toInt()) else file.size
            data = file.copyOfRange(start, end); at = 0
            headers = if(ranges) mapOf("Content-Range" to listOf("bytes $start-${end - 1}/${file.size}")) else emptyMap()
            // Like OkHttpDataSource on a 200: skip to the position, then honour the requested length.
            if(!ranges) { at = dataSpec.position.toInt(); if(dataSpec.length != C.LENGTH_UNSET.toLong()) data = data.copyOf(minOf(data.size, at + dataSpec.length.toInt())) }
            return (data.size - at).toLong()
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if(at >= data.size) return C.RESULT_END_OF_INPUT
            val n = minOf(length, data.size - at); System.arraycopy(data, at, buffer, offset, n); at += n; return n
        }
        override fun getUri(): Uri? = null
        override fun getResponseHeaders() = headers
        override fun close() {}
    }
    private fun readAll(source: DataSource, spec: DataSpec): Pair<Long, ByteArray> {
        val length = source.open(spec); val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(3000)
        while(true) { val n = source.read(buf, 0, buf.size); if(n == C.RESULT_END_OF_INPUT) break; out.write(buf, 0, n) }
        source.close(); return length to out.toByteArray()
    }
    private val file = ByteArray(10_500) { (it % 251).toByte() }

    @Test fun streamsComeInBoundedRanges() {
        val server = FakeServer(file)
        val (length, bytes) = readAll(ChunkedDataSource(server, chunk = 4_000), DataSpec(Uri.EMPTY))
        assertEquals(file.size.toLong(), length)   // known from the first Content-Range, so duration and seeking work
        assertArrayEquals(file, bytes)
        assertEquals(listOf(0L to 4_000L, 4_000L to 4_000L, 8_000L to 2_500L), server.requests)
    }

    @Test fun seekingStartsFromThePositionAndStopsAtARequestedLength() {
        val server = FakeServer(file)
        val (_, bytes) = readAll(ChunkedDataSource(server, chunk = 4_000), DataSpec.Builder().setUri(Uri.EMPTY).setPosition(9_000).build())
        assertArrayEquals(file.copyOfRange(9_000, file.size), bytes)
        val bounded = FakeServer(file)
        val (length, part) = readAll(ChunkedDataSource(bounded, chunk = 4_000), DataSpec.Builder().setUri(Uri.EMPTY).setPosition(1_000).setLength(5_000).build())
        assertEquals(5_000L, length); assertArrayEquals(file.copyOfRange(1_000, 6_000), part)
        assertTrue(bounded.requests.all { it.second <= 4_000 })
    }

    @Test fun aServerThatIgnoresRangesIsReadOnce() {
        val server = FakeServer(file, ranges = false)
        val (_, bytes) = readAll(ChunkedDataSource(server, chunk = 4_000), DataSpec(Uri.EMPTY))
        assertArrayEquals(file, bytes)
        assertEquals(C.LENGTH_UNSET.toLong(), server.requests.last().second)
    }
}
