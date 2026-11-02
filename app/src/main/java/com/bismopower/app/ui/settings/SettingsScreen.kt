package com.bismopower.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
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
import com.bismopower.app.ui.theme.COLOR_THEMES
import com.bismopower.app.ui.theme.DYNAMIC_THEME
import com.bismopower.app.ui.theme.LocalBismo
import com.bismopower.app.ui.theme.T
import kotlinx.coroutines.launch

private val THEMES = listOf("SYSTEM", "LIGHT", "DARK")
private val CROSSFADE_SECONDS = listOf(0, 3, 6, 10)
private val CROSSFADE_LABELS = listOf("Apagado", "3 s", "6 s", "10 s")

/** Nombre legible de la carpeta elegida ("primary:Music/Letras" → "Music/Letras"). */
private fun folderName(treeUri: String): String =
    runCatching {
        val id = DocumentsContract.getTreeDocumentId(Uri.parse(treeUri))
        id.substringAfter(':').ifBlank { "Almacenamiento interno" }
    }.getOrDefault("la carpeta elegida")

@Composable
fun SettingsScreen(onOpenFolders: () -> Unit = {}) {
    val c = LocalBismo.current
    val app = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val s by app.settings.flow.collectAsState(initial = AppSettings())
    val scanning by app.library.scanning.collectAsStateWithLifecycle()
    val tracks by app.library.tracks.collectAsState(initial = emptyList())
    var lastResult by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current

    // Elegir la carpeta de letras: Android pregunta y la app guarda el permiso para siempre.
    val pickLyrics =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                scope.launch {
                    app.settings.set(SettingsRepository.LYRICS_FOLDER, uri.toString())
                    app.lyrics.invalidate()
                }
            }
        }

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
                Column(Modifier.padding(top = 12.dp, bottom = 10.dp)) {
                    Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(BI.Sliders, null, tint = c.muted, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Color", style = T.body, color = c.text)
                    }
                    Spacer(Modifier.size(10.dp))
                    ColorThemePicker(s.colorTheme) { id -> scope.launch { app.settings.set(SettingsRepository.COLOR_THEME, id) } }
                }
                Divider()
                ToggleRow(
                    BI.Wave,
                    "Reducir transparencia",
                    "Paneles opacos y sin desenfoque. Se lee mejor y ahorra batería.",
                    checked = s.reduceTransparency,
                ) { scope.launch { app.settings.set(SettingsRepository.REDUCE_TRANSPARENCY, it) } }
            }
        }

        item { GroupTitle("REPRODUCCIÓN") }
        item {
            Group {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(BI.Wave, null, tint = c.muted, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Fundido entre canciones", style = T.body, color = c.text)
                    }
                    Spacer(Modifier.size(10.dp))
                    Segmented(
                        CROSSFADE_LABELS,
                        CROSSFADE_SECONDS.indexOf(s.crossfadeSec).coerceAtLeast(0),
                        { scope.launch { app.settings.set(SettingsRepository.CROSSFADE_SEC, CROSSFADE_SECONDS[it]) } },
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (s.crossfadeSec == 0) {
                            "Apagado: una canción empieza justo donde termina la otra, sin silencios (ideal para discos en vivo o mezclados)."
                        } else {
                            "La canción que termina baja mientras la siguiente sube, durante ${s.crossfadeSec} segundos."
                        },
                        style = T.tiny,
                        color = c.muted,
                    )
                }
                Divider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { pickLyrics.launch(null) }
                        .padding(start = 14.dp, end = 6.dp, top = 13.dp, bottom = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(BI.Lyrics, null, tint = c.muted, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Carpeta de letras", style = T.body, color = c.text)
                        Text(
                            s.lyricsFolder?.let { "Buscando archivos .lrc en «${folderName(it)}»" }
                                ?: "Elige dónde guardas tus archivos .lrc",
                            style = T.tiny,
                            color = c.muted,
                        )
                    }
                    if (s.lyricsFolder != null) {
                        Icon(
                            BI.Close,
                            "Quitar la carpeta de letras",
                            tint = c.muted,
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        scope.launch {
                                            app.settings.set(SettingsRepository.LYRICS_FOLDER, "")
                                            app.lyrics.invalidate()
                                        }
                                    }.padding(11.dp),
                        )
                    } else {
                        Icon(BI.Chevron, null, tint = c.muted, modifier = Modifier.size(18.dp).padding(end = 2.dp))
                    }
                }
            }
        }

        item { GroupTitle("BIBLIOTECA") }
        item {
            Group {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenFolders() }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(BI.Folder, null, tint = c.muted, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Carpetas de música", style = T.body, color = c.text)
                        Text(
                            if (s.onlyChosenFolders) {
                                "Solo " + plural(s.musicFolders.size, "carpeta elegida", "carpetas elegidas")
                            } else {
                                "Todas las carpetas del teléfono"
                            },
                            style = T.tiny,
                            color = c.muted,
                        )
                    }
                    Icon(BI.Chevron, null, tint = c.muted, modifier = Modifier.size(18.dp))
                }
                Divider()
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
                    checkedThumbColor = c.onAccent,
                    uncheckedTrackColor = c.track,
                    uncheckedBorderColor = Color.Transparent,
                ),
        )
    }
}

/** Muestras de color: el círculo de fuera son las manchas del fondo y el punto del centro es el acento. */
@Composable
private fun ColorThemePicker(
    current: String,
    onPick: (String) -> Unit,
) {
    val c = LocalBismo.current
    val context = LocalContext.current
    val options =
        buildList {
            COLOR_THEMES.forEach { t ->
                val blobs = if (c.dark) t.darkBlobs else t.lightBlobs
                add(Triple(t.id, t.name, Triple(blobs[0], blobs[1], if (c.dark) t.darkAccent else t.lightAccent)))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val d = if (c.dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                add(Triple(DYNAMIC_THEME, "Fondo de pantalla", Triple(d.primaryContainer, d.tertiaryContainer, d.primary)))
            }
        }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(options, key = { it.first }) { (id, name, colors) ->
            val on = id == current
            Column(
                Modifier
                    .width(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPick(id) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .border(2.5.dp, if (on) colors.third else Color.Transparent, CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(colors.first, colors.second))),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(colors.third),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    name,
                    style = T.tiny,
                    color = if (on) c.text else c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
