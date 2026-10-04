package run.sparo.myisp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Status colours: state, never the ISP's brand. Same meaning in both themes. */
object StatusColors {
    val good = Color(0xFF16A34A)
    val warning = Color(0xFFD97706)
    val critical = Color(0xFFDC2626)
    val neutral = Color(0xFF64748B)
}

private val Fallback = Color(0xFF3B82F6)

fun parseHex(hex: String?): Color? = runCatching {
    val clean = hex?.trim()?.removePrefix("#") ?: return null
    if (clean.length != 6) return null
    Color(0xFF000000 or clean.toLong(16))
}.getOrNull()

/** Black or white, whichever reads on the ISP's colour - ISPs pick yellows too. */
fun onColor(color: Color): Color = if (color.luminance() > 0.45f) Color(0xFF111827) else Color.White

/**
 * The app in the ISP's own colour. Everything else stays neutral, so a
 * garish brand colour reaches the buttons and accents only.
 */
@Composable
fun MyIspTheme(brandHex: String?, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    // On a dark background a dark brand colour is unreadable as text and
    // muddy as a button, so it is lifted towards white until it reads.
    val brand = (parseHex(brandHex) ?: Fallback).let { if (dark && it.luminance() < 0.3f) lerp(it, Color.White, 0.45f) else it }

    val scheme = if (dark) {
        darkColorScheme(
            primary = brand,
            onPrimary = onColor(brand),
            primaryContainer = brand.copy(alpha = 0.22f),
            onPrimaryContainer = Color.White,
            secondaryContainer = brand.copy(alpha = 0.28f),
            onSecondaryContainer = Color.White,
            background = Color(0xFF0B1120),
            surface = Color(0xFF0B1120),
            surfaceContainer = Color(0xFF131B2E),
            surfaceContainerHigh = Color(0xFF1A2338),
        )
    } else {
        lightColorScheme(
            primary = brand,
            onPrimary = onColor(brand),
            primaryContainer = brand.copy(alpha = 0.12f),
            onPrimaryContainer = Color(0xFF0F172A),
            secondaryContainer = brand.copy(alpha = 0.16f),
            onSecondaryContainer = Color(0xFF0F172A),
            background = Color(0xFFF8FAFC),
            surface = Color(0xFFF8FAFC),
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color(0xFFF1F5F9),
        )
    }

    MaterialTheme(colorScheme = scheme, content = content)
}
