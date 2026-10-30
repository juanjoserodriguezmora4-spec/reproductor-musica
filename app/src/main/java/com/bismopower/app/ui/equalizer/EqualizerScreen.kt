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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
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
import com.bismopower.app.ui.theme.LocalBismo
import com.bismopower.app.ui.theme.T
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** El gráfico muestra de −16 a +16 dB, un poco más que el máximo para que los puntos no toquen el borde. */
private const val RANGE_DB = 16f
private const val CURVE_POINTS = 140

/**
 * Pantalla del ecualizador paramétrico.
 *
 * - Curva: arrastra un punto en cualquier dirección para cambiar frecuencia y ganancia a la vez.
 * - Barras: arrastra hacia arriba o abajo para cambiar la ganancia; doble toque la vuelve a 0.
 * - Al tocar una banda se abre su editor: forma, frecuencia, ganancia y ancho (Q).
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
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = BOTTOM_SPACE),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ecualizador", style = T.h1, color = c.text)
                Text(
                    if (eq.enabled) "Ajuste: ${eq.preset}" else "Apagado: la música suena sin cambios",
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .glass()
                    .padding(horizontal = 10.dp, vertical = 12.dp),
            ) {
                EqCurve(
                    eq = shown,
                    selected = selected,
                    onSelect = { selected = it },
                    onMove = { i, f, g -> repo.setBand(i) { it.copy(freq = f, gainDb = g) } },
                    onTouching = { touching = it },
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().height(184.dp)) {
                    shownBands.forEachIndexed { i, b ->
                        GainSlider(
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
                color = if (on) Color.White else c.text,
                maxLines = 1,
                modifier =
                    Modifier
                        .then(
                            if (on) {
                                Modifier.clip(Corners.button).background(c.accent)
                            } else {
                                Modifier.glass(Corners.button, shadow = false)
                            },
                        ).clickable(enabled = name != CUSTOM_PRESET) { onPick(name) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }
}

/**
 * Curva de respuesta: cuánto sube o baja cada frecuencia con los ajustes actuales.
 * Los puntos son las bandas; se pueden arrastrar en cualquier dirección.
 */
@Composable
private fun EqCurve(
    eq: EqSettings,
    selected: Int,
    onSelect: (Int) -> Unit,
    onMove: (Int, Float, Float) -> Unit,
    onTouching: (Boolean) -> Unit,
) {
    val c = LocalBismo.current
    val measurer = rememberTextMeasurer()
    val curve = remember(eq) { responseCurve(eq) }
    val bands by rememberUpdatedState(eq.bands)
    val sel by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    val move by rememberUpdatedState(onMove)
    val touch by rememberUpdatedState(onTouching)
    val labelStyle = T.tiny.copy(color = c.muted)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
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
        val grid = c.track
        val pad = 3.dp.toPx()

        // Líneas de dB y sus números.
        for (db in listOf(12f, 6f, 0f, -6f, -12f)) {
            val y = dbToY(db, h)
            drawLine(if (db == 0f) c.muted.copy(alpha = 0.35f) else grid, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
            val text = if (db > 0) "+${db.toInt()}" else if (db < 0) "−${(-db).toInt()}" else "0 dB"
            val layout = measurer.measure(text, labelStyle)
            drawText(layout, topLeft = Offset(pad, y - layout.size.height - 1f))
        }
        // Líneas de frecuencia y sus números abajo.
        for ((f, text) in listOf(100f to "100", 1000f to "1k", 10000f to "10k")) {
            val x = freqToX(f, w)
            drawLine(grid, Offset(x, 0f), Offset(x, h), strokeWidth = 1.dp.toPx())
            val layout = measurer.measure(text, labelStyle)
            drawText(layout, topLeft = Offset(x - layout.size.width / 2f, h + 4.dp.toPx()))
        }
        for ((f, text) in listOf(MIN_FREQ to "20", MAX_FREQ to "20k")) {
            val layout = measurer.measure(text, labelStyle)
            val x = (freqToX(f, w) - if (f == MAX_FREQ) layout.size.width.toFloat() else 0f)
            drawText(layout, topLeft = Offset(x, h + 4.dp.toPx()))
        }

        // La curva y el relleno degradado hasta la línea de 0 dB.
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
                0f to c.accent.copy(alpha = 0.28f),
                0.5f to c.accent.copy(alpha = 0.08f),
                1f to c.accent.copy(alpha = 0.28f),
                startY = 0f,
                endY = h,
            ),
        )
        drawPath(line, c.accent, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

        // Puntos de las bandas.
        eq.bands.forEachIndexed { i, b ->
            val p = Offset(freqToX(b.freq, w), dbToY(b.gainDb, h))
            if (i == selected) {
                drawCircle(c.accent.copy(alpha = 0.18f), radius = 18.dp.toPx(), center = p)
                drawCircle(c.accent, radius = 8.dp.toPx(), center = p)
                drawCircle(Color.White, radius = 3.dp.toPx(), center = p)
            } else {
                drawCircle(c.bg, radius = 6.dp.toPx(), center = p)
                drawCircle(c.accent, radius = 6.dp.toPx(), center = p, style = Stroke(2.dp.toPx()))
            }
        }
    }
}

/** Barra vertical de ganancia de una banda: crece desde el centro (0 dB) hacia arriba o abajo. */
@Composable
private fun GainSlider(
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
    val tint = if (selected) c.accent else c.muted

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(fmtDb(gain), style = T.tiny, color = tint, maxLines = 1)
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
            val thumb = (if (selected) 9.dp else 7.dp).toPx()
            val top = thumb
            val bottom = size.height - thumb
            val zero = (top + bottom) / 2f
            val y = zero - gain / MAX_GAIN_DB * (bottom - top) / 2f
            val track = 4.dp.toPx()
            drawLine(c.track, Offset(cx, top), Offset(cx, bottom), strokeWidth = track, cap = StrokeCap.Round)
            drawLine(c.muted.copy(alpha = 0.4f), Offset(cx - 5.dp.toPx(), zero), Offset(cx + 5.dp.toPx(), zero), strokeWidth = 1.dp.toPx())
            drawLine(c.accent, Offset(cx, zero), Offset(cx, y), strokeWidth = track, cap = StrokeCap.Round)
            drawCircle(c.accent, radius = thumb, center = Offset(cx, y))
            drawCircle(Color.White, radius = thumb * 0.38f, center = Offset(cx, y))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = T.tiny, color = tint, maxLines = 1)
    }
}

/** Editor completo de una banda: forma, frecuencia, ganancia y ancho. */
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
            .glass()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Banda ${index + 1}", style = T.h2, color = c.text)
                Text("${fmtFreq(band.freq)}Hz · ${fmtDb(band.gainDb)} dB", style = T.small, color = c.muted)
            }
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
            style = T.tiny,
            color = c.muted,
        )

        LabeledSlider(
            title = "Frecuencia",
            value = "${fmtFreq(band.freq)}Hz",
            v = freqToFraction(band.freq),
            range = 0f..1f,
            onChange = { onChange(band.copy(freq = fractionToFreq(it).roundToInt().toFloat())) },
            onTouching = onTouching,
        )
        LabeledSlider(
            title = "Ganancia",
            value = "${fmtDb(band.gainDb)} dB",
            v = band.gainDb,
            range = -MAX_GAIN_DB..MAX_GAIN_DB,
            onChange = { onChange(band.copy(gainDb = snapDb(it))) },
            onTouching = onTouching,
        )
        LabeledSlider(
            title = "Ancho (Q)",
            value = String.format(Locale.US, "%.2f", band.q),
            v = band.q,
            range = 0.3f..6f,
            onChange = { onChange(band.copy(q = (it * 20).roundToInt() / 20f)) },
            onTouching = onTouching,
            hint = "Más bajo = afecta una zona más ancha",
        )

        Text(
            "Restablecer esta banda",
            style = T.label,
            color = c.accent,
            modifier =
                Modifier
                    .clip(Corners.button)
                    .clickable {
                        val f = EqSettings.FREQS.getOrElse(index) { band.freq }
                        onChange(EqBand(f, 0f, DEFAULT_Q, FilterType.PEAK))
                    }.padding(vertical = 6.dp, horizontal = 4.dp),
        )
    }
}

/** Preamplificador, perillas de graves y agudos, y limitador. */
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
            .glass()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("TONO", style = T.overline, color = c.muted)
        LabeledSlider(
            title = "Preamplificador",
            value = "${fmtDb(eq.preampDb)} dB",
            v = eq.preampDb,
            range = -MAX_GAIN_DB..MAX_GAIN_DB,
            onChange = { onPreamp(snapDb(it)) },
            onTouching = onTouching,
            hint = "Baja el volumen antes del ecualizador si subiste mucho las bandas",
        )
        LabeledSlider(
            title = "Graves",
            value = "${fmtDb(eq.bassDb)} dB",
            v = eq.bassDb,
            range = -MAX_TONE_DB..MAX_TONE_DB,
            onChange = { onBass(snapDb(it)) },
            onTouching = onTouching,
        )
        LabeledSlider(
            title = "Agudos",
            value = "${fmtDb(eq.trebleDb)} dB",
            v = eq.trebleDb,
            range = -MAX_TONE_DB..MAX_TONE_DB,
            onChange = { onTreble(snapDb(it)) },
            onTouching = onTouching,
        )
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
private fun LabeledSlider(
    title: String,
    value: String,
    v: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onTouching: (Boolean) -> Unit,
    hint: String? = null,
) {
    val c = LocalBismo.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = T.body, color = c.text, modifier = Modifier.weight(1f))
            Text(value, style = T.label, color = c.accent)
        }
        if (hint != null) Text(hint, style = T.tiny, color = c.muted)
        Slider(
            value = v.coerceIn(range.start, range.endInclusive),
            onValueChange = {
                onTouching(true)
                onChange(it)
            },
            onValueChangeFinished = { onTouching(false) },
            valueRange = range,
            colors =
                SliderDefaults.colors(
                    thumbColor = c.accent,
                    activeTrackColor = c.accent,
                    inactiveTrackColor = c.track,
                ),
        )
    }
}

@Composable
private fun switchColors() =
    LocalBismo.current.let { c ->
        SwitchDefaults.colors(
            checkedTrackColor = c.accent,
            checkedThumbColor = Color.White,
            uncheckedTrackColor = c.track,
            uncheckedBorderColor = Color.Transparent,
        )
    }

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

/** Alto del área del gráfico: abajo quedan 18 dp para los números de frecuencia. */
private fun androidx.compose.ui.unit.Density.plotHeight(total: Float): Float = total - 18.dp.toPx()

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
