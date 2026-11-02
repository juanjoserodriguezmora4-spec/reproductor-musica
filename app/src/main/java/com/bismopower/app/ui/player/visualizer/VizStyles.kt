package com.bismopower.app.ui.player.visualizer

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Cómo se muestra la carátula detrás del visualizador. */
enum class CoverMode { NONE, CIRCLE, SQUARE }

/**
 * Estilos del visualizador. [coverSize] es el tamaño de la carátula respecto al lado menor del
 * área (en los círculos, el diámetro) y [scrim] cuánto se oscurece para que el dibujo resalte.
 */
enum class VizStyle(
    val id: String,
    val label: String,
    val cover: CoverMode,
    val coverSize: Float = 0f,
    val scrim: Float = 0f,
) {
    RING("RING", "Anillo", CoverMode.CIRCLE, coverSize = 0.58f),
    HALO("HALO", "Halo", CoverMode.CIRCLE, coverSize = 0.52f),
    LINE("LINE", "Línea", CoverMode.SQUARE, coverSize = 1f, scrim = 0.35f),
    BARS("BARS", "Barras", CoverMode.SQUARE, coverSize = 1f, scrim = 0.45f),
    LED("LED", "LED", CoverMode.NONE),
    PULSE("PULSE", "Pulso", CoverMode.NONE),
    PEAKS("PEAKS", "Cordillera", CoverMode.NONE),
    ;

    companion object {
        fun of(id: String): VizStyle = entries.firstOrNull { it.id == id } ?: RING
    }
}

/** Paletas de color. "Carátula" toma los colores de la canción que suena. */
enum class VizPalette(
    val id: String,
    val label: String,
    val fixed: List<Color>?,
) {
    ARTWORK("ARTWORK", "Carátula", null),
    NEON("NEON", "Neón", listOf(Color(0xFF9B2CF5), Color(0xFF4A3AFF), Color(0xFF00C8FF), Color(0xFF3CF5D6))),
    LAVENDER("LAVENDER", "Lavanda", listOf(Color(0xFF5B1F8C), Color(0xFF9F4FD8), Color(0xFFD9A6F5), Color(0xFFFBEFFF))),
    FIRE("FIRE", "Fuego", listOf(Color(0xFFB3122E), Color(0xFFFF3D00), Color(0xFFFF9100), Color(0xFFFFE066))),
    AURORA("AURORA", "Aurora", listOf(Color(0xFF00E5A0), Color(0xFF00C2E0), Color(0xFF5B6CFF), Color(0xFFC04BFF))),
    ICE("ICE", "Hielo", listOf(Color(0xFF0A4BFF), Color(0xFF2D9CFF), Color(0xFF8FE3FF), Color(0xFFEFFBFF))),
    SUNSET("SUNSET", "Atardecer", listOf(Color(0xFFFF2E7E), Color(0xFFFF6A5C), Color(0xFFFFA94D), Color(0xFFFFD86B))),
    MONO("MONO", "Blanco", listOf(Color(0xFF8A93A6), Color(0xFFC9CFDB), Color(0xFFFFFFFF), Color(0xFFFFFFFF))),
    ;

    /** Los colores de la paleta; con "Carátula" se arman a partir del color de la canción. */
    fun colors(accent: Color): List<Color> =
        fixed ?: listOf(
            lerp(accent, Color.Black, 0.25f),
            accent,
            lerp(accent, Color.White, 0.45f),
            lerp(accent, Color.White, 0.8f),
        )

    companion object {
        fun of(id: String): VizPalette = entries.firstOrNull { it.id == id } ?: ARTWORK
    }
}

/** Color de la paleta en la posición [t] (0 a 1), pasando suave entre sus colores. */
fun List<Color>.at(t: Float): Color {
    val x = t.coerceIn(0f, 1f) * (size - 1)
    val i = x.toInt().coerceAtMost(size - 2)
    return lerp(this[i], this[i + 1], x - i)
}

/** Valor de las bandas en una posición continua [t] (0 = graves, 1 = agudos), interpolado. */
private fun FloatArray.sample(t: Float): Float {
    val x = t.coerceIn(0f, 1f) * (size - 1)
    val i = x.toInt().coerceAtMost(size - 2)
    val f = x - i
    return this[i] * (1 - f) + this[i + 1] * f
}

/**
 * Dibuja un estilo. [time] en segundos (para movimientos suaves), [dp] = píxeles por dp.
 * Las carátulas las pone la pantalla por debajo; aquí solo va el dibujo.
 */
fun DrawScope.drawViz(
    style: VizStyle,
    f: VizFrame,
    colors: List<Color>,
    time: Float,
) {
    when (style) {
        VizStyle.RING -> ring(f, colors)
        VizStyle.HALO -> halo(f, colors, time)
        VizStyle.LINE -> line(f, colors)
        VizStyle.BARS -> bars(f, colors)
        VizStyle.LED -> led(f, colors)
        VizStyle.PULSE -> pulse(f, colors, time)
        VizStyle.PEAKS -> peaks(f, colors)
    }
}

private fun DrawScope.sweep(colors: List<Color>): Brush = Brush.sweepGradient(colors + colors.reversed().drop(1), center)

// ---------------------------------------------------------------- Anillo

/** Carátula redonda rodeada de barras que salen hacia afuera (simétrico: graves arriba y abajo). */
private fun DrawScope.ring(
    f: VizFrame,
    colors: List<Color>,
) {
    val m = min(size.width, size.height)
    val r0 = m * VizStyle.RING.coverSize / 2f + 7.dp.toPx()
    val maxLen = m / 2f - r0 - 2.dp.toPx()
    val n = 120
    val brush = sweep(colors)
    val stroke = (2 * PI * r0 / n * 0.55).toFloat()
    // Brillo alrededor de la carátula que crece con los graves.
    drawCircle(colors[1].copy(alpha = 0.18f + f.energy * 0.35f), radius = r0 - 3.dp.toPx(), style = Stroke((3 + f.energy * 6).dp.toPx()))
    for (i in 0 until n) {
        val half = if (i < n / 2) i else n - 1 - i
        val v = f.bands.sample(half / (n / 2f - 1f) * 0.85f)
        val a = (-PI / 2 + 2 * PI * i / n).toFloat()
        val len = 3.dp.toPx() + v * maxLen
        val c = cos(a)
        val s = sin(a)
        drawLine(
            brush,
            Offset(center.x + c * r0, center.y + s * r0),
            Offset(center.x + c * (r0 + len), center.y + s * (r0 + len)),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

// ---------------------------------------------------------------- Halo

/** Aro de luz con anillos de puntos que la música empuja hacia afuera, como un eclipse. */
private fun DrawScope.halo(
    f: VizFrame,
    colors: List<Color>,
    time: Float,
) {
    val m = min(size.width, size.height)
    val r0 = m * VizStyle.HALO.coverSize / 2f + 4.dp.toPx()
    val room = m / 2f - r0
    val brush = sweep(colors)
    // Aro brillante: varias pasadas anchas y transparentes debajo de una fina y clara.
    for (k in 4 downTo 1) {
        drawCircle(brush, radius = r0, style = Stroke((k * 5).dp.toPx()), alpha = 0.07f + f.energy * 0.05f)
    }
    drawCircle(brush, radius = r0, style = Stroke(2.5.dp.toPx()), alpha = 0.95f)
    drawCircle(Color.White, radius = r0, style = Stroke(0.8.dp.toPx()), alpha = 0.6f)

    val rings = 11
    val points = 150
    val pts = ArrayList<Offset>(points)
    for (k in 1..rings) {
        pts.clear()
        val depth = k / rings.toFloat()
        for (j in 0 until points) {
            val a = (2 * PI * j / points).toFloat()
            // Simétrico de izquierda a derecha, con una ondulación lenta para que se vea vivo.
            val t = abs(((a / (2 * PI).toFloat()) * 2f) - 1f)
            val v = f.bands.sample(0.05f + t * 0.75f)
            val wobble = sin(a * 3 + time * 0.8f + k * 0.45f) * 0.5f + 0.5f
            val r = r0 + depth * room * 0.28f + v * depth * room * 0.72f * (0.75f + 0.25f * wobble)
            pts += Offset(center.x + cos(a - PI.toFloat() / 2) * r, center.y + sin(a - PI.toFloat() / 2) * r)
        }
        drawPoints(
            pts,
            PointMode.Points,
            brush,
            strokeWidth = (2.1f - depth * 1.1f).dp.toPx(),
            cap = StrokeCap.Round,
            alpha = (1f - depth * 0.8f),
        )
    }
}

// ---------------------------------------------------------------- Línea

/** Una línea de luz que cruza la carátula y se mueve con la forma de la onda. */
private fun DrawScope.line(
    f: VizFrame,
    colors: List<Color>,
) {
    val side = min(size.width, size.height)
    val left = center.x - side / 2f + 10.dp.toPx()
    val width = side - 20.dp.toPx()
    val n = f.wave.size
    val path = Path()
    for (i in 0 until n) {
        val t = i / (n - 1f)
        val taper = sin(PI * t).toFloat().pow(0.6f) // quieta en las puntas, viva en el centro
        val x = left + t * width
        val y = center.y + f.wave[i] * side * 0.24f * taper
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    val brush = Brush.horizontalGradient(colors, startX = left, endX = left + width)
    drawPath(path, brush, alpha = 0.12f, style = Stroke(14.dp.toPx(), cap = StrokeCap.Round))
    drawPath(path, brush, alpha = 0.3f, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
    drawPath(path, brush, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
    drawPath(path, Color.White, alpha = 0.55f, style = Stroke(0.8.dp.toPx(), cap = StrokeCap.Round))
}

// ---------------------------------------------------------------- Barras

/** Barras verticales sobre la carátula, con la marquita de pico que cae despacio. */
private fun DrawScope.bars(
    f: VizFrame,
    colors: List<Color>,
) {
    val side = min(size.width, size.height)
    val left = center.x - side / 2f + 14.dp.toPx()
    val bottom = center.y + side / 2f - 14.dp.toPx()
    val width = side - 28.dp.toPx()
    val maxH = side * 0.78f
    val n = 32
    val slot = width / n
    val barW = slot * 0.62f
    val brush = Brush.verticalGradient(colors, startY = bottom, endY = bottom - maxH)
    for (i in 0 until n) {
        val t = i / (n - 1f)
        val v = f.bands.sample(t * 0.9f)
        val h = 3.dp.toPx() + v * maxH
        val x = left + i * slot + (slot - barW) / 2f
        drawRoundRect(brush, Offset(x, bottom - h), Size(barW, h), CornerRadius(barW / 2f))
        val p = f.peaks.sample(t * 0.9f)
        val py = bottom - 3.dp.toPx() - p * maxH - 5.dp.toPx()
        drawRoundRect(colors.last(), Offset(x, py), Size(barW, 2.5.dp.toPx()), CornerRadius(1.dp.toPx()))
    }
}

// ---------------------------------------------------------------- LED

/** Cuadritos que se encienden como el visor de un equipo de sonido. */
private fun DrawScope.led(
    f: VizFrame,
    colors: List<Color>,
) {
    val cols = 20
    val rows = 18
    val gap = 3.dp.toPx()
    val cell = min((size.width - gap * (cols - 1)) / cols, (size.height - gap * (rows - 1)) / rows)
    val totalW = cell * cols + gap * (cols - 1)
    val totalH = cell * rows + gap * (rows - 1)
    val left = center.x - totalW / 2f
    val top = center.y - totalH / 2f
    val radius = CornerRadius(cell * 0.22f)
    for (c in 0 until cols) {
        val t = c / (cols - 1f)
        val lit = (f.bands.sample(t * 0.9f) * rows).toInt()
        val peak = (f.peaks.sample(t * 0.9f) * rows).toInt().coerceAtMost(rows - 1)
        for (r in 0 until rows) {
            val x = left + c * (cell + gap)
            val y = top + (rows - 1 - r) * (cell + gap)
            val on = r < lit || (r == peak && peak > 0)
            val color = colors.at(r / (rows - 1f))
            drawRoundRect(color, Offset(x, y), Size(cell, cell), radius, alpha = if (on) 1f else 0.07f)
        }
    }
}

// ---------------------------------------------------------------- Pulso

/** Ondas suaves en forma de lente, hechas de líneas finas, de un color a otro (de graves a agudos). */
private fun DrawScope.pulse(
    f: VizFrame,
    colors: List<Color>,
    time: Float,
) {
    val n = 140
    val margin = 8.dp.toPx()
    val width = size.width - margin * 2
    val maxA = size.height * 0.42f
    val stroke = 1.1.dp.toPx()
    drawLine(colors.at(0.5f), Offset(margin, center.y), Offset(margin + width, center.y), strokeWidth = 1.dp.toPx(), alpha = 0.35f)
    for (i in 0 until n) {
        val t = i / (n - 1f)
        val v = f.bands.sample(t * 0.88f)
        // "Lentes": la amplitud se agrupa en bultos que se desplazan despacio.
        val lens = abs(sin(t * PI.toFloat() * 7f + time * 0.6f)).pow(1.4f)
        val a = (1.2.dp.toPx() + v * maxA * (0.1f + 0.9f * lens))
        val x = margin + t * width
        val color = colors.at(t)
        drawLine(color, Offset(x, center.y - a), Offset(x, center.y + a), strokeWidth = stroke * 4f, alpha = 0.08f, cap = StrokeCap.Round)
        drawLine(color, Offset(x, center.y - a), Offset(x, center.y + a), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

// ---------------------------------------------------------------- Cordillera

/** Espectros anteriores en capas hacia el fondo: montañas que avanzan con la música. */
private fun DrawScope.peaks(
    f: VizFrame,
    colors: List<Color>,
) {
    val layers = f.historySize
    val w = size.width
    val h = size.height
    val path = Path()
    for (age in layers - 1 downTo 0) {
        val d = age / (layers - 1f) // 1 = la más lejana
        val baseY = h * 0.9f - d * h * 0.4f
        val ww = w * (0.96f - d * 0.36f)
        val x0 = (w - ww) / 2f
        val amp = h * 0.55f * (1f - d * 0.5f)
        val data = f.historyAt(age)
        path.reset()
        path.moveTo(x0, baseY)
        val n = 48
        for (i in 0 until n) {
            val t = i / (n - 1f)
            val spike = 0.65f + 0.35f * NOISE[(i + age * 7) % NOISE.size]
            val v = data.sample(t * 0.88f).pow(1.5f) * spike
            path.lineTo(x0 + t * ww, baseY - v * amp)
        }
        path.lineTo(x0 + ww, baseY)
        path.close()
        val near = 1f - d
        drawPath(
            path,
            Brush.verticalGradient(listOf(colors.at(0.6f + near * 0.4f), colors.at(0.1f)), startY = baseY - amp, endY = baseY),
            alpha = 0.18f + near * 0.82f,
        )
        drawPath(path, colors.last(), alpha = 0.1f + near * 0.5f, style = Stroke(0.8.dp.toPx()))
    }
}

private val NOISE = FloatArray(64) { i -> ((sin(i * 12.9898f) * 43758.547f) % 1f).let { abs(it) } }
