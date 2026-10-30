package com.bismopower.app.ui.equalizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bismopower.app.audio.BiquadCoefs
import com.bismopower.app.audio.CUSTOM_PRESET
import com.bismopower.app.audio.DEFAULT_Q
import com.bismopower.app.audio.EQ_PRESETS
import com.bismopower.app.audio.EqBand
import com.bismopower.app.audio.EqSettings
import com.bismopower.app.audio.FilterType
import com.bismopower.app.audio.MAX_FREQ
import com.bismopower.app.audio.MAX_GAIN_DB
import com.bismopower.app.audio.MAX_TONE_DB
import com.bismopower.app.audio.MIN_FREQ
import com.bismopower.app.container
import com.bismopower.app.ui.common.BI
import com.bismopower.app.ui.common.Corners
import com.bismopower.app.ui.common.IconChip
import com.bismopower.app.ui.common.PrimaryButton
import com.bismopower.app.ui.common.Segmented
import com.bismopower.app.ui.common.glass
import com.bismopower.app.ui.library.BOTTOM_SPACE
import com.bismopower.app.ui.theme.BismoColors
import com.bismopower.app.ui.theme.LocalBismo
import com.bismopower.app.ui.theme.T
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** El gráfico muestra de −16 a +16 dB, un poco más que el máximo para que los puntos no toquen el borde. */
private const val RANGE_DB = 16f
private const val CURVE_POINTS = 140
private const val Q_MIN = 0.3f
private const val Q_MAX = 6f

/** Números con cifras del mismo ancho: así los valores no "bailan" mientras arrastras. */
private val Num = T.label.copy(fontFeatureSettings = "tnum")
private val NumSmall = T.tiny.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontFeatureSettings = "tnum")
private val NumBig = T.big.copy(fontFeatureSettings = "tnum")

/** Fondo de la "pantalla" del ecualizador: azul casi negro, teñido apenas con el acento del tema. */
private fun inkFor(c: BismoColors): Color = lerp(Color(0xFF0B0F1C), c.accent, 0.07f)

/** El acento sobre la pantalla oscura: si es muy oscuro se aclara para que brille. */
private fun glowFor(c: BismoColors): Color {
    var g = if (c.dark) c.accent else lerp(c.accent, Color.White, 0.25f)
    if (g.luminance() < 0.3f) g = lerp(g, Color.White, 0.45f)
    return g
}

/**
 * Pantalla del ecualizador, pensada como un equipo de sonido:
 *
 * - Pantalla oscura con la curva: arrastra un punto en cualquier dirección para cambiar
 *   frecuencia y ganancia a la vez. Arriba se leen los valores de la banda elegida.
 * - Faders: arrastra hacia arriba o abajo; doble toque vuelve a 0.
 * - Perillas (editor de banda y tono): gira arrastrando hacia arriba o abajo; doble toque
 *   vuelve al valor normal.
 */
@Composable
fun EqualizerScreen() {
    val c = LocalBismo.current
    val repo = LocalContext.current.container.equalizer
    val eq by repo.state.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    var touching by remember { mutableStateOf(false) }

    // Al elegir un ajuste de fábrica las bandas se deslizan a su sitio; mientras arrastras
    // siguen al dedo sin ningún retraso.
    val shownBands =
        eq.bands.mapIndexed { i, b ->
            val gain by animateFloatAsState(b.gainDb, if (touching) snap() else tween(280), label = "gain$i")
            val freq by animateFloatAsState(b.freq, if (touching) snap() else tween(280), label = "freq$i")
            b.copy(gainDb = gain, freq = freq)
        }
    val shown = eq.copy(bands = shownBands)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = BOTTOM_SPACE),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ecualizador", style = T.h1, color = c.text)
                Text(
                    if (eq.enabled) "Ajuste ${eq.preset}" else "Apagado, la música suena sin cambios",
                    style = T.small,
                    color = c.muted,
                )
            }
            Switch(
                checked = eq.enabled,
                onCheckedChange = { repo.setEnabled(it) },
                colors = switchColors(),
            )
        }

        PresetRow(eq.preset) {
            repo.applyPreset(it)
            selected = -1
        }

        val dim = if (eq.enabled) 1f else 0.45f
        Column(
            Modifier.graphicsLayer { alpha = dim },
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // El instrumento: pantalla con la curva y debajo los faders, en un solo panel.
            Column(
                Modifier
                    .fillMaxWidth()
                    .glass(strong = true)
                    .padding(8.dp),
            ) {
                Display(
                    eq = shown,
                    selected = selected,
                    onSelect = { selected = it },
                    onMove = { i, f, g -> repo.setBand(i) { it.copy(freq = f, gainDb = g) } },
                    onTouching = { touching = it },
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(196.dp)
                        .padding(horizontal = 2.dp),
                ) {
                    shownBands.forEachIndexed { i, b ->
                        Fader(
                            gain = b.gainDb,
                            label = fmtFreq(b.freq),
                            selected = selected == i,
                            modifier = Modifier.weight(1f),
                            onSelect = { selected = i },
                            onChange = { g -> repo.setBand(i) { it.copy(gainDb = g) } },
                            onTouching = { touching = it },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            AnimatedVisibility(
                visible = selected in eq.bands.indices,
                enter = expandVertically(tween(220)) + fadeIn(tween(220)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(180)),
            ) {
                val i = selected.coerceIn(0, eq.bands.lastIndex)
                BandEditor(
                    index = i,
                    band = eq.bands[i],
                    onChange = { nb -> repo.setBand(i) { nb } },
                    onTouching = { touching = it },
                    onClose = { selected = -1 },
                )
            }

            ToneCard(eq, repo::setPreamp, repo::setBass, repo::setTreble, repo::setLimiter) { touching = it }

            PrimaryButton(
                "Dejar todo plano",
                onClick = {
                    repo.reset()
                    selected = -1
                },
                modifier = Modifier.fillMaxWidth(),
                icon = BI.Retry,
            )
        }
    }
}

/** Fila de ajustes de fábrica que se desliza de lado. */
@Composable
private fun PresetRow(
    current: String,
    onPick: (String) -> Unit,
) {
    val c = LocalBismo.current
    val names = (if (current == CUSTOM_PRESET) listOf(CUSTOM_PRESET) else emptyList()) + EQ_PRESETS.map { it.first }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(names, key = { it }) { name ->
            val on = name == current
            Text(
                name,
                style = T.label,
                color = if (on) c.onAccent else c.text,
                maxLines = 1,
                modifier =
                    Modifier
                        .then(
                            if (on) {
                                Modifier.clip(Corners.button).background(c.accent)
                            } else {
                                Modifier.glass(Corners.button, strong = true, shadow = false)
                            },
                        ).clickable(enabled = name != CUSTOM_PRESET) { onPick(name) }
                        .padding(horizontal = 15.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * La pantalla oscura: lectura de la banda elegida arriba y la curva de respuesta debajo.
 * Los puntos son las bandas; se pueden arrastrar en cualquier dirección.
 */
@Composable
private fun Display(
    eq: EqSettings,
    selected: Int,
    onSelect: (Int) -> Unit,
    onMove: (Int, Float, Float) -> Unit,
    onTouching: (Boolean) -> Unit,
) {
    val c = LocalBismo.current
    val ink = inkFor(c)
    val glow = glowFor(c)
    val measurer = rememberTextMeasurer()
    val curve = remember(eq) { responseCurve(eq) }
    val bands by rememberUpdatedState(eq.bands)
    val sel by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    val move by rememberUpdatedState(onMove)
    val touch by rememberUpdatedState(onTouching)
    val labelStyle = NumSmall.copy(color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp)
    val band = eq.bands.getOrNull(selected)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ink),
    ) {
        // Lectura: como el visor de un equipo de sonido.
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (band == null) {
                Text("Toca o arrastra un punto", style = T.small, color = Color.White.copy(alpha = 0.55f))
            } else {
                Text("${fmtFreq(band.freq)}Hz", style = NumBig, color = Color.White)
                Spacer(Modifier.width(14.dp))
                Text("${fmtDb(band.gainDb)} dB", style = NumBig, color = glow)
                Spacer(Modifier.weight(1f))
                Text("Q ${fmtQ(band.q)}", style = Num, color = Color.White.copy(alpha = 0.55f))
            }
        }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(188.dp)
                .padding(horizontal = 6.dp)
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        val i = nearestBand(bands, pos, size.width.toFloat(), plotHeight(size.height.toFloat()), maxDist = 40.dp.toPx())
                        select(if (i == sel) -1 else i)
                    }
                }.pointerInput(Unit) {
                    var index = -1
                    detectDragGestures(
                        onDragStart = { pos ->
                            index = nearestBand(bands, pos, size.width.toFloat(), plotHeight(size.height.toFloat()), maxDist = 56.dp.toPx())
                            if (index >= 0) {
                                select(index)
                                touch(true)
                            }
                        },
                        onDrag = { change, _ ->
                            if (index >= 0) {
                                change.consume()
                                val w = size.width.toFloat()
                                val h = plotHeight(size.height.toFloat())
                                val f = xToFreq(change.position.x, w).roundToInt().toFloat()
                                val g = snapDb(yToDb(change.position.y, h))
                                move(index, f, g)
                            }
                        },
                        onDragEnd = {
                            index = -1
                            touch(false)
                        },
                        onDragCancel = {
                            index = -1
                            touch(false)
                        },
                    )
                },
        ) {
            val w = size.width
            val h = plotHeight(size.height)
            val grid = Color.White.copy(alpha = 0.07f)
            val hair = 1.dp.toPx()

            // Rejilla de dB con sus números a la izquierda.
            for (db in listOf(12f, 6f, 0f, -6f, -12f)) {
                val y = dbToY(db, h)
                drawLine(if (db == 0f) Color.White.copy(alpha = 0.2f) else grid, Offset(0f, y), Offset(w, y), strokeWidth = hair)
                val text = if (db > 0) "+${db.toInt()}" else if (db < 0) "−${(-db).toInt()}" else "0"
                val layout = measurer.measure(text, labelStyle)
                drawText(layout, topLeft = Offset(2.dp.toPx(), y - layout.size.height - 1f))
            }
            // Rejilla de frecuencia con sus números abajo.
            for ((f, text) in listOf(100f to "100", 1000f to "1k", 10000f to "10k")) {
                val x = freqToX(f, w)
                drawLine(grid, Offset(x, 0f), Offset(x, h), strokeWidth = hair)
                val layout = measurer.measure(text, labelStyle)
                drawText(layout, topLeft = Offset(x - layout.size.width / 2f, h + 5.dp.toPx()))
            }
            for ((f, text) in listOf(MIN_FREQ to "20", MAX_FREQ to "20k")) {
                val layout = measurer.measure(text, labelStyle)
                val x = freqToX(f, w) - if (f == MAX_FREQ) layout.size.width.toFloat() else 0f
                drawText(layout, topLeft = Offset(x, h + 5.dp.toPx()))
            }

            // Guía vertical punteada desde la banda elegida.
            eq.bands.getOrNull(selected)?.let { b ->
                val x = freqToX(b.freq, w)
                drawLine(
                    glow.copy(alpha = 0.45f),
                    Offset(x, 0f),
                    Offset(x, h),
                    strokeWidth = hair,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                )
            }

            // La curva: un halo ancho y suave debajo de una línea fina, como un trazo que brilla.
            val line = Path()
            for (i in 0 until CURVE_POINTS) {
                val x = w * i / (CURVE_POINTS - 1f)
                val y = dbToY(curve[i].coerceIn(-RANGE_DB, RANGE_DB), h)
                if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }
            val zero = dbToY(0f, h)
            val fill =
                Path().apply {
                    addPath(line)
                    lineTo(w, zero)
                    lineTo(0f, zero)
                    close()
                }
            drawPath(
                fill,
                Brush.verticalGradient(
                    0f to glow.copy(alpha = 0.32f),
                    0.5f to glow.copy(alpha = 0.04f),
                    1f to glow.copy(alpha = 0.32f),
                    startY = 0f,
                    endY = h,
                ),
            )
            drawPath(line, glow.copy(alpha = 0.18f), style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round))
            drawPath(line, glow, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

            // Puntos de las bandas.
            eq.bands.forEachIndexed { i, b ->
                val p = Offset(freqToX(b.freq, w), dbToY(b.gainDb, h))
                if (i == selected) {
                    drawCircle(glow.copy(alpha = 0.2f), radius = 17.dp.toPx(), center = p)
                    drawCircle(glow, radius = 7.dp.toPx(), center = p)
                    drawCircle(ink, radius = 2.5.dp.toPx(), center = p)
                } else {
                    drawCircle(ink, radius = 5.5.dp.toPx(), center = p)
                    drawCircle(glow, radius = 5.5.dp.toPx(), center = p, style = Stroke(1.8.dp.toPx()))
                }
            }
        }
    }
}

/**
 * Fader de consola: ranura con marcas cada 6 dB y una tapa rectangular que se arrastra.
 * El color del acento rellena la ranura desde 0 dB hasta el valor.
 */
@Composable
private fun Fader(
    gain: Float,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onSelect: () -> Unit,
    onChange: (Float) -> Unit,
    onTouching: (Boolean) -> Unit,
) {
    val c = LocalBismo.current
    val current by rememberUpdatedState(gain)
    val select by rememberUpdatedState(onSelect)
    val change by rememberUpdatedState(onChange)
    val touch by rememberUpdatedState(onTouching)

    Column(
        modifier.semantics { contentDescription = "Banda de $label Hz, ${fmtDb(gain)} dB" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(fmtDb(gain), style = NumSmall, color = if (selected) c.accent else c.text, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { select() },
                        onDoubleTap = { change(0f) },
                    )
                }.pointerInput(Unit) {
                    var start = 0f
                    var moved = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            start = current
                            moved = 0f
                            select()
                            touch(true)
                        },
                        onDragEnd = { touch(false) },
                        onDragCancel = { touch(false) },
                        onVerticalDrag = { ch, dy ->
                            ch.consume()
                            moved += dy
                            val dbPerPx = 2f * MAX_GAIN_DB / size.height
                            change(snapDb(start - moved * dbPerPx))
                        },
                    )
                },
        ) {
            val cx = size.width / 2f
            val capW = min(size.width - 6.dp.toPx(), 28.dp.toPx())
            val capH = 14.dp.toPx()
            val top = capH / 2f
            val bottom = size.height - capH / 2f
            val zero = (top + bottom) / 2f
            val y = zero - gain / MAX_GAIN_DB * (bottom - top) / 2f
            val slot = 3.dp.toPx()
            val tick = 1.dp.toPx()

            // Marcas cada 6 dB a los dos lados de la ranura (la de 0 dB más larga).
            for (db in listOf(15f, 12f, 6f, 0f, -6f, -12f, -15f)) {
                val ty = zero - db / MAX_GAIN_DB * (bottom - top) / 2f
                val len = if (db == 0f) 9.dp.toPx() else 5.dp.toPx()
                val col = c.muted.copy(alpha = if (db == 0f) 0.6f else 0.3f)
                drawLine(col, Offset(cx - slot - 2.dp.toPx() - len, ty), Offset(cx - slot - 2.dp.toPx(), ty), strokeWidth = tick)
                drawLine(col, Offset(cx + slot + 2.dp.toPx(), ty), Offset(cx + slot + 2.dp.toPx() + len, ty), strokeWidth = tick)
            }
            drawLine(c.text.copy(alpha = 0.14f), Offset(cx, top), Offset(cx, bottom), strokeWidth = slot, cap = StrokeCap.Round)
            drawLine(c.accent, Offset(cx, zero), Offset(cx, y), strokeWidth = slot, cap = StrokeCap.Round)

            // Tapa del fader.
            val capColor = if (selected) c.accent else c.text
            val lineColor = if (selected) c.onAccent else c.bg
            if (selected) {
                drawRoundRect(
                    c.accent.copy(alpha = 0.22f),
                    topLeft = Offset(cx - capW / 2f - 4.dp.toPx(), y - capH / 2f - 4.dp.toPx()),
                    size = Size(capW + 8.dp.toPx(), capH + 8.dp.toPx()),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                )
            }
            drawRoundRect(
                capColor,
                topLeft = Offset(cx - capW / 2f, y - capH / 2f),
                size = Size(capW, capH),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            drawLine(
                lineColor,
                Offset(cx - capW / 2f + 5.dp.toPx(), y),
                Offset(cx + capW / 2f - 5.dp.toPx(), y),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = NumSmall, color = if (selected) c.accent else c.muted, maxLines = 1)
    }
}

/**
 * Perilla giratoria: arrastra hacia arriba para subir y hacia abajo para bajar; doble toque
 * vuelve al valor normal. [fraction] va de 0 a 1 y [origin] es desde dónde se pinta el arco
 * (0,5 para valores que pueden ser positivos o negativos, 0 para los que empiezan en el mínimo).
 */
@Composable
private fun Knob(
    label: String,
    value: String,
    unit: String,
    fraction: Float,
    origin: Float,
    onSet: (Float) -> Unit,
    onReset: () -> Unit,
    onTouching: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalBismo.current
    val current by rememberUpdatedState(fraction)
    val set by rememberUpdatedState(onSet)
    val reset by rememberUpdatedState(onReset)
    val touch by rememberUpdatedState(onTouching)

    Column(
        modifier.semantics { contentDescription = "$label: $value $unit" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(76.dp)
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { reset() }) }
                .pointerInput(Unit) {
                    var start = 0f
                    var moved = 0f
                    detectDragGestures(
                        onDragStart = {
                            start = current
                            moved = 0f
                            touch(true)
                        },
                        onDragEnd = { touch(false) },
                        onDragCancel = { touch(false) },
                        onDrag = { ch, amount ->
                            ch.consume()
                            // Arriba o a la derecha sube; 200 dp de recorrido = de punta a punta.
                            moved += amount.x - amount.y
                            set((start + moved / 200.dp.toPx()).coerceIn(0f, 1f))
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 5.dp.toPx()
                val r = size.minDimension / 2f - stroke / 2f
                val arcSize = Size(r * 2f, r * 2f)
                val arcTopLeft = Offset(center.x - r, center.y - r)
                val startAngle = 135f
                drawArc(c.track, startAngle, 270f, false, arcTopLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                val a0 = startAngle + origin * 270f
                val a1 = startAngle + fraction.coerceIn(0f, 1f) * 270f
                if (abs(a1 - a0) > 0.5f) {
                    drawArc(c.accent, min(a0, a1), abs(a1 - a0), false, arcTopLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                // Disco de la perilla con su marca.
                val inner = r - 9.dp.toPx()
                drawCircle(if (c.dark) Color.White.copy(alpha = 0.08f) else Color.White, radius = inner, center = center)
                drawCircle(c.text.copy(alpha = 0.08f), radius = inner, center = center, style = Stroke(1.dp.toPx()))
                val rad = Math.toRadians(a1.toDouble())
                val cos = kotlin.math.cos(rad).toFloat()
                val sin = kotlin.math.sin(rad).toFloat()
                drawLine(
                    c.accent,
                    Offset(center.x + cos * inner * 0.62f, center.y + sin * inner * 0.62f),
                    Offset(center.x + cos * inner * 0.88f, center.y + sin * inner * 0.88f),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, style = Num, color = c.text, maxLines = 1)
                Text(unit, style = T.tiny.copy(fontSize = 10.sp), color = c.muted, maxLines = 1)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = T.small, color = c.muted, maxLines = 1)
    }
}

/** Editor de la banda elegida: forma y tres perillas (frecuencia, ganancia, ancho). */
@Composable
private fun BandEditor(
    index: Int,
    band: EqBand,
    onChange: (EqBand) -> Unit,
    onTouching: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalBismo.current
    Column(
        Modifier
            .fillMaxWidth()
            .glass(strong = true)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Banda ${index + 1}", style = T.h2, color = c.text, modifier = Modifier.weight(1f))
            Text(
                "Restablecer",
                style = T.label,
                color = c.accent,
                modifier =
                    Modifier
                        .clip(Corners.button)
                        .clickable {
                            val f = EqSettings.FREQS.getOrElse(index) { band.freq }
                            onChange(EqBand(f, 0f, DEFAULT_Q, FilterType.PEAK))
                        }.padding(horizontal = 10.dp, vertical = 8.dp),
            )
            Spacer(Modifier.width(4.dp))
            IconChip(BI.Close, onClose, description = "Cerrar el editor de banda")
        }

        val types = listOf(FilterType.PEAK, FilterType.LOW_SHELF, FilterType.HIGH_SHELF)
        Segmented(
            listOf("Campana", "Graves", "Agudos"),
            types.indexOf(band.type).coerceAtLeast(0),
            { onChange(band.copy(type = types[it])) },
        )
        Text(
            when (band.type) {
                FilterType.PEAK -> "Sube o baja solo la zona alrededor de la frecuencia."
                FilterType.LOW_SHELF -> "Sube o baja todo lo que está por debajo de la frecuencia."
                FilterType.HIGH_SHELF -> "Sube o baja todo lo que está por encima de la frecuencia."
            },
            style = T.small,
            color = c.muted,
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Knob(
                label = "Frecuencia",
                value = fmtFreq(band.freq),
                unit = "Hz",
                fraction = freqToFraction(band.freq),
                origin = 0f,
                onSet = { onChange(band.copy(freq = fractionToFreq(it).roundToInt().toFloat())) },
                onReset = { onChange(band.copy(freq = EqSettings.FREQS.getOrElse(index) { band.freq })) },
                onTouching = onTouching,
            )
            Knob(
                label = "Ganancia",
                value = fmtDb(band.gainDb),
                unit = "dB",
                fraction = (band.gainDb + MAX_GAIN_DB) / (2f * MAX_GAIN_DB),
                origin = 0.5f,
                onSet = { onChange(band.copy(gainDb = snapDb(it * 2f * MAX_GAIN_DB - MAX_GAIN_DB))) },
                onReset = { onChange(band.copy(gainDb = 0f)) },
                onTouching = onTouching,
            )
            Knob(
                label = "Ancho",
                value = fmtQ(band.q),
                unit = "Q",
                fraction = (band.q - Q_MIN) / (Q_MAX - Q_MIN),
                origin = 0f,
                onSet = { onChange(band.copy(q = ((Q_MIN + it * (Q_MAX - Q_MIN)) * 20f).roundToInt() / 20f)) },
                onReset = { onChange(band.copy(q = DEFAULT_Q)) },
                onTouching = onTouching,
            )
        }
        Text("Un ancho más bajo afecta una zona más amplia. Doble toque en una perilla la devuelve a su valor normal.", style = T.tiny, color = c.muted)
    }
}

/** Preamplificador, graves y agudos como perillas, y el limitador. */
@Composable
private fun ToneCard(
    eq: EqSettings,
    onPreamp: (Float) -> Unit,
    onBass: (Float) -> Unit,
    onTreble: (Float) -> Unit,
    onLimiter: (Boolean) -> Unit,
    onTouching: (Boolean) -> Unit,
) {
    val c = LocalBismo.current
    Column(
        Modifier
            .fillMaxWidth()
            .glass(strong = true)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Tono", style = T.h2, color = c.text)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Knob(
                label = "Preamp",
                value = fmtDb(eq.preampDb),
                unit = "dB",
                fraction = (eq.preampDb + MAX_GAIN_DB) / (2f * MAX_GAIN_DB),
                origin = 0.5f,
                onSet = { onPreamp(snapDb(it * 2f * MAX_GAIN_DB - MAX_GAIN_DB)) },
                onReset = { onPreamp(0f) },
                onTouching = onTouching,
            )
            Knob(
                label = "Graves",
                value = fmtDb(eq.bassDb),
                unit = "dB",
                fraction = (eq.bassDb + MAX_TONE_DB) / (2f * MAX_TONE_DB),
                origin = 0.5f,
                onSet = { onBass(snapTone(it * 2f * MAX_TONE_DB - MAX_TONE_DB)) },
                onReset = { onBass(0f) },
                onTouching = onTouching,
            )
            Knob(
                label = "Agudos",
                value = fmtDb(eq.trebleDb),
                unit = "dB",
                fraction = (eq.trebleDb + MAX_TONE_DB) / (2f * MAX_TONE_DB),
                origin = 0.5f,
                onSet = { onTreble(snapTone(it * 2f * MAX_TONE_DB - MAX_TONE_DB)) },
                onReset = { onTreble(0f) },
                onTouching = onTouching,
            )
        }
        Text("Si subes mucho las bandas, baja el preamp para dejar espacio.", style = T.tiny, color = c.muted)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(Corners.button)
                .clickable { onLimiter(!eq.limiter) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(BI.Shield, null, tint = c.muted, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Limitador", style = T.body, color = c.text)
                Text("Redondea los picos para que el sonido no se distorsione", style = T.tiny, color = c.muted)
            }
            Switch(checked = eq.limiter, onCheckedChange = onLimiter, colors = switchColors())
        }
    }
}

@Composable
private fun switchColors() =
    LocalBismo.current.let { c ->
        SwitchDefaults.colors(
            checkedTrackColor = c.accent,
            checkedThumbColor = c.onAccent,
            uncheckedTrackColor = c.track,
            uncheckedBorderColor = Color.Transparent,
        )
    }

/** Graves y agudos van de −10 a +10 dB. */
private fun snapTone(db: Float): Float = (db.coerceIn(-MAX_TONE_DB, MAX_TONE_DB) * 2f).roundToInt() / 2f

private fun fmtQ(q: Float): String = String.format(Locale.US, "%.2f", q)

// ---------- Cuentas del gráfico ----------

private val LOG_MIN = ln(MIN_FREQ)
private val LOG_SPAN = ln(MAX_FREQ) - ln(MIN_FREQ)

/** El oído escucha las frecuencias en escala logarítmica: 20→200 Hz ocupa lo mismo que 2k→20k. */
private fun freqToFraction(f: Float): Float = ((ln(f.coerceIn(MIN_FREQ, MAX_FREQ)) - LOG_MIN) / LOG_SPAN)

private fun fractionToFreq(t: Float): Float = MIN_FREQ * (MAX_FREQ / MIN_FREQ).pow(t.coerceIn(0f, 1f))

private fun freqToX(
    f: Float,
    w: Float,
): Float = freqToFraction(f) * w

private fun xToFreq(
    x: Float,
    w: Float,
): Float = fractionToFreq(x / w)

/** Alto del área del gráfico: abajo quedan 20 dp para los números de frecuencia. */
private fun androidx.compose.ui.unit.Density.plotHeight(total: Float): Float = total - 20.dp.toPx()

private fun dbToY(
    db: Float,
    h: Float,
): Float = h / 2f - db / RANGE_DB * (h / 2f)

private fun yToDb(
    y: Float,
    h: Float,
): Float = ((h / 2f - y) / (h / 2f) * RANGE_DB).coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB)

/** Redondea a pasos de medio dB, como los ecualizadores de los equipos de sonido. */
private fun snapDb(db: Float): Float = (db.coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB) * 2f).roundToInt() / 2f

private fun nearestBand(
    bands: List<EqBand>,
    pos: Offset,
    w: Float,
    h: Float,
    maxDist: Float,
): Int {
    var best = -1
    var bestDist = maxDist
    bands.forEachIndexed { i, b ->
        val d = hypot(freqToX(b.freq, w) - pos.x, dbToY(b.gainDb, h) - pos.y)
        if (d < bestDist) {
            bestDist = d
            best = i
        }
    }
    return best
}

/** Respuesta total (en dB) de las bandas y las perillas de tono, en frecuencias repartidas en escala logarítmica. */
private fun responseCurve(eq: EqSettings): FloatArray {
    val fs = 48000.0
    val filters =
        eq.filters()
            .filter { abs(it.gainDb) >= 0.05f }
            .map { BiquadCoefs.design(it.type, it.freq.toDouble(), it.gainDb.toDouble(), it.q.toDouble(), fs) }
    return FloatArray(CURVE_POINTS) { i ->
        val f = fractionToFreq(i / (CURVE_POINTS - 1f)).toDouble()
        filters.sumOf { it.magnitudeDb(f, fs) }.toFloat()
    }
}

private fun fmtFreq(f: Float): String =
    when {
        f >= 10000f -> "${(f / 1000f).roundToInt()}k"
        f >= 1000f -> {
            val k = (f / 100f).roundToInt() / 10f
            if (k == k.toInt().toFloat()) "${k.toInt()}k" else String.format(Locale.US, "%.1fk", k)
        }
        else -> "${f.roundToInt()}"
    }

private fun fmtDb(db: Float): String {
    val v = (db * 2f).roundToInt() / 2f
    if (v == 0f) return "0"
    val num = if (v == v.toInt().toFloat()) "${abs(v.toInt())}" else String.format(Locale.US, "%.1f", abs(v))
    return (if (v > 0) "+" else "−") + num
}
