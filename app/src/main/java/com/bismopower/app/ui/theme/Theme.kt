package com.bismopower.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.bismopower.app.R

/** Colores de BismoPower: fondo con manchas de color y paneles de vidrio translúcido. */
data class BismoColors(
    val dark: Boolean,
    val bg: Color,
    val blobs: List<Color>,
    val glass: Color,
    val glassStrong: Color,
    val solid: Color,
    val border: Color,
    val selected: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    /** Color del texto y los íconos que van encima del acento (botones llenos). */
    val onAccent: Color,
    val accentSoft: Color,
    val ok: Color,
    val err: Color,
    val track: Color,
)

/**
 * Un tema de color: el acento y las cuatro manchas del fondo, en versión clara y oscura.
 * Todo lo demás (vidrio, textos, bordes) es igual en todos los temas.
 */
data class ColorTheme(
    val id: String,
    val name: String,
    val lightAccent: Color,
    val darkAccent: Color,
    val lightBlobs: List<Color>,
    val darkBlobs: List<Color>,
    val lightOnAccent: Color = Color.White,
    val darkOnAccent: Color = Color(0xFF0B0F1A),
)

/** Usa los colores del fondo de pantalla (Material You). Solo existe desde Android 12. */
const val DYNAMIC_THEME = "DINAMICO"

val dynamicSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

val COLOR_THEMES =
    listOf(
        ColorTheme(
            id = "BRUMA",
            name = "Bruma",
            lightAccent = Color(0xFF4B5BFF),
            darkAccent = Color(0xFF8B98FF),
            lightBlobs = listOf(Color(0xFFB9D2FF), Color(0xFFD8C8FF), Color(0xFFFFDCE9), Color(0xFFC6F0E4)),
            darkBlobs = listOf(Color(0xFF26357A), Color(0xFF3E2A6E), Color(0xFF4A2140), Color(0xFF0F4A40)),
            darkOnAccent = Color.White,
        ),
        ColorTheme(
            id = "OCEANO",
            name = "Océano",
            lightAccent = Color(0xFF0A7FBF),
            darkAccent = Color(0xFF5CC8F5),
            lightBlobs = listOf(Color(0xFFB3E1FF), Color(0xFFBDEFEA), Color(0xFFC9D6FF), Color(0xFFD3F4FF)),
            darkBlobs = listOf(Color(0xFF0B3A5C), Color(0xFF0E4A52), Color(0xFF1A2F6B), Color(0xFF08404A)),
        ),
        ColorTheme(
            id = "ATARDECER",
            name = "Atardecer",
            lightAccent = Color(0xFFD9480F),
            darkAccent = Color(0xFFFF9D5C),
            lightBlobs = listOf(Color(0xFFFFD6BC), Color(0xFFFFCFDB), Color(0xFFFFE6B3), Color(0xFFF3D0FF)),
            darkBlobs = listOf(Color(0xFF6B2A12), Color(0xFF5E1F3A), Color(0xFF5C4210), Color(0xFF4A1E52)),
        ),
        ColorTheme(
            id = "BOSQUE",
            name = "Bosque",
            lightAccent = Color(0xFF1B8655),
            darkAccent = Color(0xFF5FD49A),
            lightBlobs = listOf(Color(0xFFC2EDD3), Color(0xFFDDF1C0), Color(0xFFC0E7E4), Color(0xFFE6EFC2)),
            darkBlobs = listOf(Color(0xFF0F4A30), Color(0xFF2E4A12), Color(0xFF0E4446), Color(0xFF1F3D1F)),
        ),
        ColorTheme(
            id = "UVA",
            name = "Uva",
            lightAccent = Color(0xFF8B3FD9),
            darkAccent = Color(0xFFC08CFF),
            lightBlobs = listOf(Color(0xFFE2CCFF), Color(0xFFFFCCEE), Color(0xFFD6D0FF), Color(0xFFF2D4FF)),
            darkBlobs = listOf(Color(0xFF3E1A6E), Color(0xFF5A1A4E), Color(0xFF2A2470), Color(0xFF4A1460)),
        ),
        ColorTheme(
            id = "CEREZA",
            name = "Cereza",
            lightAccent = Color(0xFFD12F48),
            darkAccent = Color(0xFFFF7A8C),
            lightBlobs = listOf(Color(0xFFFFCCD5), Color(0xFFFFD8C6), Color(0xFFF5C8E0), Color(0xFFFFE0E6)),
            darkBlobs = listOf(Color(0xFF5E1422), Color(0xFF5A2A14), Color(0xFF4E1438), Color(0xFF3E0F1A)),
        ),
        ColorTheme(
            id = "GRAFITO",
            name = "Grafito",
            lightAccent = Color(0xFF353D4D),
            darkAccent = Color(0xFFD5DBE6),
            lightBlobs = listOf(Color(0xFFDCE0E7), Color(0xFFE7E7EB), Color(0xFFD3D8E1), Color(0xFFEBEDF1)),
            darkBlobs = listOf(Color(0xFF1F2633), Color(0xFF262A33), Color(0xFF1A2230), Color(0xFF2A2F3A)),
        ),
    )

private fun lightColors(
    accent: Color,
    onAccent: Color,
    blobs: List<Color>,
) = BismoColors(
    dark = false,
    bg = Color(0xFFEEF1F7),
    blobs = blobs,
    glass = Color(0x9EFFFFFF), // blanco 62 %
    glassStrong = Color(0xBFFFFFFF),
    solid = Color(0xD9FFFFFF), // sin desenfoque: 85 %
    border = Color(0xE6FFFFFF), // blanco 90 %
    selected = Color(0xFFFFFFFF),
    text = Color(0xFF121826),
    muted = Color(0xFF5B6475),
    accent = accent,
    onAccent = onAccent,
    accentSoft = accent.copy(alpha = 0.12f),
    ok = Color(0xFF13A58E),
    err = Color(0xFFE5484D),
    track = Color(0x14121826),
)

private fun darkColors(
    accent: Color,
    onAccent: Color,
    blobs: List<Color>,
) = BismoColors(
    dark = true,
    bg = Color(0xFF0B0F1A),
    blobs = blobs,
    glass = Color(0x12FFFFFF), // blanco 7 %
    glassStrong = Color(0x1CFFFFFF),
    solid = Color(0xD9161B29), // sin desenfoque: 85 %
    border = Color(0x1FFFFFFF), // blanco 12 %
    selected = Color(0x2EFFFFFF),
    text = Color(0xFFEEF1F8),
    muted = Color(0xFF9AA3B5),
    accent = accent,
    onAccent = onAccent,
    accentSoft = accent.copy(alpha = 0.16f),
    ok = Color(0xFF3CCFB4),
    err = Color(0xFFFF6B6F),
    track = Color(0x1FFFFFFF),
)

fun bismoColors(
    theme: ColorTheme,
    dark: Boolean,
): BismoColors =
    if (dark) {
        darkColors(theme.darkAccent, theme.darkOnAccent, theme.darkBlobs)
    } else {
        lightColors(theme.lightAccent, theme.lightOnAccent, theme.lightBlobs)
    }

val LightBismo = bismoColors(COLOR_THEMES[0], dark = false)
val DarkBismo = bismoColors(COLOR_THEMES[0], dark = true)

val LocalBismo = staticCompositionLocalOf { LightBismo }

/** La versión oscura del tema actual (el reproductor siempre es oscuro). */
val LocalBismoDark = staticCompositionLocalOf { DarkBismo }

/** true = paneles sólidos sin desenfoque (Android 8 a 11, o "Reducir transparencia"). */
val LocalSolid = staticCompositionLocalOf { false }

/** El desenfoque real (RenderEffect) solo es eficiente desde Android 12. */
val blurSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

val Inter =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

/** Estilos de texto usados en toda la app (iguales a Bruma). */
object T {
    val h1 = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.6).sp)
    val h2 = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = (-0.2).sp)
    val title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    val body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val small = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp)
    val tiny = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp)
    val label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    val overline = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp)
    val big = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.4).sp)
}

private fun TextStyle.inter() = copy(fontFamily = Inter)

private val BaseTypography = Typography()
private val BismoTypography =
    Typography(
        displayLarge = BaseTypography.displayLarge.inter(),
        displayMedium = BaseTypography.displayMedium.inter(),
        displaySmall = BaseTypography.displaySmall.inter(),
        headlineLarge = BaseTypography.headlineLarge.inter(),
        headlineMedium = BaseTypography.headlineMedium.inter(),
        headlineSmall = BaseTypography.headlineSmall.inter(),
        titleLarge = BaseTypography.titleLarge.inter(),
        titleMedium = BaseTypography.titleMedium.inter(),
        titleSmall = BaseTypography.titleSmall.inter(),
        bodyLarge = BaseTypography.bodyLarge.inter(),
        bodyMedium = BaseTypography.bodyMedium.inter(),
        bodySmall = BaseTypography.bodySmall.inter(),
        labelLarge = BaseTypography.labelLarge.inter(),
        labelMedium = BaseTypography.labelMedium.inter(),
        labelSmall = BaseTypography.labelSmall.inter(),
    )

/** Busca el tema de color guardado; si es el de fondo de pantalla, lo arma con Material You. */
@Composable
private fun resolveColorTheme(id: String): ColorTheme {
    val context = LocalContext.current
    if (id == DYNAMIC_THEME && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return remember(context) {
            val l = dynamicLightColorScheme(context)
            val d = dynamicDarkColorScheme(context)
            ColorTheme(
                id = DYNAMIC_THEME,
                name = "Fondo de pantalla",
                lightAccent = l.primary,
                darkAccent = d.primary,
                lightBlobs = listOf(l.primaryContainer, l.tertiaryContainer, l.secondaryContainer, l.primaryContainer),
                darkBlobs = listOf(d.primaryContainer, d.tertiaryContainer, d.secondaryContainer, d.primaryContainer),
                lightOnAccent = l.onPrimary,
                darkOnAccent = d.onPrimary,
            )
        }
    }
    return COLOR_THEMES.firstOrNull { it.id == id } ?: COLOR_THEMES[0]
}

@Composable
fun BismoTheme(
    theme: String,
    colorTheme: String,
    reduceTransparency: Boolean,
    content: @Composable () -> Unit,
) {
    val dark =
        when (theme) {
            "LIGHT" -> false
            "DARK" -> true
            else -> isSystemInDarkTheme()
        }
    val palette = resolveColorTheme(colorTheme)
    val base = bismoColors(palette, dark)
    val darkBase = bismoColors(palette, dark = true)

    // "Reducir transparencia" pinta los paneles totalmente opacos. En Android 8 a 11 (sin
    // desenfoque) y sin esa opción, los paneles quedan al 85 % para conservar algo del vidrio.
    val c =
        if (reduceTransparency) {
            base.copy(solid = if (dark) Color(0xFF161B29) else Color.White)
        } else {
            base
        }

    val scheme =
        if (dark) {
            darkColorScheme(
                primary = c.accent,
                onPrimary = c.onAccent,
                secondary = c.ok,
                error = c.err,
                background = c.bg,
                onBackground = c.text,
                surface = Color(0xFF151A27),
                onSurface = c.text,
                surfaceVariant = Color(0xFF1C2232),
                onSurfaceVariant = c.muted,
                surfaceContainer = Color(0xFF181D2B),
                surfaceContainerHigh = Color(0xFF1D2333),
                outline = Color(0x33FFFFFF),
            )
        } else {
            lightColorScheme(
                primary = c.accent,
                onPrimary = c.onAccent,
                secondary = c.ok,
                error = c.err,
                background = c.bg,
                onBackground = c.text,
                surface = Color(0xFFF8F9FC),
                onSurface = c.text,
                surfaceVariant = Color(0xFFE9ECF4),
                onSurfaceVariant = c.muted,
                surfaceContainer = Color(0xFFF3F5FA),
                surfaceContainerHigh = Color(0xFFF7F8FC),
                outline = Color(0x22121826),
            )
        }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    val solid = reduceTransparency || !blurSupported
    CompositionLocalProvider(
        LocalBismo provides c,
        LocalBismoDark provides darkBase,
        LocalSolid provides solid,
    ) {
        MaterialTheme(colorScheme = scheme, typography = BismoTypography, content = content)
    }
}
