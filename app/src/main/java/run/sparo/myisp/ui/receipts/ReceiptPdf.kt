package run.sparo.myisp.ui.receipts

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import run.sparo.myisp.R
import run.sparo.myisp.data.Payment
import run.sparo.myisp.ui.dateTime
import run.sparo.myisp.ui.kes
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Receipts and statements as PDF, drawn on the phone with Android's own
 * PdfDocument: real text that stays sharp and can be searched, in the app's
 * font and the ISP's colour, with no PDF library in the APK.
 *
 * They name the ISP, never the platform behind it - the same rule as the
 * portals.
 */

/** Who the document is from and for. */
data class PdfParty(
    val isp: String,
    /** The ISP's brand colour, as ARGB. */
    val brand: Int,
    val logo: Bitmap?,
    val contactPhone: String?,
    val customerName: String,
    val username: String,
    val accountRef: String?,
    val appName: String,
)

/** A statement's range, for its heading. */
data class StatementPeriod(val from: Date, val to: Date)

object ReceiptPdf {
    // Points: A5 for one receipt, A4 for a statement.
    private const val A5_W = 420
    private const val A5_H = 595
    private const val A4_W = 595
    private const val A4_H = 842
    private const val MARGIN = 36f

    private val INK = Color.parseColor("#0F172A")
    private val MUTED = Color.parseColor("#64748B")
    private val LINE = Color.parseColor("#E2E8F0")
    private val ZEBRA = Color.parseColor("#F8FAFC")
    private val GOOD = Color.parseColor("#16A34A")

    fun receiptNumber(p: Payment): String = "RCP-" + p.id.toString().padStart(6, '0')

    /** One payment, on an A5 page. */
    fun receipt(context: Context, party: PdfParty, p: Payment, labels: Labels): File {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(A5_W, A5_H, 1).create())
        val c = page.canvas
        val f = Fonts(context)

        var y = header(c, f, party, A5_W, labels.receipt.uppercase(Locale.UK), receiptNumber(p))

        y += 34f
        text(c, labels.amountPaid, MARGIN, y, f.regular, 11f, MUTED)
        y += 38f
        text(c, kes(p.amount), MARGIN, y, f.extraBold, 34f, INK)
        stamp(c, f, labels.paid, A5_W - MARGIN - 92f, y - 44f)

        y += 28f
        val rows = listOfNotNull(
            labels.receiptNo to receiptNumber(p),
            labels.date to dateTime(p.at),
            p.plan?.let { labels.plan to it },
            p.method?.let { labels.method to it.replaceFirstChar(Char::uppercase) },
            p.reference?.takeIf { it.isNotBlank() }?.let { labels.reference to it },
            labels.customer to party.customerName,
            labels.username to party.username,
            party.accountRef?.let { labels.accountNo to it },
        )
        rows.forEach { (k, v) ->
            line(c, MARGIN, y, A5_W - MARGIN)
            y += 22f
            text(c, k, MARGIN, y, f.regular, 11f, MUTED)
            val room = A5_W - MARGIN * 2 - paint(f.regular, 11f, MUTED).measureText(k) - 16f
            textRight(c, ellipsize(v, f.bold, 11f, room), A5_W - MARGIN, y, f.bold, 11f, INK)
            y += 10f
        }
        line(c, MARGIN, y, A5_W - MARGIN)

        footer(c, f, party, labels, A5_W, A5_H, page = null)
        doc.finishPage(page)
        return write(context, doc, "receipt-${receiptNumber(p)}.pdf")
    }

    /** Every payment in [period], on as many A4 pages as it takes. */
    fun statement(context: Context, party: PdfParty, period: StatementPeriod, payments: List<Payment>, labels: Labels): File {
        val doc = PdfDocument()
        val f = Fonts(context)
        val day = SimpleDateFormat("d MMM yyyy", Locale.UK)
        val range = "${day.format(period.from)} – ${day.format(period.to)}"
        val rowH = 24f
        val total = payments.sumOf { it.amount }

        // Columns: date, plan, reference, method, amount (right-aligned).
        val cols = floatArrayOf(MARGIN, MARGIN + 110f, MARGIN + 250f, MARGIN + 380f)
        val right = A4_W - MARGIN

        val firstRows = 20
        val laterRows = 28
        val pages = if (payments.size <= firstRows) 1 else 1 + (payments.size - firstRows + laterRows - 1) / laterRows
        var index = 0

        for (n in 1..pages) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, n).create())
            val c = page.canvas
            var y = header(c, f, party, A4_W, labels.statement.uppercase(Locale.UK), range)

            if (n == 1) {
                // Who it is for, and the totals at a glance.
                y += 30f
                text(c, party.customerName, MARGIN, y, f.bold, 13f, INK)
                textRight(c, labels.generated + " " + day.format(Date()), right, y, f.regular, 10f, MUTED)
                y += 16f
                val who = listOfNotNull(party.username, party.accountRef?.let { labels.accountNo + " " + it }).joinToString("  ·  ")
                text(c, who, MARGIN, y, f.regular, 10f, MUTED)

                y += 22f
                val tileW = (A4_W - MARGIN * 2 - 12f) / 2
                tile(c, f, MARGIN, y, tileW, labels.totalPaid, kes(total), party.brand)
                tile(c, f, MARGIN + tileW + 12f, y, tileW, labels.payments, payments.size.toString(), party.brand)
                y += 66f
            }

            // Table heading.
            y += 18f
            val head = listOf(labels.date, labels.plan, labels.reference, labels.method)
            head.forEachIndexed { i, h -> text(c, h.uppercase(Locale.UK), cols[i], y, f.bold, 8.5f, MUTED) }
            textRight(c, labels.amount.uppercase(Locale.UK), right, y, f.bold, 8.5f, MUTED)
            y += 8f
            line(c, MARGIN, y, right)

            val take = if (n == 1) firstRows else laterRows
            val rows = payments.drop(index).take(take)
            rows.forEachIndexed { i, p ->
                if (i % 2 == 1) fill(c, MARGIN, y, right, y + rowH, ZEBRA)
                val base = y + 16f
                text(c, short(dateTime(p.at)), cols[0] + 4f, base, f.regular, 9.5f, INK)
                text(c, ellipsize(p.plan ?: "—", f.regular, 9.5f, 130f), cols[1], base, f.regular, 9.5f, INK)
                text(c, ellipsize(p.reference ?: "—", f.regular, 9.5f, 120f), cols[2], base, f.regular, 9.5f, MUTED)
                text(c, (p.method ?: "—").replaceFirstChar(Char::uppercase), cols[3], base, f.regular, 9.5f, MUTED)
                textRight(c, kes(p.amount), right - 4f, base, f.bold, 9.5f, INK)
                y += rowH
            }
            index += rows.size

            if (payments.isEmpty()) {
                y += 28f
                text(c, labels.noPayments, MARGIN, y, f.regular, 11f, MUTED)
            }

            if (n == pages) {
                line(c, MARGIN, y, right)
                y += 20f
                text(c, labels.total, MARGIN + 4f, y, f.bold, 11f, INK)
                textRight(c, kes(total), right - 4f, y, f.extraBold, 12f, INK)
            }

            footer(c, f, party, labels, A4_W, A4_H, page = labels.pageOf.format(n, pages))
            doc.finishPage(page)
        }
        val stamp = SimpleDateFormat("yyyyMMdd", Locale.UK)
        return write(context, doc, "statement-${stamp.format(period.from)}-${stamp.format(period.to)}.pdf")
    }

    /** Words on the page, from strings.xml, so they can be translated. */
    data class Labels(
        val receipt: String,
        val statement: String,
        val amountPaid: String,
        val paid: String,
        val receiptNo: String,
        val date: String,
        val plan: String,
        val method: String,
        val reference: String,
        val customer: String,
        val username: String,
        val accountNo: String,
        val amount: String,
        val total: String,
        val totalPaid: String,
        val payments: String,
        val generated: String,
        val noPayments: String,
        val thanks: String,
        val issuedBy: String,
        /** "Page %1$d of %2$d" */
        val pageOf: String,
    )

    // ── Drawing ─────────────────────────────────────────────────────────────

    private class Fonts(context: Context) {
        val regular: Typeface = ResourcesCompat.getFont(context, R.font.jakarta_regular) ?: Typeface.DEFAULT
        val bold: Typeface = ResourcesCompat.getFont(context, R.font.jakarta_bold) ?: Typeface.DEFAULT_BOLD
        val extraBold: Typeface = ResourcesCompat.getFont(context, R.font.jakarta_extrabold) ?: Typeface.DEFAULT_BOLD
    }

    /** The ISP's band across the top: logo, name, document kind. Returns the y below it. */
    private fun header(c: Canvas, f: Fonts, party: PdfParty, width: Int, kind: String, sub: String): Float {
        val h = 96f
        fill(c, 0f, 0f, width.toFloat(), h, party.brand)
        val on = onColour(party.brand)
        var x = MARGIN
        val logo = party.logo
        if (logo != null) {
            val size = 40f
            val dst = RectF(x, (h - size) / 2, x + size, (h + size) / 2)
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            c.drawRoundRect(RectF(dst.left - 3, dst.top - 3, dst.right + 3, dst.bottom + 3), 10f, 10f, bg)
            c.drawBitmap(logo, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            x += size + 14f
        }
        text(c, ellipsize(party.isp, f.extraBold, 18f, width - x - MARGIN - 130f), x, h / 2 + 6f, f.extraBold, 18f, on)
        textRight(c, kind, width - MARGIN, h / 2 - 2f, f.bold, 10f, withAlpha(on, 0.85f), spacing = 0.12f)
        textRight(c, sub, width - MARGIN, h / 2 + 14f, f.regular, 9.5f, withAlpha(on, 0.75f))
        return h
    }

    /** A rubber stamp: outlined, tilted, faint - there, not shouting. */
    private fun stamp(c: Canvas, f: Fonts, word: String, x: Float, y: Float) {
        c.save()
        c.rotate(-12f, x + 46f, y + 18f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
            color = withAlpha(GOOD, 0.55f)
        }
        c.drawRoundRect(RectF(x, y, x + 92f, y + 36f), 8f, 8f, p)
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = f.extraBold
            textSize = 17f
            color = withAlpha(GOOD, 0.65f)
            letterSpacing = 0.18f
            textAlign = Paint.Align.CENTER
        }
        c.drawText(word.uppercase(Locale.UK), x + 46f, y + 24f, t)
        c.restore()
    }

    private fun tile(c: Canvas, f: Fonts, x: Float, y: Float, w: Float, label: String, value: String, brand: Int) {
        val r = RectF(x, y, x + w, y + 58f)
        c.drawRoundRect(r, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(brand, 0.08f) })
        text(c, label, x + 14f, y + 22f, f.regular, 9.5f, MUTED)
        text(c, value, x + 14f, y + 44f, f.extraBold, 17f, INK)
    }

    private fun footer(c: Canvas, f: Fonts, party: PdfParty, labels: Labels, width: Int, height: Int, page: String?) {
        val y = height - MARGIN
        line(c, MARGIN, y - 30f, width - MARGIN)
        val contact = listOfNotNull(labels.thanks, party.contactPhone?.let { "${party.isp} · $it" }).joinToString("  ")
        text(c, ellipsize(contact, f.regular, 9f, width - MARGIN * 2 - 70f), MARGIN, y - 12f, f.regular, 9f, MUTED)
        text(c, labels.issuedBy.format(party.appName), MARGIN, y + 2f, f.regular, 8f, MUTED)
        page?.let { textRight(c, it, width - MARGIN, y - 12f, f.regular, 9f, MUTED) }
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, face: Typeface, size: Float, colour: Int) {
        c.drawText(s, x, y, paint(face, size, colour))
    }

    private fun textRight(c: Canvas, s: String, x: Float, y: Float, face: Typeface, size: Float, colour: Int, spacing: Float = 0f) {
        val p = paint(face, size, colour).apply {
            textAlign = Paint.Align.RIGHT
            letterSpacing = spacing
        }
        c.drawText(s, x, y, p)
    }

    private fun paint(face: Typeface, size: Float, colour: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textSize = size
        color = colour
    }

    private fun line(c: Canvas, x1: Float, y: Float, x2: Float) {
        c.drawLine(x1, y, x2, y, Paint().apply { color = LINE; strokeWidth = 0.8f })
    }

    private fun fill(c: Canvas, l: Float, t: Float, r: Float, b: Float, colour: Int) {
        c.drawRect(l, t, r, b, Paint().apply { color = colour })
    }

    private fun ellipsize(s: String, face: Typeface, size: Float, max: Float): String {
        val p = paint(face, size, INK)
        if (p.measureText(s) <= max) return s
        var end = s.length
        while (end > 1 && p.measureText(s.take(end) + "…") > max) end--
        return s.take(end) + "…"
    }

    /** "3 Oct 2026, 23:59" -> "3 Oct 2026" for a table cell. */
    private fun short(s: String): String = s.substringBefore(',')

    private fun withAlpha(colour: Int, alpha: Float): Int =
        Color.argb((alpha * 255).toInt(), Color.red(colour), Color.green(colour), Color.blue(colour))

    /** Black or white, whichever reads on the brand colour. */
    private fun onColour(brand: Int): Int {
        val l = (0.299 * Color.red(brand) + 0.587 * Color.green(brand) + 0.114 * Color.blue(brand)) / 255
        return if (l > 0.6) INK else Color.WHITE
    }

    private fun write(context: Context, doc: PdfDocument, name: String): File {
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        // Only the newest few: these are for sharing, not keeping.
        dir.listFiles { f -> f.extension == "pdf" }?.sortedByDescending { it.lastModified() }?.drop(8)?.forEach(File::delete)
        val file = File(dir, name)
        file.outputStream().use(doc::writeTo)
        doc.close()
        return file
    }

    // ── Handing it over ─────────────────────────────────────────────────────

    fun share(context: Context, file: File, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".receipts", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle))
    }

    /** Into Downloads, where the Files app shows it. Android 10 and later: no permission needed. */
    val canSave: Boolean get() = Build.VERSION.SDK_INT >= 29

    fun saveToDownloads(context: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: return false
            true
        }.getOrDefault(false)
    }
}
