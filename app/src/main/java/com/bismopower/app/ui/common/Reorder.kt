package com.bismopower.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * Reordenar filas de una lista arrastrando un asa (≡).
 *
 * Todas las filas deben medir lo mismo ([rememberReorderState] recibe ese alto): así, con lo
 * que se arrastró el dedo, se sabe a qué posición va la fila sin medir nada. Mientras arrastras,
 * las demás filas se corren para hacerle espacio.
 */
@Stable
class ReorderState internal constructor(
    val itemHeightPx: Float,
) {
    var from by mutableIntStateOf(-1)
        private set
    var offset by mutableFloatStateOf(0f)
        private set
    internal var count = 0

    val dragging: Boolean get() = from >= 0

    /** Posición donde caería la fila si sueltas ahora. */
    val to: Int
        get() = if (from < 0 || count == 0) -1 else (from + (offset / itemHeightPx).roundToInt()).coerceIn(0, count - 1)

    internal fun start(index: Int) {
        from = index
        offset = 0f
    }

    internal fun drag(dy: Float) {
        offset += dy
    }

    /** Termina el arrastre y devuelve (desde, hasta) si la fila cambió de lugar. */
    internal fun end(): Pair<Int, Int>? {
        val result = if (from >= 0 && to != from) from to to else null
        from = -1
        offset = 0f
        return result
    }

    /** Cuánto se corre la fila [index] para dejar el hueco. */
    fun shiftFor(index: Int): Float {
        val t = to
        return when {
            from < 0 || index == from -> 0f
            from < t && index in (from + 1)..t -> -itemHeightPx
            from > t && index in t until from -> itemHeightPx
            else -> 0f
        }
    }
}

@Composable
fun rememberReorderState(itemHeight: Dp): ReorderState {
    val px = with(LocalDensity.current) { itemHeight.toPx() }
    return remember(px) { ReorderState(px) }
}

/** Se pone en cada fila: la mueve con el dedo (la arrastrada) o la corre para dejar hueco (las demás). */
@Composable
fun Modifier.reorderItem(
    state: ReorderState,
    index: Int,
    count: Int,
): Modifier {
    state.count = count
    val isDragged = state.from == index
    // Al soltar, todo vuelve a su sitio de golpe (en ese mismo instante la lista ya está reordenada).
    val shift by animateFloatAsState(
        state.shiftFor(index),
        if (state.dragging) tween(150) else snap(),
        label = "shift",
    )
    return this
        .zIndex(if (isDragged) 1f else 0f)
        .graphicsLayer {
            translationY = if (isDragged) state.offset else shift
            val s = if (isDragged) 1.03f else 1f
            scaleX = s
            scaleY = s
            shadowElevation = if (isDragged) 12f else 0f
        }
}

/** Se pone en el asa (≡) de la fila. [onMove] recibe (desde, hasta) al soltar. */
@Composable
fun Modifier.dragHandle(
    state: ReorderState,
    index: Int,
    onMove: (Int, Int) -> Unit,
): Modifier {
    val i by rememberUpdatedState(index)
    val move by rememberUpdatedState(onMove)
    return this.pointerInput(state) {
        detectDragGestures(
            onDragStart = { state.start(i) },
            onDrag = { change, amount ->
                change.consume()
                state.drag(amount.y)
            },
            onDragEnd = { state.end()?.let { (a, b) -> move(a, b) } },
            onDragCancel = { state.end() },
        )
    }
}

/** Mueve un elemento de una lista (para reordenar en pantalla antes de guardar). */
fun <T> List<T>.moved(
    from: Int,
    to: Int,
): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    val m = toMutableList()
    m.add(to, m.removeAt(from))
    return m
}
