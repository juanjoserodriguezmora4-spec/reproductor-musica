package com.bismopower.app.ui.sheets

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bismopower.app.container
import com.bismopower.app.data.SettingsRepository
import com.bismopower.app.data.db.TrackEntity
import com.bismopower.app.library.artKey
import com.bismopower.app.library.artUri
import com.bismopower.app.library.trackArtUri
import com.bismopower.app.player.SleepState
import com.bismopower.app.ui.common.BI
import com.bismopower.app.ui.common.Corners
import com.bismopower.app.ui.common.PrimaryButton
import com.bismopower.app.ui.common.Thumb
import com.bismopower.app.ui.common.glass
import com.bismopower.app.ui.common.plural
import com.bismopower.app.ui.player.visualizer.CoverMode
import com.bismopower.app.ui.player.visualizer.PreviewFrame
import com.bismopower.app.ui.player.visualizer.VizPalette
import com.bismopower.app.ui.player.visualizer.VizStyle
import com.bismopower.app.ui.player.visualizer.drawViz
import com.bismopower.app.ui.theme.LocalBismo
import com.bismopower.app.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Las hojas que suben desde abajo con acciones. Se abren desde cualquier pantalla con [LocalSheets]. */
sealed interface Sheet {
    /** Menú de una canción. Con [playlistId] aparece "Quitar de esta lista". */
    data class TrackMenu(
        val track: TrackEntity,
        val playlistId: Long? = null,
    ) : Sheet

    data class AddToPlaylist(
        val trackIds: List<Long>,
    ) : Sheet

    /** Crear una lista (sin [playlistId]) o cambiarle el nombre. [thenAdd] se añaden al crearla. */
    data class NamePlaylist(
        val playlistId: Long? = null,
        val initial: String = "",
        val thenAdd: List<Long> = emptyList(),
    ) : Sheet

    data class PlaylistMenu(
        val id: Long,
        val name: String,
    ) : Sheet

    data object SleepTimer : Sheet

    /** Estilo y color del visualizador. [accent] es el color de la canción (para la paleta "Carátula"). */
    data class Visualizer(
        val accent: Color,
    ) : Sheet
}

@Stable
class SheetController {
    var current by mutableStateOf<Sheet?>(null)
        private set

    fun open(sheet: Sheet) {
        current = sheet
    }

    fun close() {
        current = null
    }
}

val LocalSheets = staticCompositionLocalOf { SheetController() }

fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

/** Dibuja la hoja abierta encima de todo. Va al final de la pantalla raíz. */
@Composable
fun SheetHost(controller: SheetController) {
    val current = controller.current
    // Se recuerda la última hoja para que se vea mientras se cierra.
    var last by remember { mutableStateOf<Sheet?>(null) }
    if (current != null) last = current

    AnimatedVisibility(
        visible = current != null,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(200)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controller.close() },
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = current != null,
            enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 420f)) { it },
            exit = slideOutVertically(tween(200)) { it },
        ) {
            // Aquí dentro para que se registre de último y gane a los demás "atrás".
            BackHandler { controller.close() }
            val c = LocalBismo.current
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(if (c.dark) Color(0xFF141926) else Color(0xFFF8F9FC))
                    // Que tocar la hoja no la cierre (el fondo oscuro sí la cierra).
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 38.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(c.muted.copy(alpha = 0.35f)),
                )
                Spacer(Modifier.height(12.dp))
                AnimatedContent(
                    targetState = last,
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                    contentKey = { it?.javaClass },
                    label = "sheet",
                ) { sheet ->
                    Column {
                        when (sheet) {
                            is Sheet.TrackMenu -> TrackMenu(sheet, controller)
                            is Sheet.AddToPlaylist -> AddToPlaylist(sheet, controller)
                            is Sheet.NamePlaylist -> NamePlaylist(sheet, controller)
                            is Sheet.PlaylistMenu -> PlaylistMenu(sheet, controller)
                            Sheet.SleepTimer -> SleepTimerSheet(controller)
                            is Sheet.Visualizer -> VisualizerSheet(sheet)
                            null -> Unit
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- menú de canción

@Composable
private fun ColumnScope.TrackMenu(
    sheet: Sheet.TrackMenu,
    controller: SheetController,
) {
    val context = LocalContext.current
    val app = context.container
    val t = sheet.track
    val favs by app.playlists.favoriteIds.collectAsState(initial = emptySet())
    val fav = t.id in favs

    Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Thumb(t.artUri(), t.artKey(), Modifier.size(48.dp), corner = 10.dp, icon = BI.Music)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, style = T.title, color = LocalBismo.current.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.artist, style = T.small, color = LocalBismo.current.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    SheetRow(BI.PlayNext, "Reproducir después de la actual") {
        app.player.playNext(listOf(t))
        context.toast("Sonará después de la actual")
        controller.close()
    }
    SheetRow(BI.Queue, "Añadir al final de la cola") {
        app.player.addToQueue(listOf(t))
        context.toast("Añadida a la cola")
        controller.close()
    }
    SheetRow(if (fav) BI.HeartFill else BI.Heart, if (fav) "Quitar de favoritas" else "Añadir a favoritas", highlight = fav) {
        app.appScope.launch { app.playlists.toggleFavorite(t.id) }
    }
    SheetRow(BI.PlaylistAdd, "Añadir a una lista") { controller.open(Sheet.AddToPlaylist(listOf(t.id))) }
    val playlistId = sheet.playlistId
    if (playlistId != null) {
        SheetRow(BI.Trash, "Quitar de esta lista", danger = true) {
            app.appScope.launch { app.playlists.remove(playlistId, t.id) }
            controller.close()
        }
    }
}

// ---------------------------------------------------------------- añadir a una lista

@Composable
private fun ColumnScope.AddToPlaylist(
    sheet: Sheet.AddToPlaylist,
    controller: SheetController,
) {
    val c = LocalBismo.current
    val context = LocalContext.current
    val app = context.container
    val scope = rememberCoroutineScope()
    val lists by app.playlists.playlists.collectAsState(initial = emptyList())

    SheetTitle(if (sheet.trackIds.size == 1) "Añadir a una lista" else "Añadir ${sheet.trackIds.size} canciones a una lista")
    SheetRow(BI.Plus, "Nueva lista", accent = true) { controller.open(Sheet.NamePlaylist(thenAdd = sheet.trackIds)) }
    LazyColumn(Modifier.heightIn(max = 360.dp)) {
        items(lists, key = { it.id }) { p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(Corners.button)
                    .clickable {
                        scope.launch {
                            val added = app.playlists.add(p.id, sheet.trackIds)
                            context.toast(if (added == 0) "Ya estaba en «${p.name}»" else "Añadida a «${p.name}»")
                            controller.close()
                        }
                    }.padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val first = p.firstTrackId
                Thumb(
                    if (first != null) trackArtUri(first, p.firstAlbumId ?: 0L, useAlbumArt = false) else null,
                    p.name,
                    Modifier.size(40.dp),
                    corner = 10.dp,
                    icon = BI.ListMusic,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = T.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(plural(p.count, "canción", "canciones"), style = T.tiny, color = c.muted)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- crear o renombrar

@Composable
private fun ColumnScope.NamePlaylist(
    sheet: Sheet.NamePlaylist,
    controller: SheetController,
) {
    val c = LocalBismo.current
    val context = LocalContext.current
    val app = context.container
    val scope = rememberCoroutineScope()
    var text by remember(sheet) { mutableStateOf(TextFieldValue(sheet.initial, TextRange(sheet.initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(250) // espera a que la hoja termine de subir
        runCatching { focus.requestFocus() }
    }
    val renaming = sheet.playlistId != null
    val name = text.text.trim()

    fun submit() {
        if (name.isEmpty()) return
        scope.launch {
            val id = sheet.playlistId
            if (id != null) {
                app.playlists.rename(id, name)
            } else {
                val newId = app.playlists.create(name)
                if (sheet.thenAdd.isNotEmpty()) app.playlists.add(newId, sheet.thenAdd)
                context.toast("Lista «$name» creada")
            }
            controller.close()
        }
    }

    SheetTitle(if (renaming) "Cambiar el nombre" else "Nueva lista")
    BasicTextField(
        value = text,
        onValueChange = { if (it.text.length <= 60) text = it },
        singleLine = true,
        textStyle = T.body.copy(color = c.text, fontSize = 16.sp),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(focus),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .glass(Corners.button, strong = true, shadow = false)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                if (text.text.isEmpty()) Text("Por ejemplo: Para la moto", style = T.body.copy(fontSize = 16.sp), color = c.muted)
                inner()
            }
        },
    )
    Spacer(Modifier.height(12.dp))
    PrimaryButton(
        if (renaming) "Guardar" else "Crear lista",
        onClick = { submit() },
        modifier = Modifier.fillMaxWidth(),
        icon = BI.Check,
        enabled = name.isNotEmpty(),
    )
}

// ---------------------------------------------------------------- menú de una lista

@Composable
private fun ColumnScope.PlaylistMenu(
    sheet: Sheet.PlaylistMenu,
    controller: SheetController,
) {
    val c = LocalBismo.current
    val app = LocalContext.current.container
    var confirm by remember(sheet) { mutableStateOf(false) }

    SheetTitle(sheet.name)
    AnimatedContent(confirm, label = "confirm") { asking ->
        Column {
            if (!asking) {
                SheetRow(BI.Edit, "Cambiar el nombre") { controller.open(Sheet.NamePlaylist(sheet.id, sheet.name)) }
                SheetRow(BI.Trash, "Eliminar la lista", danger = true) { confirm = true }
            } else {
                Text(
                    "¿Eliminar «${sheet.name}»? Las canciones siguen en tu teléfono; solo se borra la lista.",
                    style = T.body,
                    color = c.text,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .glass(Corners.button, strong = true, shadow = false)
                            .clickable { confirm = false }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Cancelar", style = T.label, color = c.text) }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(Corners.button)
                            .background(c.err)
                            .clickable {
                                app.appScope.launch { app.playlists.delete(sheet.id) }
                                controller.close()
                            }.padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Eliminar", style = T.label, color = Color.White) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- visualizador

@Composable
private fun ColumnScope.VisualizerSheet(sheet: Sheet.Visualizer) {
    val c = LocalBismo.current
    val app = LocalContext.current.container
    val settings by app.settings.flow.collectAsState(initial = null)
    val style = VizStyle.of(settings?.vizStyle ?: "")
    val palette = VizPalette.of(settings?.vizPalette ?: "")
    val colors = palette.colors(sheet.accent)

    SheetTitle("Visualizador")
    Text(
        "También puedes cambiar de estilo deslizando el visualizador a los lados.",
        style = T.small,
        color = c.muted,
        modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
    )
    // Miniaturas de cada estilo, dibujadas con el mismo código del visualizador.
    VizStyle.entries.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { s ->
                val on = s == style
                Column(
                    Modifier
                        .weight(1f)
                        .clip(Corners.button)
                        .clickable { app.appScope.launch { app.settings.set(SettingsRepository.VIZ_STYLE, s.id) } },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(Corners.button)
                            .background(Color(0xFF0B0F1A))
                            .border(if (on) 2.dp else 0.dp, if (on) c.accent else Color.Transparent, Corners.button)
                            .padding(6.dp),
                    ) {
                        val side = kotlin.math.min(size.width, size.height)
                        when (s.cover) {
                            CoverMode.CIRCLE -> drawCircle(Color(0xFF2A3145), radius = side * s.coverSize / 2f)
                            CoverMode.SQUARE ->
                                drawRoundRect(
                                    Color(0xFF2A3145),
                                    topLeft = androidx.compose.ui.geometry.Offset(center.x - side / 2f, center.y - side / 2f),
                                    size = androidx.compose.ui.geometry.Size(side, side),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                                )
                            CoverMode.NONE -> Unit
                        }
                        drawViz(s, PreviewFrame, colors, 0f)
                    }
                    Text(s.label, style = T.tiny, color = if (on) c.accent else c.text, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    Text("Color", style = T.title, color = c.text, modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 8.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(VizPalette.entries, key = { it.id }) { p ->
            val on = p == palette
            Column(
                Modifier
                    .width(58.dp)
                    .clip(Corners.button)
                    .clickable { app.appScope.launch { app.settings.set(SettingsRepository.VIZ_PALETTE, p.id) } }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .border(2.5.dp, if (on) c.accent else Color.Transparent, CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(p.colors(sheet.accent))),
                )
                Spacer(Modifier.height(4.dp))
                Text(p.label, style = T.tiny, color = if (on) c.text else c.muted, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------- temporizador

private val SLEEP_MINUTES = listOf(15, 30, 45, 60, 90)

@Composable
private fun ColumnScope.SleepTimerSheet(controller: SheetController) {
    val c = LocalBismo.current
    val timer = LocalContext.current.container.sleepTimer
    val state by timer.state.collectAsState()

    SheetTitle("Temporizador")
    Text(
        sleepLabel(state) ?: "La música se detiene sola, bajando el volumen poco a poco.",
        style = T.small,
        color = if (state.active) c.accent else c.muted,
        modifier = Modifier.padding(start = 6.dp, bottom = 12.dp),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SLEEP_MINUTES.forEach { m ->
            Box(
                Modifier
                    .weight(1f)
                    .glass(Corners.button, strong = true, shadow = false)
                    .clickable {
                        timer.start(m)
                        controller.close()
                    }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$m", style = T.h2.copy(fontFeatureSettings = "tnum"), color = c.text)
                    Text("min", style = T.tiny, color = c.muted)
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    SheetRow(BI.Music, "Al terminar esta canción", highlight = state.endOfTrack) {
        timer.stopAtEndOfTrack()
        controller.close()
    }
    if (state.active) {
        SheetRow(BI.Close, "Apagar el temporizador", danger = true) {
            timer.cancel()
            controller.close()
        }
    }
}

/** "Se detiene en 23 min" / "Se detiene al terminar esta canción", o null si está apagado. */
@Composable
fun sleepLabel(state: SleepState): String? {
    if (state.endOfTrack) return "Se detiene al terminar esta canción"
    val end = state.endsAt ?: return null
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(end) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }
    val minutes = ((end - now + 59_999) / 60_000).coerceAtLeast(1)
    return "Se detiene en $minutes min"
}

// ---------------------------------------------------------------- piezas

/** Botón ⋯ de una fila de canción: abre su menú. */
@Composable
fun TrackMoreButton(
    track: TrackEntity,
    playlistId: Long? = null,
) {
    val sheets = LocalSheets.current
    Icon(
        BI.More,
        "Más opciones de ${track.title}",
        tint = LocalBismo.current.muted,
        modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable { sheets.open(Sheet.TrackMenu(track, playlistId)) }
                .padding(10.dp),
    )
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        style = T.h2,
        color = LocalBismo.current.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
    )
}

@Composable
private fun SheetRow(
    icon: ImageVector,
    text: String,
    highlight: Boolean = false,
    accent: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val c = LocalBismo.current
    val tint =
        when {
            danger -> c.err
            highlight || accent -> c.accent
            else -> c.text
        }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Corners.button)
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (danger || highlight || accent) tint else c.muted, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, style = T.body.copy(fontSize = 15.sp), color = tint)
    }
}
