package run.sparo.myisp.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/*
 * The app's icons: Lucide's line style (ISC licence, licenses/), the same
 * family as the Sparo web app, drawn from path data - only the icons used
 * are in the APK, a few hundred bytes each. Built on first use.
 *
 * Use with Material's Icon(), which tints them with the content colour.
 */
object AppIcons {
    val Home by lazy { icon("home", "M3 10.5 12 3l9 7.5", "M5 9.5V21h14V9.5") }
    val Usage by lazy { icon("usage", "M5 20V11", "M12 20V4", "M19 20v-6") }
    val Receipt by lazy { icon("receipt", "M6 3h12v18l-3-2-3 2-3-2-3 2V3z", "M9 8h6", "M9 12h6") }
    val Help by lazy { icon("help", "M21 12a8.5 8.5 0 0 1-12.4 7.6L3 21l1.4-5.4A8.5 8.5 0 1 1 21 12z") }
    val Settings by lazy {
        icon("settings", "M4 6h9", "M17 6h3", "M4 12h3", "M11 12h9", "M4 18h11", "M19 18h1",
            circle(15f, 6f, 2f), circle(9f, 12f, 2f), circle(17f, 18f, 2f))
    }
    val Back by lazy { icon("back", "M19 12H5", "M12 19l-7-7 7-7") }
    val ChevronRight by lazy { icon("chevron", "M9 6l6 6-6 6") }
    val Copy by lazy { icon("copy", rect(9f, 9f, 11f, 11f, 2f), "M5 15V6a2 2 0 0 1 2-2h9") }
    val Alert by lazy { icon("alert", "M12 3 2 20h20L12 3z", "M12 10v4", "M12 17h.01") }
    val Info by lazy { icon("info", circle(12f, 12f, 10f), "M12 16v-4", "M12 8h.01") }
    val Check by lazy { icon("check", "M20 6 9 17l-5-5") }
    val Lock by lazy { icon("lock", rect(5f, 11f, 14f, 10f, 2f), "M8 11V7a4 4 0 0 1 8 0v4") }
    val Share by lazy { icon("share", "M12 3v13", "M7 8l5-5 5 5", "M5 14v5a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-5") }
    val Eye by lazy { icon("eye", "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z", circle(12f, 12f, 3f)) }
    val EyeOff by lazy {
        icon("eye-off", "M10.7 5.1A10 10 0 0 1 12 5c6.5 0 10 7 10 7a13 13 0 0 1-1.7 2.7",
            "M6.6 6.6A13.5 13.5 0 0 0 2 12s3.5 7 10 7a9.7 9.7 0 0 0 5.4-1.6", "M14.1 14.1a3 3 0 1 1-4.2-4.2", "M2 2l20 20")
    }
    val Phone by lazy {
        icon("phone", "M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1.9.4 1.8.7 2.7a2 2 0 0 1-.5 2.1L8 9.8a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.7.7a2 2 0 0 1 1.7 2z")
    }
    val Wifi by lazy { icon("wifi", "M5 12.5a10 10 0 0 1 14 0", "M8.5 16a5 5 0 0 1 7 0", "M2 9a15 15 0 0 1 20 0", "M12 20h.01") }
    val Wallet by lazy {
        icon("wallet", "M19 7V5a2 2 0 0 0-2-2H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1",
            "M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4")
    }
    val Refresh by lazy { icon("refresh", "M3 12a9 9 0 0 1 15-6.7L21 8", "M21 3v5h-5", "M21 12a9 9 0 0 1-15 6.7L3 16", "M3 21v-5h5") }
    val SignOut by lazy { icon("sign-out", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4", "M16 17l5-5-5-5", "M21 12H9") }
    val Language by lazy {
        icon("language", circle(12f, 12f, 10f), "M2 12h20",
            "M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z")
    }
    val Theme by lazy { icon("theme", "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9z") }
    val Key by lazy { icon("key", circle(7.5f, 15.5f, 5.5f), "M21 2l-9.6 9.6", "M15.5 7.5l3 3L22 7l-3-3") }
    val Provider by lazy { icon("provider", "M3 21h18", "M5 21V7l7-4 7 4v14", "M9 21v-6h6v6") }
    val Plus by lazy { icon("plus", "M12 5v14", "M5 12h14") }
    val Close by lazy { icon("close", "M18 6 6 18", "M6 6l12 12") }
    val Diamond by lazy { icon("diamond", "M12 2.5 2.5 12 12 21.5 21.5 12z", "M12 8l-4 4 4 4 4-4z") }
    /** WhatsApp's own mark (Simple Icons, CC0) - filled, and meant to stay in its green. */
    val WhatsApp by lazy { filled("whatsapp", "M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413Z") }
    val Send by lazy { icon("send", "M22 2 11 13", "M22 2l-7 20-4-9-9-4 20-7z") }
}

/** A circle as path data: two half-arcs. */
private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

/** A rounded rectangle as path data. */
private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

/** A solid shape rather than a line: for brand marks. */
private fun filled(name: String, d: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black))
    }.build()

private fun icon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
