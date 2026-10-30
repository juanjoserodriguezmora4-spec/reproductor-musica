package com.bismopower.app.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bismopower.app.ui.common.fmtDuration
import com.bismopower.app.ui.theme.T
import kotlin.math.roundToInt
import kotlin.random.Random

private const val BARS = 56

/**
 * Barra de progreso en forma de onda, al estilo de los reproductores avanzados.
 *
 * Por ahora la forma es decorativa: se genera a partir del id de la canción, así cada canción
 * tiene siempre la misma onda. La onda real (leída del audio) llega en una fase posterior.
 *
 * Gestos: tocar en cualquier punto salta ahí; arrastrar muestra el tiempo en una burbuja
 * y salta al soltar.
 */
@Composable
fun WaveSeekBar(
    trackKey: Long?,
    fraction: Float,
    durationMs: Long,
    accent: Color,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val heights = remember(trackKey) { waveFor(trackKey) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) dragFraction else fraction.coerceIn(0f, 1f)

    // Al cambiar de canción las barras "crecen" desde la línea central.
    val grow = remember { Animatable(0f) }
    LaunchedEffect(trackKey) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    // Mientras se arrastra la onda se hace un poco más alta, para que se note qué se está tocando.
    val lift by animateFloatAsState(if (dragging) 1.18f else 1f, tween(160), label = "lift")

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPx = with(density) { maxWidth.toPx() }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(top = 14.dp)
                .pointerInput(Unit) {
                    detectTapGestures { pos -> onSeek((pos.x / size.width).coerceIn(0f, 1f)) }
                }.pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { pos ->
                            dragging = true
                            dragFraction = (pos.x / size.width).coerceIn(0f, 1f)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            onSeek(dragFraction)
                            dragging = false
                        },
                        onDragCancel = { dragging = false },
                    )
                },
        ) {
            val gap = 3.dp.toPx()
            val barW = (size.width - gap * (BARS - 1)) / BARS
            val mid = size.height / 2f
            val played = shown * size.width
            for (i in 0 until BARS) {
                val x = i * (barW + gap)
                val h = (heights[i] * size.height * grow.value * lift).coerceAtLeast(barW)
                val color =
                    when {
                        x + barW <= played -> accent
                        x < played -> lerpColor(Color.White.copy(alpha = 0.28f), accent, (played - x) / barW)
                        else -> Color.White.copy(alpha = 0.28f)
                    }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, mid - h / 2f),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(barW / 2f, barW / 2f),
                )
            }
        }

        // Burbuja con el tiempo mientras se arrastra.
        if (dragging) {
            val bubbleW = with(density) { 64.dp.toPx() }
            val x = (dragFraction * widthPx - bubbleW / 2f).coerceIn(0f, widthPx - bubbleW)
            Box(
                Modifier
                    .offset { IntOffset(x.roundToInt(), with(density) { (-12).dp.roundToPx() }) }
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(fmtDuration((dragFraction * durationMs).toLong()), style = T.label, color = Color.Black.copy(alpha = 0.85f))
            }
        }
    }
}

private fun lerpColor(
    a: Color,
    b: Color,
    t: Float,
): Color = androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))

/** Alturas (0..1) de la onda decorativa: suaves y con forma de canción (más baja al inicio y al final). */
private fun waveFor(key: Long?): FloatArray {
    val r = Random(key ?: 7L)
    val raw = FloatArray(BARS) { 0.25f + r.nextFloat() * 0.75f }
    return FloatArray(BARS) { i ->
        val prev = raw[(i - 1).coerceAtLeast(0)]
        val next = raw[(i + 1).coerceAtMost(BARS - 1)]
        val smooth = (prev + raw[i] * 2f + next) / 4f
        val edge = (minOf(i, BARS - 1 - i) / 6f).coerceAtMost(1f)
        (smooth * (0.45f + 0.55f * edge)).coerceIn(0.12f, 1f)
    }
}
