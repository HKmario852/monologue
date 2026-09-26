package io.hkmario.monologue.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.Fetcher
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

fun embeddedArtwork(uri: String): String=Uri.Builder().scheme("monologue-art").authority("embedded").appendQueryParameter("audio",uri).build().toString()

/** Decodes only authorized local audio. Coil owns cancellation, memory caching and image lifecycle. */
class EmbeddedArtworkFetcher(private val context: Context,private val audio: Uri): Fetcher {
    override suspend fun fetch()=withContext(Dispatchers.IO) {
        require(audio.scheme in setOf("content","file"))
        val metadata=MediaMetadataRetriever()
        try {
            metadata.setDataSource(context,audio)
            val data=metadata.embeddedPicture ?: throw IOException("歌曲未有內嵌封面")
            require(data.size<=32*1024*1024)
            val options=BitmapFactory.Options().apply {inJustDecodeBounds=true}
            BitmapFactory.decodeByteArray(data,0,data.size,options)
            options.inSampleSize=1
            while(maxOf(options.outWidth,options.outHeight)/options.inSampleSize>1200) options.inSampleSize*=2
            options.inJustDecodeBounds=false
            val bitmap=BitmapFactory.decodeByteArray(data,0,data.size,options) ?: throw IOException("封面格式無法讀取")
            DrawableResult(bitmap.toDrawable(context.resources),options.inSampleSize>1,DataSource.DISK)
        } finally {metadata.release()}
    }
    class Factory(private val context: Context): Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if(data.scheme!="monologue-art") return null
            val audio=data.getQueryParameter("audio") ?: return null
            return EmbeddedArtworkFetcher(context,Uri.parse(audio))
        }
    }
}
