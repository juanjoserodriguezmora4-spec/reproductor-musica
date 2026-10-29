package com.bismopower.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bismopower.app.BuildConfig
import com.bismopower.app.container
import com.bismopower.app.data.AppSettings
import com.bismopower.app.data.SettingsRepository
import com.bismopower.app.ui.common.BI
import com.bismopower.app.ui.common.Segmented
import com.bismopower.app.ui.common.glass
import com.bismopower.app.ui.common.plural
import com.bismopower.app.ui.library.BOTTOM_SPACE
import com.bismopower.app.ui.theme.LocalBismo
import com.bismopower.app.ui.theme.T
import com.bismopower.app.ui.theme.blurSupported
import kotlinx.coroutines.launch

private val THEMES = listOf("SYSTEM", "LIGHT", "DARK")

@Composable
fun SettingsScreen() {
    val c = LocalBismo.current
    val app = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val s by app.settings.flow.collectAsState(initial = AppSettings())
    val scanning by app.library.scanning.collectAsStateWithLifecycle()
    val tracks by app.library.tracks.collectAsState(initial = emptyList())
    var lastResult by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = BOTTOM_SPACE),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Ajustes", style = T.h1, color = c.text) }

        item { GroupTitle("APARIENCIA") }
        item {
            Group {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (c.dark) BI.Moon else BI.Sun, null, tint = c.muted, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Tema", style = T.body, color = c.text)
                    }
                    Spacer(Modifier.size(10.dp))
                    Segmented(
                        listOf("Sistema", "Claro", "Oscuro"),
                        THEMES.indexOf(s.theme).coerceAtLeast(0),
                        { scope.launch { app.settings.set(SettingsRepository.THEME, THEMES[it]) } },
                    )
                }
                Divider()
                ToggleRow(
                    BI.Wave,
                    "Reducir transparencia",
                    if (blurSupported) "Paneles sólidos, sin desenfoque. Ahorra batería." else "Tu versión de Android usa paneles sólidos siempre.",
                    checked = s.reduceTransparency || !blurSupported,
                    enabled = blurSupported,
                ) { scope.launch { app.settings.set(SettingsRepository.REDUCE_TRANSPARENCY, it) } }
            }
        }

        item { GroupTitle("BIBLIOTECA") }
        item {
            Group {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !scanning) {
                            scope.launch { lastResult = app.library.scan() }
                        }.padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(BI.Retry, null, tint = c.muted, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Volver a buscar música", style = T.body, color = c.text)
                        val sub =
                            when {
                                scanning -> "Buscando…"
                                lastResult != null -> "Listo: ${plural(lastResult ?: 0, "canción", "canciones")}"
                                else -> plural(tracks.size, "canción", "canciones") + " en tu biblioteca"
                            }
                        Text(sub, style = T.tiny, color = c.muted)
                    }
                    if (scanning) CircularProgressIndicator(Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp)
                }
            }
        }

        item { GroupTitle("ACERCA DE") }
        item {
            Group {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(BI.Info, null, tint = c.muted, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("BismoPower", style = T.body, color = c.text)
                        Text("Versión ${BuildConfig.VERSION_NAME} · Sin anuncios ni rastreadores", style = T.tiny, color = c.muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    val c = LocalBismo.current
    Text(text, style = T.overline, color = c.muted, modifier = Modifier.padding(start = 6.dp, top = 10.dp))
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(),
    ) { content() }
}

@Composable
private fun Divider() {
    val c = LocalBismo.current
    HorizontalDivider(color = c.track, modifier = Modifier.padding(start = 45.dp))
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val c = LocalBismo.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.muted, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = T.body, color = c.text)
            if (subtitle != null) Text(subtitle, style = T.tiny, color = c.muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors =
                SwitchDefaults.colors(
                    checkedTrackColor = c.accent,
                    checkedThumbColor = Color.White,
                    uncheckedTrackColor = c.track,
                    uncheckedBorderColor = Color.Transparent,
                ),
        )
    }
}
