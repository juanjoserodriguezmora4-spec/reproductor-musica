package com.bismopower.app.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bismopower.app.container
import com.bismopower.app.library.Lyrics
import com.bismopower.app.ui.common.BI
import com.bismopower.app.ui.theme.T

/** Estado de la búsqueda de la letra. */
private sealed interface LyricsState {
    data object Loading : LyricsState

    data object Missing : LyricsState

    data class Found(
        val lyrics: Lyrics,
    ) : LyricsState
}

/**
 * Letra de la canción en el lugar de la carátula.
 *
 * Con letra sincronizada (LRC), la línea que se está cantando se ilumina y la lista la sigue
 * sola; tocar una línea salta a ese momento. Si arrastras la lista, deja de seguir la canción
 * por 4 segundos para que puedas leer tranquilo.
 */
@Composable
fun LyricsView(
    trackId: Long?,
    positionMs: Long,
    accent: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = LocalContext.current.container.lyrics
    var retry by remember { mutableIntStateOf(0) }
    val state by produceState<LyricsState>(LyricsState.Loading, trackId, retry) {
        value = LyricsState.Loading
        val found = trackId?.let { repo.forTrack(it) }
        value = if (found != null) LyricsState.Found(found) else LyricsState.Missing
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        when (val s = state) {
            LyricsState.Loading ->
                CircularProgressIndicator(Modifier.size(26.dp), color = Color.White.copy(alpha = 0.7f), strokeWidth = 2.dp)
            LyricsState.Missing ->
                NoLyrics {
                    repo.invalidate()
                    retry++
                }
            is LyricsState.Found ->
                if (s.lyrics.synced) {
                    SyncedLyrics(s.lyrics, positionMs, accent, onSeek)
                } else {
                    PlainLyrics(s.lyrics)
                }
        }
    }
}

@Composable
private fun SyncedLyrics(
    lyrics: Lyrics,
    positionMs: Long,
    accent: Color,
    onSeek: (Long) -> Unit,
) {
    val lines = lyrics.lines
    // Se adelanta un poco para que la línea se ilumine justo cuando empieza a cantarse.
    val now = positionMs + 200
    val current = remember(now, lines) { lines.indexOfLast { it.timeMs <= now } }
    val listState = rememberLazyListState()
    val dragging by listState.interactionSource.collectIsDraggedAsState()
    var lastDrag by remember { mutableLongStateOf(0L) }
    // Guarda cuándo empezó y cuándo terminó de arrastrar.
    LaunchedEffect(dragging) { if (dragging || lastDrag > 0L) lastDrag = System.currentTimeMillis() }

    LaunchedEffect(current) {
        if (current < 0) return@LaunchedEffect
        if (System.currentTimeMillis() - lastDrag < 4_000) return@LaunchedEffect
        // Deja la línea actual a un tercio de la altura, como en los reproductores con karaoke.
        val viewport = listState.layoutInfo.viewportSize.height
        listState.animateScrollToItem(current, scrollOffset = -(viewport / 3))
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().fadeEdges(),
        contentPadding = PaddingValues(vertical = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(lines, key = { i, _ -> i }) { i, line ->
            val isCurrent = i == current
            val color by animateColorAsState(
                when {
                    isCurrent -> Color.White
                    i < current -> Color.White.copy(alpha = 0.38f)
                    else -> Color.White.copy(alpha = 0.6f)
                },
                tween(250),
                label = "lineColor",
            )
            val scale by animateFloatAsState(if (isCurrent) 1f else 0.92f, tween(250), label = "lineScale")
            Text(
                line.text.ifBlank { "♪" },
                style = T.big.copy(fontSize = 22.sp, lineHeight = 29.sp, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold),
                color = if (line.text.isBlank() && isCurrent) accent else color,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        }.clickable { onSeek(line.timeMs) }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun PlainLyrics(lyrics: Lyrics) {
    LazyColumn(
        Modifier.fillMaxSize().fadeEdges(),
        contentPadding = PaddingValues(vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text(
                "Esta letra no tiene tiempos, así que no avanza con la canción.",
                style = T.tiny,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        itemsIndexed(lyrics.lines) { _, line ->
            Text(
                line.text,
                style = T.body.copy(fontSize = 17.sp, lineHeight = 25.sp),
                color = Color.White.copy(alpha = 0.88f),
            )
        }
    }
}

@Composable
private fun NoLyrics(onRetry: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(BI.Lyrics, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(12.dp))
        Text("Esta canción no tiene letra", style = T.h2, color = Color.White)
        Spacer(Modifier.height(6.dp))
        Text(
            "Pon un archivo .lrc con el mismo nombre de la canción en tu carpeta de letras " +
                "(Ajustes → Carpeta de letras), o usa canciones que traigan la letra incluida.",
            style = T.small,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Buscar otra vez",
            style = T.label,
            color = Color.White,
            modifier =
                Modifier
                    .clickable { onRetry() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** Desvanece el texto arriba y abajo para que las líneas no se corten de golpe. */
private fun Modifier.fadeEdges(): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.14f to Color.Black,
                    0.86f to Color.Black,
                    1f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
