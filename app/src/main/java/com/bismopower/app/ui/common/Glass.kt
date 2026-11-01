package com.bismopower.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bismopower.app.ui.theme.LocalBismo
import com.bismopower.app.ui.theme.LocalSolid
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** Esquinas del sistema de diseño. */
object Corners {
    val card = RoundedCornerShape(20.dp)
    val button = RoundedCornerShape(14.dp)
    val bar = RoundedCornerShape(26.dp)
}

/** Lo que hay detrás de las barras flotantes; los paneles con `blur = true` lo desenfocan. */
val LocalHaze = staticCompositionLocalOf<HazeState?> { null }

/**
 * Panel de vidrio: blanco translúcido con borde fino de 1 dp y sombra muy suave.
 *
 * Con `blur = true` (barras flotantes, mini reproductor) desenfoca lo que pasa por detrás con Haze.
 * En Android 8 a 11 o con "Reducir transparencia" se vuelve sólido al 85 % y sin desenfoque.
 * Las tarjetas de las listas no desenfocan: detrás solo tienen las manchas del fondo, que ya
 * están difuminadas, así que el resultado es el mismo y la app va más fluida.
 */
@Composable
fun Modifier.glass(
    shape: Shape = Corners.card,
    strong: Boolean = false,
    blur: Boolean = false,
    shadow: Boolean = true,
): Modifier {
    val c = LocalBismo.current
    val solid = LocalSolid.current
    val haze = LocalHaze.current
    val tint =
        when {
            solid -> c.solid
            strong -> c.glassStrong
            else -> c.glass
        }
    val base =
        this
            .then(if (shadow) Modifier.shadow(if (c.dark) 0.dp else 6.dp, shape, ambientColor = SHADOW, spotColor = SHADOW) else Modifier)
            .clip(shape)
    val filled =
        if (blur && !solid && haze != null) {
            base.hazeEffect(
                haze,
                HazeStyle(
                    backgroundColor = c.bg,
                    tints = listOf(HazeTint(tint)),
                    blurRadius = 22.dp,
                    noiseFactor = 0f,
                ),
            )
        } else {
            base.background(tint, shape)
        }
    return filled.border(1.dp, if (solid) c.track else c.border, shape)
}

private val SHADOW = Color(0x2E1B2440)

/**
 * Trozo de una tarjeta de vidrio larga dentro de una lista perezosa: la fila [index] de [count].
 * Solo la primera y la última tienen esquinas, así las filas se ven como una sola tarjeta.
 */
@Composable
fun Modifier.glassSegment(
    index: Int,
    count: Int,
): Modifier {
    val c = LocalBismo.current
    val solid = LocalSolid.current
    val top = if (index == 0) 20.dp else 0.dp
    val bottom = if (index == count - 1) 20.dp else 0.dp
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    return this.background(if (solid) c.solid else c.glass, shape)
}

/** Fondo con 4 manchas grandes de color muy difuminadas (degradados radiales). */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    seed: Int = 0,
    colors: List<Color>? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalBismo.current
    val blobs = colors ?: c.blobs
    val alpha = if (c.dark) 0.6f else 0.95f
    Box(
        modifier
            .fillMaxSize()
            .background(c.bg)
            .drawBehind {
                val w = size.width
                val h = size.height
                SPOTS.forEachIndexed { i, (x, y, r) ->
                    val color = blobs[(i + seed) % blobs.size]
                    val center = Offset(x * w, y * h)
                    val radius = r * w
                    drawCircle(
                        brush =
                            Brush.radialGradient(
                                colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
                                center = center,
                                radius = radius,
                            ),
                        radius = radius,
                        center = center,
                    )
                }
            },
        content = content,
    )
}

private val SPOTS =
    listOf(
        Triple(0.05f, 0.04f, 0.85f),
        Triple(1.02f, 0.30f, 0.80f),
        Triple(-0.05f, 0.72f, 0.75f),
        Triple(0.95f, 0.98f, 0.70f),
    )

/**
 * Degradado de respaldo cuando no hay carátula: siempre el mismo para el mismo nombre, pero
 * distinto entre nombres distintos (el tono sale del nombre, así hay cientos de combinaciones).
 * Colores pastel vivos, como los de Bruma.
 */
fun gradientFor(key: String): List<Color> {
    val h = key.hashCode()
    val hue = ((h ushr 1) % 360).toFloat()
    val shift = 30f + ((h ushr 9) % 60)
    return listOf(
        Color.hsv(hue, 0.42f, 0.98f),
        Color.hsv((hue + shift) % 360f, 0.58f, 0.90f),
    )
}

/** Miniatura o carátula; si no hay imagen (o no carga) se ve el degradado. */
@Composable
fun Thumb(
    model: Any?,
    key: String,
    modifier: Modifier,
    corner: Dp = 12.dp,
    icon: ImageVector? = null,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(gradientFor(key))),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
