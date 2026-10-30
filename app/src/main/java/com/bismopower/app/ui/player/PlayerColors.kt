package com.bismopower.app.ui.player

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.bismopower.app.ui.common.gradientFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Colores del reproductor sacados de la carátula: el acento de los controles y dos brillos para el fondo. */
@Immutable
data class PlayerColors(
    val accent: Color,
    val glowTop: Color,
    val glowBottom: Color,
)

/**
 * Como en los reproductores grandes, cada canción tiñe la pantalla con su carátula.
 * El acento se aclara si hace falta para que siempre se lea sobre el fondo oscuro.
 * Sin carátula se usan los degradados de Bruma según el nombre del álbum.
 */
@Composable
fun rememberPlayerColors(
    artwork: String?,
    albumKey: String,
): PlayerColors {
    val context = LocalContext.current
    val raw by produceState<List<Color>?>(null, artwork) {
        value =
            if (artwork == null) {
                null
            } else {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val request =
                            ImageRequest
                                .Builder(context)
                                .data(artwork)
                                .size(96)
                                .allowHardware(false)
                                .build()
                        val result = context.imageLoader.execute(request) as? SuccessResult
                        val bitmap = (result?.drawable as? BitmapDrawable)?.bitmap ?: return@runCatching null
                        val p = Palette.from(bitmap).maximumColorCount(16).generate()
                        val main = p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.dominantSwatch ?: return@runCatching null
                        val top = p.darkVibrantSwatch ?: p.mutedSwatch ?: main
                        val bottom = p.darkMutedSwatch ?: p.mutedSwatch ?: main
                        listOf(Color(main.rgb), Color(top.rgb), Color(bottom.rgb))
                    }.getOrNull()
                }
            }
    }

    val fallback = gradientFor(albumKey)
    val base = raw ?: listOf(fallback[0], fallback[1], fallback[0])

    // El acento tiene que verse sobre un fondo casi negro: si es muy oscuro, se aclara.
    val accentTarget = base[0].let { if (it.luminance() < 0.35f) lerp(it, Color.White, 0.45f) else it }
    val accent by animateColorAsState(accentTarget, tween(500), label = "accent")
    val top by animateColorAsState(base[1], tween(700), label = "glowTop")
    val bottom by animateColorAsState(base[2], tween(700), label = "glowBottom")
    return PlayerColors(accent, top, bottom)
}
