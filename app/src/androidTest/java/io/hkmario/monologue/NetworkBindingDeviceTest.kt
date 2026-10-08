package io.hkmario.monologue

import android.net.ConnectivityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.hkmario.monologue.cloud.on
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Downloads are kept on the network the download work was given (sockets and DNS bound to it). */
@RunWith(AndroidJUnit4::class)
class NetworkBindingDeviceTest {
    @Test fun aClientBoundToANetworkStillConnects() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val network = context.getSystemService(ConnectivityManager::class.java).activeNetwork!!
        val response = OkHttpClient().on(network).newCall(Request.Builder().url("https://www.google.com/generate_204").build()).execute()
        response.use { assertEquals(204, it.code) }
    }
}
