@file:Suppress("UnstableApiUsage")
package io.hkmario.monologue

import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.Cache
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.hkmario.monologue.data.ProtectedLru
import org.junit.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StorageDeviceTest {
    @Test fun cacheAdmissionProtectsCurrentTrackAndOfflineFiles() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(context.cacheDir,"cache-test-${UUID.randomUUID()}").apply {mkdirs()}
        val offline=File(context.filesDir,"offline-test-${UUID.randomUUID()}").apply {writeText("permanent audio")}
        val evictor=ProtectedLru(1024)
        val cache=SimpleCache(root,evictor,StandaloneDatabaseProvider(context))
        fun write(key: String,count: Int) {
            val hole=cache.startReadWrite(key,0,count.toLong())
            try {
                val file=cache.startFile(key,0,count.toLong());file.writeBytes(ByteArray(count));cache.commitFile(file,count.toLong())
            } finally {cache.releaseHoleSpan(hole);evictor.releaseReservations(key)}
        }
        try {
            write("playing",700);evictor.pin(setOf("playing"))
            try {write("new",400);Assert.fail("Over-budget cache write must be refused")} catch(expected: Cache.CacheException) { }
            Assert.assertEquals(700L,cache.cacheSpace)
            Assert.assertTrue(evictor.clearSafe(cache))
            Assert.assertEquals(700L,cache.cacheSpace)
            Assert.assertEquals("permanent audio",offline.readText())
            evictor.pin(emptySet());Assert.assertFalse(evictor.clearSafe(cache));Assert.assertEquals(0L,cache.cacheSpace)
            write("older",700);Thread.sleep(10);write("newer",700)
            Assert.assertFalse(cache.isCached("older",0,700));Assert.assertTrue(cache.isCached("newer",0,700))
        } finally {cache.release();offline.delete();root.deleteRecursively()}
    }
}
