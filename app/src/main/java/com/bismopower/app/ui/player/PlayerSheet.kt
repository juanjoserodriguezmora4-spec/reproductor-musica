package com.bismopower.app.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.bismopower.app.container
import com.bismopower.app.ui.common.BI
import com.bismopower.app.ui.common.GlassBackground
import com.bismopower.app.ui.common.IconChip
import com.bismopower.app.ui.common.Thumb
import com.bismopower.app.ui.common.fmtDuration
import com.bismopower.app.ui.common.glass
import com.bismopower.app.ui.theme.LocalBismo
import com.bismopower.app.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Reproductor completo: hoja que sube, se cierra con el botón, con "atrás" o deslizando hacia abajo. */
@Composable
fun PlayerSheet(onClose: () -> Unit) {
    val c = LocalBismo.current
    val player = LocalContext.current.container.player
    val s by player.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var position by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val offset = remember { Animatable(0f) }

    LaunchedEffect(s.currentId, s.isPlaying) {
        position = player.position()
        while (s.isPlaying) {
            delay(500)
            if (!dragging) position = player.position()
        }
    }

    val blobs = rememberArtworkColors(s.artwork, s.album.ifBlank { s.title })

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val closeAt = with(density) { maxHeight.toPx() } * 0.22f
        GlassBackground(
            Modifier
                .offset { IntOffset(0, offset.value.roundToInt()) }
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { d -> scope.launch { offset.snapTo((offset.value + d).coerceAtLeast(0f)) } },
                    onDragStopped = { velocity ->
                        if (offset.value > closeAt || velocity > 2400f) {
                            onClose()
                        } else {
                            offset.animateTo(0f, tween(200))
                        }
                    },
                ),
            colors = blobs,
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Asa para deslizar
                Box(
                    Modifier
                        .padding(bottom = 10.dp)
                        .size(width = 38.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(c.muted.copy(alpha = 0.4f)),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconChip(BI.Down, onClose, description = "Cerrar reproductor")
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("REPRODUCIENDO", style = T.overline, color = c.muted)
                        if (s.count > 1) Text("${s.index + 1} de ${s.count}", style = T.tiny, color = c.muted)
                    }
                    Spacer(Modifier.size(36.dp))
                }

                if (s.currentId == null) {
                    Spacer(Modifier.weight(1f))
                    Text("No hay nada sonando", style = T.h2, color = c.text)
                    Spacer(Modifier.height(6.dp))
                    Text("Elige una canción en tu biblioteca.", style = T.small, color = c.muted)
                    Spacer(Modifier.weight(1f))
                    return@Column
                }

                Spacer(Modifier.weight(0.6f))
                // La carátula se encoge en pantallas bajas para que los controles siempre quepan.
                Thumb(
                    s.artwork,
                    s.album.ifBlank { s.title },
                    Modifier
                        .weight(6f, fill = false)
                        .aspectRatio(1f)
                        .glass(RoundedCornerShape(20.dp)),
                    corner = 20.dp,
                    icon = BI.Music,
                )
                Spacer(Modifier.weight(0.6f))

                Text(
                    s.title,
                    style = T.big,
                    color = c.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    listOf(s.artist, s.album).filter { it.isNotBlank() }.joinToString(" · "),
                    style = T.body,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))

                val dur = s.durationMs.coerceAtLeast(1L)
                Slider(
                    value = if (dragging) dragValue else (position.toFloat() / dur).coerceIn(0f, 1f),
                    onValueChange = {
                        dragging = true
                        dragValue = it
                    },
                    onValueChangeFinished = {
                        player.seek((dragValue * dur).toLong())
                        position = (dragValue * dur).toLong()
                        dragging = false
                    },
                    colors =
                        SliderDefaults.colors(
                            thumbColor = c.accent,
                            activeTrackColor = c.accent,
                            inactiveTrackColor = c.track,
                        ),
                )
                Row(Modifier.fillMaxWidth()) {
                    val shown = if (dragging) (dragValue * dur).toLong() else position
                    Text(fmtDuration(shown), style = T.tiny, color = c.muted, modifier = Modifier.weight(1f))
                    Text(fmtDuration(s.durationMs), style = T.tiny, color = c.muted)
                }
                Spacer(Modifier.height(10.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(26.dp), shadow = false)
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlIcon(BI.Shuffle, if (s.shuffle) c.accent else c.muted, "Aleatorio") { player.setShuffle(!s.shuffle) }
                    ControlIcon(BI.Prev, c.text, "Anterior", size = 28) { player.previous() }
                    Box(
                        Modifier
                            .size(66.dp)
                            .clip(CircleShape)
                            .background(c.accent)
                            .clickable { player.toggle() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (s.isPlaying) BI.Pause else BI.Play,
                            if (s.isPlaying) "Pausar" else "Reproducir",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    ControlIcon(BI.Next, c.text, "Siguiente", size = 28) { player.next() }
                    ControlIcon(
                        if (s.repeat == Player.REPEAT_MODE_ONE) BI.RepeatOne else BI.Repeat,
                        if (s.repeat == Player.REPEAT_MODE_OFF) c.muted else c.accent,
                        when (s.repeat) {
                            Player.REPEAT_MODE_ONE -> "Repetir una"
                            Player.REPEAT_MODE_ALL -> "Repetir todas"
                            else -> "Sin repetición"
                        },
                    ) { player.cycleRepeat() }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ControlIcon(
    icon: ImageVector,
    tint: Color,
    description: String,
    size: Int = 22,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(size.dp))
    }
}
