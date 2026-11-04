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
    HALO("HALO", "Halo", CoverMode.CIRCLE, coverSize = 0.52f),
    ECLIPSE("ECLIPSE", "Eclipse", CoverMode.NONE),
    AURA("AURA", "Aura", CoverMode.NONE),
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
        VizStyle.HALO -> halo(f, colors, time)
        VizStyle.ECLIPSE -> eclipse(f, colors, time)
        VizStyle.AURA -> aura(f, colors, time)
        VizStyle.LINE -> line(f, colors, time)
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

/**
 * Una línea de luz que cruza la carátula y ondula con la música.
 * No dibuja la onda cruda (tiembla demasiado): suma cinco ondas suaves cuyo tamaño sigue a
 * graves, medios y agudos, y que viajan en sentidos opuestos a la velocidad de [VizFrame.spin]
 * (más rápido cuando la música está fuerte).
 */
private fun DrawScope.line(
    f: VizFrame,
    colors: List<Color>,
    time: Float,
) {
    val side = min(size.width, size.height)
    val left = center.x - side / 2f + 10.dp.toPx()
    val width = side - 20.dp.toPx()
    val groups = 5
    val amps =
        FloatArray(groups) { g ->
            var sum = 0f
            for (j in 0 until 8) {
                val t = (g * 8 + j) / 44f
                sum += 0.65f * f.bands.sample(t) + 0.35f * f.slow.sample(t)
            }
            sum / 8f
        }
    val n = 120
    val path = Path()
    for (i in 0 until n) {
        val t = i / (n - 1f)
        val taper = sin(PI * t).toFloat().pow(0.7f) // quieta en las puntas, viva en el centro
        var y = 0f
        for (g in 0 until groups) {
            val dir = if (g % 2 == 0) 1f else -1f
            y += amps[g] * sin(2f * PI.toFloat() * t * (1.5f + g * 1.1f) + dir * f.spin * (1.6f + g * 0.6f) + g * 1.7f)
        }
        y /= groups * 0.55f
        val x = left + t * width
        val py = center.y + y * side * 0.3f * taper
        if (i == 0) path.moveTo(x, py) else path.lineTo(x, py)
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

/** Tono (en grados) de la corona del eclipse en el ángulo [a]: da la vuelta con [VizFrame.spin]. */
private fun eclipseHue(
    a: Float,
    f: VizFrame,
    time: Float,
): Float = a * 180f / PI.toFloat() + f.spin * 70f + 28f * sin(2f * a - time * 0.9f)

private fun hueColor(
    hue: Float,
    saturation: Float,
): Color = Color.hsv(((hue % 360f) + 360f) % 360f, saturation, 1f)

/**
 * Disco negro con una corona de arcoíris pegada al borde, como un eclipse. Los colores giran
 * alrededor (más rápido cuando la música está fuerte) y brillan más donde empujan las bandas.
 * Usa su propio arcoíris: no depende de la paleta.
 */
@Suppress("UNUSED_PARAMETER")
private fun DrawScope.eclipse(
    f: VizFrame,
    colors: List<Color>,
    time: Float,
) {
    val m = min(size.width, size.height)
    val r0 = m * 0.3f
    // 1) Corona: muchos anillos finos del mismo degradado, más densos cerca del borde.
    val stops = Array(25) { i -> i / 24f to hueColor(eclipseHue(TWO_PI * i / 24f, f, time), 0.9f) }
    val ring = Brush.sweepGradient(*stops, center = center)
    val glow = (0.1f * (0.75f + f.energy * 0.5f)).coerceAtMost(1f)
    for (k in 1..14) {
        val e = r0 * 0.4f * (k / 14f).pow(1.6f)
        drawCircle(ring, radius = r0, style = Stroke(e * 2f), alpha = glow)
    }
    // 2) Manchas de luz que crecen con la música y viajan alrededor.
    val n = 40
    for (i in 0 until n) {
        val a = TWO_PI * i / n
        val turned = ((a + f.spin * 0.8f) % TWO_PI + TWO_PI) % TWO_PI
        val v = f.bands.sample(0.03f + abs(turned / PI.toFloat() - 1f) * 0.75f)
        val reach = r0 * (0.1f + v * 0.42f)
        val c = hueColor(eclipseHue(a, f, time), 0.85f)
        val p = Offset(center.x + cos(a) * (r0 + reach * 0.25f), center.y + sin(a) * (r0 + reach * 0.25f))
        drawCircle(
            Brush.radialGradient(listOf(c.copy(alpha = (0.55f * v + 0.1f).coerceAtMost(1f)), c.copy(alpha = 0f)), center = p, radius = reach),
            radius = reach,
            center = p,
        )
    }
    // 3) Borde blanco caliente que late con los graves.
    drawCircle(Color.White, radius = r0 + 1.5.dp.toPx(), style = Stroke(7.dp.toPx()), alpha = (0.22f + f.energy * 0.25f).coerceAtMost(1f))
    drawCircle(Color.White, radius = r0 + 0.5.dp.toPx(), style = Stroke((1.4f + f.energy * 2f).dp.toPx()), alpha = 0.85f)
    drawCircle(Color(0xFF030407), radius = r0)
}

// ---------------------------------------------------------------- Aura

/**
 * Esfera de luz: un contorno brillante con cintas hechas de muchas líneas finas que se tuercen
 * y giran por dentro, y humo que sube por arriba con los agudos. Colores de abajo (primero de la
 * paleta) hacia arriba.
 */
private fun DrawScope.aura(
    f: VizFrame,
    colors: List<Color>,
    time: Float,
) {
    val m = min(size.width, size.height)
    val cx = center.x
    val cy = center.y + m * 0.07f
    val r = m * 0.3f
    val spin = f.spin
    var treble = 0f
    for (i in 36 until 60) treble += f.bands[i]
    treble /= 24f
    val bass = f.energy
    val body =
        Brush.verticalGradient(
            0f to colors.at(0.75f).copy(alpha = 0.6f),
            0.45f to colors.at(0.45f),
            1f to colors.at(0f),
            startY = cy - r * 1.9f,
            endY = cy + r,
        )
    val rim = r * (1f + bass * 0.04f)
    for ((w, a) in listOf(8f to 0.08f, 3f to 0.25f, 1f to 0.8f)) {
        drawCircle(body, radius = rim, center = Offset(cx, cy), style = Stroke(w.dp.toPx()), alpha = a)
    }

    // Cintas: cada una es un trozo de círculo sobre la esfera, inclinado en 3D, hecho de 16
    // líneas paralelas; el ancho se tuerce a lo largo y todo gira con la música.
    val path = Path()
    val thin = Stroke(0.7.dp.toPx())
    val lines = 16
    val pts = 80
    for (s in 0 until 4) {
        val yaw = spin * (0.35f + s * 0.12f) + s * 1.6f
        val pitch = sin(time * 0.3f + s * 2.1f) * 1.1f + s * 0.7f
        val cyw = cos(yaw)
        val syw = sin(yaw)
        val cp = cos(pitch)
        val sp = sin(pitch)
        val start = spin * (0.5f + s * 0.2f) + s * 2f
        val span = PI.toFloat() * (1.1f + 0.35f * sin(time * 0.5f + s))
        for (k in 0 until lines) {
            path.reset()
            for (j in 0..pts) {
                val ph = start + span * j / pts
                val v = f.slow.sample(0.04f + abs(sin(ph)) * 0.6f)
                val twist = cos(ph * 1.5f + time * 0.8f + s)
                val d = (k / (lines - 1f) - 0.5f) * 0.55f * twist + 0.28f * sin(3 * ph + time * 1.2f + s * 1.9f)
                val rr = r * (1f + 0.1f * v * sin(3 * ph + spin * 2f + s) + 0.05f * sin(5 * ph - time))
                val x = cos(ph) * cos(d)
                val y = sin(ph) * cos(d)
                val z = sin(d)
                val y2 = y * cp - z * sp
                val z2 = y * sp + z * cp
                val px = cx + (x * cyw + z2 * syw) * rr
                val py = cy + y2 * rr
                if (j == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, body, alpha = 0.28f, style = thin)
        }
    }

    // Base brillante abajo.
    val base = colors.at(0f)
    for ((w, a) in listOf(12f to 0.12f, 4f to 0.35f, 1.6f to 0.9f)) {
        drawArc(
            base,
            startAngle = 27f,
            sweepAngle = 126f,
            useCenter = false,
            topLeft = Offset(cx - rim, cy - rim),
            size = Size(rim * 2f, rim * 2f),
            alpha = a,
            style = Stroke(w.dp.toPx(), cap = StrokeCap.Round),
        )
    }

    // Humo: abanicos de líneas que suben ondulando desde la parte de arriba.
    val smokeLine = Stroke(0.6.dp.toPx())
    val plumes = 6
    for (q in 0 until plumes) {
        val th = -1.15f + 2.3f * q / (plumes - 1) + 0.12f * sin(time * 0.4f + q)
        val bx = cx + sin(th) * r * 0.95f
        val by = cy - cos(th) * r * 0.95f
        val h = r * (0.45f + treble * 1.3f + bass * 0.35f) * (0.6f + 0.4f * sin(time * 1.3f + q * 1.9f))
        val lean = sin(th) * 0.6f
        val smoke =
            Brush.verticalGradient(
                0f to colors.at(0.5f).copy(alpha = 0.5f),
                0.5f to colors.at(0.7f).copy(alpha = 0.3f),
                1f to colors.at(0.85f).copy(alpha = 0f),
                startY = by,
                endY = by - h,
            )
        for (j in 0 until 14) {
            path.reset()
            for (i in 0..34) {
                val u = i / 34f
                val x = bx + sin(u * 3.5f + time * 2.4f + q * 1.3f + j * 0.1f) * u * r * 0.32f + (j - 6.5f) * u * r * 0.045f + lean * u * u * r * 0.45f
                val y = by - u * h
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, smoke, alpha = 0.55f, style = smokeLine)
        }
    }
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
