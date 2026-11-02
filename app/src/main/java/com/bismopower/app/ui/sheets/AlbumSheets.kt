package com.bismopower.app.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bismopower.app.container
import com.bismopower.app.data.db.TrackEntity
import com.bismopower.app.library.artKey
import com.bismopower.app.library.artUri
import com.bismopower.app.library.fold
import com.bismopower.app.library.hasRealAlbum
import com.bismopower.app.library.trackArtUri
import com.bismopower.app.ui.common.BI
import com.bismopower.app.ui.common.Corners
import com.bismopower.app.ui.common.PrimaryButton
import com.bismopower.app.ui.common.SelectCircle
import com.bismopower.app.ui.common.Thumb
import com.bismopower.app.ui.common.glass
import com.bismopower.app.ui.common.plural
import com.bismopower.app.ui.theme.LocalBismo
import com.bismopower.app.ui.theme.T
import kotlinx.coroutines.launch

/** Campo de texto para buscar dentro de una hoja. */
@Composable
private fun SheetSearch(
    value: String,
    onValue: (String) -> Unit,
    hint: String,
) {
    val c = LocalBismo.current
    BasicTextField(
        value = value,
        onValueChange = { if (it.length <= 80) onValue(it) },
        singleLine = true,
        textStyle = T.body.copy(color = c.text, fontSize = 16.sp),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .glass(Corners.button, strong = true, shadow = false)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(BI.Search, null, tint = c.muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(hint, style = T.body.copy(fontSize = 16.sp), color = c.muted)
                    inner()
                }
            }
        },
    )
}

// ---------------------------------------------------------------- mover a un álbum

/**
 * Elegir el álbum de destino. Arriba salen los del mismo artista. Si escribes un nombre que
 * no existe, aparece "Crear álbum". El cambio es solo dentro de BismoPower.
 */
@Composable
internal fun ColumnScope.MoveToAlbumSheet(
    sheet: Sheet.MoveToAlbum,
    controller: SheetController,
) {
    val c = LocalBismo.current
    val context = LocalContext.current
    val app = context.container
    val scope = rememberCoroutineScope()
    val albums by app.library.albums.collectAsState(initial = emptyList())
    val overridden by app.library.overriddenIds.collectAsState(initial = emptyList())
    var query by remember(sheet) { mutableStateOf("") }
    val tracks = sheet.tracks
    val artist = fold(tracks.firstOrNull()?.artist ?: "")
    val current = tracks.map { it.albumId }.toSet()

    val q = fold(query)
    val shown =
        remember(albums, q, artist) {
            albums
                .filter { it.albumId !in current || current.size > 1 }
                .filter { q.isEmpty() || fold("${it.album} ${it.artist}").contains(q) }
                .sortedWith(
                    compareByDescending<com.bismopower.app.data.db.AlbumRow> { artist.isNotEmpty() && fold(it.artist).contains(artist) }
                        .thenBy { fold(it.album) },
                )
        }
    val exact = albums.any { fold(it.album) == q }

    fun move(
        name: String,
        id: Long?,
    ) {
        scope.launch {
            app.library.moveToAlbum(tracks, name, id)
            context.toast(if (tracks.size == 1) "Movida a «$name»" else "${tracks.size} movidas a «$name»")
            controller.close()
        }
    }

    Text(
        if (tracks.size == 1) "Mover «${tracks[0].title}» a un álbum" else "Mover ${tracks.size} canciones a un álbum",
        style = T.h2,
        color = c.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
    )
    Text(
        "Solo cambia dentro de BismoPower; el archivo no se toca.",
        style = T.small,
        color = c.muted,
        modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
    )
    SheetSearch(query, { query = it }, "Buscar o escribir un álbum nuevo")
    Spacer(Modifier.height(6.dp))
    if (query.isNotBlank() && !exact) {
        ChoiceRow(BI.Plus, "Crear álbum «${query.trim()}»", null, accent = true) { move(query.trim(), null) }
    }
    if (tracks.any { it.id in overridden }) {
        ChoiceRow(BI.Retry, "Volver a su álbum original", null) {
            scope.launch {
                app.library.restoreAlbum(tracks.map { it.id })
                controller.close()
            }
        }
    }
    LazyColumn(Modifier.heightIn(max = 340.dp)) {
        items(shown, key = { it.albumId }) { a ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { move(a.album, a.albumId) }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Thumb(trackArtUri(a.firstTrackId, a.albumId), a.album, Modifier.size(42.dp), corner = 10.dp, icon = BI.Disc)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.album, style = T.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${a.artist} · ${plural(a.count, "canción", "canciones")}", style = T.tiny, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    sub: String?,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val c = LocalBismo.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (accent) c.accent else c.muted, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = T.body.copy(fontSize = 15.sp), color = if (accent) c.accent else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = T.tiny, color = c.muted)
        }
    }
}

// ---------------------------------------------------------------- añadir canciones a un álbum

/**
 * Desde un álbum: lista de canciones de otros álbumes para traerlas. Primero las "sueltas"
 * (sin álbum de verdad, como las que quedaron en "Música") del mismo artista, ya marcadas.
 */
@Composable
internal fun ColumnScope.AddToAlbumSheet(
    sheet: Sheet.AddToAlbum,
    controller: SheetController,
) {
    val c = LocalBismo.current
    val context = LocalContext.current
    val app = context.container
    val scope = rememberCoroutineScope()
    val all by app.library.tracks.collectAsState(initial = null)
    var query by remember(sheet) { mutableStateOf("") }
    var picked by remember(sheet) { mutableStateOf<Set<Long>>(emptySet()) }
    var seeded by remember(sheet) { mutableStateOf(false) }
    val artist = fold(sheet.artist)

    fun sameArtist(t: TrackEntity): Boolean {
        val a = fold(t.artist)
        return artist.isNotEmpty() && a.isNotEmpty() && (a.contains(artist) || artist.contains(a))
    }

    val candidates =
        remember(all, sheet) {
            (all ?: emptyList())
                .filter { it.albumId != sheet.albumId }
                .sortedWith(
                    compareByDescending<TrackEntity> { !it.hasRealAlbum() && sameArtist(it) }
                        .thenByDescending { sameArtist(it) }
                        .thenByDescending { !it.hasRealAlbum() }
                        .thenBy { fold(it.title) },
                )
        }
    // La primera vez se marcan solas las sueltas del mismo artista.
    LaunchedEffect(all) {
        if (!seeded && all != null) {
            picked = candidates.filter { !it.hasRealAlbum() && sameArtist(it) }.map { it.id }.toSet()
            seeded = true
        }
    }
    val q = fold(query)
    val shown = if (q.isEmpty()) candidates else candidates.filter { fold("${it.title} ${it.artist} ${it.album}").contains(q) }

    Text(
        "Añadir canciones a «${sheet.album}»",
        style = T.h2,
        color = c.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
    )
    Text(
        "Ya marcamos las de ${sheet.artist.ifBlank { "este artista" }} que no tienen álbum. Solo cambia dentro de BismoPower.",
        style = T.small,
        color = c.muted,
        modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
    )
    SheetSearch(query, { query = it }, "Buscar canción")
    Spacer(Modifier.height(6.dp))
    LazyColumn(Modifier.heightIn(max = 340.dp)) {
        items(shown, key = { it.id }) { t ->
            val on = t.id in picked
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { picked = if (on) picked - t.id else picked + t.id }
                    .padding(horizontal = 6.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Thumb(t.artUri(), t.artKey(), Modifier.size(40.dp), corner = 10.dp, icon = BI.Music)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = T.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${t.artist} · ${t.album}", style = T.tiny, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SelectCircle(on, Modifier.padding(start = 8.dp))
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    PrimaryButton(
        if (picked.isEmpty()) "Marca canciones para añadir" else "Añadir " + plural(picked.size, "canción", "canciones"),
        onClick = {
            val chosen = candidates.filter { it.id in picked }
            scope.launch {
                app.library.moveToAlbum(chosen, sheet.album, sheet.albumId)
                context.toast("${plural(chosen.size, "canción añadida", "canciones añadidas")} a «${sheet.album}»")
                controller.close()
            }
        },
        modifier = Modifier.fillMaxWidth(),
        icon = BI.Plus,
        enabled = picked.isNotEmpty(),
    )
}
