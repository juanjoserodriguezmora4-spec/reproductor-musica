package com.bismopower.app.ui.player.visualizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.bismopower.app.container
import com.bismopower.app.ui.common.BI
import com.bismopower.app.ui.common.Thumb
import com.bismopower.app.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

/**
 * Visualizador de la canción, en el lugar de la carátula.
 *
 * - Deslizar a la izquierda o la derecha cambia de estilo.
 * - Un toque pausa o reproduce.
 * - El botón de la esquina abre la hoja para elegir estilo y color.
 *
 * Se dibuja 60 veces por segundo mientras suena; en pausa las barras bajan y luego se detiene
 * el dibujo para no gastar batería.
 */
@Composable
fun VisualizerView(
    artwork: String?,
    artKey: String,
    isPlaying: Boolean,
    style: VizStyle,
    colors: List<Color>,
    onStyle: (VizStyle) -> Unit,
    onTap: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val analyzer = LocalContext.current.container.audioEffects.analyzer
    val spectrum = remember { Spectrum(analyzer) }
    val playing by rememberUpdatedState(isPlaying)
    var frame by remember { mutableLongStateOf(0L) }
    var spin by remember { mutableFloatStateOf(0f) }
    var seconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(spectrum) {
        var last = 0L
        while (isActive) {
            withFrameNanos { t ->
                val dt = if (last == 0L) 0f else ((t - last) / 1e9f).coerceIn(0f, 0.1f)
                last = t
                spectrum.update(t, playing)
                seconds += dt
                if (playing) spin = (spin + dt * 360f / 24f) % 360f // una vuelta cada 24 s, como un disco lento
                frame = t
            }
            // En pausa y ya quieto: se deja de dibujar hasta que vuelva a sonar.
            if (!playing && spectrum.level < 0.003f) {
                last = 0L
                delay(150)
            }
        }
    }

    // Nombre del estilo al cambiarlo con el dedo.
    var showName by remember { mutableStateOf(false) }
    LaunchedEffect(style) {
        showName = true
        delay(1_200)
        showName = false
    }
    val current by rememberUpdatedState(style)
    val pick by rememberUpdatedState(onStyle)
    val tap by rememberUpdatedState(onTap)
    val swipeMin = with(LocalDensity.current) { 56.dp.toPx() }

    BoxWithConstraints(
        modifier
            .pointerInput(Unit) { detectTapGestures(onTap = { tap() }) }
            .pointerInput(Unit) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dx += amount
                    },
                    onDragEnd = {
                        if (abs(dx) > swipeMin) {
                            val all = VizStyle.entries
                            val step = if (dx < 0) 1 else -1
                            pick(all[(all.indexOf(current) + step + all.size) % all.size])
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val side = if (maxWidth < maxHeight) maxWidth else maxHeight

        when (style.cover) {
            CoverMode.CIRCLE ->
                Thumb(
                    artwork,
                    artKey,
                    Modifier
                        .size(side * style.coverSize)
                        .graphicsLayer {
                            frame.let { } // se vuelve a dibujar con cada cuadro
                            rotationZ = spin
                            val pulse = 1f + spectrum.energy * 0.05f
                            scaleX = pulse
                            scaleY = pulse
                        }.clip(CircleShape),
                    corner = side,
                    icon = BI.Music,
                )
            CoverMode.SQUARE ->
                Box(
                    Modifier
                        .size(side)
                        .clip(RoundedCornerShape(18.dp)),
                ) {
                    Thumb(artwork, artKey, Modifier.fillMaxSize(), corner = 18.dp, icon = BI.Music)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = style.scrim)),
                    )
                }
            CoverMode.NONE -> Unit
        }

        Canvas(Modifier.fillMaxSize()) {
            frame.let { }
            drawViz(style, spectrum, colors, seconds)
        }

        AnimatedVisibility(
            visible = showName,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Text(
                style.label,
                style = T.label,
                color = Color.White,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .pointerInput(Unit) { detectTapGestures(onTap = { onOpenSettings() }) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(BI.Sliders, "Estilo y color del visualizador", tint = Color.White, modifier = Modifier.size(19.dp))
        }
    }
}
