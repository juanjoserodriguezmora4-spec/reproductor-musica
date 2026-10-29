package com.bismopower.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import com.bismopower.app.ui.common.BI
import com.bismopower.app.ui.common.BottomNav
import com.bismopower.app.ui.common.GlassBackground
import com.bismopower.app.ui.common.LocalHaze
import com.bismopower.app.ui.common.MiniPlayerBar
import com.bismopower.app.ui.library.LibraryScreen
import com.bismopower.app.ui.player.PlayerSheet
import com.bismopower.app.ui.settings.SettingsScreen
import com.bismopower.app.ui.soon.ComingSoonScreen
import com.bismopower.app.ui.theme.LocalBismo
import com.bismopower.app.ui.theme.LocalSolid
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun BismoRoot(
    startRoute: String?,
    onRouteConsumed: () -> Unit,
) {
    val app = LocalContext.current.container
    val player by app.player.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val holder = rememberSaveableStateHolder()
    val solid = LocalSolid.current
    val haze = rememberHazeState(blurEnabled = !solid)

    LaunchedEffect(Unit) { app.player.connect() }
    LaunchedEffect(startRoute) {
        if (startRoute == "player") sheetOpen = true
        if (startRoute != null) onRouteConsumed()
    }
    BackHandler(enabled = sheetOpen) { sheetOpen = false }

    CompositionLocalProvider(LocalHaze provides haze) {
        Box(Modifier.fillMaxSize()) {
            // Capa 1 y contenido: lo que las barras de vidrio desenfocan al pasar por encima.
            GlassBackground(Modifier.hazeSource(haze), seed = tab) {
                Crossfade(tab, animationSpec = tween(180), label = "tab") { t ->
                    holder.SaveableStateProvider(t) {
                        when (t) {
                            0 -> LibraryScreen()
                            1 ->
                                ComingSoonScreen(
                                    title = "Listas",
                                    icon = BI.ListMusic,
                                    text = "Pronto podrás crear listas, ver tus favoritas y lo que más escuchas.",
                                )
                            2 ->
                                ComingSoonScreen(
                                    title = "Ecualizador",
                                    icon = BI.Sliders,
                                    text = "Un ecualizador paramétrico con filtros precisos para cada banda llega en la próxima versión.",
                                )
                            else -> SettingsScreen()
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
                AnimatedVisibility(
                    visible = player.currentId != null,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut(),
                ) {
                    MiniPlayerBar(player, app.player) { sheetOpen = true }
                }
                BottomNav(tab) { tab = it }
            }

            // Reproductor completo: hoja que sube por encima de todo.
            AnimatedVisibility(
                visible = sheetOpen,
                enter = slideInVertically(tween(320)) { it },
                exit = slideOutVertically(tween(260)) { it },
            ) {
                PlayerSheet(onClose = { sheetOpen = false })
            }
        }
    }
}
