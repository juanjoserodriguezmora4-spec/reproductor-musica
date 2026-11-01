package com.bismopower.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bismopower.app.container
import com.bismopower.app.data.db.AlbumRow
import com.bismopower.app.data.db.ArtistRow
import com.bismopower.app.data.db.FolderRow
import com.bismopower.app.data.db.TrackEntity
import com.bismopower.app.library.artKey
import com.bismopower.app.library.artUri
import com.bismopower.app.library.trackArtUri
import com.bismopower.app.ui.common.BI
import com.bismopower.app.ui.common.Corners
import com.bismopower.app.ui.common.EmptyState
import com.bismopower.app.ui.common.IconChip
import com.bismopower.app.ui.common.ListRow
import com.bismopower.app.ui.common.PrimaryButton
import com.bismopower.app.ui.common.Segmented
import com.bismopower.app.ui.common.Thumb
import com.bismopower.app.ui.common.fmtDuration
import com.bismopower.app.ui.common.glass
import com.bismopower.app.ui.common.glassSegment
import com.bismopower.app.ui.common.plural
import com.bismopower.app.ui.sheets.TrackMoreButton
import com.bismopower.app.ui.theme.LocalBismo
import com.bismopower.app.ui.theme.T
import java.io.File
import java.text.Normalizer
import kotlinx.coroutines.launch

/** Espacio libre al final de las listas para que el mini reproductor y la barra no tapen nada. */
val BOTTOM_SPACE = 190.dp

private val SECTIONS = listOf("Canciones", "Álbumes", "Artistas", "Carpetas")

private sealed interface Detail {
    val title: String

    data class Album(
        val id: Long,
        override val title: String,
        val artist: String,
    ) : Detail

    data class Artist(
        override val title: String,
    ) : Detail

    data class Folder(
        val path: String,
    ) : Detail {
        override val title: String get() = File(path).name.ifBlank { path }
    }
}

@Composable
fun LibraryScreen(onOpenFolders: () -> Unit = {}) {
    val app = LocalContext.current.container
    val permission = rememberAudioPermission()
    var section by rememberSaveable { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Detail?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(permission.granted) {
        if (permission.granted) app.library.scanOnce()
    }
    BackHandler(enabled = searching && detail == null) {
        searching = false
        query = ""
    }
    BackHandler(enabled = detail != null) { detail = null }

    if (!permission.granted) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text("Biblioteca", style = T.h1, color = LocalBismo.current.text)
            Spacer(Modifier.height(18.dp))
            EmptyState(
                icon = BI.Music,
                title = "Tu música, aquí",
                text = "BismoPower necesita permiso para leer las canciones guardadas en tu teléfono. Nada sale de tu dispositivo.",
                action = "Permitir acceso",
                onAction = permission.request,
            )
        }
        return
    }

    AnimatedContent(
        detail,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
        label = "detail",
    ) { d ->
        if (d == null) {
            if (searching) {
                SearchScreen(
                    query = query,
                    onQuery = { query = it },
                    onClose = {
                        searching = false
                        query = ""
                    },
                    onOpen = { detail = it },
                )
            } else {
                Sections(section, { section = it }, onOpen = { detail = it }, onOpenFolders = onOpenFolders, onSearch = { searching = true })
            }
        } else {
            DetailScreen(d, onBack = { detail = null })
        }
    }
}

@Composable
private fun Sections(
    section: Int,
    onSection: (Int) -> Unit,
    onOpen: (Detail) -> Unit,
    onOpenFolders: () -> Unit,
    onSearch: () -> Unit,
) {
    val app = LocalContext.current.container
    val tracks by app.library.tracks.collectAsState(initial = null)
    val scanning by app.library.scanning.collectAsStateWithLifecycle()
    val list = tracks

    val header: @Composable () -> Unit = {
        LibraryHeader(section, onSection, list?.size ?: 0, onOpenFolders, onSearch)
    }

    when {
        list == null -> Box(Modifier.fillMaxSize())
        list.isEmpty() ->
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                header()
                Spacer(Modifier.height(14.dp))
                if (scanning) {
                    EmptyState(BI.Search, "Buscando música…", "Revisando las canciones de tu teléfono.")
                } else {
                    val scope = rememberCoroutineScope()
                    EmptyState(
                        BI.Music,
                        "No encontramos música",
                        "Copia canciones a tu teléfono (por ejemplo a la carpeta Música) o revisa qué carpetas elegiste.",
                        action = "Buscar de nuevo",
                        onAction = { scope.launch { app.library.scan() } },
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("Elegir carpetas", onOpenFolders, Modifier.fillMaxWidth(), icon = BI.Folder)
                }
            }
        section == 0 -> SongsSection(list, header)
        section == 1 -> AlbumsSection(header, onOpen)
        section == 2 -> ArtistsSection(header, onOpen)
        else -> FoldersSection(header, onOpen)
    }
}

@Composable
private fun LibraryHeader(
    section: Int,
    onSection: (Int) -> Unit,
    total: Int,
    onOpenFolders: () -> Unit,
    onSearch: () -> Unit,
) {
    val c = LocalBismo.current
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Biblioteca", style = T.h1, color = c.text)
                Text(plural(total, "canción", "canciones"), style = T.small, color = c.muted)
            }
            IconChip(BI.Search, onSearch, size = 40.dp, description = "Buscar en la biblioteca")
            Spacer(Modifier.width(8.dp))
            IconChip(BI.Folder, onOpenFolders, size = 40.dp, description = "Elegir carpetas de música")
        }
        Spacer(Modifier.height(14.dp))
        Segmented(SECTIONS, section, onSection)
    }
}

private val listPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = BOTTOM_SPACE)

@Composable
private fun SongsSection(
    tracks: List<TrackEntity>,
    header: @Composable () -> Unit,
) {
    val current by LocalContext.current.container.player.state.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = listPadding,
    ) {
        item(key = "header") { header() }
        item(key = "actions") { PlayButtons(tracks, Modifier.padding(vertical = 14.dp)) }
        trackItems(tracks, current.currentId)
    }
}

@Composable
internal fun PlayButtons(
    tracks: List<TrackEntity>,
    modifier: Modifier = Modifier,
) {
    val c = LocalBismo.current
    val player = LocalContext.current.container.player
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryButton("Reproducir", { player.play(tracks, 0) }, Modifier.weight(1f), icon = BI.Play)
        Row(
            Modifier
                .weight(1f)
                .glass(Corners.button, strong = true, shadow = false)
                .clickable { player.play(tracks, 0, shuffle = true) }
                .padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(BI.Shuffle, null, tint = c.accent, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text("Aleatorio", style = T.label, color = c.text)
        }
    }
}

/** Filas de canciones dentro de una tarjeta de vidrio continua. */
private fun LazyListScope.trackItems(
    tracks: List<TrackEntity>,
    currentId: Long?,
    numbered: Boolean = false,
) {
    itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
        val app = LocalContext.current.container
        val c = LocalBismo.current
        ListRow(
            title = t.title,
            subtitle = "${t.artist} · ${fmtDuration(t.durationMs)}",
            highlight = currentId == t.id,
            leading = {
                if (numbered) {
                    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        Text(if (t.trackNumber > 0) "${t.trackNumber}" else "·", style = T.small, color = c.muted)
                    }
                } else {
                    Thumb(t.artUri(), t.artKey(), Modifier.size(44.dp), corner = 10.dp, icon = BI.Music)
                }
            },
            trailing = { TrackMoreButton(t) },
            onClick = { app.player.play(tracks, i) },
            modifier =
                Modifier
                    .glassSegment(i, tracks.size)
                    .padding(horizontal = 6.dp, vertical = if (i == 0 || i == tracks.size - 1) 4.dp else 0.dp),
        )
    }
}

@Composable
private fun AlbumsSection(
    header: @Composable () -> Unit,
    onOpen: (Detail) -> Unit,
) {
    val app = LocalContext.current.container
    val albums by app.library.albums.collectAsState(initial = emptyList())
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        contentPadding = listPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) { Column(Modifier.padding(bottom = 2.dp)) { header() } }
        items(albums, key = { it.albumId }) { a -> AlbumCard(a) { onOpen(Detail.Album(a.albumId, a.album, a.artist)) } }
    }
}

@Composable
private fun AlbumCard(
    a: AlbumRow,
    onClick: () -> Unit,
) {
    val c = LocalBismo.current
    Column(
        Modifier
            .glass()
            .clickable { onClick() }
            .padding(8.dp),
    ) {
        Thumb(
            trackArtUri(a.firstTrackId, a.albumId),
            a.album,
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            corner = 14.dp,
            icon = BI.Disc,
        )
        Spacer(Modifier.height(8.dp))
        Text(a.album, style = T.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        Text(
            "${a.artist} · ${a.count}",
            style = T.tiny,
            color = c.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun ArtistsSection(
    header: @Composable () -> Unit,
    onOpen: (Detail) -> Unit,
) {
    val app = LocalContext.current.container
    val artists by app.library.artists.collectAsState(initial = emptyList())
    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = listPadding,
    ) {
        item(key = "header") { Column(Modifier.padding(bottom = 14.dp)) { header() } }
        itemsIndexed(artists, key = { _, a -> a.artist }) { i, a -> ArtistRowItem(a, i, artists.size) { onOpen(Detail.Artist(a.artist)) } }
    }
}

@Composable
private fun ArtistRowItem(
    a: ArtistRow,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    ListRow(
        title = a.artist,
        subtitle = "${plural(a.albums, "álbum", "álbumes")} · ${plural(a.count, "canción", "canciones")}",
        leading = { Thumb(trackArtUri(a.firstTrackId, a.albumId), a.artist, Modifier.size(44.dp), corner = 22.dp, icon = BI.User) },
        trailing = { androidx.compose.material3.Icon(BI.Chevron, null, tint = LocalBismo.current.muted, modifier = Modifier.size(16.dp)) },
        onClick = onClick,
        modifier =
            Modifier
                .glassSegment(index, count)
                .padding(horizontal = 6.dp, vertical = if (index == 0 || index == count - 1) 4.dp else 0.dp),
    )
}

@Composable
private fun FoldersSection(
    header: @Composable () -> Unit,
    onOpen: (Detail) -> Unit,
) {
    val app = LocalContext.current.container
    val folders by app.library.folders.collectAsState(initial = emptyList())
    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = listPadding,
    ) {
        item(key = "header") { Column(Modifier.padding(bottom = 14.dp)) { header() } }
        itemsIndexed(folders, key = { _, f -> f.folder }) { i, f -> FolderRowItem(f, i, folders.size) { onOpen(Detail.Folder(f.folder)) } }
    }
}

@Composable
private fun FolderRowItem(
    f: FolderRow,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    val name = File(f.folder).name.ifBlank { f.folder }
    ListRow(
        title = name,
        subtitle = "${plural(f.count, "canción", "canciones")} · ${f.folder}",
        leading = { Thumb(null, name, Modifier.size(44.dp), corner = 10.dp, icon = BI.Folder) },
        trailing = { androidx.compose.material3.Icon(BI.Chevron, null, tint = LocalBismo.current.muted, modifier = Modifier.size(16.dp)) },
        onClick = onClick,
        modifier =
            Modifier
                .glassSegment(index, count)
                .padding(horizontal = 6.dp, vertical = if (index == 0 || index == count - 1) 4.dp else 0.dp),
    )
}

@Composable
private fun DetailScreen(
    d: Detail,
    onBack: () -> Unit,
) {
    val c = LocalBismo.current
    val app = LocalContext.current.container
    val flow =
        remember(d) {
            when (d) {
                is Detail.Album -> app.library.album(d.id)
                is Detail.Artist -> app.library.artist(d.title)
                is Detail.Folder -> app.library.folder(d.path)
            }
        }
    val tracks by flow.collectAsState(initial = emptyList())
    val total = tracks.sumOf { it.durationMs }
    val current by app.player.state.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = listPadding,
    ) {
        item(key = "top") {
            Column(Modifier.fillMaxWidth()) {
                IconChip(BI.Back, onBack, description = "Volver")
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val art = tracks.firstOrNull()?.artUri()
                    val icon =
                        when (d) {
                            is Detail.Album -> BI.Disc
                            is Detail.Artist -> BI.User
                            is Detail.Folder -> BI.Folder
                        }
                    Thumb(
                        if (d is Detail.Folder) null else art,
                        d.title,
                        Modifier.size(112.dp),
                        corner = if (d is Detail.Artist) 56.dp else 20.dp,
                        icon = icon,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.title, style = T.big, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (d is Detail.Album) Text(d.artist, style = T.body, color = c.muted, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        Text("${plural(tracks.size, "canción", "canciones")} · ${fmtDuration(total)}", style = T.small, color = c.muted)
                    }
                }
                PlayButtons(tracks, Modifier.padding(vertical = 16.dp))
            }
        }
        trackItems(tracks, current.currentId, numbered = d is Detail.Album)
    }
}

// ---------------------------------------------------------------- búsqueda

/** Quita tildes y pasa a minúsculas: así "cancion" encuentra "Canción". */
private fun fold(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD).replace(MARKS, "").lowercase()

private val MARKS = Regex("\\p{Mn}+")

/** true si todas las palabras de la búsqueda aparecen en el texto (en cualquier orden). */
private fun matches(
    haystack: String,
    words: List<String>,
): Boolean = words.all { haystack.contains(it) }

/**
 * Busca mientras escribes en canciones, artistas y álbumes. Varias palabras se buscan
 * todas a la vez: "shakira loba" encuentra "Loba" de Shakira.
 */
@Composable
private fun SearchScreen(
    query: String,
    onQuery: (String) -> Unit,
    onClose: () -> Unit,
    onOpen: (Detail) -> Unit,
) {
    val c = LocalBismo.current
    val app = LocalContext.current.container
    val tracks by app.library.tracks.collectAsState(initial = emptyList())
    val albums by app.library.albums.collectAsState(initial = emptyList())
    val artists by app.library.artists.collectAsState(initial = emptyList())
    val current by app.player.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // El texto de cada canción se prepara una sola vez, no en cada letra.
    val index = remember(tracks) { tracks.map { it to fold("${it.title} ${it.artist} ${it.album}") } }
    val words = remember(query) { fold(query).split(' ').filter { it.isNotBlank() } }
    val songs = remember(index, words) { if (words.isEmpty()) emptyList() else index.filter { matches(it.second, words) }.map { it.first } }
    val foundArtists = remember(artists, words) { if (words.isEmpty()) emptyList() else artists.filter { matches(fold(it.artist), words) }.take(4) }
    val foundAlbums =
        remember(albums, words) {
            if (words.isEmpty()) emptyList() else albums.filter { matches(fold("${it.album} ${it.artist}"), words) }.take(4)
        }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconChip(BI.Back, onClose, size = 40.dp, description = "Cerrar la búsqueda")
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = T.body.copy(color = c.text, fontSize = 16.sp),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier =
                    Modifier
                        .weight(1f)
                        .focusRequester(focus),
                decorationBox = { inner ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .glass(Corners.button, strong = true, shadow = false)
                            .padding(start = 12.dp, end = 6.dp)
                            .height(44.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Icon(BI.Search, null, tint = c.muted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text("Canción, artista o álbum", style = T.body.copy(fontSize = 16.sp), color = c.muted)
                            inner()
                        }
                        if (query.isNotEmpty()) {
                            androidx.compose.material3.Icon(
                                BI.Close,
                                "Borrar",
                                tint = c.muted,
                                modifier =
                                    Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .clickable { onQuery("") }
                                        .padding(8.dp),
                            )
                        }
                    }
                },
            )
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
            when {
                words.isEmpty() ->
                    item(key = "hint") {
                        Text(
                            "Escribe para buscar entre ${plural(tracks.size, "canción", "canciones")}.",
                            style = T.small,
                            color = c.muted,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                songs.isEmpty() && foundArtists.isEmpty() && foundAlbums.isEmpty() ->
                    item(key = "empty") {
                        EmptyState(BI.Search, "Nada para «$query»", "Prueba con otra palabra o revisa qué carpetas elegiste.")
                    }
                else -> {
                    if (foundArtists.isNotEmpty()) {
                        item(key = "h-artists") { SearchHeading("Artistas") }
                        itemsIndexed(foundArtists, key = { _, a -> "artist-${a.artist}" }) { i, a ->
                            ArtistRowItem(a, i, foundArtists.size) { onOpen(Detail.Artist(a.artist)) }
                        }
                    }
                    if (foundAlbums.isNotEmpty()) {
                        item(key = "h-albums") { SearchHeading("Álbumes") }
                        itemsIndexed(foundAlbums, key = { _, a -> "album-${a.albumId}" }) { i, a ->
                            ListRow(
                                title = a.album,
                                subtitle = "${a.artist} · ${plural(a.count, "canción", "canciones")}",
                                leading = { Thumb(trackArtUri(a.firstTrackId, a.albumId), a.album, Modifier.size(44.dp), corner = 10.dp, icon = BI.Disc) },
                                onClick = { onOpen(Detail.Album(a.albumId, a.album, a.artist)) },
                                modifier =
                                    Modifier
                                        .glassSegment(i, foundAlbums.size)
                                        .padding(horizontal = 6.dp, vertical = if (i == 0 || i == foundAlbums.size - 1) 4.dp else 0.dp),
                            )
                        }
                    }
                    if (songs.isNotEmpty()) {
                        item(key = "h-songs") { SearchHeading(plural(songs.size, "canción", "canciones")) }
                        trackItems(songs, current.currentId)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHeading(text: String) {
    Text(
        text,
        style = T.h2,
        color = LocalBismo.current.text,
        modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 8.dp),
    )
}

