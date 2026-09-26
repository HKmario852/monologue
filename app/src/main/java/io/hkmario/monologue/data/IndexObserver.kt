package io.hkmario.monologue.data

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import io.hkmario.monologue.AppGraph
import kotlinx.coroutines.*

class IndexObserver(private val graph: AppGraph): ContentObserver(Handler(Looper.getMainLooper())) {
    private var job: Job?=null
    fun register() {graph.context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,true,this)}
    override fun onChange(selfChange: Boolean) {
        job?.cancel()
        job=graph.scope.launch {
            delay(500)
            if(graph.settings.snapshot().bool("autoScan",true)) runCatching {graph.scanner.scan()}
        }
    }
}
