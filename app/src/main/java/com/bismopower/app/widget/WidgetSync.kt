package com.bismopower.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import com.bismopower.app.library.ArtLoader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Vive en el servicio de reproducción y actualiza el widget cuando cambia la canción o se
 * pausa. La carátula se carga en otro hilo y solo una vez por canción.
 */
class WidgetSync(
    private val context: Context,
    private val player: Player,
) {
    private val main = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var artFor: Uri? = null
    private var art: Bitmap? = null

    private val listener =
        object : Player.Listener {
            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) {
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_MEDIA_METADATA_CHANGED,
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_TIMELINE_CHANGED,
                    )
                ) {
                    push()
                }
            }
        }

    fun attach() {
        player.addListener(listener)
        push()
    }

    fun release() {
        player.removeListener(listener)
        executor.shutdownNow()
        main.removeCallbacksAndMessages(null)
        WidgetUpdater.push(context, WidgetState.IDLE)
    }

    private fun push() {
        if (player.currentMediaItem == null) {
            WidgetUpdater.push(context, WidgetState.IDLE)
            return
        }
        val meta = player.mediaMetadata
        val uri = meta.artworkUri
        WidgetUpdater.push(
            context,
            WidgetState(
                hasMedia = true,
                title = meta.title?.toString().orEmpty(),
                artist = meta.artist?.toString().orEmpty(),
                playing = player.isPlaying,
                art = if (uri == artFor) art else null,
            ),
        )
        if (uri != artFor) {
            artFor = uri
            art = null
            if (uri != null && !executor.isShutdown) {
                executor.execute {
                    val bitmap = runCatching { ArtLoader.load(context, uri, ART_PX) }.getOrNull()
                    main.post {
                        if (artFor == uri && bitmap != null) {
                            art = bitmap
                            push()
                        }
                    }
                }
            }
        }
    }

    private companion object {
        /** Tamaño de la carátula del widget: suficiente para 52 dp y liviana para Android. */
        const val ART_PX = 220
    }
}
