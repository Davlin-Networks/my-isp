package run.sparo.myisp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import run.sparo.myisp.R

/*
 * The design system, signed off in the Phase 0
 * mockups. Every screen takes its colours, type, spacing and corners from
 * here; nothing hard-codes a hex value or a dp size of its own.
 */

/** Plus Jakarta Sans, four static weights, Latin subset (96 KB). OFL - licenses/. */
val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
)

object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object Radius {
    val s = 12.dp
    val m = 16.dp
    val l = 24.dp
}

enum class ThemeMode { System, Light, Dark }

/** What Material's colour scheme has no slot for. */
@Immutable
data class AppColors(
    val dark: Boolean,
    val muted: Color,
    val line: Color,
    val brandSoft: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val onHero: Color,
    val good: Color,
    val warning: Color,
    val critical: Color,
    val neutral: Color,
    val glass: Color,
) {
    val hero: Brush get() = Brush.linearGradient(listOf(heroStart, heroEnd))
    fun soft(c: Color): Color = c.copy(alpha = if (dark) 0.16f else 0.12f)
}

private val LocalAppColors = staticCompositionLocalOf<AppColors> { error("MyIspTheme not applied") }

object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
}

/**
 * State colours - online, expired, over a cap. Never the ISP's brand, and
 * never on their own: every one sits beside a word. Darker in the light
 * theme, so they still read as text there.
 */
object StatusColors {
    val good: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.good
    val warning: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.warning
    val critical: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.critical
    val neutral: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.neutral
}

// ── Colour maths ────────────────────────────────────────────────────────────

private val White = Color(0xFFFFFFFF)
private val Ink = Color(0xFF111827)
private val Fallback = Color(0xFF3B82F6)

fun parseHex(hex: String?): Color? = runCatching {
    val clean = hex?.trim()?.removePrefix("#") ?: return null
    if (clean.length != 6) return null
    Color(0xFF000000 or clean.toLong(16))
}.getOrNull()

/** WCAG contrast ratio, 1 to 21. */
fun contrast(a: Color, b: Color): Float {
    val x = a.luminance()
    val y = b.luminance()
    return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
}

/**
 * White or near-black, whichever reads better on [color] - measured, not
 * guessed from brightness. ISPs pick yellows and pale teals too.
 */
fun onColor(color: Color): Color = if (contrast(color, White) >= contrast(color, Ink)) White else Ink

/** Nudges [color] towards [towards] until it reads at 4.5:1 on [against]. */
private fun readableAgainst(color: Color, against: Color, towards: Color): Color {
    var c = color
    repeat(12) {
        if (contrast(c, against) >= 4.5f) return c
        c = lerp(c, towards, 0.12f)
    }
    return c
}

/** [color], darkened or lightened just enough to read as text on [background]. */
fun textOn(color: Color, background: Color): Color =
    readableAgainst(color, background, if (background.luminance() > 0.5f) Color.Black else White)

/** The hero gradient's far end: the brand turned 20° round the wheel, a touch deeper. */
private fun gradientEnd(brand: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(brand.toArgb(), hsv)
    hsv[0] = (hsv[0] + 20f) % 360f
    hsv[2] = (hsv[2] - 0.07f).coerceAtLeast(0f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

// ── Type ────────────────────────────────────────────────────────────────────

/** Tabular figures throughout: money, data and times never jitter as they change. */
private fun TextStyle.jakarta() = copy(fontFamily = Jakarta, fontFeatureSettings = "tnum")

private val AppTypography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.jakarta().copy(fontWeight = FontWeight.ExtraBold),
        displayMedium = t.displayMedium.jakarta().copy(fontWeight = FontWeight.ExtraBold),
        displaySmall = t.displaySmall.jakarta().copy(fontWeight = FontWeight.ExtraBold),
        headlineLarge = t.headlineLarge.jakarta().copy(fontWeight = FontWeight.ExtraBold),
        headlineMedium = t.headlineMedium.jakarta().copy(fontWeight = FontWeight.ExtraBold),
        headlineSmall = t.headlineSmall.jakarta().copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = t.titleLarge.jakarta().copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.jakarta().copy(fontWeight = FontWeight.Bold),
        titleSmall = t.titleSmall.jakarta().copy(fontWeight = FontWeight.Bold),
        bodyLarge = t.bodyLarge.jakarta(),
        bodyMedium = t.bodyMedium.jakarta(),
        bodySmall = t.bodySmall.jakarta(),
        labelLarge = t.labelLarge.jakarta().copy(fontWeight = FontWeight.Bold),
        labelMedium = t.labelMedium.jakarta().copy(fontWeight = FontWeight.SemiBold),
        labelSmall = t.labelSmall.jakarta().copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(Radius.s),
    medium = RoundedCornerShape(Radius.m),
    large = RoundedCornerShape(Radius.l),
)

/**
 * The app in the ISP's own colour. The brand reaches buttons, accents and
 * the hero card; everything else stays neutral, so any brand colour - a
 * garish one included - leaves the app calm and readable.
 */
@Composable
fun MyIspTheme(brandHex: String?, mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val brand = parseHex(brandHex) ?: Fallback

    val background = if (dark) Color(0xFF0B1120) else Color(0xFFF6F7F9)
    val card = if (dark) Color(0xFF131B2E) else Color.White
    val cardHigh = if (dark) Color(0xFF1A2338) else Color(0xFFEEF1F5)
    val text = if (dark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val muted = if (dark) Color(0xFF94A3B8) else Color(0xFF526071)
    val line = if (dark) Color.White.copy(alpha = 0.09f) else Color(0xFF0F172A).copy(alpha = 0.10f)

    // As text and as a fill, the brand must read on the cards it sits on.
    val ui = readableAgainst(brand, card, if (dark) White else Color.Black)
    val heroEnd = gradientEnd(brand)

    val colors = AppColors(
        dark = dark,
        muted = muted,
        line = line,
        brandSoft = ui.copy(alpha = if (dark) 0.16f else 0.12f),
        heroStart = brand,
        heroEnd = heroEnd,
        onHero = onColor(lerp(brand, heroEnd, 0.5f)),
        good = if (dark) Color(0xFF4ADE80) else Color(0xFF15803D),
        warning = if (dark) Color(0xFFFBBF24) else Color(0xFFB45309),
        critical = if (dark) Color(0xFFF87171) else Color(0xFFB91C1C),
        neutral = muted,
        glass = if (dark) Color(0xFF111829).copy(alpha = 0.86f) else Color.White.copy(alpha = 0.88f),
    )

    val scheme = if (dark) {
        darkColorScheme(
            primary = ui, onPrimary = onColor(ui),
            primaryContainer = colors.brandSoft, onPrimaryContainer = text,
            secondaryContainer = colors.brandSoft, onSecondaryContainer = text,
            background = background, onBackground = text,
            surface = background, onSurface = text, onSurfaceVariant = muted,
            surfaceContainerLowest = background, surfaceContainerLow = card, surfaceContainer = card,
            surfaceContainerHigh = cardHigh, surfaceContainerHighest = cardHigh,
            outline = muted, outlineVariant = line,
            error = colors.critical,
        )
    } else {
        lightColorScheme(
            primary = ui, onPrimary = onColor(ui),
            primaryContainer = colors.brandSoft, onPrimaryContainer = text,
            secondaryContainer = colors.brandSoft, onSecondaryContainer = text,
            background = background, onBackground = text,
            surface = background, onSurface = text, onSurfaceVariant = muted,
            surfaceContainerLowest = Color.White, surfaceContainerLow = card, surfaceContainer = card,
            surfaceContainerHigh = cardHigh, surfaceContainerHighest = cardHigh,
            outline = muted, outlineVariant = line,
            error = colors.critical,
        )
    }

    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
