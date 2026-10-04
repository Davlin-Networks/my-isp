package run.sparo.myisp.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import run.sparo.myisp.AppContainer
import run.sparo.myisp.data.Day
import run.sparo.myisp.ui.bytes
import run.sparo.myisp.ui.theme.StatusColors

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun Banner(text: String, tone: String = "info", modifier: Modifier = Modifier) {
    val color = when (tone) {
        "urgent", "error" -> StatusColors.critical
        "maintenance", "warning" -> StatusColors.warning
        else -> MaterialTheme.colorScheme.primary
    }
    Text(
        text,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** A state - Active, Expired - labelled, never told by colour alone. */
@Composable
fun StatusChip(status: String) {
    val (label, color) = when (status) {
        "active" -> "Active" to StatusColors.good
        "expired" -> "Expired" to StatusColors.critical
        "suspended" -> "Suspended" to StatusColors.critical
        "inactive" -> "Inactive" to StatusColors.neutral
        "online" -> "Online" to StatusColors.good
        "offline" -> "Offline" to StatusColors.neutral
        "open" -> "Open" to StatusColors.warning
        "answered" -> "Answered" to StatusColors.good
        "closed" -> "Closed" to StatusColors.neutral
        else -> status.replaceFirstChar { it.uppercase() } to StatusColors.neutral
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** An error in words the customer can act on, with a way to try again. */
@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        if (onRetry != null) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) { Text("Try again") }
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true, busy: Boolean = false, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text(text, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Progress towards the next fair-usage step, drawn on a canvas: no chart
 * library in the APK for one ring. The words below it carry the meaning;
 * the colour only reinforces it.
 */
@Composable
fun UsageRing(used: Long, limit: Long?, throttled: Boolean, size: Dp = 200.dp) {
    val fraction = if (limit != null && limit > 0) (used.toFloat() / limit).coerceIn(0f, 1f) else 0f
    val animated by animateFloatAsState(fraction, tween(900), label = "ring")
    val color = when {
        throttled -> StatusColors.critical
        fraction > 0.85f -> StatusColors.warning
        else -> MaterialTheme.colorScheme.primary
    }
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val label = if (limit != null) "${bytes(used)} of ${bytes(limit)} used" else "${bytes(used)} used"

    Box(Modifier.size(size).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(track, -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (limit != null) {
                drawArc(color, -90f, animated * 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(bytes(used), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(if (limit != null) "of ${bytes(limit)}" else "used", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 30 days of usage as bars, oldest on the left. */
@Composable
fun DailyBars(days: List<Day>, modifier: Modifier = Modifier) {
    val totals = days.map { it.down + it.up }
    val max = (totals.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val color = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)

    Canvas(modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = "Daily usage, last ${days.size} days" }) {
        if (totals.isEmpty()) return@Canvas
        val gap = 3.dp.toPx()
        val barWidth = (size.width - gap * (totals.size - 1)) / totals.size
        totals.forEachIndexed { i, total ->
            val h = (total.toFloat() / max) * size.height
            val x = i * (barWidth + gap)
            drawRect(empty, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Size(barWidth, size.height))
            if (h > 0f) {
                drawRect(color, androidx.compose.ui.geometry.Offset(x, size.height - h), androidx.compose.ui.geometry.Size(barWidth, h))
            }
        }
    }
}

/**
 * The ISP's logo, fetched through the app's own HTTP client and cache - no
 * image library in the APK for one picture. Nothing shows if it fails.
 */
@Composable
fun RemoteLogo(url: String?, app: AppContainer, size: Dp, modifier: Modifier = Modifier) {
    if (url.isNullOrBlank()) return
    val image by produceState<ImageBitmap?>(app.images.get(url)?.asImageBitmap(), url) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                app.http.newCall(Request.Builder().url(url).build()).execute().use { res ->
                    if (!res.isSuccessful) return@use null
                    res.body?.byteStream()?.let(BitmapFactory::decodeStream)?.also { app.images.put(url, it) }
                }
            }.getOrNull()?.asImageBitmap()
        }
    }
    image?.let {
        Image(it, contentDescription = null, modifier = modifier.size(size).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit)
    }
}

@Composable
fun Gap(height: Dp) = androidx.compose.foundation.layout.Spacer(Modifier.height(height))

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

