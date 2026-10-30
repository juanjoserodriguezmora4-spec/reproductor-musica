package com.bismopower.app.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.bismopower.app.R
import com.bismopower.app.audio.BismoRenderersFactory
import com.bismopower.app.container
import com.bismopower.app.library.TrackArtBitmapLoader
import com.bismopower.app.ui.MainActivity
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

const val EXTRA_ROUTE = "route"

/** Mantiene la música sonando con la pantalla apagada; Media3 pone los controles en la notificación y la pantalla de bloqueo. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var artLoader: TrackArtBitmapLoader? = null

    override fun onCreate() {
        super.onCreate()
        val player =
            ExoPlayer
                .Builder(this, BismoRenderersFactory(this, container.audioEffects))
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true,
                ).setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()

        val intent =
            Intent(this, MainActivity::class.java)
                .putExtra(EXTRA_ROUTE, "player")
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending =
            PendingIntent.getActivity(
                this,
                10,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider
                .Builder(this)
                .build()
                .apply { setSmallIcon(R.drawable.ic_stat_bismo) },
        )

        // Carga la carátula de cada canción (también las de Bruma) para la notificación y la pantalla de bloqueo.
        val loader = TrackArtBitmapLoader(this)
        artLoader = loader

        session =
            MediaSession
                .Builder(this, player)
                .setSessionActivity(pending)
                .setBitmapLoader(CacheBitmapLoader(loader))
                .setCallback(
                    object : MediaSession.Callback {
                        // Los elementos que llegan desde la interfaz traen la ruta del archivo en requestMetadata.
                        override fun onAddMediaItems(
                            mediaSession: MediaSession,
                            controller: MediaSession.ControllerInfo,
                            mediaItems: MutableList<MediaItem>,
                        ): ListenableFuture<MutableList<MediaItem>> {
                            val resolved =
                                mediaItems
                                    .map { item ->
                                        val uri = item.requestMetadata.mediaUri ?: item.localConfiguration?.uri
                                        if (uri != null) item.buildUpon().setUri(uri).build() else item
                                    }.toMutableList()
                            return Futures.immediateFuture(resolved)
                        }
                    },
                ).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        artLoader?.release()
        artLoader = null
        super.onDestroy()
    }
}
