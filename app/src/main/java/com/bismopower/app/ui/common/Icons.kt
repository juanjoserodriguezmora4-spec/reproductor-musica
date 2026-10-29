package com.bismopower.app.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Íconos de línea de Bruma, compartidos con BismoPower (24 × 24), dibujados con trazos redondeados. */
object BI {
    private fun circ(
        cx: Float,
        cy: Float,
        r: Float,
    ): String = "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"

    private fun build(
        name: String,
        strokes: List<String>,
        fills: List<String> = emptyList(),
        width: Float = 1.8f,
    ): ImageVector {
        val b =
            ImageVector.Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            )
        strokes.forEach { d ->
            b.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        fills.forEach { d ->
            b.addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
        }
        return b.build()
    }

    val Link by lazy {
        build(
            "link",
            listOf(
                "M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7",
                "M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7",
            ),
        )
    }
    val Paste by lazy {
        build(
            "paste",
            listOf(
                "M9 2h6a1 1 0 0 1 1 1v2a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1z",
                "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2",
            ),
        )
    }
    val Download by lazy { build("download", listOf("M12 4v12", "M6 11l6 6 6-6", "M5 21h14")) }
    val Home by lazy { build("home", listOf("M3 10l9-7 9 7v10a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z")) }
    val Music by lazy { build("music", listOf("M9 18V5l12-2v13", circ(6f, 18f, 3f), circ(18f, 16f, 3f))) }
    val Gear by lazy {
        build(
            "gear",
            listOf(
                circ(12f, 12f, 3f),
                "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z",
            ),
        )
    }
    val Pause by lazy { build("pause", listOf("M9 5v14", "M15 5v14"), width = 2.4f) }
    val Play by lazy { build("play", emptyList(), listOf("M7 4.5v15l12-7.5z")) }
    val Close by lazy { build("close", listOf("M6 6l12 12", "M18 6L6 18")) }
    val Retry by lazy {
        build(
            "retry",
            listOf(
                "M3 12a9 9 0 0 1 15.5-6.3L21 8",
                "M21 3v5h-5",
                "M21 12a9 9 0 0 1-15.5 6.3L3 16",
                "M3 21v-5h5",
            ),
        )
    }
    val Check by lazy { build("check", listOf("M5 12l5 5 9-10")) }
    val Next by lazy { build("next", listOf("M19 5v14"), listOf("M5 5l10 7-10 7z"), width = 2.2f) }
    val Prev by lazy { build("prev", listOf("M5 5v14"), listOf("M19 5L9 12l10 7z"), width = 2.2f) }
    val Shuffle by lazy { build("shuffle", listOf("M16 3h5v5", "M4 20L21 3", "M21 16v5h-5", "M15 15l6 6", "M4 4l5 5")) }
    val Repeat by lazy {
        build("repeat", listOf("M17 2l4 4-4 4", "M3 11V9a3 3 0 0 1 3-3h15", "M7 22l-4-4 4-4", "M21 13v2a3 3 0 0 1-3 3H3"))
    }
    val RepeatOne by lazy {
        build(
            "repeat_one",
            listOf(
                "M17 2l4 4-4 4",
                "M3 11V9a3 3 0 0 1 3-3h15",
                "M7 22l-4-4 4-4",
                "M21 13v2a3 3 0 0 1-3 3H3",
                "M11 10.5l1.2-.8v5",
            ),
        )
    }
    val Chevron by lazy { build("chevron", listOf("M9 6l6 6-6 6")) }
    val Back by lazy { build("back", listOf("M15 6l-6 6 6 6")) }
    val Up by lazy { build("up", listOf("M6 15l6-6 6 6")) }
    val Down by lazy { build("down", listOf("M6 9l6 6 6-6")) }
    val More by lazy {
        build("more", emptyList(), listOf(circ(12f, 5f, 1.6f), circ(12f, 12f, 1.6f), circ(12f, 19f, 1.6f)))
    }
    val Plus by lazy { build("plus", listOf("M12 5v14", "M5 12h14")) }
    val Video by lazy {
        build("video", listOf("M4 5h11a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M17 10l5-3v10l-5-3"))
    }
    val Wave by lazy { build("wave", listOf("M4 10v4", "M8 6v12", "M12 3v18", "M16 7v10", "M20 10v4")) }
    val Moon by lazy { build("moon", listOf("M21 13A9 9 0 1 1 11 3a7 7 0 0 0 10 10z")) }
    val Sun by lazy {
        build(
            "sun",
            listOf(
                circ(12f, 12f, 4f),
                "M12 2v2",
                "M12 20v2",
                "M4.9 4.9l1.4 1.4",
                "M17.7 17.7l1.4 1.4",
                "M2 12h2",
                "M20 12h2",
                "M4.9 19.1l1.4-1.4",
                "M17.7 6.3l1.4-1.4",
            ),
        )
    }
    val Search by lazy { build("search", listOf(circ(11f, 11f, 7f), "M20 20l-3.5-3.5")) }
    val Trash by lazy {
        build(
            "trash",
            listOf(
                "M3 6h18",
                "M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2",
                "M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6",
                "M10 11v6",
                "M14 11v6",
            ),
        )
    }
    val Share by lazy {
        build("share", listOf(circ(18f, 5f, 3f), circ(6f, 12f, 3f), circ(18f, 19f, 3f), "M8.6 13.5l6.8 4", "M15.4 6.5l-6.8 4"))
    }
    val PlaylistAdd by lazy { build("playlist_add", listOf("M3 6h12", "M3 12h12", "M3 18h7", "M18 14v8", "M14 18h8")) }
    val Queue by lazy { build("queue", listOf("M3 6h13", "M3 12h13", "M3 18h9"), listOf("M17 15v6l5-3z")) }
    val Timer by lazy { build("timer", listOf(circ(12f, 13f, 8f), "M12 9v4l2 2", "M9 2h6")) }
    val Folder by lazy { build("folder", listOf("M3 6a2 2 0 0 1 2-2h4l2 3h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")) }
    val Alert by lazy { build("alert", listOf(circ(12f, 12f, 9f), "M12 7v6"), listOf(circ(12f, 16.5f, 1.1f))) }
    val Info by lazy { build("info", listOf(circ(12f, 12f, 9f), "M12 11v6"), listOf(circ(12f, 7.5f, 1.1f))) }
    val Edit by lazy { build("edit", listOf("M4 20h4L19 9l-4-4L4 16z", "M14 6l4 4")) }
    val Fullscreen by lazy { build("fullscreen", listOf("M4 9V4h5", "M20 9V4h-5", "M4 15v5h5", "M20 15v5h-5")) }
    val Wifi by lazy {
        build("wifi", listOf("M2 8.5a15 15 0 0 1 20 0", "M5 12a10 10 0 0 1 14 0", "M8.5 15.5a5 5 0 0 1 7 0"), listOf(circ(12f, 19f, 1.1f)))
    }
    val Shield by lazy { build("shield", listOf("M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z", "M9 12l2 2 4-4")) }
    val Sort by lazy { build("sort", listOf("M4 6h16", "M7 12h10", "M10 18h4")) }
    val Disc by lazy { build("disc", listOf(circ(12f, 12f, 9f), circ(12f, 12f, 2.5f))) }
    val User by lazy { build("user", listOf(circ(12f, 8f, 4f), "M4 21a8 8 0 0 1 16 0")) }
    val Sliders by lazy {
        build("sliders", listOf("M6 3v18", "M12 3v18", "M18 3v18"), listOf(circ(6f, 15f, 2.4f), circ(12f, 8f, 2.4f), circ(18f, 13f, 2.4f)))
    }
    val ListMusic by lazy { build("list_music", listOf("M3 6h12", "M3 12h12", "M3 18h7", "M19 18V6l2 1", circ(16.5f, 18f, 2.5f))) }
}
