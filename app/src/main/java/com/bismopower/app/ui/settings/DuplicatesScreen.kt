package com.bismopower.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bismopower.app.container
import com.bismopower.app.data.db.TrackEntity
import com.bismopower.app.library.DuplicateGroup
import com.bismopower.app.library.artKey
import com.bismopower.app.library.artUri
import com.bismopower.app.ui.common.BI
import com.bismopower.app.ui.common.Badge
import com.bismopower.app.ui.common.EmptyState
import com.bismopower.app.ui.common.GlassBackground
import com.bismopower.app.ui.common.IconChip
import com.bismopower.app.ui.common.PrimaryButton
import com.bismopower.app.ui.common.SelectCircle
import com.bismopower.app.ui.common.Thumb
import com.bismopower.app.ui.common.fmtDuration
import com.bismopower.app.ui.common.glass
import com.bismopower.app.ui.common.plural
import com.bismopower.app.ui.sheets.LocalDeleter
import com.bismopower.app.ui.theme.LocalBismo
import com.bismopower.app.ui.theme.T
import java.io.File
import java.util.Locale

/**
 * Canciones que están más de una vez en el teléfono. De cada grupo se marca para quedarse la
 * copia más pesada (normalmente la de mejor calidad) y las demás quedan listas para borrar.
 * Puedes desmarcar las que quieras conservar.
 */
@Composable
fun DuplicatesScreen(onClose: () -> Unit) {
    val c = LocalBismo.current
    val app = LocalContext.current.container
    val deleter = LocalDeleter.current
    var groups by remember { mutableStateOf<List<DuplicateGroup>?>(null) }
    var sizes by remember { mutableStateOf<Map<Long, Long>>(emptyMap()) }
    var marked by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        val (found, s) = app.library.duplicates()
        groups = found
        sizes = s
        marked = found.flatMap { g -> g.extras.map { it.id } }.toSet()
    }

    val list = groups
    val toDelete: List<TrackEntity> = list?.flatMap { it.extras }?.filter { it.id in marked } ?: emptyList()

    GlassBackground(seed = 3) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconChip(BI.Back, onClose, description = "Volver")
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Canciones repetidas", style = T.h2, color = c.text)
                    Text(
                        when {
                            list == null -> "Buscando…"
                            list.isEmpty() -> "No hay copias"
                            else -> plural(list.size, "canción repetida", "canciones repetidas")
                        },
                        style = T.small,
                        color = c.muted,
                    )
                }
            }

            Box(Modifier.weight(1f)) {
                when {
                    list == null ->
                        CircularProgressIndicator(
                            Modifier
                                .align(Alignment.Center)
                                .size(28.dp),
                            color = c.accent,
                            strokeWidth = 2.5.dp,
                        )
                    list.isEmpty() ->
                        EmptyState(
                            BI.Check,
                            "Todo en orden",
                            "No encontramos canciones repetidas en tus carpetas de música.",
                            Modifier.padding(16.dp),
                        )
                    else ->
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            item(key = "hint") {
                                Text(
                                    "Se queda la copia más pesada (suele ser la de mejor calidad). Toca una copia para no borrarla.",
                                    style = T.small,
                                    color = c.muted,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                                )
                            }
                            items(list, key = { it.keep.id }) { g ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .glass()
                                        .padding(vertical = 6.dp),
                                ) {
                                    CopyRow(g.keep, sizes[g.keep.id], keep = true, marked = false) {}
                                    g.extras.forEach { t ->
                                        val on = t.id in marked
                                        CopyRow(t, sizes[t.id], keep = false, marked = on) {
                                            marked = if (on) marked - t.id else marked + t.id
                                        }
                                    }
                                }
                            }
                        }
                }
            }

            PrimaryButton(
                if (toDelete.isEmpty()) "Nada para borrar" else "Borrar " + plural(toDelete.size, "copia", "copias"),
                onClick = { deleter.request(toDelete) { reload++ } },
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                icon = BI.Trash,
                enabled = toDelete.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun CopyRow(
    t: TrackEntity,
    size: Long?,
    keep: Boolean,
    marked: Boolean,
    onToggle: () -> Unit,
) {
    val c = LocalBismo.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !keep) { onToggle() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(t.artUri(), t.artKey(), Modifier.size(42.dp), corner = 10.dp, icon = BI.Music)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, style = T.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(fmtSize(size), fmtDuration(t.durationMs), File(t.folder).name.ifBlank { null }).joinToString(" · "),
                style = T.tiny,
                color = c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (keep) {
            Badge("Se queda", color = c.ok)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SelectCircle(marked)
                Spacer(Modifier.height(2.dp))
                Text(if (marked) "Borrar" else "Dejar", style = T.tiny, color = if (marked) c.err else c.muted)
            }
        }
    }
}

private fun fmtSize(bytes: Long?): String? {
    if (bytes == null || bytes <= 0) return null
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(Locale.forLanguageTag("es-CO"), "%.1f MB", mb)
}
