package com.bismopower.app.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bismopower.app.player.QueueItem
import com.bismopower.app.ui.common.BI
import com.bismopower.app.ui.common.Thumb
import com.bismopower.app.ui.common.dragHandle
import com.bismopower.app.ui.common.moved
import com.bismopower.app.ui.common.rememberReorderState
import com.bismopower.app.ui.common.reorderItem
import com.bismopower.app.ui.theme.T
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SheetColor = Color(0xF2121622)

/** Alto fijo de cada fila de la cola (lo necesita el reordenar arrastrando). */
private val QUEUE_ROW = 60.dp

/**
 * Cola de reproducción: sube desde abajo encima del reproductor.
 * Tocar una canción salta a ella; la X la quita de la cola y el asa ≡ la cambia de lugar.
 * Se cierra arrastrando la parte
 * de arriba hacia abajo, tocando fuera o con "atrás".
 */
@Composable
fun QueueSheet(
    items: List<QueueItem>,
    currentIndex: Int,
    accent: Color,
    onPlay: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dragY = remember { Animatable(0f) }
    // Abre con la canción actual a la vista (dos filas por encima para dar contexto).
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0))

    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.78f)
            .offset { IntOffset(0, dragY.value.roundToInt()) }
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(SheetColor)
            .navigationBarsPadding(),
    ) {
        // Cabecera: es la parte que se arrastra para cerrar.
        Column(
            Modifier
                .fillMaxWidth()
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { d -> scope.launch { dragY.snapTo((dragY.value + d).coerceAtLeast(0f)) } },
                    onDragStopped = { velocity ->
                        if (dragY.value > with(density) { 110.dp.toPx() } || velocity > 2000f) {
                            onClose()
                        } else {
                            dragY.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f))
                        }
                    },
                ).padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.3f)),
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Cola", style = T.h2, color = Color.White)
                    Text(
                        if (items.size == 1) "1 canción" else "${items.size} canciones",
                        style = T.tiny,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
                Icon(
                    BI.Close,
                    "Cerrar la cola",
                    tint = Color.White,
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable { onClose() }
                            .padding(9.dp),
                )
            }
        }

        // Orden en pantalla: al soltar una fila se reordena aquí al instante y luego en el reproductor.
        var order by remember(items) { mutableStateOf(items) }
        val reorder = rememberReorderState(QUEUE_ROW)
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth(), userScrollEnabled = !reorder.dragging) {
            itemsIndexed(order, key = { _, it -> "${it.index}-${it.id}" }) { pos, item ->
                val current = item.index == currentIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .reorderItem(reorder, pos, order.size)
                        .background(
                            when {
                                reorder.from == pos -> Color(0xFF232A3B)
                                current -> accent.copy(alpha = 0.14f)
                                else -> Color.Transparent
                            },
                        ).clickable { onPlay(item.index) }
                        .padding(end = 2.dp)
                        .height(QUEUE_ROW),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Barra de color a la izquierda de la canción que está sonando.
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(36.dp)
                            .background(if (current) accent else Color.Transparent),
                    )
                    Spacer(Modifier.width(15.dp))
                    Thumb(item.artwork, item.artKey, Modifier.size(44.dp), corner = 10.dp, icon = BI.Music)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title,
                            style = T.title,
                            color = if (current) accent else Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (item.artist.isNotBlank()) {
                            Text(
                                item.artist,
                                style = T.small,
                                color = Color.White.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (current) {
                        Icon(BI.Wave, "Sonando ahora", tint = accent, modifier = Modifier.padding(8.dp).size(20.dp))
                    } else {
                        Icon(
                            BI.Close,
                            "Quitar de la cola",
                            tint = Color.White.copy(alpha = 0.55f),
                            modifier =
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable { onRemove(item.index) }
                                    .padding(10.dp),
                        )
                    }
                    Icon(
                        BI.Grip,
                        "Arrastra para mover ${item.title}",
                        tint = Color.White.copy(alpha = 0.55f),
                        modifier =
                            Modifier
                                .size(44.dp)
                                .dragHandle(reorder, pos) { from, to ->
                                    order = order.moved(from, to)
                                    onMove(from, to)
                                }.padding(11.dp),
                    )
                }
            }
        }
    }
}
