package io.hkmario.monologue

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.hkmario.monologue.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PersistenceDeviceTest {
    @Test fun outboxSurvivesDatabaseReopenAndDuplicateInsertion() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="qa-outbox-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,MusicDatabase::class.java,name).build()
        var db=open()
        try {
            val row=OutboxRow("stable-instance","test-owner","{\"listened_at\":1}",1)
            db.dao().enqueueListen(row);db.dao().enqueueListen(row)
            db.dao().outboxError(listOf(row.id),"offline")
            db.close();db=open()
            assertEquals(1,db.dao().pending("test-owner").size)
            assertEquals(row.payload,db.dao().pending("test-owner").single().payload)
            assertEquals("offline",db.dao().pending("test-owner").single().error)
            assertTrue(db.dao().pending("another-owner").isEmpty())
            db.dao().acknowledge(listOf(row.id))
            assertTrue(db.dao().pending("test-owner").isEmpty())
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun retryOnlyRequeuesFailedRowsAfterReopen() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="qa-download-${UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,MusicDatabase::class.java,name).build()
        var db=open()
        try {
            db.dao().putDownload(DownloadRow("done","drive:a","a","1",null,10,"Complete",10))
            db.dao().putDownload(DownloadRow("failed","drive:b","b","1",null,10,"Failed",3,"offline"))
            db.dao().control(DownloadControl(phase="Waiting",pauseBetween=true))
            db.close();db=open()
            assertEquals("Waiting",db.dao().control()!!.phase)
            db.dao().retryFailed()
            val rows=db.dao().downloads().associateBy {it.id}
            assertEquals("Complete",rows.getValue("done").status)
            assertEquals(10L,rows.getValue("done").received)
            assertEquals("Queued",rows.getValue("failed").status)
            assertEquals(0L,rows.getValue("failed").received)
        } finally {db.close();context.deleteDatabase(name)}
    }
}
