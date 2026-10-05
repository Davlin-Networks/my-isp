package run.sparo.myisp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import run.sparo.myisp.AppContainer
import run.sparo.myisp.R
import run.sparo.myisp.data.ApiException
import run.sparo.myisp.data.Payment
import run.sparo.myisp.data.Provider
import run.sparo.myisp.data.SessionRow
import run.sparo.myisp.data.Usage
import run.sparo.myisp.ui.AppState
import run.sparo.myisp.ui.bytes
import run.sparo.myisp.ui.components.BarChart
import run.sparo.myisp.ui.components.ErrorBox
import run.sparo.myisp.ui.components.Loading
import run.sparo.myisp.ui.components.Muted
import run.sparo.myisp.ui.components.ProgressRing
import run.sparo.myisp.ui.components.ReceiptData
import run.sparo.myisp.ui.components.shareReceipt
import run.sparo.myisp.ui.date
import run.sparo.myisp.ui.dateTime
import run.sparo.myisp.ui.dayLabel
import run.sparo.myisp.ui.duration
import run.sparo.myisp.ui.kes
import run.sparo.myisp.ui.parseIso
import run.sparo.myisp.ui.theme.AppIcons
import run.sparo.myisp.ui.theme.AppTheme
import run.sparo.myisp.ui.theme.Radius
import run.sparo.myisp.ui.theme.Space
import java.text.SimpleDateFormat
import java.util.Locale

data class UsageState(
    val value: Usage? = null,
    val loadedAt: Long = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val sessions: List<SessionRow> = emptyList(),
    val sessionsPage: Int = 0,
    val sessionsMore: Boolean = true,
    val sessionsLoading: Boolean = false,
)

/**
 * Usage and sessions: the calls that read the ISP's session records, so
 * loaded after the account card is on screen, never before it.
 */
class UsageVm(private val app: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(UsageState())
    val state: StateFlow<UsageState> = _state

    /** The server reuses a reading for a minute; asking sooner gains nothing. */
    fun loadIfStale() {
        if (System.currentTimeMillis() - _state.value.loadedAt > 60_000) load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val usage = app.api.usage()
                _state.update { it.copy(value = usage, loadedAt = System.currentTimeMillis(), loading = false) }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun moreSessions() {
        val s = _state.value
        if (s.sessionsLoading || !s.sessionsMore) return
        _state.update { it.copy(sessionsLoading = true) }
        viewModelScope.launch {
            try {
                val page = app.api.sessions(s.sessionsPage + 1)
                _state.update {
                    it.copy(sessions = it.sessions + page.data, sessionsPage = s.sessionsPage + 1, sessionsMore = page.hasMore, sessionsLoading = false)
                }
            } catch (e: ApiException) {
                _state.update { it.copy(sessionsLoading = false) }
            }
        }
    }
}

/** A card on the scrolling page: the app's one card shape. */
@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(18.dp)) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageTab(vm: UsageVm, appState: AppState) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    LaunchedEffect(Unit) {
        vm.loadIfStale()
        if (state.sessionsPage == 0) vm.moreSessions()
    }
    val planName = appState.account?.plan?.name.orEmpty()

    PullToRefreshBox(isRefreshing = state.loading && state.value != null, onRefresh = vm::load, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val usage = state.value
            val fair = usage?.usage?.fair
            val period = usage?.usage?.period
            item(key = "title") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.usage_title), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        when {
                            fair != null -> stringResource(R.string.usage_subtitle_fair, planName)
                            period != null -> stringResource(R.string.usage_subtitle_period, planName, date(period.since))
                            else -> planName
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.muted,
                    )
                }
            }

            when {
                usage == null && state.error != null -> item(key = "error") { ErrorBox(state.error!!, vm::load) }
                usage == null -> item(key = "loading") { Loading() }
                else -> {
                    item(key = "meter") { Meter(usage) }
                    if (usage.daily.any { it.down + it.up > 0 }) {
                        item(key = "chart") { DailyCard(usage) }
                        item(key = "chips") { WeekChips(usage) }
                    }
                }
            }

            sessions(state, vm)
        }
    }
}

@Composable
private fun Meter(usage: Usage) {
    val colors = AppTheme.colors
    val brand = MaterialTheme.colorScheme.primary
    val fair = usage.usage.fair
    val period = usage.usage.period
    Card {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when {
                fair != null -> {
                    val throttled = fair.tier > 0
                    val next = fair.tiers.getOrNull(fair.tier)
                    val step = next?.threshold ?: fair.tiers.lastOrNull()?.threshold ?: 0L
                    ProgressRing(
                        fraction = if (step > 0) fair.used.toFloat() / step else 0f,
                        color = if (throttled) colors.critical else brand,
                        track = MaterialTheme.colorScheme.surfaceContainerHigh,
                        size = 176.dp,
                        stroke = 14.dp,
                        label = bytes(fair.used),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(bytes(fair.used), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                            if (step > 0) Text(stringResource(R.string.usage_of_full, bytes(step)), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                        }
                    }
                    Text(
                        when {
                            next != null && !throttled -> stringResource(R.string.usage_full_for, fair.baseRate.orEmpty(), bytes((next.threshold - fair.used).coerceAtLeast(0)), next.rateLimit)
                            throttled && next != null -> stringResource(R.string.usage_throttled_until, fair.tiers[fair.tier - 1].rateLimit, next.rateLimit, bytes(next.threshold))
                            throttled -> stringResource(R.string.usage_throttled, fair.tiers[fair.tier - 1].rateLimit)
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        fair.tiers.firstOrNull()?.let { Muted(stringResource(R.string.usage_after, bytes(it.threshold), it.rateLimit)) }
                        fair.resetsAt?.let { Muted(stringResource(R.string.usage_resets, date(it))) }
                    }
                }
                period != null -> {
                    // No cap, so nothing to fill: a ring would only be an empty circle.
                    Text(bytes(period.down + period.up), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
                    Muted(stringResource(R.string.usage_down_up, bytes(period.down), bytes(period.up)) + " · " + stringResource(R.string.usage_no_cap))
                }
                else -> Muted(stringResource(R.string.usage_none))
            }
        }
    }
}

@Composable
private fun DailyCard(usage: Usage) {
    val colors = AppTheme.colors
    val brand = MaterialTheme.colorScheme.primary
    val days = usage.daily
    var selected by rememberSaveable { mutableIntStateOf(days.lastIndex) }
    val day = days.getOrNull(selected) ?: return
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text(stringResource(R.string.usage_30_days), style = MaterialTheme.typography.labelLarge, color = colors.muted)
                    Text(bytes(day.down + day.up), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (selected == days.lastIndex) stringResource(R.string.usage_today) else dayLabel(day.day), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.usage_down_up, bytes(day.down), bytes(day.up)), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                }
            }
            BarChart(
                values = days.map { it.down + it.up },
                selected = selected,
                onSelect = { selected = it },
                color = brand,
                tint = brand.copy(alpha = if (colors.dark) 0.32f else 0.24f),
                label = stringResource(R.string.chart_label, days.size),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Muted(dayLabel(days.first().day))
                Muted(stringResource(R.string.usage_hint))
                Muted(stringResource(R.string.usage_today))
            }
        }
    }
}

@Composable
private fun WeekChips(usage: Usage) {
    val week = usage.daily.takeLast(7)
    val busiest = week.maxByOrNull { it.down + it.up }
    val busiestName = busiest?.let { parseIso(it.day) }?.let { SimpleDateFormat("EEE", Locale.UK).format(it) } ?: "—"
    val avg = usage.daily.sumOf { it.down + it.up } / maxOf(1, usage.daily.size)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Chip(stringResource(R.string.usage_week), bytes(week.sumOf { it.down + it.up }), Modifier.weight(1f))
        Chip(stringResource(R.string.usage_busiest), busiestName, Modifier.weight(1f))
        Chip(stringResource(R.string.usage_avg), bytes(avg), Modifier.weight(1f))
    }
}

@Composable
private fun Chip(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(Radius.m)).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.muted)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    }
}

/** Connections, grouped by day, as rows of the lazy list - long histories stay smooth. */
private fun LazyListScope.sessions(state: UsageState, vm: UsageVm) {
    if (state.sessions.isEmpty()) return
    item(key = "sessions-title") {
        Text(stringResource(R.string.usage_connections), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
    }
    state.sessions.groupBy { dayLabel(it.startedAt) }.forEach { (day, rows) ->
        item(key = "day-$day") { Muted(day, Modifier.padding(top = 4.dp)) }
        items(rows, key = { "s-${it.id}" }) { s -> SessionItem(s) }
    }
    if (state.sessionsMore) {
        item(key = "more") {
            TextButton(onClick = vm::moreSessions, enabled = !state.sessionsLoading, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(stringResource(R.string.usage_more))
            }
        }
    }
}

@Composable
private fun SessionItem(s: SessionRow) {
    val colors = AppTheme.colors
    val time = SimpleDateFormat("HH:mm", Locale.UK)
    val start = parseIso(s.startedAt)?.let(time::format) ?: "—"
    val end = parseIso(s.endedAt)?.let(time::format)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (s.online) colors.good else colors.neutral))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (s.online) stringResource(R.string.usage_online_now) + " · $start" else "$start – ${end ?: "…"}", style = MaterialTheme.typography.titleSmall)
            Text(duration(s.startedAt, s.endedAt) + (s.endedBy?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
        Text(bytes(s.down + s.up), style = MaterialTheme.typography.titleSmall)
    }
}

// ── Payments ────────────────────────────────────────────────────────────────

data class PaymentsState(
    val payments: List<Payment> = emptyList(),
    val nextBefore: Long? = null,
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

class PaymentsVm(private val app: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(PaymentsState())
    val state: StateFlow<PaymentsState> = _state

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val page = app.api.payments(null)
                _state.update { PaymentsState(page.payments, page.nextBefore, loaded = true) }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun more() {
        val before = _state.value.nextBefore ?: return
        if (_state.value.loading) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val page = app.api.payments(before)
                _state.update { it.copy(payments = it.payments + page.payments, nextBefore = page.nextBefore, loading = false) }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsTab(app: AppContainer, provider: Provider) {
    val vm: PaymentsVm = viewModel { PaymentsVm(app) }
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    var receipt by remember { mutableStateOf<Payment?>(null) }
    val month = remember { SimpleDateFormat("MMMM yyyy", Locale.UK) }

    PullToRefreshBox(isRefreshing = state.loading && state.loaded, onRefresh = vm::load, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "title") { Text(stringResource(R.string.payments_title), style = MaterialTheme.typography.headlineMedium) }
            when {
                !state.loaded && state.error != null -> item(key = "error") { ErrorBox(state.error!!, vm::load) }
                !state.loaded -> item(key = "loading") { Loading() }
                state.payments.isEmpty() -> item(key = "empty") { Muted(stringResource(R.string.payments_none), Modifier.padding(vertical = 24.dp)) }
                else -> {
                    state.payments.groupBy { parseIso(it.at)?.let(month::format) ?: "—" }.forEach { (m, rows) ->
                        item(key = "m-$m") {
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(m, style = MaterialTheme.typography.labelLarge, color = colors.muted)
                                Text(kes(rows.sumOf { it.amount }), style = MaterialTheme.typography.labelLarge, color = colors.muted)
                            }
                        }
                        items(rows, key = { "p-${it.id}" }) { p ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .clickable(role = Role.Button) { receipt = p }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.brandSoft), contentAlignment = Alignment.Center) {
                                    Icon(AppIcons.Receipt, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(p.plan ?: stringResource(R.string.receipt_title), style = MaterialTheme.typography.titleSmall)
                                    Text(dateTime(p.at) + (p.reference?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(kes(p.amount), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                            }
                        }
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

    receipt?.let { p -> ReceiptSheet(p, provider, onClose = { receipt = null }) }
}

/** A receipt that looks like one, and shares as an image. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptSheet(p: Payment, provider: Provider, onClose: () -> Unit) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    val rows = listOfNotNull(
        p.plan?.let { stringResource(R.string.receipt_plan) to it },
        stringResource(R.string.receipt_paid) to dateTime(p.at),
        p.method?.let { stringResource(R.string.receipt_method) to it.replaceFirstChar(Char::uppercase) },
        p.reference?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.receipt_reference) to it },
    )
    val title = stringResource(R.string.receipt_title)
    val shareTitle = stringResource(R.string.share_receipt)

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(Space.l)) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.l)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(provider.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                Text(kes(p.amount), fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
                rows.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(k, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                        Text(v, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            OutlinedButton(
                onClick = { shareReceipt(context, ReceiptData(provider.name, title, kes(p.amount), rows, colors.heroStart), shareTitle) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(15.dp),
            ) {
                Icon(AppIcons.Share, null, modifier = Modifier.size(18.dp))
                Text("  $shareTitle", fontWeight = FontWeight.Bold)
            }
        }
    }
}
