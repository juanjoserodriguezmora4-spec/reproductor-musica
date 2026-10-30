package com.bismopower.app.ui.player

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.bismopower.app.container
import com.bismopower.app.player.PlayerConnection
import com.bismopower.app.player.PlayerUiState
import com.bismopower.app.ui.common.BI
import com.bismopower.app.ui.common.Thumb
import com.bismopower.app.ui.common.fmtDuration
import com.bismopower.app.ui.theme.LocalBismo
import com.bismopower.app.ui.theme.LocalBismoDark
import com.bismopower.app.ui.theme.LocalSolid
import com.bismopower.app.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val PlayerBg = Color(0xFF07090F)
private val OnAccent = Color(0xFF0B0F1A)

/**
 * Reproductor a pantalla completa, con los gestos de un reproductor avanzado:
 *
 * - Carátula: deslizar a la izquierda / derecha = siguiente / anterior (la carátula sigue al dedo).
 * - Carátula: deslizar hacia arriba = abrir la cola; hacia abajo = cerrar el reproductor.
 * - Carátula: un toque = pausa / reproducir; doble toque a la izquierda o derecha = −10 s / +10 s.
 * - Botones anterior / siguiente: tocar = cambiar de canción; mantener = retroceder / adelantar.
 * - Onda de progreso: tocar o arrastrar para saltar a otro punto.
 *
 * Siempre es oscuro (aunque la app esté en modo claro) para que la carátula sea la protagonista.
 */
@Composable
fun PlayerScreen(
    onClose: () -> Unit,
    onOpenEqualizer: () -> Unit,
) {
    val appDark = LocalBismo.current.dark
    val darkColors = LocalBismoDark.current
    val player = LocalContext.current.container.player
    val s by player.state.collectAsStateWithLifecycle()
    val queue by player.queue.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val colors = rememberPlayerColors(s.artwork, s.album.ifBlank { s.title })
    var queueOpen by remember { mutableStateOf(false) }

    // Íconos claros en la barra de estado mientras el reproductor está abierto.
    val view = LocalView.current
    DisposableEffect(appDark) {
        val window = (view.context as? Activity)?.window
        val ctrl = window?.let { WindowCompat.getInsetsController(it, view) }
        ctrl?.isAppearanceLightStatusBars = false
        ctrl?.isAppearanceLightNavigationBars = false
        onDispose {
            ctrl?.isAppearanceLightStatusBars = !appDark
            ctrl?.isAppearanceLightNavigationBars = !appDark
        }
    }
    BackHandler(enabled = queueOpen) { queueOpen = false }

    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(s.currentId, s.isPlaying) {
        position = player.position()
        while (s.isPlaying) {
            delay(250)
            position = player.position()
        }
    }

    // Desplazamiento vertical de toda la pantalla al arrastrarla hacia abajo para cerrarla.
    val sheetY = remember { Animatable(0f) }

    CompositionLocalProvider(LocalBismo provides darkColors) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(0, sheetY.value.roundToInt()) }
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)),
        ) {
            val heightPx = with(density) { maxHeight.toPx() }
            val closeDrag: (Float) -> Unit = { d -> scope.launch { sheetY.snapTo((sheetY.value + d).coerceAtLeast(0f)) } }
            val closeEnd: () -> Unit = {
                scope.launch {
                    if (sheetY.value > heightPx * 0.2f) {
                        onClose()
                    } else {
                        sheetY.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f))
                    }
                }
            }

            PlayerBackdrop(s.artwork, colors)

            Column(
                Modifier
                    .fillMaxSize()
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { d -> closeDrag(d) },
                        onDragStopped = { velocity -> if (velocity > 2400f) onClose() else closeEnd() },
                    ).statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TopBar(s, onClose = onClose, onQueue = { queueOpen = true })

                if (s.currentId == null) {
                    Spacer(Modifier.weight(1f))
                    Text("No hay nada sonando", style = T.h2, color = Color.White)
                    Spacer(Modifier.height(6.dp))
                    Text("Elige una canción en tu biblioteca.", style = T.small, color = Color.White.copy(alpha = 0.6f))
                    Spacer(Modifier.weight(1f))
                    return@Column
                }

                Spacer(Modifier.weight(0.5f))
                SwipeArtwork(
                    state = s,
                    player = player,
                    onDragDown = closeDrag,
                    onDragDownEnd = closeEnd,
                    onSwipeUp = { queueOpen = true },
                    modifier =
                        Modifier
                            .weight(6f, fill = false)
                            .aspectRatio(1f),
                )
                Spacer(Modifier.weight(0.5f))

                TrackTitle(s)
                Spacer(Modifier.height(6.dp))

                val dur = s.durationMs.coerceAtLeast(1L)
                WaveSeekBar(
                    trackKey = s.currentId,
                    fraction = position.toFloat() / dur,
                    durationMs = s.durationMs,
                    accent = colors.accent,
                    onSeek = { f ->
                        val ms = (f * dur).toLong()
                        player.seek(ms)
                        position = ms
                    },
                )
                Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    Text(fmtDuration(position), style = T.tiny, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                    Text("−" + fmtDuration((s.durationMs - position).coerceAtLeast(0L)), style = T.tiny, color = Color.White.copy(alpha = 0.7f))
                }
                Spacer(Modifier.height(14.dp))

                MainControls(s, player, colors.accent)
                Spacer(Modifier.height(16.dp))
                SecondaryControls(
                    s = s,
                    accent = colors.accent,
                    onShuffle = { player.setShuffle(!s.shuffle) },
                    onRepeat = { player.cycleRepeat() },
                    onEqualizer = onOpenEqualizer,
                    onQueue = { queueOpen = true },
                )
                Spacer(Modifier.height(8.dp))
            }

            AnimatedVisibility(
                visible = queueOpen,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180)),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .pointerInput(Unit) { detectTapGestures { queueOpen = false } },
                )
            }
            AnimatedVisibility(
                visible = queueOpen,
                enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = 420f)) { it },
                exit = slideOutVertically(tween(220)) { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                QueueSheet(
                    items = queue,
                    currentIndex = s.index,
                    accent = colors.accent,
                    onPlay = { player.playAt(it) },
                    onRemove = { player.removeAt(it) },
                    onClose = { queueOpen = false },
                )
            }
        }
    }
}

/** Fondo: la carátula muy desenfocada y oscurecida, con dos brillos del color de la canción. */
@Composable
private fun PlayerBackdrop(
    artwork: String?,
    colors: PlayerColors,
) {
    val solid = LocalSolid.current
    Box(
        Modifier
            .fillMaxSize()
            .background(PlayerBg)
            .drawBehind {
                val w = size.width
                val h = size.height
                drawCircle(
                    Brush.radialGradient(
                        listOf(colors.glowTop.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(w * 0.2f, h * 0.12f),
                        radius = w * 1.1f,
                    ),
                    radius = w * 1.1f,
                    center = Offset(w * 0.2f, h * 0.12f),
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(colors.glowBottom.copy(alpha = 0.45f), Color.Transparent),
                        center = Offset(w * 0.9f, h * 0.95f),
                        radius = w * 1.0f,
                    ),
                    radius = w * 1.0f,
                    center = Offset(w * 0.9f, h * 0.95f),
                )
            },
    ) {
        // El desenfoque real solo existe desde Android 12; antes de eso quedan los brillos de color.
        if (!solid && artwork != null) {
            AnimatedContent(
                targetState = artwork,
                transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(600)) },
                label = "backdrop",
            ) { art ->
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = 1.35f
                                scaleY = 1.35f
                                alpha = 0.55f
                            }.blur(70.dp),
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.25f),
                        0.55f to Color.Black.copy(alpha = 0.35f),
                        1f to Color.Black.copy(alpha = 0.75f),
                    ),
                ),
        )
    }
}

@Composable
private fun TopBar(
    s: PlayerUiState,
    onClose: () -> Unit,
    onQueue: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RoundIcon(BI.Down, "Cerrar reproductor", Color.White, onClose)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("REPRODUCIENDO", style = T.overline, color = Color.White.copy(alpha = 0.6f))
            if (s.count > 1) Text("${s.index + 1} de ${s.count}", style = T.tiny, color = Color.White.copy(alpha = 0.6f))
        }
        RoundIcon(BI.Queue, "Abrir la cola", Color.White, onQueue)
    }
}

/**
 * Carátula que sigue al dedo. Al soltarla pasada cierta distancia sale volando hacia ese lado,
 * cambia la canción y la nueva carátula entra por el lado contrario con un pequeño rebote.
 */
@Composable
private fun SwipeArtwork(
    state: PlayerUiState,
    player: PlayerConnection,
    onDragDown: (Float) -> Unit,
    onDragDownEnd: () -> Unit,
    onSwipeUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val s by rememberUpdatedState(state)
    val dragDown by rememberUpdatedState(onDragDown)
    val dragDownEnd by rememberUpdatedState(onDragDownEnd)
    val swipeUp by rememberUpdatedState(onSwipeUp)

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val dragX = remember { Animatable(0f) }
        // +1 = la canción nueva entra por la derecha (siguiente), −1 = por la izquierda (anterior).
        var pendingDir by remember { mutableIntStateOf(0) }
        var lastId by remember { mutableStateOf(state.currentId) }
        var lastIndex by remember { mutableIntStateOf(state.index) }

        LaunchedEffect(state.currentId) {
            // Se guarda todo antes de animar: si la canción vuelve a cambiar a mitad de la
            // animación, este efecto se cancela y el siguiente parte de datos correctos.
            val prev = lastId
            val prevIndex = lastIndex
            val requested = pendingDir
            lastId = state.currentId
            lastIndex = state.index
            pendingDir = 0
            if (prev != null && state.currentId != prev) {
                val dir =
                    when {
                        requested != 0 -> requested
                        state.index < prevIndex -> -1
                        else -> 1
                    }
                dragX.snapTo(dir * widthPx * 0.85f)
                dragX.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 340f))
            }
        }

        // Pausa: la carátula se encoge un poco; al reproducir vuelve con un rebote suave.
        val pausedScale by animateFloatAsState(
            if (state.isPlaying) 1f else 0.88f,
            spring(dampingRatio = 0.55f, stiffness = 260f),
            label = "pausedScale",
        )

        // Ícono grande que aparece y se desvanece al tocar la carátula.
        var flashIcon by remember { mutableStateOf<ImageVector?>(null) }
        var flashCount by remember { mutableIntStateOf(0) }
        val flashAlpha = remember { Animatable(0f) }
        val flashScale = remember { Animatable(1f) }
        LaunchedEffect(flashCount) {
            if (flashCount == 0) return@LaunchedEffect
            flashAlpha.snapTo(1f)
            flashScale.snapTo(0.7f)
            launch { flashScale.animateTo(1.15f, tween(420)) }
            delay(160)
            flashAlpha.animateTo(0f, tween(300))
        }
        fun flash(icon: ImageVector) {
            flashIcon = icon
            flashCount++
        }

        fun settle() = scope.launch { dragX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 420f)) }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            flash(if (s.isPlaying) BI.Pause else BI.Play)
                            player.toggle()
                        },
                        onDoubleTap = { pos ->
                            val w = size.width
                            when {
                                pos.x < w / 3f -> {
                                    player.seekBy(-10_000)
                                    flash(BI.Prev)
                                }
                                pos.x > w * 2f / 3f -> {
                                    player.seekBy(10_000)
                                    flash(BI.Next)
                                }
                                else -> {
                                    flash(if (s.isPlaying) BI.Pause else BI.Play)
                                    player.toggle()
                                }
                            }
                        },
                    )
                }.pointerInput(Unit) {
                    var axis = 0 // 0 = sin decidir, 1 = horizontal, 2 = vertical
                    var totalY = 0f
                    detectDragGestures(
                        onDragStart = {
                            axis = 0
                            totalY = 0f
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            if (axis == 0) axis = if (abs(amount.x) >= abs(amount.y)) 1 else 2
                            if (axis == 1) {
                                // Si no hay canción hacia ese lado, la carátula se resiste.
                                val towardNext = dragX.value + amount.x < 0f
                                val free = if (towardNext) s.hasNext else s.hasPrevious
                                val step = amount.x * if (free) 1f else 0.25f
                                scope.launch { dragX.snapTo(dragX.value + step) }
                            } else {
                                totalY += amount.y
                                if (totalY > 0f) dragDown(amount.y)
                            }
                        },
                        onDragEnd = {
                            if (axis == 1) {
                                val threshold = widthPx * 0.28f
                                when {
                                    dragX.value < -threshold && s.hasNext -> {
                                        pendingDir = 1
                                        scope.launch {
                                            dragX.animateTo(-widthPx, tween(170))
                                            player.nextTrack()
                                            delay(700)
                                            // Si por algo no cambió la canción, la carátula vuelve.
                                            if (pendingDir != 0) {
                                                pendingDir = 0
                                                settle()
                                            }
                                        }
                                    }
                                    dragX.value > threshold && s.hasPrevious -> {
                                        pendingDir = -1
                                        scope.launch {
                                            dragX.animateTo(widthPx, tween(170))
                                            player.previousTrack()
                                            delay(700)
                                            if (pendingDir != 0) {
                                                pendingDir = 0
                                                settle()
                                            }
                                        }
                                    }
                                    else -> settle()
                                }
                            } else if (axis == 2) {
                                if (totalY < -size.height * 0.15f) swipeUp()
                                if (totalY > 0f) dragDownEnd()
                            }
                        },
                        onDragCancel = {
                            settle()
                            if (axis == 2 && totalY > 0f) dragDownEnd()
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val progress = (abs(dragX.value) / widthPx).coerceIn(0f, 1f)
                        translationX = dragX.value
                        rotationZ = dragX.value / widthPx * 5f
                        val sc = pausedScale * (1f - progress * 0.12f)
                        scaleX = sc
                        scaleY = sc
                        alpha = 1f - progress * 0.6f
                    }.shadow(24.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black, spotColor = Color.Black),
            ) {
                Thumb(state.artwork, state.album.ifBlank { state.title }, Modifier.fillMaxSize(), corner = 18.dp, icon = BI.Music)
            }

            val icon = flashIcon
            if (icon != null) {
                Box(
                    Modifier
                        .size(84.dp)
                        .graphicsLayer {
                            alpha = flashAlpha.value
                            scaleX = flashScale.value
                            scaleY = flashScale.value
                        }.clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }
        }
    }
}

/** Título que se desliza solo si no cabe, y cambia con una pequeña animación entre canciones. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackTitle(s: PlayerUiState) {
    AnimatedContent(
        targetState = Triple(s.currentId, s.title, listOf(s.artist, s.album).filter { it.isNotBlank() }.joinToString(" · ")),
        transitionSpec = {
            (fadeIn(tween(260, delayMillis = 90)) + slideInVertically(tween(260, delayMillis = 90)) { it / 3 }) togetherWith
                fadeOut(tween(120))
        },
        label = "title",
    ) { (_, title, subtitle) ->
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                style = T.big,
                color = Color.White,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = T.body,
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MainControls(
    s: PlayerUiState,
    player: PlayerConnection,
    accent: Color,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HoldButton(BI.Prev, "Anterior (mantén para retroceder)", onTap = { player.previous() }, onHoldStep = { player.seekBy(-5_000) })
        HoldButton(
            icon = null,
            description = if (s.isPlaying) "Pausar" else "Reproducir",
            size = 76.dp,
            background = accent,
            onTap = { player.toggle() },
            onHoldStep = null,
        ) {
            AnimatedContent(
                targetState = s.isPlaying,
                transitionSpec = {
                    (scaleIn(tween(200), initialScale = 0.5f) + fadeIn(tween(200))) togetherWith
                        (scaleOut(tween(150), targetScale = 0.5f) + fadeOut(tween(150)))
                },
                label = "playPause",
            ) { playing ->
                Icon(
                    if (playing) BI.Pause else BI.Play,
                    null,
                    tint = OnAccent,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        HoldButton(BI.Next, "Siguiente (mantén para adelantar)", onTap = { player.next() }, onHoldStep = { player.seekBy(5_000) })
    }
}

/**
 * Botón redondo que se hunde al tocarlo y rebota al soltarlo.
 * Con [onHoldStep], mantenerlo presionado repite esa acción (adelantar / retroceder) hasta soltar.
 */
@Composable
private fun HoldButton(
    icon: ImageVector?,
    description: String,
    onTap: () -> Unit,
    onHoldStep: (() -> Unit)?,
    size: Dp = 60.dp,
    background: Color = Color.Transparent,
    content: @Composable (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val tap by rememberUpdatedState(onTap)
    val hold by rememberUpdatedState(onHoldStep)
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }.clip(CircleShape)
            .background(background)
            .semantics {
                contentDescription = description
                role = Role.Button
            }.pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        scope.launch { scale.animateTo(0.86f, tween(90)) }
                        var held = false
                        val step = hold
                        val job =
                            if (step != null) {
                                scope.launch {
                                    delay(420)
                                    held = true
                                    while (isActive) {
                                        step()
                                        delay(180)
                                    }
                                }
                            } else {
                                null
                            }
                        val released = tryAwaitRelease()
                        job?.cancel()
                        scope.launch { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 600f)) }
                        if (released && !held) tap()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) {
            content()
        } else if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun SecondaryControls(
    s: PlayerUiState,
    accent: Color,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onEqualizer: () -> Unit,
    onQueue: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleIcon(BI.Shuffle, if (s.shuffle) "Aleatorio activado" else "Aleatorio desactivado", s.shuffle, accent, onShuffle)
        ToggleIcon(
            if (s.repeat == Player.REPEAT_MODE_ONE) BI.RepeatOne else BI.Repeat,
            when (s.repeat) {
                Player.REPEAT_MODE_ONE -> "Repetir una"
                Player.REPEAT_MODE_ALL -> "Repetir todas"
                else -> "Sin repetición"
            },
            s.repeat != Player.REPEAT_MODE_OFF,
            accent,
            onRepeat,
        )
        ToggleIcon(BI.Sliders, "Ecualizador", false, accent, onEqualizer)
        ToggleIcon(BI.Queue, "Cola", false, accent, onQueue)
    }
}

/** Ícono pequeño que se enciende con el color de la canción y muestra un punto debajo cuando está activo. */
@Composable
private fun ToggleIcon(
    icon: ImageVector,
    description: String,
    on: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(if (on) accent else Color.White.copy(alpha = 0.75f), tween(200), label = "toggle")
    val dot by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.5f), label = "dot")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HoldButton(icon = null, description = description, size = 48.dp, onTap = onClick, onHoldStep = null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Box(
            Modifier
                .size(4.dp)
                .graphicsLayer {
                    scaleX = dot
                    scaleY = dot
                }.clip(CircleShape)
                .background(accent),
        )
    }
}

@Composable
private fun RoundIcon(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
) {
    HoldButton(
        icon = null,
        description = description,
        size = 40.dp,
        background = Color.White.copy(alpha = 0.1f),
        onTap = onClick,
        onHoldStep = null,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}
