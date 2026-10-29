package com.bismopower.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bismopower.app.player.PlayerConnection
import com.bismopower.app.player.PlayerUiState
import com.bismopower.app.ui.theme.LocalBismo
import com.bismopower.app.ui.theme.T
import kotlinx.coroutines.delay

data class Tab(
    val label: String,
    val icon: ImageVector,
)

val TABS =
    listOf(
        Tab("Biblioteca", BI.Music),
        Tab("Listas", BI.ListMusic),
        Tab("Ecualizador", BI.Sliders),
        Tab("Ajustes", BI.Gear),
    )

/** Barra de navegación inferior flotante de vidrio (esquinas de 26 dp). */
@Composable
fun BottomNav(
    current: Int,
    onSelect: (Int) -> Unit,
) {
    val c = LocalBismo.current
    Row(
        Modifier
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .fillMaxWidth()
            .glass(Corners.bar, strong = true, blur = true)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        TABS.forEachIndexed { i, t ->
            val on = current == i
            val tint by animateColorAsState(if (on) c.accent else c.muted, label = "tab")
            Column(
                Modifier
                    .clip(Corners.button)
                    .background(if (on) c.accentSoft else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(t.icon, t.label, tint = tint, modifier = Modifier.size(22.dp))
                Text(t.label, style = T.tiny, color = tint, maxLines = 1)
            }
        }
    }
}

/** Mini reproductor de vidrio que flota encima de la barra inferior. */
@Composable
fun MiniPlayerBar(
    state: PlayerUiState,
    connection: PlayerConnection,
    onOpen: () -> Unit,
) {
    val c = LocalBismo.current
    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.currentId, state.isPlaying) {
        position = connection.position()
        while (state.isPlaying) {
            delay(1000)
            position = connection.position()
        }
    }
    Column(
        Modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .glass(Corners.card, strong = true, blur = true)
            .clickable { onOpen() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumb(state.artwork, state.album.ifBlank { state.title }, Modifier.size(40.dp), corner = 10.dp, icon = BI.Music)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(state.title, style = T.small, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state.artist.isNotBlank()) {
                    Text(state.artist, style = T.tiny, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(
                if (state.isPlaying) BI.Pause else BI.Play,
                if (state.isPlaying) "Pausar" else "Reproducir",
                tint = c.text,
                modifier =
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { connection.toggle() }
                        .padding(8.dp),
            )
            Icon(
                BI.Next,
                "Siguiente",
                tint = c.text,
                modifier =
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { connection.next() }
                        .padding(9.dp),
            )
        }
        val fraction = if (state.durationMs > 0) position.toFloat() / state.durationMs else 0f
        ProgressLine(fraction, c.accent, Modifier.padding(top = 6.dp), height = 3.dp)
    }
}
