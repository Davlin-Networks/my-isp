package run.sparo.myisp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import run.sparo.myisp.AppContainer
import run.sparo.myisp.data.Account
import run.sparo.myisp.data.ApiException
import run.sparo.myisp.data.Plan
import run.sparo.myisp.data.Plans
import run.sparo.myisp.ui.AppState
import run.sparo.myisp.ui.AppViewModel
import run.sparo.myisp.ui.components.Banner
import run.sparo.myisp.ui.components.ErrorBox
import run.sparo.myisp.ui.components.Loading
import run.sparo.myisp.ui.components.Muted
import run.sparo.myisp.ui.components.PrimaryButton
import run.sparo.myisp.ui.dateTime
import run.sparo.myisp.ui.kes
import run.sparo.myisp.ui.theme.StatusColors
import run.sparo.myisp.ui.validity
import java.util.UUID

sealed interface PayStage {
    data object Choose : PayStage
    data object Sending : PayStage
    data class Waiting(val amount: Int, val phone: String?) : PayStage
    data class Done(val message: String, val receipt: String?, val account: Account?) : PayStage

    /** [sameAgain]: no answer came, so "Try again" repeats this payment, not a new one. */
    data class Failed(val message: String, val sameAgain: Boolean) : PayStage

    /** M-Pesa has not answered in two minutes; the payment may still land. */
    data object Slow : PayStage
}

data class RenewState(
    val plans: Plans? = null,
    val error: String? = null,
    val stage: PayStage = PayStage.Choose,
)

/**
 * One payment at a time, with an Idempotency-Key per tap of Pay. Mobile
 * networks drop the reply to a request that went through; retrying with the
 * same key gets the first answer back and sends no second M-Pesa prompt.
 */
class RenewVm(private val app: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(RenewState())
    val state: StateFlow<RenewState> = _state

    private var key: String? = null
    private var poller: Job? = null

    init {
        loadPlans()
    }

    fun loadPlans() {
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            try {
                val plans = app.api.plans()
                _state.update { it.copy(plans = plans) }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun pay(plan: Plan, phone: String, onAccount: (Account) -> Unit) {
        if (_state.value.stage is PayStage.Sending || _state.value.stage is PayStage.Waiting) return
        val attempt = key ?: UUID.randomUUID().toString().also { key = it }
        _state.update { it.copy(stage = PayStage.Sending) }

        viewModelScope.launch {
            try {
                val checkout = app.api.checkout(plan.id, phone.trim().ifEmpty { null }, attempt)
                if (checkout.status == "confirmed") {
                    key = null
                    checkout.account?.let(onAccount)
                    _state.update { it.copy(stage = PayStage.Done(checkout.message ?: "Renewed.", null, checkout.account)) }
                } else {
                    _state.update { it.copy(stage = PayStage.Waiting(checkout.amount, checkout.phone)) }
                    poll(checkout.id, onAccount)
                }
            } catch (e: ApiException) {
                // No answer, or the same payment still being sent: keep the
                // key, so trying again cannot make a second prompt.
                val same = e.retryable || e.code == "checkout_in_progress"
                if (!same) key = null
                _state.update { it.copy(stage = PayStage.Failed(e.message, same)) }
            }
        }
    }

    private fun poll(id: String, onAccount: (Account) -> Unit) {
        poller?.cancel()
        poller = viewModelScope.launch {
            val started = System.currentTimeMillis()
            val waits = listOf(2_000L, 3_000L, 5_000L)
            var n = 0
            while (System.currentTimeMillis() - started < 120_000) {
                delay(waits.getOrElse(n++) { 5_000L })
                val status = try {
                    app.api.checkoutStatus(id)
                } catch (e: ApiException) {
                    if (e.status == 404) break else continue
                }
                when (status.status) {
                    "confirmed" -> {
                        key = null
                        status.account?.let(onAccount)
                        _state.update { it.copy(stage = PayStage.Done("Payment received.", status.receipt, status.account)) }
                        return@launch
                    }
                    "failed" -> {
                        key = null
                        _state.update { it.copy(stage = PayStage.Failed(status.message ?: "The payment was cancelled or failed.", false)) }
                        return@launch
                    }
                }
            }
            key = null
            _state.update { it.copy(stage = PayStage.Slow) }
        }
    }

    fun again() {
        if (_state.value.stage is PayStage.Failed && !(_state.value.stage as PayStage.Failed).sameAgain) key = null
        _state.update { it.copy(stage = PayStage.Choose) }
    }
}

@Composable
fun RenewScreen(state: AppState, app: AppContainer, appVm: AppViewModel, onClose: () -> Unit) {
    val vm: RenewVm = viewModel { RenewVm(app) }
    val renew by vm.state.collectAsStateWithLifecycle()
    val account = state.account
    val providerName = state.provider?.name.orEmpty()
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    var phone by rememberSaveable { mutableStateOf("") }

    val plans = renew.plans
    val visible: List<Plan> = when {
        plans == null -> emptyList()
        plans.currentOnly && account?.plan != null ->
            listOf(plans.plans.firstOrNull { it.id == account.plan.id } ?: account.plan)
        else -> plans.plans
    }
    LaunchedEffect(visible) {
        if (selected == null || visible.none { it.id == selected }) {
            selected = (visible.firstOrNull { it.id == account?.plan?.id } ?: visible.firstOrNull())?.id
        }
    }
    val plan = visible.firstOrNull { it.id == selected }

    PageScaffold("Renew", onClose) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val stage = renew.stage) {
                PayStage.Choose, PayStage.Sending -> {
                    when {
                        plans == null && renew.error != null -> ErrorBox(renew.error!!, vm::loadPlans)
                        plans == null -> Loading()
                        visible.isEmpty() -> Muted("There are no plans to buy right now. Contact $providerName.")
                        else -> {
                            if (plans.currentOnly && visible.size == 1) {
                                Muted("Your plan is still running, so it renews on the same plan.")
                            }
                            visible.forEach { p ->
                                PlanCard(p, selected = p.id == selected, onClick = { selected = p.id })
                            }
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' }.take(13) },
                                label = { Text("M-Pesa number (optional)") },
                                supportingText = { Text("Leave empty to pay from the number on your account. Anyone can pay for you.") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (plan != null && account != null) {
                                // Everything named before money moves: whose line, which ISP.
                                Muted("Pays ${kes(plan.price)} to $providerName for line ${account.username}. Wallet credit is used first.")
                                PrimaryButton(
                                    "Pay ${kes(plan.price)}",
                                    { vm.pay(plan, phone, appVm::applyAccount) },
                                    busy = stage == PayStage.Sending,
                                )
                            }
                        }
                    }
                }

                is PayStage.Waiting -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Check your phone", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Enter your M-Pesa PIN to pay ${kes(stage.amount)}" + (stage.phone?.let { " from $it" } ?: "") + ".",
                        textAlign = TextAlign.Center,
                    )
                    Muted("This page updates by itself once M-Pesa confirms.")
                }

                is PayStage.Done -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Renewed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = StatusColors.good)
                    Text(stage.message, textAlign = TextAlign.Center)
                    stage.account?.expiresAt?.let { Text("Your plan now runs until ${dateTime(it)}.", textAlign = TextAlign.Center) }
                    stage.receipt?.let { Muted("M-Pesa receipt $it") }
                    PrimaryButton("Done", onClose)
                }

                is PayStage.Failed -> {
                    Banner(stage.message, "error")
                    PrimaryButton("Try again", vm::again)
                    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Back") }
                }

                PayStage.Slow -> {
                    Banner("M-Pesa hasn't confirmed yet. If you entered your PIN, the payment will show under Payments and your plan will update - no need to pay again.", "warning")
                    PrimaryButton("Done", {
                        appVm.refreshAccount()
                        onClose()
                    })
                }
            }
        }
    }
}

@Composable
private fun PlanCard(plan: Plan, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(plan.name, fontWeight = FontWeight.SemiBold)
                Muted(listOfNotNull(plan.rateLimit, validity(plan.validityDays)).joinToString(" · "))
                plan.description?.takeIf { it.isNotBlank() }?.let { Muted(it) }
            }
            Text(kes(plan.price), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
