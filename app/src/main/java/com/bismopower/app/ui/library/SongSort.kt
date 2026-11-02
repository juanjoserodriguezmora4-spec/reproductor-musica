package com.bismopower.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.bismopower.app.data.db.TrackEntity
import com.bismopower.app.ui.common.BI
import com.bismopower.app.ui.common.Corners
import com.bismopower.app.ui.common.glass
import com.bismopower.app.ui.theme.LocalBismo
import com.bismopower.app.ui.theme.T
import java.text.Collator
import java.util.Locale
import kotlin.random.Random

/** Formas de ordenar la lista de Canciones. */
enum class SongSort(
    val id: String,
    val label: String,
    val icon: ImageVector,
) {
    TITLE("TITLE", "Nombre", BI.Sort),
    ARTIST("ARTIST", "Artista", BI.User),
    ALBUM("ALBUM", "Álbum", BI.Disc),
    DATE("DATE", "Agregadas hace poco", BI.History),
    DURATION("DURATION", "Duración", BI.Timer),
    SHUFFLE("SHUFFLE", "Aleatorio", BI.Shuffle),
    ;

    companion object {
        fun of(id: String): SongSort = entries.firstOrNull { it.id == id } ?: TITLE
    }
}

/** Compara textos como en español: sin importar tildes ni mayúsculas ("Ángel" va con la A). */
private val spanish: Collator = Collator.getInstance(Locale.forLanguageTag("es")).apply { strength = Collator.PRIMARY }

/** La lista en el orden elegido. "Aleatorio" usa la semilla guardada, así no cambia sola. */
fun List<TrackEntity>.sortedFor(
    sort: SongSort,
    seed: Long,
): List<TrackEntity> =
    when (sort) {
        SongSort.TITLE -> sortedWith(compareBy<TrackEntity, String>(spanish) { it.title })
        SongSort.ARTIST ->
            sortedWith(
                compareBy<TrackEntity, String>(spanish) { it.artist }
                    .thenBy(spanish) { it.album }
                    .thenBy { it.trackNumber },
            )
        SongSort.ALBUM ->
            sortedWith(
                compareBy<TrackEntity, String>(spanish) { it.album }
                    .thenBy { it.trackNumber }
                    .thenBy(spanish) { it.title },
            )
        SongSort.DATE -> sortedByDescending { it.dateAdded }
        SongSort.DURATION -> sortedByDescending { it.durationMs }
        SongSort.SHUFFLE -> sortedBy { it.id }.shuffled(Random(seed))
    }

/**
 * Botón pequeño con el orden actual. Abre un menú para elegir otro orden o mezclar la lista.
 * Con "Aleatorio" la lista misma se mezcla y "Reproducir" la toca en ese orden, una tras otra.
 */
@Composable
fun SortButton(
    current: SongSort,
    onPick: (SongSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalBismo.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .glass(Corners.button, strong = true, shadow = false)
                .clickable { open = true }
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(current.icon, null, tint = c.accent, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text(if (current == SongSort.SHUFFLE) "Mezclada" else current.label, style = T.label, color = c.text, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Icon(BI.Down, null, tint = c.muted, modifier = Modifier.size(15.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            offset = DpOffset(0.dp, 6.dp),
            shape = RoundedCornerShape(16.dp),
            containerColor = if (c.dark) Color(0xFF1A2030) else Color.White,
        ) {
            Text(
                "Ordenar por",
                style = T.small,
                color = c.muted,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 4.dp),
            )
            SongSort.entries.filter { it != SongSort.SHUFFLE }.forEach { option ->
                MenuRow(option.icon, option.label, selected = option == current) {
                    open = false
                    onPick(option)
                }
            }
            HorizontalDivider(color = c.track, modifier = Modifier.padding(vertical = 4.dp))
            MenuRow(
                BI.Shuffle,
                if (current == SongSort.SHUFFLE) "Mezclar otra vez" else "Mezclar la lista",
                selected = current == SongSort.SHUFFLE,
            ) {
                open = false
                onPick(SongSort.SHUFFLE)
            }
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalBismo.current
    DropdownMenuItem(
        text = { Text(text, style = T.body, color = if (selected) c.accent else c.text) },
        onClick = onClick,
        leadingIcon = { Icon(icon, null, tint = if (selected) c.accent else c.muted, modifier = Modifier.size(19.dp)) },
        trailingIcon = if (selected) ({ Icon(BI.Check, null, tint = c.accent, modifier = Modifier.size(18.dp)) }) else null,
    )
}
