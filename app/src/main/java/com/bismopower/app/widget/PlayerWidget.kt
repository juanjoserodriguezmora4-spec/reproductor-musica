package com.bismopower.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import com.bismopower.app.R
import com.bismopower.app.container
import com.bismopower.app.player.EXTRA_ROUTE
import com.bismopower.app.ui.MainActivity
import java.util.concurrent.atomic.AtomicBoolean

/** Lo que muestra el widget. */
data class WidgetState(
    val hasMedia: Boolean,
    val title: String = "",
    val artist: String = "",
    val playing: Boolean = false,
    val art: Bitmap? = null,
) {
    companion object {
        val IDLE = WidgetState(hasMedia = false)
    }
}

/**
 * Widget de la pantalla de inicio (4 × 1).
 *
 * Los botones mandan un aviso a esta misma clase, que le da la orden al reproductor. Cuando no
 * hay nada en la cola (por ejemplo después de reiniciar el teléfono), todo el widget abre la app,
 * porque Android no deja empezar a sonar desde el fondo sin una canción elegida.
 */
class PlayerWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        WidgetUpdater.pushLast(context)
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action
        if (action != ACTION_TOGGLE && action != ACTION_NEXT && action != ACTION_PREV) {
            super.onReceive(context, intent)
            return
        }
        // goAsync: el aviso puede esperar unos segundos a que la app se conecte al reproductor.
        val pending = goAsync()
        val finished = AtomicBoolean(false)
        val finish = { if (finished.compareAndSet(false, true)) pending.finish() }
        context.container.player.command { c ->
            when (action) {
                ACTION_TOGGLE -> if (c.isPlaying) c.pause() else c.play()
                ACTION_NEXT -> c.seekToNext()
                ACTION_PREV -> c.seekToPrevious()
            }
            finish()
        }
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 8_000)
    }

    companion object {
        const val ACTION_TOGGLE = "com.bismopower.app.widget.TOGGLE"
        const val ACTION_NEXT = "com.bismopower.app.widget.NEXT"
        const val ACTION_PREV = "com.bismopower.app.widget.PREV"
    }
}

/** Arma el widget y se lo entrega a Android. */
object WidgetUpdater {
    @Volatile private var last: WidgetState? = null

    fun push(
        context: Context,
        state: WidgetState,
    ) {
        last = state
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))
        if (ids.isEmpty()) return
        runCatching { manager.updateAppWidget(ids, build(context, state)) }
    }

    fun pushLast(context: Context) = push(context, last ?: WidgetState.IDLE)

    private fun build(
        context: Context,
        s: WidgetState,
    ): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_player)
        val openApp = openApp(context, s.hasMedia)
        v.setOnClickPendingIntent(R.id.widget_art, openApp)
        v.setOnClickPendingIntent(R.id.widget_text, openApp)

        if (!s.hasMedia) {
            v.setTextViewText(R.id.widget_title, context.getString(R.string.widget_idle_title))
            v.setTextViewText(R.id.widget_artist, context.getString(R.string.widget_idle_artist))
            showPlaceholder(context, v)
            v.setImageViewResource(R.id.widget_play, R.drawable.ic_widget_play)
            v.setContentDescription(R.id.widget_play, context.getString(R.string.widget_play))
            v.setOnClickPendingIntent(R.id.widget_play, openApp)
            v.setOnClickPendingIntent(R.id.widget_prev, openApp)
            v.setOnClickPendingIntent(R.id.widget_next, openApp)
            return v
        }

        v.setTextViewText(R.id.widget_title, s.title)
        v.setTextViewText(R.id.widget_artist, s.artist)
        if (s.art != null) {
            v.setViewPadding(R.id.widget_art, 0, 0, 0, 0)
            v.setImageViewBitmap(R.id.widget_art, s.art)
        } else {
            showPlaceholder(context, v)
        }
        v.setImageViewResource(R.id.widget_play, if (s.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
        v.setContentDescription(R.id.widget_play, context.getString(if (s.playing) R.string.widget_pause else R.string.widget_play))
        v.setOnClickPendingIntent(R.id.widget_play, command(context, PlayerWidget.ACTION_TOGGLE, 1))
        v.setOnClickPendingIntent(R.id.widget_prev, command(context, PlayerWidget.ACTION_PREV, 2))
        v.setOnClickPendingIntent(R.id.widget_next, command(context, PlayerWidget.ACTION_NEXT, 3))
        return v
    }

    /** Sin carátula: el degradado con una nota pequeña en el centro (14 dp de margen). */
    private fun showPlaceholder(
        context: Context,
        v: RemoteViews,
    ) {
        val pad = (14 * context.resources.displayMetrics.density).toInt()
        v.setViewPadding(R.id.widget_art, pad, pad, pad, pad)
        v.setImageViewResource(R.id.widget_art, R.drawable.widget_art_placeholder)
    }

    private fun command(
        context: Context,
        action: String,
        code: Int,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, PlayerWidget::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openApp(
        context: Context,
        toPlayer: Boolean,
    ): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (toPlayer) intent.putExtra(EXTRA_ROUTE, "player")
        return PendingIntent.getActivity(
            context,
            if (toPlayer) 21 else 20,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
