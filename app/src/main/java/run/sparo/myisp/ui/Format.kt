package run.sparo.myisp.ui

import android.text.format.DateUtils
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * How numbers and dates read. Unambiguous dates - "3 Oct 2026, 23:59", never
 * 03/10/2026 - as the web portal writes them. java.text rather than java.time:
 * minSdk 24, and desugaring would add to the APK for this alone.
 */

private val number = NumberFormat.getIntegerInstance(Locale.UK)

fun kes(amount: Int): String = "KES ${number.format(amount)}"

fun bytes(value: Long): String {
    val v = value.toDouble()
    return when {
        v >= 1e12 -> "%.2f TB".format(Locale.UK, v / 1e12)
        v >= 1e9 -> "%.1f GB".format(Locale.UK, v / 1e9)
        v >= 1e6 -> "%.0f MB".format(Locale.UK, v / 1e6)
        v >= 1e3 -> "%.0f KB".format(Locale.UK, v / 1e3)
        else -> "$value B"
    }
}

/** ISO 8601 from the API, e.g. 2026-10-04T17:38:14+00:00. */
fun parseIso(value: String?): Date? {
    if (value.isNullOrBlank()) return null
    return runCatching { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(value) }.getOrNull()
        ?: runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value) }.getOrNull()
}

fun dateTime(value: String?): String =
    parseIso(value)?.let { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.UK).format(it) } ?: "—"

fun date(value: String?): String =
    parseIso(value)?.let { SimpleDateFormat("d MMM yyyy", Locale.UK).format(it) } ?: "—"

fun dayLabel(value: String?): String =
    parseIso(value)?.let { SimpleDateFormat("EEE d MMM", Locale.UK).format(it) } ?: "—"

/** "5 minutes ago", "yesterday". */
fun ago(millis: Long): String =
    if (System.currentTimeMillis() - millis < 60_000) "just now"
    else DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

fun agoIso(value: String?): String = parseIso(value)?.let { ago(it.time) } ?: "—"

fun duration(fromIso: String?, toIso: String?): String {
    val from = parseIso(fromIso)?.time ?: return "—"
    val to = parseIso(toIso)?.time ?: System.currentTimeMillis()
    val minutes = ((to - from) / 60_000).coerceAtLeast(0)
    return when {
        minutes >= 1440 -> "${minutes / 1440} d ${(minutes % 1440) / 60} h"
        minutes >= 60 -> "${minutes / 60} h ${minutes % 60} min"
        else -> "$minutes min"
    }
}

fun validity(days: Double): String = when {
    days >= 1 && days % 1.0 == 0.0 -> if (days == 1.0) "1 day" else "${days.toInt()} days"
    days * 24 >= 1 -> "${(days * 24).toInt()} hours"
    else -> "${(days * 1440).toInt()} minutes"
}
