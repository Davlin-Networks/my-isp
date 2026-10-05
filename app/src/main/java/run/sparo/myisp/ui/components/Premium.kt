package run.sparo.myisp.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import run.sparo.myisp.R
import java.io.File
import kotlin.math.sin
import kotlin.random.Random

/*
 * The app's signature pieces, drawn on Canvas: no chart, animation or image
 * library in the APK for any of them.
 * Animations follow the phone's animation scale, so "remove animations"
 * turns them off.
 */

/** A ring that fills to [fraction], animating from wherever it was. */
@Composable
fun ProgressRing(
    fraction: Float,
    color: Color,
    track: Color,
    size: Dp,
    stroke: Dp,
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900), label = "ring")
    Box(modifier.size(size).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val inset = w / 2
            val arc = Size(this.size.width - w, this.size.height - w)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
            if (animated > 0f) {
                drawArc(color, -90f, animated * 360f, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** "Online": a dot with a ring breathing out of it. */
@Composable
fun PulseDot(color: Color, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val t by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "t")
    Canvas(modifier.size(40.dp)) {
        val r = 6.dp.toPx()
        drawCircle(color.copy(alpha = 0.5f * (1f - t)), radius = r * (1f + 1.6f * t))
        drawCircle(color.copy(alpha = 0.18f), radius = r + 4.dp.toPx())
        drawCircle(color, radius = r)
    }
}

/** About a second of confetti from the top edge, once. */
@Composable
fun Confetti(colors: List<Color>, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(1400, easing = LinearEasing)) }
    val pieces = remember {
        val rnd = Random(7)
        List(36) {
            floatArrayOf(rnd.nextFloat(), rnd.nextFloat() * 0.25f, 0.6f + rnd.nextFloat() * 0.6f, rnd.nextFloat() * 360f, (rnd.nextFloat() - 0.5f) * 0.35f)
        }
    }
    Canvas(modifier) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        pieces.forEachIndexed { i, (x, delay, speed, spin, drift) ->
            val t = ((p - delay) / (1f - delay)).coerceIn(0f, 1f)
            if (t <= 0f) return@forEachIndexed
            val cx = (x + drift * t + 0.02f * sin(t * 12f + i)) * size.width
            val cy = -20f + t * speed * size.height
            rotate(spin + t * 540f, Offset(cx, cy)) {
                drawRoundRect(
                    colors[i % colors.size].copy(alpha = 1f - t * t),
                    Offset(cx - 4.dp.toPx(), cy - 6.dp.toPx()),
                    Size(8.dp.toPx(), 12.dp.toPx()),
                    CornerRadius(2.dp.toPx()),
                )
            }
        }
    }
}

/**
 * Bars a finger can scrub: tap or drag across, and [onSelect] gets the
 * day under it. The selected bar is the brand colour, the rest a tint.
 */
@Composable
fun BarChart(
    values: List<Long>,
    selected: Int,
    onSelect: (Int) -> Unit,
    color: Color,
    tint: Color,
    label: String,
    modifier: Modifier = Modifier,
) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    fun indexAt(x: Float, width: Float) = ((x / width) * values.size).toInt().coerceIn(0, values.lastIndex)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(128.dp)
            .semantics { contentDescription = label }
            .pointerInput(values.size) { detectTapGestures { onSelect(indexAt(it.x, size.width.toFloat())) } }
            .pointerInput(values.size) {
                detectHorizontalDragGestures { change, _ -> onSelect(indexAt(change.position.x, size.width.toFloat())) }
            },
    ) {
        if (values.isEmpty()) return@Canvas
        val gap = 3.dp.toPx()
        val bar = (size.width - gap * (values.size - 1)) / values.size
        val radius = CornerRadius(minOf(bar / 2, 4.dp.toPx()))
        values.forEachIndexed { i, v ->
            val h = maxOf(4.dp.toPx(), (v.toFloat() / max) * size.height)
            drawRoundRect(if (i == selected) color else tint, Offset(i * (bar + gap), size.height - h), Size(bar, h), radius)
        }
    }
}

/** The confirm buzz after a payment goes through; a long-press tick on older phones. */
fun confirmHaptic(view: View) {
    view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
}

/** What a receipt image shows - and the same lines as plain text for the share. */
data class ReceiptData(val isp: String, val title: String, val amount: String, val rows: List<Pair<String, String>>, val brand: Color)

/**
 * Shares a receipt as an image (with the text alongside), drawn here in
 * the app's own font - in practice it goes to WhatsApp. Written to the
 * cache and handed over through a FileProvider; nothing is kept.
 */
fun shareReceipt(context: Context, receipt: ReceiptData, chooserTitle: String) {
    val w = 1080
    val h = 760 + receipt.rows.size * 92
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val bold = ResourcesCompat.getFont(context, R.font.jakarta_extrabold)
    val regular = ResourcesCompat.getFont(context, R.font.jakarta_regular)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    c.drawColor(android.graphics.Color.WHITE)
    paint.color = receipt.brand.toArgb()
    c.drawRect(0f, 0f, w.toFloat(), 220f, paint)
    paint.color = if (receipt.brand.red * 0.299f + receipt.brand.green * 0.587f + receipt.brand.blue * 0.114f > 0.6f) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    paint.typeface = bold; paint.textSize = 64f
    c.drawText(receipt.isp, 72f, 140f, paint)

    paint.color = 0xFF526071.toInt(); paint.typeface = regular; paint.textSize = 40f
    c.drawText(receipt.title, 72f, 330f, paint)
    paint.color = 0xFF0F172A.toInt(); paint.typeface = bold; paint.textSize = 112f
    c.drawText(receipt.amount, 72f, 470f, paint)

    var y = 600f
    receipt.rows.forEach { (k, v) ->
        paint.color = 0xFFE5E7EB.toInt(); c.drawRect(72f, y - 64f, w - 72f, y - 62f, paint)
        paint.color = 0xFF526071.toInt(); paint.typeface = regular; paint.textSize = 38f
        c.drawText(k, 72f, y, paint)
        paint.color = 0xFF0F172A.toInt(); paint.typeface = bold
        c.drawText(v, w - 72f - paint.measureText(v), y, paint)
        y += 92f
    }
    paint.color = 0xFF94A3B8.toInt(); paint.typeface = regular; paint.textSize = 30f
    c.drawText("My ISP", 72f, h - 56f, paint)

    val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
    val file = File(dir, "receipt.png")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bmp.recycle()

    val uri = FileProvider.getUriForFile(context, context.packageName + ".receipts", file)
    val text = buildString {
        appendLine(receipt.isp + " - " + receipt.title)
        appendLine(receipt.amount)
        receipt.rows.forEach { (k, v) -> appendLine("$k: $v") }
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text.trim())
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}

