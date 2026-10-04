package run.sparo.myisp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import run.sparo.myisp.AppContainer
import run.sparo.myisp.data.ApiException
import run.sparo.myisp.data.Payment
import run.sparo.myisp.data.SessionRow
import run.sparo.myisp.data.Usage
import run.sparo.myisp.ui.bytes
import run.sparo.myisp.ui.components.DailyBars
import run.sparo.myisp.ui.components.ErrorBox
import run.sparo.myisp.ui.components.Loading
import run.sparo.myisp.ui.components.Muted
import run.sparo.myisp.ui.components.SectionCard
import run.sparo.myisp.ui.components.UsageRing
import run.sparo.myisp.ui.date
import run.sparo.myisp.ui.dateTime
import run.sparo.myisp.ui.dayLabel
import run.sparo.myisp.ui.duration
import run.sparo.myisp.ui.kes

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageTab(vm: UsageVm) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        vm.loadIfStale()
        if (state.sessionsPage == 0) vm.moreSessions()
    }

    PullToRefreshBox(isRefreshing = state.loading, onRefresh = vm::load, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val usage = state.value
            when {
                usage == null && state.error != null -> ErrorBox(state.error!!, vm::load)
                usage == null -> Loading()
                else -> {
                    val fair = usage.usage.fair
                    val period = usage.usage.period
                    SectionCard {
                        if (fair != null) {
                            val next = fair.tiers.getOrNull(fair.tier)
                            val throttled = fair.tier > 0
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                UsageRing(fair.used, next?.threshold ?: fair.tiers.lastOrNull()?.threshold, throttled)
                            }
                            Text(
                                when {
                                    next != null && !throttled -> "Full speed (${fair.baseRate}) for ${bytes((next.threshold - fair.used).coerceAtLeast(0))} more, then ${next.rateLimit}."
                                    throttled -> "Your speed is reduced to ${fair.tiers[fair.tier - 1].rateLimit}" +
                                        (next?.let { " until ${bytes(it.threshold)}." } ?: ".")
                                    else -> "Full speed."
                                },
                            )
                            fair.resetsAt?.let { Muted("Resets ${dateTime(it)}") }
                        } else if (period != null) {
                            // No cap, so nothing to fill: a ring would only be an empty circle.
                            Muted("Used since ${date(period.since)}")
                            Text(bytes(period.down + period.up), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                            Muted("${bytes(period.down)} down · ${bytes(period.up)} up. Your plan has no data cap.")
                        } else {
                            Muted("No usage recorded for this plan yet.")
                        }
                    }

                    if (usage.daily.any { it.down + it.up > 0 }) {
                        SectionCard {
                            Text("Last 30 days", fontWeight = FontWeight.SemiBold)
                            DailyBars(usage.daily)
                            val week = usage.daily.takeLast(7).sumOf { it.down + it.up }
                            Muted("This week: ${bytes(week)}")
                        }
                    }
                }
            }

            if (state.sessions.isNotEmpty()) {
                SectionCard {
                    Text("Connections", fontWeight = FontWeight.SemiBold)
                    state.sessions.forEach { s ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(dayLabel(s.startedAt) + (if (s.online) " · online now" else ""))
                                Muted("${duration(s.startedAt, s.endedAt)}${s.endedBy?.let { " · $it" } ?: ""}")
                            }
                            Text(bytes(s.down + s.up))
                        }
                    }
                    if (state.sessionsMore) {
                        TextButton(onClick = vm::moreSessions, enabled = !state.sessionsLoading) { Text("Show more") }
                    }
                }
            }
        }
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
fun PaymentsTab(app: AppContainer, providerName: String) {
    val vm: PaymentsVm = viewModel { PaymentsVm(app) }
    val state by vm.state.collectAsStateWithLifecycle()
    var receipt by remember { mutableStateOf<Payment?>(null) }

    PullToRefreshBox(isRefreshing = state.loading && state.loaded, onRefresh = vm::load, modifier = Modifier.fillMaxSize()) {
        when {
            !state.loaded && state.error != null -> ErrorBox(state.error!!, vm::load)
            !state.loaded -> Loading()
            state.payments.isEmpty() -> Muted("No payments yet.", Modifier.padding(24.dp))
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.payments, key = { it.id }) { p ->
                    SectionCard(Modifier.clickable { receipt = p }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(p.plan ?: "Payment", fontWeight = FontWeight.SemiBold)
                            Text(kes(p.amount), fontWeight = FontWeight.SemiBold)
                        }
                        Muted("${dateTime(p.at)}${p.reference?.let { " · $it" } ?: ""}")
                    }
                }
                if (state.nextBefore != null) {
                    item {
                        TextButton(onClick = vm::more, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text("Show older payments") }
                    }
                }
            }
        }
    }

    receipt?.let { p ->
        AlertDialog(
            onDismissRequest = { receipt = null },
            confirmButton = { TextButton(onClick = { receipt = null }) { Text("Close") } },
            title = { Text("Payment receipt") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(providerName, style = MaterialTheme.typography.labelLarge)
                    Text(kes(p.amount), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    p.plan?.let { Text("Plan: $it") }
                    Text("Paid: ${dateTime(p.at)}")
                    p.method?.let { Text("Method: ${it.replaceFirstChar(Char::uppercase)}") }
                    p.reference?.let { Text("Reference: $it") }
                }
            },
        )
    }
}
