package com.bismopower.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bismopower.app.data.db.TrackEntity
import com.bismopower.app.ui.theme.LocalBismo
import com.bismopower.app.ui.theme.T

/**
 * Canciones seleccionadas (al mantener presionada una canción).
 * La pantalla que muestra canciones pone en [visible] su lista, para "Seleccionar todo" y
 * para que las acciones respeten el orden en que se ven.
 */
@Stable
class SelectionState {
    private val chosen = mutableStateMapOf<Long, TrackEntity>()
    var visible: List<TrackEntity> by mutableStateOf(emptyList())

    val active: Boolean get() = chosen.isNotEmpty()
    val count: Int get() = chosen.size

    fun isSelected(id: Long): Boolean = id in chosen

    fun toggle(t: TrackEntity) {
        if (t.id in chosen) chosen.remove(t.id) else chosen[t.id] = t
    }

    fun selectAll() = visible.forEach { chosen[it.id] = it }

    val allSelected: Boolean get() = visible.isNotEmpty() && visible.all { it.id in chosen }

    fun clear() = chosen.clear()

    /** Las seleccionadas en el orden de la pantalla (y al final las que ya no se ven). */
    fun inOrder(): List<TrackEntity> {
        val shown = visible.filter { it.id in chosen }
        val shownIds = shown.mapTo(HashSet()) { it.id }
        return shown + chosen.values.filter { it.id !in shownIds }
    }
}

val LocalSelection = staticCompositionLocalOf { SelectionState() }

/** Círculo de marcar: vacío o lleno con un visto. */
@Composable
fun SelectCircle(
    on: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = LocalBismo.current
    val scale by animateFloatAsState(if (on) 1f else 0.85f, label = "check")
    Box(
        modifier
            .size(24.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.clip(CircleShape)
            .then(if (on) Modifier.background(c.accent) else Modifier.border(2.dp, c.muted.copy(alpha = 0.7f), CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Icon(BI.Check, null, tint = c.onAccent, modifier = Modifier.size(15.dp))
    }
}

/**
 * Barra que reemplaza al mini reproductor y a las pestañas mientras hay canciones seleccionadas.
 */
@Composable
fun SelectionBar(
    state: SelectionState,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onQueue: () -> Unit,
    onAddToList: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalBismo.current
    Column(
        Modifier
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .fillMaxWidth()
            .glass(Corners.bar, strong = true, blur = true)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                BI.Close,
                "Quitar la selección",
                tint = c.text,
                modifier =
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable { state.clear() }
                        .padding(8.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                plural(state.count, "seleccionada", "seleccionadas"),
                style = T.title.copy(fontFeatureSettings = "tnum"),
                color = c.text,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (state.allSelected) "Ninguna" else "Todas",
                style = T.label,
                color = c.accent,
                modifier =
                    Modifier
                        .clip(Corners.button)
                        .clickable { if (state.allSelected) state.clear() else state.selectAll() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            BarAction(BI.Play, "Tocar", onClick = onPlay)
            BarAction(BI.PlayNext, "Después", onClick = onPlayNext)
            BarAction(BI.Queue, "Cola", onClick = onQueue)
            BarAction(BI.PlaylistAdd, "Lista", onClick = onAddToList)
            BarAction(BI.Heart, "Favorita", onClick = onFavorite)
            BarAction(BI.Trash, "Borrar", tint = c.err, onClick = onDelete)
        }
    }
}

@Composable
private fun BarAction(
    icon: ImageVector,
    label: String,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val c = LocalBismo.current
    Column(
        Modifier
            .clip(Corners.button)
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint ?: c.text, modifier = Modifier.size(21.dp))
        Text(label, style = T.tiny, color = tint ?: c.muted, maxLines = 1)
    }
}
