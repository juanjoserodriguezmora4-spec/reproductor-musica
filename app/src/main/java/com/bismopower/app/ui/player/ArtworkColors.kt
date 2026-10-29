package com.bismopower.app.ui.player

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.bismopower.app.ui.common.gradientFor
import com.bismopower.app.ui.theme.LocalBismo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Colores para las manchas del fondo del reproductor, sacados de la carátula con Palette.
 * Sin carátula usa el degradado de Bruma según el nombre del álbum.
 * Se suavizan hacia el tono del tema para que el texto siempre se lea bien.
 */
@Composable
fun rememberArtworkColors(
    artwork: String?,
    albumKey: String,
): List<Color> {
    val context = LocalContext.current
    val c = LocalBismo.current
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
                                .size(96) // Palette no necesita más; así es rápido en celulares viejos.
                                .allowHardware(false)
                                .build()
                        val result = context.imageLoader.execute(request) as? SuccessResult
                        val bitmap = (result?.drawable as? BitmapDrawable)?.bitmap ?: return@runCatching null
                        val p = Palette.from(bitmap).maximumColorCount(16).generate()
                        listOfNotNull(
                            p.vibrantSwatch,
                            p.mutedSwatch,
                            p.lightVibrantSwatch ?: p.lightMutedSwatch,
                            p.darkVibrantSwatch ?: p.darkMutedSwatch,
                        ).map { Color(it.rgb) }.takeIf { it.isNotEmpty() }
                    }.getOrNull()
                }
            }
    }

    val fallback = gradientFor(albumKey)
    val base = raw ?: listOf(fallback[0], fallback[1], c.blobs[1], c.blobs[3])
    // Claro: colores pastel; oscuro: colores profundos, como las manchas de Bruma.
    val target = if (c.dark) Color(0xFF0B0F1A) else Color.White
    val amount = if (c.dark) 0.45f else 0.5f
    return List(4) { i ->
        val color = lerp(base[i % base.size], target, amount)
        val animated by animateColorAsState(color, tween(600), label = "blob$i")
        animated
    }
}
