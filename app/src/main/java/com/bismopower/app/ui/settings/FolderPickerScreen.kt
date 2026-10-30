package com.bismopower.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bismopower.app.container
import com.bismopower.app.library.DeviceFolder
import com.bismopower.app.library.isInside
import com.bismopower.app.ui.common.BI
import com.bismopower.app.ui.common.EmptyState
import com.bismopower.app.ui.common.GlassBackground
import com.bismopower.app.ui.common.IconChip
import com.bismopower.app.ui.common.PrimaryButton
import com.bismopower.app.ui.common.Segmented
import com.bismopower.app.ui.common.glassSegment
import com.bismopower.app.ui.common.plural
import com.bismopower.app.ui.theme.LocalBismo
import com.bismopower.app.ui.theme.T
import kotlinx.coroutines.launch
import java.io.File

/**
 * Elegir de qué carpetas saca BismoPower la música.
 *
 * Marcar una carpeta incluye también todo lo que tiene adentro: si marcas "Music",
 * "Music/Rock" entra sola y aparece marcada en gris.
 */
@Composable
fun FolderPickerScreen(onClose: () -> Unit) {
    val c = LocalBismo.current
    val app = LocalContext.current.container
    val scope = rememberCoroutineScope()

    var folders by remember { mutableStateOf<List<DeviceFolder>?>(null) }
    var onlyChosen by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(Unit) {
        val s = app.settings.current()
        onlyChosen = s.onlyChosenFolders
        chosen = s.musicFolders
        folders = app.library.deviceFolders()
    }

    val list = folders
    val total = list?.sumOf { it.count } ?: 0
    val included = list?.filter { f -> chosen.any { isInside(f.path, it) } }?.sumOf { it.count } ?: 0
    val canSave = !onlyChosen || chosen.isNotEmpty()

    GlassBackground(seed = 2) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconChip(BI.Back, onClose, description = "Volver")
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Carpetas de música", style = T.h2, color = c.text)
                    Text("Elige de dónde saca BismoPower tus canciones", style = T.small, color = c.muted)
                }
            }
            Spacer(Modifier.height(14.dp))
            Segmented(
                listOf("Todas las carpetas", "Solo las que elija"),
                if (onlyChosen) 1 else 0,
                { onlyChosen = it == 1 },
                Modifier.padding(horizontal = 16.dp),
            )
            Text(
                when {
                    list == null -> "Buscando carpetas…"
                    !onlyChosen -> "Se usan las ${plural(total, "canción", "canciones")} de ${plural(list.size, "carpeta", "carpetas")}."
                    chosen.isEmpty() -> "Marca al menos una carpeta."
                    else -> "Vas a tener $included de $total canciones."
                },
                style = T.small,
                color = if (onlyChosen && chosen.isEmpty()) c.err else c.muted,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
            )

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
                            BI.Folder,
                            "No hay carpetas con música",
                            "Copia canciones a tu teléfono y vuelve a abrir esta pantalla.",
                            Modifier.padding(16.dp),
                        )
                    else ->
                        LazyColumn(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = if (onlyChosen) 1f else 0.5f },
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                        ) {
                            itemsIndexed(list, key = { _, f -> f.path }) { i, f ->
                                val direct = f.path in chosen
                                val parent = chosen.firstOrNull { it != f.path && isInside(f.path, it) }
                                FolderRow(
                                    folder = f,
                                    checked = direct || parent != null,
                                    implied = !direct && parent != null,
                                    enabled = onlyChosen,
                                    modifier = Modifier.glassSegment(i, list.size),
                                    onToggle = {
                                        chosen = if (direct) chosen - f.path else chosen + f.path
                                    },
                                )
                            }
                        }
                }
            }

            PrimaryButton(
                "Guardar y buscar música",
                onClick = {
                    scope.launch {
                        app.settings.setFolders(onlyChosen, chosen)
                        // El escaneo sigue aunque se cierre esta pantalla.
                        app.appScope.launch { app.library.scan() }
                        onClose()
                    }
                },
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                icon = BI.Check,
                enabled = canSave && list != null,
            )
        }
    }
}

@Composable
private fun FolderRow(
    folder: DeviceFolder,
    checked: Boolean,
    implied: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onToggle: () -> Unit,
) {
    val c = LocalBismo.current
    val file = File(folder.path)
    Row(
        modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !implied) { onToggle() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckBox(checked, implied)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name.ifBlank { folder.path }, style = T.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (implied) "Incluida por la carpeta de arriba" else prettyPath(file.parent ?: ""),
                style = T.tiny,
                color = c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(folder.count.toString(), style = T.label, color = c.muted)
    }
}

@Composable
private fun CheckBox(
    checked: Boolean,
    implied: Boolean,
) {
    val c = LocalBismo.current
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier
            .size(22.dp)
            .clip(shape)
            .then(
                if (checked) {
                    Modifier.background(if (implied) c.accent.copy(alpha = 0.4f) else c.accent)
                } else {
                    Modifier.border(2.dp, c.muted.copy(alpha = 0.6f), shape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(BI.Check, null, tint = c.onAccent, modifier = Modifier.size(15.dp))
    }
}

/** Ruta legible: cambia /storage/emulated/0 por "Almacenamiento interno" y las tarjetas SD por "Tarjeta SD". */
private fun prettyPath(path: String): String {
    val internal = "/storage/emulated/0"
    if (path.startsWith(internal)) return "Almacenamiento interno" + path.removePrefix(internal)
    val sd = Regex("^/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}").find(path)
    if (sd != null) return "Tarjeta SD" + path.removePrefix(sd.value)
    return path
}
