package run.sparo.myisp.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import run.sparo.myisp.R
import run.sparo.myisp.data.Account
import run.sparo.myisp.data.ApiException
import run.sparo.myisp.data.Plan
import run.sparo.myisp.data.Plans
import run.sparo.myisp.ui.AppState
import run.sparo.myisp.ui.AppViewModel
import run.sparo.myisp.ui.components.Banner
import run.sparo.myisp.ui.components.Confetti
import run.sparo.myisp.ui.components.ErrorBox
import run.sparo.myisp.ui.components.Loading
import run.sparo.myisp.ui.components.Muted
import run.sparo.myisp.ui.components.PrimaryButton
import run.sparo.myisp.ui.components.ReceiptData
import run.sparo.myisp.ui.components.confirmHaptic
import run.sparo.myisp.ui.components.shareReceipt
import run.sparo.myisp.ui.date
import run.sparo.myisp.ui.dateTime
import run.sparo.myisp.ui.kes
import run.sparo.myisp.ui.theme.AppIcons
import run.sparo.myisp.ui.theme.AppTheme
import run.sparo.myisp.ui.theme.Radius
import run.sparo.myisp.ui.theme.Space
import run.sparo.myisp.ui.validity
import java.util.UUID

sealed interface PayStage {
    data object Choose : PayStage
    data object Sending : PayStage
    data class Waiting(val amount: Int, val phone: String?) : PayStage

    /** [paid]: what M-Pesa took; [credit]: what the wallet covered. */
    data class Done(val account: Account?, val receipt: String?, val paid: Int, val credit: Int) : PayStage

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
    private var pending: Pair<Int, Int> = 0 to 0

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
        val s = _state.value.stage
        if (s is PayStage.Sending || s is PayStage.Waiting) return
        val attempt = key ?: UUID.randomUUID().toString().also { key = it }
        _state.update { it.copy(stage = PayStage.Sending) }

        viewModelScope.launch {
            try {
                val checkout = app.api.checkout(plan.id, phone.trim().ifEmpty { null }, attempt)
                if (checkout.status == "confirmed") {
                    key = null
                    checkout.account?.let(onAccount)
                    _state.update { it.copy(stage = PayStage.Done(checkout.account, null, 0, checkout.creditUsed)) }
                } else {
                    pending = checkout.amount to checkout.creditUsed
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
                        _state.update { it.copy(stage = PayStage.Done(status.account, status.receipt, pending.first, pending.second)) }
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
        val s = _state.value.stage
        if (s is PayStage.Failed && !s.sameAgain) key = null
        _state.update { it.copy(stage = PayStage.Choose) }
    }
}

/**
 * Renewing, as a sheet over Home: the account stays in view behind it.
 * Everything that matters is named before money moves - which plan, what
 * the wallet covers, what the prompt will ask, which ISP and which line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenewSheet(state: AppState, app: AppContainer, appVm: AppViewModel, onClose: () -> Unit) {
    val vm: RenewVm = viewModel { RenewVm(app) }
    val renew by vm.state.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }
    val waiting = renew.stage is PayStage.Waiting || renew.stage == PayStage.Sending
    val waitingNow by rememberUpdatedState(waiting)

    // Mid-payment, a swipe or Back asks first rather than losing the payment from view.
    val sheet = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            if (target == SheetValue.Hidden && waitingNow) {
                confirmLeave = true
                false
            } else {
                true
            }
        },
    )

    ModalBottomSheet(
        onDismissRequest = { if (waitingNow) confirmLeave = true else onClose() },
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Box {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(start = 22.dp, end = 22.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(Space.l),
            ) {
                when (val stage = renew.stage) {
                    PayStage.Choose, PayStage.Sending -> ChoosePlan(state, renew, sending = stage == PayStage.Sending) { plan, phone ->
                        vm.pay(plan, phone, appVm::applyAccount)
                    }
                    is PayStage.Waiting -> Waiting(stage)
                    is PayStage.Done -> Done(state, stage, onClose)
                    is PayStage.Failed -> {
                        Text(stringResource(R.string.renew_failed_title), style = MaterialTheme.typography.headlineSmall)
                        Banner(stage.message, "error")
                        PrimaryButton(stringResource(R.string.try_again), vm::again)
                        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.back)) }
                    }
                    PayStage.Slow -> {
                        Banner(stringResource(R.string.renew_slow), "warning")
                        PrimaryButton(stringResource(R.string.done), {
                            appVm.refreshAccount()
                            onClose()
                        })
                    }
                }
            }
            if (renew.stage is PayStage.Done) {
                Confetti(
                    listOf(MaterialTheme.colorScheme.primary, AppTheme.colors.good, AppTheme.colors.warning, AppTheme.colors.heroEnd),
                    Modifier.matchParentSize(),
                )
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.renew_leave_title)) },
            text = { Text(stringResource(R.string.renew_leave_body)) },
            confirmButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.renew_stay)) } },
            dismissButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    appVm.refreshAccount()
                    onClose()
                }) { Text(stringResource(R.string.renew_leave)) }
            },
        )
    }
}

@Composable
private fun ChoosePlan(state: AppState, renew: RenewState, sending: Boolean, onPay: (Plan, String) -> Unit) {
    val account = state.account
    val providerName = state.provider?.name.orEmpty()
    val colors = AppTheme.colors
    val plans = renew.plans
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    var phone by rememberSaveable { mutableStateOf("") }
    var otherNumber by rememberSaveable { mutableStateOf(false) }

    val currentId = account?.plan?.id
    val locked = plans?.currentOnly == true && account?.plan != null
    val listed: List<Plan> = when {
        plans == null -> emptyList()
        locked -> listOf(plans.plans.firstOrNull { it.id == currentId } ?: account!!.plan!!) + plans.plans.filter { it.id != currentId }
        else -> plans.plans
    }
    LaunchedEffect(listed) {
        if (selected == null || listed.none { it.id == selected }) {
            selected = (listed.firstOrNull { it.id == currentId } ?: listed.firstOrNull())?.id
        }
    }

    Text(stringResource(R.string.renew_title), style = MaterialTheme.typography.headlineSmall)
    when {
        plans == null && renew.error != null -> return ErrorBox(renew.error, null)
        plans == null -> return Loading()
        listed.isEmpty() -> return Muted(stringResource(R.string.renew_no_plans, providerName))
    }
    Muted(
        if (locked) stringResource(R.string.renew_same_plan, date(account?.expiresAt)) else stringResource(R.string.renew_choose),
    )

    listed.forEach { p ->
        val disabled = locked && p.id != currentId
        PlanRow(p, selected = p.id == selected, disabled = disabled, isCurrent = p.id == currentId, lockedUntil = date(account?.expiresAt)) {
            if (!disabled) selected = p.id
        }
    }

    val plan = listed.firstOrNull { it.id == selected } ?: return
    val credit = account?.wallet?.takeIf { it.enabled }?.balance ?: 0
    val creditUsed = minOf(credit, plan.price)
    val prompt = plan.price - creditUsed

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Line(stringResource(R.string.renew_line_plan), kes(plan.price))
        if (creditUsed > 0) Line(stringResource(R.string.renew_line_wallet), "− " + kes(creditUsed), valueColor = colors.good)
        HorizontalDivider(color = colors.line)
        Line(stringResource(R.string.renew_line_prompt), kes(prompt), bold = true)
    }

    if (prompt > 0) {
        if (otherNumber) {
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' }.take(13) },
                label = { Text(stringResource(R.string.renew_other_label)) },
                supportingText = { Text(stringResource(R.string.renew_other_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TextButton(onClick = { otherNumber = true }, modifier = Modifier.height(44.dp)) { Text(stringResource(R.string.renew_other_number)) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton(
            if (prompt > 0) stringResource(R.string.renew_pay, kes(prompt)) else stringResource(R.string.renew_pay_credit, kes(creditUsed)),
            { onPay(plan, if (otherNumber) phone else "") },
            busy = sending,
        )
        Text(
            stringResource(R.string.renew_to, providerName, account?.username.orEmpty()),
            style = MaterialTheme.typography.bodySmall,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PlanRow(plan: Plan, selected: Boolean, disabled: Boolean, isCurrent: Boolean, lockedUntil: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val brand = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (disabled) 0.55f else 1f)
            .clip(shape)
            .background(if (selected) colors.brandSoft else MaterialTheme.colorScheme.surfaceContainer)
            .border(if (selected) BorderStroke(2.dp, brand) else BorderStroke(1.5.dp, colors.line), shape)
            .clickable(enabled = !disabled, role = Role.RadioButton, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (selected) brand else MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            when {
                selected -> Icon(AppIcons.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                disabled -> Icon(AppIcons.Lock, null, tint = colors.muted, modifier = Modifier.size(14.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold)
            val meta = listOfNotNull(plan.rateLimit, validity(plan.validityDays), if (isCurrent) stringResource(R.string.renew_your_plan) else null).joinToString(" · ")
            Text(if (disabled) stringResource(R.string.renew_locked_after, lockedUntil) else meta, style = MaterialTheme.typography.bodySmall, color = colors.muted)
        }
        Text(kes(plan.price), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun Line(label: String, value: String, bold: Boolean = false, valueColor: androidx.compose.ui.graphics.Color? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (bold) MaterialTheme.colorScheme.onSurface else AppTheme.colors.muted, fontWeight = if (bold) FontWeight.ExtraBold else null)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor ?: MaterialTheme.colorScheme.onSurface, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Bold)
    }
}

@Composable
private fun Waiting(stage: PayStage.Waiting) {
    val brand = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    val soft = AppTheme.colors.brandSoft
    val pulse = rememberInfiniteTransition(label = "pin")
    val t by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "t")

    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // A phone with a PIN being entered: the four dots light in turn.
        Canvas(Modifier.size(width = 116.dp, height = 176.dp)) {
            val w = 5.dp.toPx()
            drawRoundRect(ink, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(20.dp.toPx()), style = Stroke(w))
            drawRoundRect(ink, Offset(size.width / 2 - 18.dp.toPx(), 12.dp.toPx()), Size(36.dp.toPx(), 6.dp.toPx()), CornerRadius(3.dp.toPx()))
            drawRoundRect(soft, Offset(16.dp.toPx(), 36.dp.toPx()), Size(size.width - 32.dp.toPx(), 46.dp.toPx()), CornerRadius(10.dp.toPx()))
            for (i in 0 until 4) {
                val phase = ((t * 4f) - i).let { if (it < 0) it + 4f else it }
                val lit = if (phase < 1f) 1f - phase else 0.25f
                drawCircle(brand.copy(alpha = 0.25f + 0.75f * lit), radius = 6.dp.toPx(), center = Offset(size.width * (0.27f + i * 0.155f), 59.dp.toPx()))
            }
        }
        Text(stringResource(R.string.renew_wait_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            if (stage.phone != null) stringResource(R.string.renew_wait_body_from, kes(stage.amount), stage.phone) else stringResource(R.string.renew_wait_body, kes(stage.amount)),
            style = MaterialTheme.typography.bodyLarge,
            color = AppTheme.colors.muted,
            textAlign = TextAlign.Center,
        )
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = brand, trackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            Muted(stringResource(R.string.renew_wait_progress))
        }
        Text(
            stringResource(R.string.renew_wait_note),
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.colors.muted,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp),
        )
    }
}

@Composable
private fun Done(state: AppState, stage: PayStage.Done, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val colors = AppTheme.colors
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) {
        confirmHaptic(view)
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
    }
    val account = stage.account ?: state.account
    val provider = state.provider
    val paidLabel = stringResource(R.string.renew_paid)
    val creditLabel = stringResource(R.string.renew_credit_used)
    val receiptLabel = stringResource(R.string.renew_receipt)
    val rows = buildList {
        if (stage.paid > 0) add(paidLabel to kes(stage.paid))
        if (stage.credit > 0) add(creditLabel to kes(stage.credit))
        stage.receipt?.let { add(receiptLabel to it) }
    }
    val receiptTitle = stringResource(R.string.receipt_title)
    val shareTitle = stringResource(R.string.share_receipt)

    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier.size(76.dp).scale(pop.value).clip(CircleShape).background(colors.soft(colors.good)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Check, null, tint = colors.good, modifier = Modifier.size(38.dp))
        }
        Text(stringResource(R.string.renew_done_title), style = MaterialTheme.typography.headlineMedium)
        if (account?.expiresAt != null) {
            Text(
                stringResource(R.string.renew_done_body, account.plan?.name.orEmpty(), dateTime(account.expiresAt)),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.muted,
                textAlign = TextAlign.Center,
            )
        }
        if (rows.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rows.forEach { (k, v) -> Line(k, v) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    shareReceipt(
                        context,
                        ReceiptData(
                            isp = provider?.name.orEmpty(),
                            title = receiptTitle,
                            amount = kes(stage.paid + stage.credit),
                            rows = buildList {
                                account?.plan?.name?.let { add("Plan" to it) }
                                addAll(rows)
                                account?.expiresAt?.let { add("Runs until" to dateTime(it)) }
                                account?.username?.let { add("Line" to it) }
                            },
                            brand = colors.heroStart,
                        ),
                        shareTitle,
                    )
                },
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(15.dp),
            ) {
                Icon(AppIcons.Share, null, modifier = Modifier.size(18.dp))
                Text("  " + shareTitle, fontWeight = FontWeight.Bold)
            }
            PrimaryButton(stringResource(R.string.done), onClose, modifier = Modifier.weight(1f))
        }
    }
}
