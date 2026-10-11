package run.sparo.myisp.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import run.sparo.myisp.AppContainer
import run.sparo.myisp.R
import run.sparo.myisp.data.Account
import run.sparo.myisp.data.ApiException
import run.sparo.myisp.data.Payment
import run.sparo.myisp.data.Provider
import run.sparo.myisp.ui.components.ErrorBox
import run.sparo.myisp.ui.components.Loading
import run.sparo.myisp.ui.components.Muted
import run.sparo.myisp.ui.components.PrimaryButton
import run.sparo.myisp.ui.components.ReceiptData
import run.sparo.myisp.ui.components.shareReceipt
import run.sparo.myisp.ui.dateTime
import run.sparo.myisp.ui.kes
import run.sparo.myisp.ui.parseIso
import run.sparo.myisp.ui.receipts.PdfParty
import run.sparo.myisp.ui.receipts.ReceiptPdf
import run.sparo.myisp.ui.receipts.StatementPeriod
import run.sparo.myisp.ui.theme.AppIcons
import run.sparo.myisp.ui.theme.AppTheme
import run.sparo.myisp.ui.theme.Radius
import run.sparo.myisp.ui.theme.Space
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/*
 * Payments: the list, each payment's receipt, and statements - as PDFs drawn
 * on the phone (ui/receipts/ReceiptPdf.kt), shared or saved to Downloads.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsTab(app: AppContainer, provider: Provider, account: Account?) {
    val vm: PaymentsVm = viewModel { PaymentsVm(app) }
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    var receipt by remember { mutableStateOf<Payment?>(null) }
    var statement by remember { mutableStateOf(false) }
    val month = remember { SimpleDateFormat("MMMM yyyy", Locale.UK) }

    PullToRefreshBox(isRefreshing = state.loading && state.loaded, onRefresh = vm::load, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "title") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.payments_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    if (state.payments.isNotEmpty()) {
                        Row(
                            Modifier
                                .clip(CircleShape)
                                .background(colors.brandSoft)
                                .clickable(role = Role.Button) { statement = true }
                                .heightIn(min = 40.dp)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(AppIcons.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text(stringResource(R.string.statement_button), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            when {
                !state.loaded && state.error != null -> item(key = "error") { ErrorBox(state.error!!, vm::load) }
                !state.loaded -> item(key = "loading") { Loading() }
                state.payments.isEmpty() -> item(key = "empty") { Muted(stringResource(R.string.payments_none), Modifier.padding(vertical = 24.dp)) }
                else -> {
                    state.payments.groupBy { parseIso(it.at)?.let(month::format) ?: "—" }.forEach { (m, rows) ->
                        item(key = "m-$m") {
                            Row(Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(m, style = MaterialTheme.typography.labelLarge, color = colors.muted, modifier = Modifier.semantics { heading() })
                                Text(kes(rows.sumOf { it.amount }), style = MaterialTheme.typography.labelLarge, color = colors.muted)
                            }
                        }
                        items(rows, key = { "p-${it.id}" }) { p -> PaymentRow(p) { receipt = p } }
                    }
                    if (state.nextBefore != null) {
                        item(key = "older") {
                            TextButton(onClick = vm::more, enabled = !state.loading, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                                Text(stringResource(R.string.payments_older))
                            }
                        }
                    }
                }
            }
        }
    }

    receipt?.let { p -> ReceiptSheet(p, provider, account, app, onClose = { receipt = null }) }
    if (statement) StatementSheet(provider, account, app, onClose = { statement = false })
}

/** How it was paid, as an icon: the wallet, M-Pesa, or by hand. */
private fun methodIcon(method: String?): ImageVector = when (method?.lowercase()) {
    "wallet" -> AppIcons.Wallet
    "mpesa", "m-pesa", "stk", "c2b", "paybill", "till" -> AppIcons.Phone
    else -> AppIcons.Receipt
}

@Composable
private fun PaymentRow(p: Payment, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(colors.brandSoft), contentAlignment = Alignment.Center) {
            Icon(methodIcon(p.method), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(p.plan ?: stringResource(R.string.receipt_title), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                dateTime(p.at) + (p.method?.let { " · " + it.replaceFirstChar(Char::uppercase) } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(kes(p.amount), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
            Text(stringResource(R.string.receipt_paid_word), style = MaterialTheme.typography.labelSmall, color = colors.good, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** The receipt as it will print: the ISP's band, the amount, the details - and PDF, image or save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptSheet(p: Payment, provider: Provider, account: Account?, app: AppContainer, onClose: () -> Unit) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()
    val labels = pdfLabels()
    var busy by remember { mutableStateOf(false) }
    val rows = listOfNotNull(
        stringResource(R.string.receipt_number) to ReceiptPdf.receiptNumber(p),
        p.plan?.let { stringResource(R.string.receipt_plan) to it },
        stringResource(R.string.receipt_paid) to dateTime(p.at),
        p.method?.let { stringResource(R.string.receipt_method) to it.replaceFirstChar(Char::uppercase) },
        p.reference?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.receipt_reference) to it },
    )
    val title = stringResource(R.string.receipt_title)
    val shareTitle = stringResource(R.string.share_receipt)
    val saved = stringResource(R.string.pdf_saved)
    val brand = colors.heroStart

    fun pdf(then: (File) -> Unit) {
        busy = true
        scope.launch {
            val file = withContext(Dispatchers.IO) { ReceiptPdf.receipt(context, party(app, provider, account, brand.toArgb()), p, labels) }
            busy = false
            then(file)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(Space.l)) {
            // A paper receipt: white in both themes, as it will print.
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.l)).background(Color.White)) {
                Row(
                    Modifier.fillMaxWidth().background(colors.hero).padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(provider.name, color = colors.onHero, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(title.uppercase(), color = colors.onHero.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                }
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(kes(p.amount), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A), modifier = Modifier.weight(1f))
                        Box(
                            Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF16A34A).copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(stringResource(R.string.receipt_paid_word).uppercase(), color = Color(0xFF15803D), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                        }
                    }
                    rows.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                            Text(k, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF64748B))
                            Text(
                                v,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            PrimaryButton(stringResource(R.string.pdf_share), { pdf { ReceiptPdf.share(context, it, shareTitle) } }, busy = busy)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                OutlinedButton(
                    onClick = { shareReceipt(context, ReceiptData(provider.name, title, kes(p.amount), rows, brand), shareTitle) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(AppIcons.Share, null, modifier = Modifier.size(16.dp))
                    Text("  " + stringResource(R.string.pdf_share_image), fontWeight = FontWeight.SemiBold)
                }
                if (ReceiptPdf.canSave) {
                    OutlinedButton(
                        onClick = {
                            pdf {
                                val ok = ReceiptPdf.saveToDownloads(context, it)
                                if (ok) Toast.makeText(context, saved, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(AppIcons.Download, null, modifier = Modifier.size(16.dp))
                        Text("  " + stringResource(R.string.pdf_save), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private enum class Range { Month, Quarter, Year, Custom }

/** A statement for a period: pick it, then share or save the PDF. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatementSheet(provider: Provider, account: Account?, app: AppContainer, onClose: () -> Unit) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()
    val labels = pdfLabels()
    val brand = colors.heroStart.toArgb()
    val day = remember { SimpleDateFormat("d MMM yyyy", Locale.UK) }
    var range by remember { mutableStateOf(Range.Month) }
    var custom by remember { mutableStateOf<StatementPeriod?>(null) }
    var picking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val shareTitle = stringResource(R.string.statement_share_title)
    val saved = stringResource(R.string.pdf_saved)

    val period = when (range) {
        Range.Custom -> custom
        else -> periodFor(range)
    }

    fun build(then: (File) -> Unit) {
        val chosen = period ?: return
        busy = true
        error = null
        scope.launch {
            try {
                val payments = paymentsIn(app, chosen)
                val file = withContext(Dispatchers.IO) { ReceiptPdf.statement(context, party(app, provider, account, brand), chosen, payments, labels) }
                then(file)
            } catch (e: ApiException) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(Space.l)) {
            Text(stringResource(R.string.statement_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.statement_body, provider.name), style = MaterialTheme.typography.bodyMedium, color = colors.muted)

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Range.entries.forEach { r ->
                    FilterChip(
                        selected = range == r,
                        onClick = {
                            range = r
                            if (r == Range.Custom) picking = true
                        },
                        label = {
                            Text(
                                stringResource(
                                    when (r) {
                                        Range.Month -> R.string.statement_this_month
                                        Range.Quarter -> R.string.statement_three_months
                                        Range.Year -> R.string.statement_this_year
                                        Range.Custom -> R.string.statement_custom
                                    },
                                ),
                            )
                        },
                    )
                }
            }

            period?.let {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.m),
                ) {
                    Icon(AppIcons.Receipt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("${day.format(it.from)} – ${day.format(it.to)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            error?.let { Text(it, color = colors.critical, style = MaterialTheme.typography.bodySmall) }

            PrimaryButton(stringResource(R.string.pdf_share), { build { ReceiptPdf.share(context, it, shareTitle) } }, enabled = period != null, busy = busy)
            if (ReceiptPdf.canSave) {
                OutlinedButton(
                    onClick = {
                        build {
                            if (ReceiptPdf.saveToDownloads(context, it)) Toast.makeText(context, saved, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = period != null && !busy,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(AppIcons.Download, null, modifier = Modifier.size(16.dp))
                    Text("  " + stringResource(R.string.pdf_save), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (picking) {
        val picker = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val from = picker.selectedStartDateMillis
                        val to = picker.selectedEndDateMillis ?: from
                        if (from != null && to != null) custom = StatementPeriod(startOfDay(Date(from)), endOfDay(Date(to)))
                        picking = false
                    },
                    enabled = picker.selectedStartDateMillis != null,
                ) { Text(stringResource(R.string.statement_use_dates)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.update_close)) } },
        ) {
            DateRangePicker(state = picker, modifier = Modifier.heightIn(max = 520.dp))
        }
    }
}

@Composable
private fun pdfLabels() = ReceiptPdf.Labels(
    receipt = stringResource(R.string.receipt_title),
    statement = stringResource(R.string.statement_title),
    amountPaid = stringResource(R.string.pdf_amount_paid),
    paid = stringResource(R.string.receipt_paid_word),
    receiptNo = stringResource(R.string.receipt_number),
    date = stringResource(R.string.pdf_date),
    plan = stringResource(R.string.receipt_plan),
    method = stringResource(R.string.receipt_method),
    reference = stringResource(R.string.receipt_reference),
    customer = stringResource(R.string.pdf_customer),
    username = stringResource(R.string.pdf_username),
    accountNo = stringResource(R.string.pdf_account_no),
    amount = stringResource(R.string.pdf_amount),
    total = stringResource(R.string.pdf_total),
    totalPaid = stringResource(R.string.pdf_total_paid),
    payments = stringResource(R.string.payments_title),
    generated = stringResource(R.string.pdf_generated),
    noPayments = stringResource(R.string.pdf_no_payments),
    thanks = stringResource(R.string.pdf_thanks),
    issuedBy = stringResource(R.string.pdf_issued_by),
    pageOf = stringResource(R.string.pdf_page_of),
)

/** Who the document is from and for; the logo from the cache, or fetched once. */
private suspend fun party(app: AppContainer, provider: Provider, account: Account?, brand: Int): PdfParty {
    val url = provider.branding.logoUrl
    val logo: Bitmap? = url?.let {
        app.images.get(it) ?: withContext(Dispatchers.IO) {
            runCatching {
                app.http.newCall(Request.Builder().url(it).build()).execute().use { res ->
                    if (res.isSuccessful) res.body?.byteStream()?.let(BitmapFactory::decodeStream) else null
                }
            }.getOrNull()
        }
    }
    return PdfParty(
        isp = provider.name,
        brand = brand,
        logo = logo,
        contactPhone = provider.branding.contactPhone,
        customerName = account?.name?.takeIf { it.isNotBlank() } ?: account?.username.orEmpty(),
        username = account?.username.orEmpty(),
        accountRef = account?.accountRef,
        appName = app.appName,
    )
}

/** Every payment in [period]: pages back until one is older than its start. */
private suspend fun paymentsIn(app: AppContainer, period: StatementPeriod): List<Payment> {
    val found = mutableListOf<Payment>()
    var before: Long? = null
    repeat(60) {
        val page = app.api.payments(before)
        page.payments.forEach { p ->
            val at = parseIso(p.at) ?: return@forEach
            if (!at.before(period.from) && !at.after(period.to)) found += p
        }
        val oldest = page.payments.lastOrNull()?.let { parseIso(it.at) }
        if (page.nextBefore == null || oldest == null || oldest.before(period.from)) return found
        before = page.nextBefore
    }
    return found
}

private fun periodFor(range: Range): StatementPeriod {
    val now = Date()
    val c = Calendar.getInstance()
    when (range) {
        Range.Month -> c.set(Calendar.DAY_OF_MONTH, 1)
        Range.Quarter -> {
            c.add(Calendar.MONTH, -2)
            c.set(Calendar.DAY_OF_MONTH, 1)
        }
        Range.Year -> c.set(Calendar.DAY_OF_YEAR, 1)
        Range.Custom -> Unit
    }
    return StatementPeriod(startOfDay(c.time), now)
}

private fun startOfDay(d: Date): Date = Calendar.getInstance().apply {
    time = d
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.time

private fun endOfDay(d: Date): Date = Calendar.getInstance().apply {
    time = d
    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
}.time
