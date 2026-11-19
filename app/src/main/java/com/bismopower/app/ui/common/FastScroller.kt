package com.bismopower.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bismopower.app.ui.theme.LocalBismo
import com.bismopower.app.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Lo que la barra de desplazamiento necesita saber de una lista, sea de filas o de cuadrícula.
 * Las lecturas van dentro de lambdas para que la barra se mueva sin recomponer la pantalla.
 */
class ScrollSource(
    val total: () -> Int,
    val first: () -> Int,
    val visible: () -> Int,
    val scrolling: () -> Boolean,
    val jumpTo: suspend (Int) -> Unit,
)

@Composable
fun rememberScrollSource(state: LazyListState): ScrollSource = remember(state) { state.asScrollSource() }

@Composable
fun rememberScrollSource(state: LazyGridState): ScrollSource = remember(state) { state.asScrollSource() }

private fun LazyListState.asScrollSource(): ScrollSource =
    ScrollSource(
        total = { layoutInfo.totalItemsCount },
        first = { firstVisibleItemIndex },
        visible = { layoutInfo.visibleItemsInfo.size },
        scrolling = { isScrollInProgress },
        jumpTo = { scrollToItem(it) },
    )

private fun LazyGridState.asScrollSource(): ScrollSource =
    ScrollSource(
        total = { layoutInfo.totalItemsCount },
        first = { firstVisibleItemIndex },
        visible = { layoutInfo.visibleItemsInfo.size },
        scrolling = { isScrollInProgress },
        jumpTo = { scrollToItem(it) },
    )

/**
 * Barra a la derecha para subir y bajar rápido por una lista larga.
 *
 * Aparece cuando mueves la lista y se esconde sola 1,5 s después. Arrastra el asa para ir a
 * cualquier parte: al lado sale una burbuja con [label] (la letra, el artista...). Si [marker]
 * no es null, un punto marca dónde está la canción que suena; tocarlo lleva hasta ella.
 * Escondida no recibe toques, así no estorba a los botones de la orilla.
 *
 * Va encima de la lista, dentro de un Box que ocupe lo mismo que ella.
 */
@Composable
fun FastScroller(
    source: ScrollSource,
    modifier: Modifier = Modifier,
    minItems: Int = 30,
    label: (Int) -> String? = { null },
    marker: Int? = null,
) {
    val c = LocalBismo.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val labelFor by rememberUpdatedState(label)

    val long by remember(source) { derivedStateOf { source.total() >= minItems && source.total() > source.visible() + 2 } }
    if (!long) return

    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var shown by remember { mutableStateOf(false) }
    val scrolling by remember(source) { derivedStateOf { source.scrolling() } }
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) {
            shown = true
        } else {
            delay(1500)
            shown = false
        }
    }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(if (shown) 150 else 300), label = "scroller")

    // Dónde va el asa (0 = arriba, 1 = abajo) según lo que se ve de la lista.
    val listFraction by remember(source) {
        derivedStateOf {
            val room = (source.total() - source.visible()).coerceAtLeast(1)
            (source.first().toFloat() / room).coerceIn(0f, 1f)
        }
    }
    fun indexAt(f: Float): Int {
        val room = (source.total() - source.visible()).coerceAtLeast(0)
        return (f * room).roundToInt().coerceIn(0, (source.total() - 1).coerceAtLeast(0))
    }

    BoxWithConstraints(
        modifier
            .fillMaxHeight()
            .width(TOUCH_WIDTH)
            .alpha(alpha),
    ) {
        val density = LocalDensity.current
        val trackPx = with(density) { maxHeight.toPx() }
        val thumbPx = with(density) { THUMB_HEIGHT.toPx() }
        val travel = (trackPx - thumbPx).coerceAtLeast(1f)

        // Riel fino.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = 4.dp)
                .width(3.dp)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(c.muted.copy(alpha = 0.18f)),
        )

        // Punto de la canción que suena.
        if (marker != null && shown) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        // Se calcula al acomodar (no al componer) para no recomponer en cada cuadro.
                        val room = (source.total() - source.visible()).coerceAtLeast(1)
                        val f = (marker.toFloat() / room).coerceIn(0f, 1f)
                        IntOffset(0, (f * travel + thumbPx / 2f - 14.dp.toPx()).roundToInt())
                    }
                    .size(28.dp)
                    .pointerInput(marker) {
                        detectTapGestures {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { source.jumpTo((marker - 2).coerceAtLeast(0)) }
                        }
                    },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .padding(end = 1.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(c.accent)
                        .border(1.5.dp, c.bg, CircleShape),
                )
            }
        }

        // Asa: se arrastra para moverse por la lista.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, ((if (dragging) dragFraction else listFraction) * travel).roundToInt()) }
                .size(width = TOUCH_WIDTH, height = THUMB_HEIGHT)
                .then(
                    if (shown) {
                        Modifier.pointerInput(source, travel) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragFraction = listFraction
                                    dragging = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false },
                            ) { change, dy ->
                                change.consume()
                                dragFraction = (dragFraction + dy / travel).coerceIn(0f, 1f)
                                val target = indexAt(dragFraction)
                                scope.launch { source.jumpTo(target) }
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier
                    .padding(end = 1.dp)
                    .size(width = if (dragging) 9.dp else 7.dp, height = THUMB_HEIGHT - 6.dp)
                    .clip(CircleShape)
                    .background(if (dragging) c.accent else c.muted.copy(alpha = 0.75f)),
            )
        }

        // Burbuja con la letra o el nombre de donde vas.
        if (dragging) {
            val text = labelFor(indexAt(dragFraction))
            if (!text.isNullOrBlank()) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset {
                            IntOffset(
                                -(TOUCH_WIDTH + 6.dp).roundToPx(),
                                (dragFraction * travel + thumbPx / 2f - 22.dp.toPx()).roundToInt(),
                            )
                        }
                        // Más ancha que la barra: crece hacia la izquierda.
                        .wrapContentWidth(Alignment.End, unbounded = true),
                ) {
                    Text(
                        text,
                        style = if (text.length <= 2) T.big else T.label,
                        color = c.onAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .widthIn(min = 44.dp, max = 220.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(c.accent)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

private val TOUCH_WIDTH = 30.dp
private val THUMB_HEIGHT = 46.dp
