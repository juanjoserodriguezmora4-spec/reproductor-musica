package com.bismopower.app.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.pxOrElse
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Enseña a Coil (las imágenes de la interfaz) a cargar las direcciones bismoart://. */
class TrackArtFetcher(
    private val data: Uri,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val w = options.size.width.pxOrElse { 512 }
        val h = options.size.height.pxOrElse { 512 }
        val px = maxOf(w, h).coerceIn(64, 1024)
        val bitmap =
            withContext(Dispatchers.IO) { ArtLoader.load(options.context, data, px) }
                ?: throw IOException("La canción no tiene carátula")
        return DrawableResult(
            drawable = BitmapDrawable(options.context.resources, bitmap),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? = if (data.scheme == ART_SCHEME) TrackArtFetcher(data, options) else null
    }
}

/**
 * Enseña a Media3 (notificación, pantalla de bloqueo, Bluetooth del carro) a cargar las
 * direcciones bismoart://. Las demás las sigue cargando el cargador normal de Media3.
 */
@UnstableApi
class TrackArtBitmapLoader(
    private val context: Context,
) : BitmapLoader {
    private val fallback = DataSourceBitmapLoader(context)
    private val executor: ListeningExecutorService = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String): Boolean = fallback.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = fallback.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        if (uri.scheme == ART_SCHEME) {
            executor.submit(
                Callable<Bitmap> {
                    ArtLoader.load(context, uri, 512) ?: throw IOException("La canción no tiene carátula")
                },
            )
        } else {
            fallback.loadBitmap(uri)
        }

    fun release() {
        executor.shutdown()
    }
}
