package com.bismopower.app.ui.playlists

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bismopower.app.container
import com.bismopower.app.data.db.PlaylistRow
import com.bismopower.app.data.db.TrackEntity
import com.bismopower.app.library.artKey
import com.bismopower.app.library.artUri
import com.bismopower.app.library.trackArtUri
import com.bismopower.app.ui.common.BI
import com.bismopower.app.ui.common.EmptyState
import com.bismopower.app.ui.common.IconChip
import com.bismopower.app.ui.common.Thumb
import com.bismopower.app.ui.common.dragHandle
import com.bismopower.app.ui.common.fmtDuration
import com.bismopower.app.ui.common.glass
import com.bismopower.app.ui.common.glassSegment
import com.bismopower.app.ui.common.moved
import com.bismopower.app.ui.common.plural
import com.bismopower.app.ui.common.rememberReorderState
import com.bismopower.app.ui.common.reorderItem
import com.bismopower.app.ui.library.BOTTOM_SPACE
import com.bismopower.app.ui.library.PlayButtons
import com.bismopower.app.ui.sheets.LocalSheets
import com.bismopower.app.ui.sheets.Sheet
import com.bismopower.app.ui.sheets.TrackMoreButton
import com.bismopower.app.ui.theme.LocalBismo
import com.bismopower.app.ui.theme.T
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Alto fijo de cada fila: el reordenar arrastrando lo necesita para saber dónde cae la fila. */
private val ROW_HEIGHT = 62.dp

private val listPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = BOTTOM_SPACE)

/** Qué lista se está viendo. */
private sealed interface ListView {
    val title: String

    data object Favorites : ListView {
        override val title = "Favoritas"
    }

    data object MostPlayed : ListView {
        override val title = "Más escuchadas"
    }

    data object Recent : ListView {
        override val title = "Escuchadas hace poco"
    }

    data class User(
        val id: Long,
        override val title: String,
    ) : ListView
}

/** Una canción de una lista, con las veces que se ha escuchado (solo en las listas automáticas). */
private data class Entry(
    val track: TrackEntity,
    val plays: Int? = null,
)

/** Pestaña Listas: las automáticas (favoritas, más escuchadas, recientes) y las tuyas. */
@Composable
fun PlaylistsScreen() {
    var open by remember { mutableStateOf<ListView?>(null) }
    BackHandler(enabled = open != null) { open = null }

    AnimatedContent(
        open,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
        label = "playlist",
    ) { view ->
        if (view == null) Overview(onOpen = { open = it }) else ListDetail(view, onBack = { open = null })
    }
}

@Composable
private fun Overview(onOpen: (ListView) -> Unit) {
    val c = LocalBismo.current
    val app = LocalContext.current.container
    val sheets = LocalSheets.current
    val lists by app.playlists.playlists.collectAsState(initial = null)
    val favs by app.playlists.favoriteIds.collectAsState(initial = emptySet())
    val most by app.playlists.mostPlayed.collectAsState(initial = emptyList())
    val recent by app.playlists.recentlyPlayed.collectAsState(initial = emptyList())
    val mine = lists ?: emptyList()

    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = listPadding,
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Listas", style = T.h1, color = c.text)
                    Text(plural(mine.size, "lista tuya", "listas tuyas"), style = T.small, color = c.muted)
                }
                IconChip(BI.Plus, { sheets.open(Sheet.NamePlaylist()) }, size = 40.dp, description = "Nueva lista")
            }
        }
        item(key = "smart") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SmartCard(BI.HeartFill, "Favoritas", favs.size, Modifier.weight(1f)) { onOpen(ListView.Favorites) }
                SmartCard(BI.TrendUp, "Más escuchadas", most.size, Modifier.weight(1f)) { onOpen(ListView.MostPlayed) }
                SmartCard(BI.History, "Recientes", recent.size, Modifier.weight(1f)) { onOpen(ListView.Recent) }
            }
        }
        item(key = "mine-title") {
            Text("Tus listas", style = T.h2, color = c.text, modifier = Modifier.padding(start = 6.dp, top = 24.dp, bottom = 10.dp))
        }
        if (lists != null && mine.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    BI.ListMusic,
                    "Todavía no tienes listas",
                    "Crea una y llénala desde el menú ⋯ de cada canción o desde el reproductor.",
                    action = "Nueva lista",
                    onAction = { sheets.open(Sheet.NamePlaylist()) },
                )
            }
        }
        itemsIndexed(mine, key = { _, p -> p.id }) { i, p ->
            PlaylistRowItem(p, i, mine.size, onOpen = { onOpen(ListView.User(p.id, p.name)) })
        }
    }
}

/** Tarjeta de una lista automática: ícono, nombre y cuántas canciones tiene. */
@Composable
private fun SmartCard(
    icon: ImageVector,
    title: String,
    count: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = LocalBismo.current
    Column(
        modifier
            .glass(strong = true)
            .clickable { onClick() }
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(c.accentSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = c.accent, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.height(12.dp))
        Text(title, style = T.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$count", style = T.small.copy(fontFeatureSettings = "tnum"), color = c.muted)
    }
}

@Composable
private fun PlaylistRowItem(
    p: PlaylistRow,
    index: Int,
    count: Int,
    onOpen: () -> Unit,
) {
    val c = LocalBismo.current
    val sheets = LocalSheets.current
    Row(
        Modifier
            .fillMaxWidth()
            .glassSegment(index, count)
            .clickable { onOpen() }
            .padding(start = 12.dp, end = 4.dp)
            .height(ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaylistCover(p, Modifier.size(44.dp), corner = 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = T.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(plural(p.count, "canción", "canciones"), style = T.tiny, color = c.muted)
        }
        Icon(
            BI.More,
            "Opciones de ${p.name}",
            tint = c.muted,
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { sheets.open(Sheet.PlaylistMenu(p.id, p.name)) }
                    .padding(10.dp),
        )
    }
}

@Composable
private fun PlaylistCover(
    p: PlaylistRow,
    modifier: Modifier,
    corner: androidx.compose.ui.unit.Dp,
) {
    val first = p.firstTrackId
    Thumb(
        if (first != null) trackArtUri(first, p.firstAlbumId ?: 0L, useAlbumArt = false) else null,
        p.name,
        modifier,
        corner = corner,
        icon = BI.ListMusic,
    )
}

// ---------------------------------------------------------------- detalle de una lista

@Composable
private fun ListDetail(
    view: ListView,
    onBack: () -> Unit,
) {
    val c = LocalBismo.current
    val app = LocalContext.current.container
    val sheets = LocalSheets.current
    val playlistId = (view as? ListView.User)?.id

    val flow: Flow<List<Entry>> =
        remember(view) {
            when (view) {
                ListView.Favorites -> app.playlists.favorites.map { l -> l.map { Entry(it) } }
                ListView.MostPlayed -> app.playlists.mostPlayed.map { l -> l.map { Entry(it.track, it.plays) } }
                ListView.Recent -> app.playlists.recentlyPlayed.map { l -> l.map { Entry(it.track, it.plays) } }
                is ListView.User -> app.playlists.tracks(view.id).map { l -> l.map { Entry(it) } }
            }
        }
    val entries by flow.collectAsState(initial = null)
    val current by app.player.state.collectAsStateWithLifecycle()

    // Si la lista se borró (o se renombró) desde su menú, la pantalla se entera.
    val lists by app.playlists.playlists.collectAsState(initial = null)
    val row = playlistId?.let { id -> lists?.firstOrNull { it.id == id } }
    LaunchedEffect(lists) {
        if (playlistId != null && lists != null && row == null) onBack()
    }
    val title = row?.name ?: view.title

    // Orden en pantalla: al soltar una fila se reordena aquí al instante y luego se guarda.
    var order by remember(entries) { mutableStateOf(entries ?: emptyList()) }
    val reorder = rememberReorderState(ROW_HEIGHT)
    val tracks = order.map { it.track }
    val total = tracks.sumOf { it.durationMs }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = listPadding,
        userScrollEnabled = !reorder.dragging,
    ) {
        item(key = "top") {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconChip(BI.Back, onBack, description = "Volver")
                    Spacer(Modifier.weight(1f))
                    if (playlistId != null) {
                        IconChip(BI.More, { sheets.open(Sheet.PlaylistMenu(playlistId, title)) }, description = "Opciones de la lista")
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val first = tracks.firstOrNull()
                    Thumb(
                        first?.artUri(),
                        first?.artKey() ?: title,
                        Modifier.size(112.dp),
                        corner = 20.dp,
                        icon =
                            when (view) {
                                ListView.Favorites -> BI.HeartFill
                                ListView.MostPlayed -> BI.TrendUp
                                ListView.Recent -> BI.History
                                is ListView.User -> BI.ListMusic
                            },
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = T.big, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (tracks.isEmpty()) "Vacía" else "${plural(tracks.size, "canción", "canciones")} · ${fmtDuration(total)}",
                            style = T.small,
                            color = c.muted,
                        )
                        if (playlistId != null && tracks.size > 1) {
                            Text("Arrastra ≡ para cambiar el orden", style = T.tiny, color = c.muted)
                        }
                    }
                }
                if (tracks.isNotEmpty()) PlayButtons(tracks, Modifier.padding(vertical = 16.dp))
            }
        }

        if (entries != null && order.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    when (view) {
                        ListView.Favorites -> BI.Heart
                        ListView.MostPlayed -> BI.TrendUp
                        ListView.Recent -> BI.History
                        is ListView.User -> BI.ListMusic
                    },
                    "Aquí no hay nada todavía",
                    when (view) {
                        ListView.Favorites -> "Toca el corazón en el reproductor o en el menú ⋯ de una canción."
                        ListView.MostPlayed, ListView.Recent -> "Escucha música y aquí aparece. Una canción cuenta después de 30 segundos."
                        is ListView.User -> "Añade canciones desde el menú ⋯ de cada canción o desde el reproductor."
                    },
                    Modifier.padding(top = 16.dp),
                )
            }
        }

        itemsIndexed(order, key = { _, e -> e.track.id }) { i, e ->
            val t = e.track
            val playing = current.currentId == t.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .reorderItem(reorder, i, order.size)
                    .then(
                        if (reorder.from == i) {
                            Modifier.background(if (c.dark) Color(0xFF1D2333) else Color.White, RoundedCornerShape(12.dp))
                        } else {
                            Modifier.glassSegment(i, order.size)
                        },
                    ).clickable { app.player.play(tracks, i) }
                    .padding(start = 12.dp, end = 2.dp)
                    .height(ROW_HEIGHT),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Thumb(t.artUri(), t.artKey(), Modifier.size(44.dp), corner = 10.dp, icon = BI.Music)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = T.title, color = if (playing) c.accent else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val plays = e.plays
                    Text(
                        if (plays != null) "${t.artist} · ${plural(plays, "vez", "veces")}" else "${t.artist} · ${fmtDuration(t.durationMs)}",
                        style = T.tiny,
                        color = c.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TrackMoreButton(t, playlistId)
                if (playlistId != null) {
                    Icon(
                        BI.Grip,
                        "Arrastra para mover ${t.title}",
                        tint = c.muted,
                        modifier =
                            Modifier
                                .size(44.dp)
                                .dragHandle(reorder, i) { from, to ->
                                    val newOrder = order.moved(from, to)
                                    order = newOrder
                                    app.appScope.launch { app.playlists.reorder(playlistId, newOrder.map { it.track.id }) }
                                }.padding(11.dp),
                    )
                }
            }
        }
    }
}
