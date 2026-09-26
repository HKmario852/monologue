package io.hkmario.monologue

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Explicit maintenance of fixtures created by this test suite, never user media. */
class QaFixtureCleanup {
    @Test fun removeOnlyKnownInstrumentationFixtures()=runBlocking {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MonologueApp
        val db=app.graph.db.openHelper.writableDatabase
        val rows=app.graph.db.dao().tracks().filter {
            (it.id=="search-1" && it.uri=="file:test") || (it.id=="search-2" && it.uri=="file:test2") ||
                (it.id in setOf("device-test","device-test-2") && it.artist=="monologue QA" && it.uri.endsWith("/test-audio.wav"))
        }
        db.beginTransaction()
        try {
            rows.forEach {r->
                db.execSQL("DELETE FROM playback_checkpoint WHERE entryId IN (SELECT id FROM queue_entries WHERE trackId=?)",arrayOf(r.id))
                listOf("queue_entries","listening_events","tracks").forEach {table->db.execSQL("DELETE FROM $table WHERE ${if(table=="tracks") "id" else "trackId"}=?",arrayOf(r.id))}
            }
            db.setTransactionSuccessful()
        } finally {db.endTransaction()}
    }
}
