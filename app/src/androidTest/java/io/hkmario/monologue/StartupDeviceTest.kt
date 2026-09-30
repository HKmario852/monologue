package io.hkmario.monologue

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import io.hkmario.monologue.cloud.WorkScheduler
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** ML Kit and WorkManager no longer start with the app; each starts on first use. */
@RunWith(AndroidJUnit4::class)
class StartupDeviceTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MonologueApp

    @Test fun translatorStartsMlKitOnFirstUse() = runBlocking {
        // The language model may need downloading on a fresh device, so allow time for that.
        val translated = withTimeout(120_000) { app.graph.translator.translate("[00:01.00]ありがとう\n[00:03.00]さようなら", "繁體中文") }
        assertNotNull(translated)
        assertEquals(2, translated!!.lines().count { it.startsWith("[00:0") })
    }

    @Test fun workManagerStartsOnFirstUse() {
        WorkScheduler.sync(app)
        val work = WorkManager.getInstance(app).getWorkInfosForUniqueWork("monologue-sync").get()
        assertTrue("sync work was enqueued", work.isNotEmpty())
        WorkManager.getInstance(app).cancelUniqueWork("monologue-sync").result.get()
    }
}
