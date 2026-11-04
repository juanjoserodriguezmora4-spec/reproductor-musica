package com.bismopower.app.ui.player.visualizer

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
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
    HALO("HALO", "Halo", CoverMode.CIRCLE, coverSize = 0.52f),
    ECLIPSE("ECLIPSE", "Eclipse", CoverMode.NONE),
    AURA("AURA", "Aura", CoverMode.CIRCLE, coverSize = 0.44f),
    LINE("LINE", "Línea", CoverMode.SQUARE, coverSize = 1f, scrim = 0.35f),
    BARS("BARS", "Barras", CoverMode.SQUARE, coverSize = 1f, scrim = 0.45f),
    LED("LED", "LED", CoverMode.NONE),
    PULSE("PULSE", "Pulso", CoverMode.NONE),
    PEAKS("PEAKS", "Cordillera", CoverMode.NONE),
    GLOW("GLOW", "Brillo", CoverMode.NONE),
    MIRROR("MIRROR", "Espejo", CoverMode.NONE),
    ;

    companion object {
        fun of(id: String): VizStyle = entries.firstOrNull { it.id == id } ?: HALO
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

    /** Un color que elige el usuario con las barras de tono e intensidad. */
    CUSTOM("CUSTOM", "Mi color", null),
    ;

    /**
     * Los colores de la paleta. Con "Carátula" se arman a partir del color de la canción
     * ([accent]); con "Mi color", a partir del tono ([customHue], 0 a 360) y la intensidad
     * ([customSat], 0 = blanco, 1 = color puro) guardados en Ajustes.
     */
    fun colors(
        accent: Color,
        customHue: Float = DEFAULT_HUE,
        customSat: Float = DEFAULT_SAT,
    ): List<Color> =
        fixed ?: if (this == CUSTOM) {
            customColors(customHue, customSat)
        } else {
            listOf(
                lerp(accent, Color.Black, 0.25f),
                accent,
                lerp(accent, Color.White, 0.45f),
                lerp(accent, Color.White, 0.8f),
            )
        }

    companion object {
        const val DEFAULT_HUE = 265f
        const val DEFAULT_SAT = 0.75f

        fun of(id: String): VizPalette = entries.firstOrNull { it.id == id } ?: ARTWORK
    }
}

/** El color elegido: [hue] de 0 a 360 y [sat] de 0 (blanco) a 1 (color puro). */
fun customColor(
    hue: Float,
    sat: Float,
): Color = Color.hsv(((hue % 360f) + 360f) % 360f, sat.coerceIn(0f, 1f), 1f)

/** Cuatro tonos alrededor del color elegido, de oscuro a casi blanco, como las otras paletas. */
fun customColors(
    hue: Float,
    sat: Float,
): List<Color> {
    val s = sat.coerceIn(0f, 1f)
    fun hsv(
        h: Float,
        sa: Float,
        v: Float,
    ) = Color.hsv(((h % 360f) + 360f) % 360f, sa.coerceIn(0f, 1f), v)
    return listOf(
        hsv(hue - 18f, s, 0.7f),
        hsv(hue, s, 1f),
        hsv(hue + 14f, s * 0.55f, 1f),
        hsv(hue + 24f, s * 0.2f, 1f),
    )
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
        VizStyle.HALO -> halo(f, colors, time)
        VizStyle.ECLIPSE -> eclipse(f, colors, time)
        VizStyle.AURA -> aura(f, colors, time)
        VizStyle.LINE -> line(f, colors)
        VizStyle.BARS -> bars(f, colors)
        VizStyle.LED -> led(f, colors)
        VizStyle.PULSE -> pulse(f, colors, time)
        VizStyle.PEAKS -> peaks(f, colors)
        VizStyle.GLOW -> glow(f, colors, time)
        VizStyle.MIRROR -> mirror(f, colors, time)
    }
}

private fun DrawScope.sweep(colors: List<Color>): Brush = Brush.sweepGradient(colors + colors.reversed().drop(1), center)

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
    // Luz blanca y partículas, como en Aura.
    whiteGlow(r0, room, f.energy, colors.at(0.5f))
    particles(r0, f, time, alpha = 0.8f, dotSize = 1f)
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

// ---------------------------------------------------------------- Eclipse

private const val TWO_PI = 6.2831855f

/**
 * Disco negro con luz de verdad alrededor, como un eclipse: muy fuerte y casi blanca pegada al
 * borde, y que se va apagando poco a poco hasta mezclarse con el fondo negro.
 * Se pinta en una capa aparte: primero los colores de la paleta alrededor (dos o tres tonos
 * grandes que giran despacio y brillan más donde empuja la música), encima un núcleo blanco, y
 * al final una máscara redonda ([BlendMode.DstIn]) que decide cuánta luz queda a cada distancia.
 */
private fun DrawScope.eclipse(
    f: VizFrame,
    colors: List<Color>,
    time: Float,
) {
    val m = min(size.width, size.height)
    val r0 = m * 0.27f
    val out = m / 2f
    val bass = f.energy
    drawIntoCanvas { it.saveLayer(Rect(center.x - out, center.y - out, center.x + out, center.y + out), Paint()) }
    // 1) Colores alrededor.
    val hues =
        Array(37) { i ->
            val a = TWO_PI * i / 36f
            val turned = ((a + f.spin * 0.35f) % TWO_PI + TWO_PI) % TWO_PI
            val v = f.bands.sample(0.03f + abs(turned / PI.toFloat() - 1f) * 0.75f)
            val u = 0.5f + 0.42f * sin(a - f.spin * 0.3f) + 0.12f * sin(2f * a + time * 0.4f)
            val alpha = (0.5f + v * 0.5f + 0.12f * sin(a * 3f + time * 0.7f)).coerceIn(0f, 1f)
            i / 36f to lerp(colors.at(u), Color.White, 0.08f).copy(alpha = alpha)
        }
    drawCircle(Brush.sweepGradient(*hues, center = center), radius = out)
    // 2) Núcleo blanco pegado al borde.
    val core = r0 * 1.35f
    drawCircle(
        Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.8f),
            r0 / core to Color.White.copy(alpha = 0.8f),
            (r0 * 1.07f) / core to Color.White.copy(alpha = 0.3f),
            1f to Color.White.copy(alpha = 0f),
            center = center,
            radius = core,
        ),
        radius = core,
    )
    // 3) Máscara: cuánta luz queda a cada distancia del borde (suma de varias caídas suaves).
    val l1 = r0 * 0.05f
    val l2 = r0 * (0.3f + bass * 0.15f)
    val l3 = r0 * (0.75f + bass * 0.25f)
    val l4 = r0 * (1.6f + bass * 0.4f)
    val steps = 40
    val mask = ArrayList<Pair<Float, Color>>(steps + 2)
    mask += 0f to Color.Black
    for (k in 0..steps) {
        val x = (k / steps.toFloat()).pow(1.5f)
        val d = x * (out - r0)
        val fade = (1f - x * x).let { it * it }
        val light =
            (0.75f * exp(-d / l1) + (0.4f + bass * 0.2f) * exp(-d / l2) + (0.3f + bass * 0.1f) * exp(-d / l3) + 0.22f * exp(-d / l4))
                .coerceAtMost(1f) * fade
        mask += (r0 + d) / out to Color.Black.copy(alpha = light)
    }
    drawCircle(Brush.radialGradient(*mask.toTypedArray(), center = center, radius = out), radius = out, blendMode = BlendMode.DstIn)
    drawIntoCanvas { it.restore() }

    particles(r0, f, time, alpha = 0.55f, dotSize = 0.8f)
    drawCircle(Color(0xFF020306), radius = r0)
}

// ---------------------------------------------------------------- Aura

private fun fract(x: Float): Float = x - floor(x)

/** Resplandor blanco alrededor de un círculo de radio [r0] (transparente por dentro), crece con los graves. */
private fun DrawScope.whiteGlow(
    r0: Float,
    room: Float,
    bass: Float,
    tint: Color,
) {
    val glowR = r0 + room * (0.55f + bass * 0.45f)
    val glowA = (0.25f + bass * 0.45f).coerceAtMost(1f)
    drawCircle(
        Brush.radialGradient(
            0f to Color.Transparent,
            (r0 - 1f) / glowR to Color.Transparent,
            r0 / glowR to Color.White.copy(alpha = glowA),
            1f to tint.copy(alpha = 0f),
            center = center,
            radius = glowR,
        ),
        radius = glowR,
    )
}

/** Partículas blancas que salen del borde del círculo y se desvanecen; más rápido con música fuerte. */
private fun DrawScope.particles(
    r0: Float,
    f: VizFrame,
    time: Float,
    alpha: Float,
    dotSize: Float,
) {
    val m = min(size.width, size.height)
    for (i in 0 until 46) {
        val h1 = fract(sin(i * 12.9898f) * 43758.547f)
        val h2 = fract(sin(i * 78.233f) * 12543.12f)
        val a = h1 * TWO_PI + time * 0.05f
        val prog = fract(f.spin * 0.07f * (0.5f + h2) + h2)
        val rr = r0 + prog * (m / 2f - r0)
        drawCircle(
            Color.White,
            radius = ((0.7f + h2 * 1.5f) * dotSize).dp.toPx(),
            center = Offset(center.x + cos(a) * rr, center.y + sin(a) * rr),
            alpha = ((1f - prog) * min(1f, prog * 6f) * alpha).coerceIn(0f, 1f),
        )
    }
}

/**
 * Estilo de los videos de NoCopyrightSounds: la carátula en un círculo y alrededor barras de
 * luz blanca que salen hacia afuera (iguales a izquierda y derecha), un resplandor que crece con
 * los graves y partículas que se alejan más rápido cuando la música está fuerte.
 */
private fun DrawScope.aura(
    f: VizFrame,
    colors: List<Color>,
    time: Float,
) {
    val m = min(size.width, size.height)
    val r0 = m * VizStyle.AURA.coverSize / 2f + 3.dp.toPx()
    val room = m / 2f - r0 - 4.dp.toPx()
    val bass = f.energy
    val tint = colors.at(0.5f)
    whiteGlow(r0, room, bass, tint)
    particles(r0, f, time, alpha = 0.8f, dotSize = 1f)

    // Barras blancas, simétricas (graves arriba, agudos abajo a los dos lados).
    val n = 120
    val barW = TWO_PI * r0 / n * 0.55f
    for (i in 0 until n) {
        val half = if (i < n / 2) i else n - 1 - i
        val t = half / (n / 2 - 1f)
        val v = f.bands.sample(0.02f + t * 0.7f)
        val len = 2.dp.toPx() + v.pow(1.2f) * room * 0.85f
        val a = -PI.toFloat() / 2f + TWO_PI * i / n
        val ca = cos(a)
        val sa = sin(a)
        val from = Offset(center.x + ca * (r0 + 2.dp.toPx()), center.y + sa * (r0 + 2.dp.toPx()))
        val to = Offset(center.x + ca * (r0 + 2.dp.toPx() + len), center.y + sa * (r0 + 2.dp.toPx() + len))
        drawLine(tint, from, to, strokeWidth = barW * 5f, alpha = 0.06f, cap = StrokeCap.Round)
        drawLine(Color.White, from, to, strokeWidth = barW * 2.4f, alpha = 0.16f, cap = StrokeCap.Round)
        drawLine(Color.White, from, to, strokeWidth = barW, cap = StrokeCap.Round)
    }

    // Aro blanco pegado a la carátula, que late con los graves.
    drawCircle(Color.White, radius = r0, style = Stroke(9.dp.toPx()), alpha = (0.12f + bass * 0.3f).coerceAtMost(1f))
    drawCircle(Color.White, radius = r0, style = Stroke(2.dp.toPx()), alpha = 0.95f)
}

// ---------------------------------------------------------------- Brillo

/** Barras que salen del centro hacia arriba y abajo, agrupadas en racimos y con un halo de luz. */
private fun DrawScope.glow(
    f: VizFrame,
    colors: List<Color>,
    time: Float,
) {
    val n = 52
    val margin = 14.dp.toPx()
    val width = size.width - margin * 2
    val slot = width / n
    val barW = slot * 0.42f
    val maxA = size.height * 0.36f
    for (i in 0 until n) {
        val t = i / (n - 1f)
        val v = f.bands.sample(t * 0.85f)
        // Racimos: la altura se agrupa en bultos que se desplazan muy despacio.
        val cluster = 0.3f + 0.7f * abs(sin(t * PI.toFloat() * 5f + time * 0.15f)).pow(1.3f)
        val a = 2.dp.toPx() + v * maxA * cluster
        val x = margin + i * slot + slot / 2f
        val color = colors.at(t)
        val top = Offset(x, center.y - a)
        val bottom = Offset(x, center.y + a)
        drawLine(color, top, bottom, strokeWidth = barW * 4.5f, alpha = 0.07f, cap = StrokeCap.Round)
        drawLine(color, top, bottom, strokeWidth = barW * 2f, alpha = 0.2f, cap = StrokeCap.Round)
        drawLine(color, top, bottom, strokeWidth = barW, cap = StrokeCap.Round)
        drawLine(Color.White, top, bottom, strokeWidth = barW * 0.35f, alpha = 0.45f, cap = StrokeCap.Round)
    }
}

// ---------------------------------------------------------------- Espejo

/** Barras redondeadas sobre una línea, con su reflejo más tenue debajo. */
private fun DrawScope.mirror(
    f: VizFrame,
    colors: List<Color>,
    time: Float,
) {
    val n = 60
    val margin = 16.dp.toPx()
    val width = size.width - margin * 2
    val slot = width / n
    val barW = slot * 0.5f
    val maxA = size.height * 0.36f
    val gap = 2.dp.toPx()
    for (i in 0 until n) {
        val t = i / (n - 1f)
        val v = f.bands.sample(t * 0.85f)
        val cluster = 0.2f + 0.8f * abs(sin(t * PI.toFloat() * 4.5f + time * 0.2f)).pow(1.5f)
        val a = 1.5.dp.toPx() + v * maxA * cluster
        val x = margin + i * slot + slot / 2f
        val color = colors.at(0.4f + t * 0.6f)
        drawLine(color, Offset(x, center.y - gap / 2f), Offset(x, center.y - gap / 2f - a), strokeWidth = barW, cap = StrokeCap.Round)
        drawLine(
            color,
            Offset(x, center.y + gap / 2f),
            Offset(x, center.y + gap / 2f + a * 0.6f),
            strokeWidth = barW,
            alpha = 0.35f,
            cap = StrokeCap.Round,
        )
    }
}
