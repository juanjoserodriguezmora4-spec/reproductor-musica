package com.bismopower.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bismopower.app.container
import com.bismopower.app.ui.common.BottomNav
import com.bismopower.app.ui.common.GlassBackground
import com.bismopower.app.ui.common.LocalHaze
import com.bismopower.app.ui.common.LocalSelection
import com.bismopower.app.ui.common.MiniPlayerBar
import com.bismopower.app.ui.common.SelectionBar
import com.bismopower.app.ui.common.SelectionState
import com.bismopower.app.ui.common.plural
import com.bismopower.app.ui.equalizer.EqualizerScreen
import com.bismopower.app.ui.library.LibraryScreen
import com.bismopower.app.ui.player.PlayerScreen
import com.bismopower.app.ui.playlists.PlaylistsScreen
import com.bismopower.app.ui.settings.DuplicatesScreen
import com.bismopower.app.ui.settings.FolderPickerScreen
import com.bismopower.app.ui.settings.SettingsScreen
import com.bismopower.app.ui.sheets.LocalDeleter
import com.bismopower.app.ui.sheets.LocalSheets
import com.bismopower.app.ui.sheets.Sheet
import com.bismopower.app.ui.sheets.SheetController
import com.bismopower.app.ui.sheets.SheetHost
import com.bismopower.app.ui.sheets.rememberTrackDeleter
import com.bismopower.app.ui.sheets.toast
import com.bismopower.app.ui.theme.LocalBismo
import com.bismopower.app.ui.theme.LocalSolid
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

@Composable
fun BismoRoot(
    startRoute: String?,
    onRouteConsumed: () -> Unit,
) {
    val app = LocalContext.current.container
    val player by app.player.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var foldersOpen by rememberSaveable { mutableStateOf(false) }
    val holder = rememberSaveableStateHolder()
    val solid = LocalSolid.current
    val haze = rememberHazeState(blurEnabled = !solid)
    val sheets = remember { SheetController() }
    val selection = remember { SelectionState() }
    val deleter = rememberTrackDeleter(sheets)
    var duplicatesOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    // Al cambiar de pestaña se suelta la selección.
    LaunchedEffect(tab) { selection.clear() }

    LaunchedEffect(Unit) { app.player.connect() }
    LaunchedEffect(startRoute) {
        if (startRoute == "player") sheetOpen = true
        if (startRoute != null) onRouteConsumed()
    }
    BackHandler(enabled = foldersOpen && !sheetOpen) { foldersOpen = false }
    BackHandler(enabled = duplicatesOpen && !sheetOpen) { duplicatesOpen = false }
    BackHandler(enabled = sheetOpen) { sheetOpen = false }

    CompositionLocalProvider(
        LocalHaze provides haze,
        LocalSheets provides sheets,
        LocalSelection provides selection,
        LocalDeleter provides deleter,
    ) {
        Box(Modifier.fillMaxSize()) {
            // Capa 1 y contenido: lo que las barras de vidrio desenfocan al pasar por encima.
            GlassBackground(Modifier.hazeSource(haze), seed = tab) {
                Crossfade(tab, animationSpec = tween(180), label = "tab") { t ->
                    holder.SaveableStateProvider(t) {
                        when (t) {
                            0 -> LibraryScreen(onOpenFolders = { foldersOpen = true })
                            1 -> PlaylistsScreen()
                            2 -> EqualizerScreen()
                            else ->
                                SettingsScreen(
                                    onOpenFolders = { foldersOpen = true },
                                    onOpenDuplicates = { duplicatesOpen = true },
                                )
                        }
                    }
                }
            }

            // Sin desenfoque, un degradado suave separa el contenido de las barras.
            val bg = LocalBismo.current.bg
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .then(
                        if (solid) {
                            Modifier.background(Brush.verticalGradient(0f to bg.copy(alpha = 0f), 0.5f to bg.copy(alpha = 0.7f), 1f to bg))
                        } else {
                            Modifier
                        },
                    ).padding(top = if (solid) 28.dp else 0.dp)
                    .navigationBarsPadding(),
            ) {
                if (selection.active) {
                    // Mientras hay canciones marcadas, "atrás" quita la selección.
                    BackHandler { selection.clear() }
                    SelectionBar(
                        selection,
                        onPlay = {
                            app.player.play(selection.inOrder(), 0)
                            selection.clear()
                        },
                        onPlayNext = {
                            app.player.playNext(selection.inOrder())
                            context.toast("Sonarán después de la actual")
                            selection.clear()
                        },
                        onQueue = {
                            val added = app.player.addToQueue(selection.inOrder())
                            context.toast(if (added == 0) "Ya estaban en la cola" else plural(added, "añadida a la cola", "añadidas a la cola"))
                            selection.clear()
                        },
                        onAddToList = {
                            sheets.open(Sheet.AddToPlaylist(selection.inOrder().map { it.id }))
                            selection.clear()
                        },
                        onFavorite = {
                            val ids = selection.inOrder().map { it.id }
                            app.appScope.launch { app.playlists.addFavorites(ids) }
                            context.toast("Añadidas a favoritas")
                            selection.clear()
                        },
                        onDelete = {
                            deleter.request(selection.inOrder()) { selection.clear() }
                        },
                    )
                } else {
                    AnimatedVisibility(
                        visible = player.currentId != null,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut(),
                    ) {
                        MiniPlayerBar(player, app.player) { sheetOpen = true }
                    }
                    BottomNav(tab) { tab = it }
                }
            }

            // Elegir carpetas de música: entra desde la derecha, por encima de las pestañas.
            AnimatedVisibility(
                visible = foldersOpen,
                enter = slideInHorizontally(tween(260)) { it } + fadeIn(tween(200)),
                exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(200)),
            ) {
                FolderPickerScreen(onClose = { foldersOpen = false })
            }

            // Canciones repetidas: igual que las carpetas, entra desde la derecha.
            AnimatedVisibility(
                visible = duplicatesOpen,
                enter = slideInHorizontally(tween(260)) { it } + fadeIn(tween(200)),
                exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(200)),
            ) {
                DuplicatesScreen(onClose = { duplicatesOpen = false })
            }

            // Reproductor a pantalla completa: sube desde el mini reproductor con un rebote suave.
            AnimatedVisibility(
                visible = sheetOpen,
                enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 380f)) { it } + fadeIn(tween(160)),
                exit = slideOutVertically(tween(240)) { it } + fadeOut(tween(240)),
            ) {
                PlayerScreen(
                    onClose = { sheetOpen = false },
                    onOpenEqualizer = {
                        tab = 2
                        sheetOpen = false
                    },
                )
            }

            // Menús y hojas de acciones (canción, listas, temporizador): encima de todo, incluso del reproductor.
            SheetHost(sheets)
        }
    }
}
