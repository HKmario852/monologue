package io.hkmario.monologue.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.graphics.drawable.toDrawable
import androidx.media3.common.util.BitmapLoader
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.Fetcher
import coil.request.Options
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.Executors

fun embeddedArtwork(uri: String): String=Uri.Builder().scheme("monologue-art").authority("embedded").appendQueryParameter("audio",uri).build().toString()

/**
 * The cover stored inside a local audio file, scaled down so its longer side is at most [maxSize] (returns the
 * scaling factor too). Only authorized local audio ("content" or "file") is read.
 */
@Throws(IOException::class)
fun decodeEmbeddedArtwork(context: Context,audio: Uri,maxSize: Int): Pair<Bitmap,Int> {
    require(audio.scheme in setOf("content","file"))
    val metadata=MediaMetadataRetriever()
    try {
        metadata.setDataSource(context,audio)
        val data=metadata.embeddedPicture ?: throw IOException("歌曲未有內嵌封面")
        require(data.size<=32*1024*1024)
        val options=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(data,0,data.size,options)
        options.inSampleSize=1
        while(maxOf(options.outWidth,options.outHeight)/options.inSampleSize>maxSize) options.inSampleSize*=2
        options.inJustDecodeBounds=false
        val bitmap=BitmapFactory.decodeByteArray(data,0,data.size,options) ?: throw IOException("封面格式無法讀取")
        return bitmap to options.inSampleSize
    } finally {metadata.release()}
}

/** Decodes only authorized local audio. Coil owns cancellation, memory caching and image lifecycle. */
class EmbeddedArtworkFetcher(private val context: Context,private val audio: Uri): Fetcher {
    override suspend fun fetch()=withContext(Dispatchers.IO) {
        val (bitmap,sample)=decodeEmbeddedArtwork(context,audio,1200)
        DrawableResult(bitmap.toDrawable(context.resources),sample>1,DataSource.DISK)
    }
    class Factory(private val context: Context): Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if(data.scheme!="monologue-art") return null
            val audio=data.getQueryParameter("audio") ?: return null
            return EmbeddedArtworkFetcher(context,Uri.parse(audio))
        }
    }
}

/**
 * Loads artwork for the media notification, lock screen and other controllers. Downloaded songs and songs from chosen
 * folders keep their cover inside the audio file ("monologue-art" URIs), which Media3's own loader cannot read, so
 * the controls showed a blank note; those are read from the file here, everything else goes to [fallback].
 */
@androidx.annotation.OptIn(markerClass=[androidx.media3.common.util.UnstableApi::class])
class EmbeddedArtworkBitmapLoader(private val context: Context,private val fallback: BitmapLoader): BitmapLoader {
    private val executor=MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    override fun supportsMimeType(mimeType: String)=fallback.supportsMimeType(mimeType)
    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = fallback.decodeBitmap(data)
    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val audio=uri.takeIf { it.scheme=="monologue-art" }?.getQueryParameter("audio") ?: return fallback.loadBitmap(uri)
        return executor.submit<Bitmap> { decodeEmbeddedArtwork(context,Uri.parse(audio),512).first }
    }
}
