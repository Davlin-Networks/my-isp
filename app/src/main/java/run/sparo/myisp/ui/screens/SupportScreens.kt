package run.sparo.myisp.ui.screens

import androidx.compose.runtime.remember
import run.sparo.myisp.ui.theme.Space
import run.sparo.myisp.ui.theme.Radius
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import run.sparo.myisp.R
import run.sparo.myisp.ui.theme.AppIcons
import run.sparo.myisp.ui.theme.AppTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import run.sparo.myisp.AppContainer
import run.sparo.myisp.data.ApiException
import run.sparo.myisp.data.Ticket
import run.sparo.myisp.data.Tickets
import run.sparo.myisp.ui.AppState
import run.sparo.myisp.ui.agoIso
import run.sparo.myisp.ui.components.Banner
import run.sparo.myisp.ui.components.ErrorBox
import run.sparo.myisp.ui.components.Loading
import run.sparo.myisp.ui.components.Muted
import run.sparo.myisp.ui.components.PrimaryButton
import run.sparo.myisp.ui.components.SectionCard
import run.sparo.myisp.ui.components.StatusChip
import run.sparo.myisp.ui.dateTime

data class SupportState(
    val list: Tickets? = null,
    val listError: String? = null,
    val loading: Boolean = false,
    val thread: Ticket? = null,
    val threadError: String? = null,
    val busy: Boolean = false,
    val actionError: String? = null,
    /** Messages typed into the open thread that the server has not taken yet. */
    val pending: List<PendingMessage> = emptyList(),
)

/** A message on its way, shown at once; [failed] keeps it on screen to retry. */
data class PendingMessage(val localId: Long, val body: String, val failed: Boolean = false)

/** The same requests as the web portal's Help tab: one thread, either door. */
class SupportVm(private val app: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(SupportState())
    val state: StateFlow<SupportState> = _state

    fun load() {
        _state.update { it.copy(loading = true, listError = null) }
        viewModelScope.launch {
            try {
                val list = app.api.tickets()
                _state.update { it.copy(list = list, loading = false) }
            } catch (e: ApiException) {
                _state.update { it.copy(loading = false, listError = e.message) }
            }
        }
    }

    fun open(id: Long) {
        if (_state.value.thread?.id != id) _state.update { it.copy(thread = null, pending = emptyList()) }
        _state.update { it.copy(threadError = null, actionError = null) }
        viewModelScope.launch {
            try {
                val ticket = app.api.ticket(id)
                _state.update { it.copy(thread = ticket) }
            } catch (e: ApiException) {
                _state.update { it.copy(threadError = e.message) }
            }
        }
    }

    fun create(category: String, body: String, onOpened: (Long) -> Unit) = act({ app.api.openTicket(category, body) }) {
        load()
        onOpened(it.id)
    }

    /**
     * Fetch the open thread again without clearing the screen. Called every
     * few seconds while the chat is visible and when the app comes back; a
     * failure keeps what is shown.
     */
    fun refresh(id: Long) {
        viewModelScope.launch {
            try {
                val ticket = app.api.ticket(id)
                if (_state.value.thread?.id == id) _state.update { it.copy(thread = ticket) }
            } catch (_: ApiException) {
            }
        }
    }

    private val sendLock = Mutex()
    private var nextLocalId = -1L

    /** Show the message at once, then send it. One at a time, in order. */
    fun send(id: Long, body: String) {
        val message = PendingMessage(nextLocalId--, body)
        _state.update { it.copy(pending = it.pending + message, actionError = null) }
        deliver(id, message)
    }

    fun retry(id: Long, localId: Long) {
        val message = _state.value.pending.find { it.localId == localId } ?: return
        _state.update { s -> s.copy(pending = s.pending.map { if (it.localId == localId) it.copy(failed = false) else it }) }
        deliver(id, message)
    }

    private fun deliver(id: Long, message: PendingMessage) {
        viewModelScope.launch {
            sendLock.withLock {
                try {
                    val ticket = app.api.reply(id, message.body)
                    _state.update { s -> s.copy(thread = ticket, pending = s.pending.filterNot { it.localId == message.localId }) }
                } catch (e: ApiException) {
                    _state.update { s ->
                        s.copy(
                            pending = s.pending.map { if (it.localId == message.localId) it.copy(failed = true) else it },
                            actionError = e.message,
                        )
                    }
                }
            }
        }
    }

    fun close(id: Long) = act({ app.api.closeTicket(id) }) { load() }

    private fun act(call: suspend () -> Ticket, after: (Ticket) -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, actionError = null) }
        viewModelScope.launch {
            try {
                val ticket = call()
                _state.update { it.copy(thread = ticket, busy = false) }
                after(ticket)
            } catch (e: ApiException) {
                _state.update { it.copy(busy = false, actionError = e.message) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpTab(state: AppState, vm: SupportVm, onNew: () -> Unit, onOpen: (Long) -> Unit) {
    val support by vm.state.collectAsStateWithLifecycle()
    val provider = state.provider ?: return
    val context = LocalContext.current
    val colors = AppTheme.colors
    var fix by remember { mutableStateOf<QuickFix?>(null) }
    var showClosed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.load() }

    PullToRefreshBox(isRefreshing = support.loading && support.list != null, onRefresh = vm::load, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(Space.l),
        ) {
            Text(stringResource(R.string.help_title), style = MaterialTheme.typography.headlineMedium)

            // Talk to a person: the ISP's own card, on its gradient.
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.l)).background(colors.hero).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(colors.onHero.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Icon(AppIcons.Headset, contentDescription = null, tint = colors.onHero, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.help_here, provider.name), color = colors.onHero, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        Text(
                            provider.content.supportHours?.let { stringResource(R.string.help_open, it) } ?: stringResource(R.string.help_reach_us),
                            color = colors.onHero.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                provider.branding.contactPhone?.takeIf { it.isNotBlank() }?.let { phone ->
                    val digits = phone.filter(Char::isDigit)
                    val wa = if (digits.startsWith("0")) "254" + digits.drop(1) else digits
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HeroAction(stringResource(R.string.help_call), AppIcons.Phone, colors.onHero, colors.onHero.copy(alpha = 0.16f), Modifier.weight(1f)) {
                            openUrl(context, "tel:$phone")
                        }
                        // WhatsApp's mark keeps its own green: it is recognised by it.
                        HeroAction(stringResource(R.string.help_whatsapp), AppIcons.WhatsApp, Color.White, Color(0xFF25D366), Modifier.weight(1f)) {
                            openUrl(context, "https://wa.me/$wa")
                        }
                    }
                }
            }

            // Fix it yourself first: the three things people ask about most.
            SectionLabel(stringResource(R.string.help_quick_fixes))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickFix.entries.forEach { q ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(Radius.m))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable(role = Role.Button) { fix = q }
                            .padding(horizontal = 8.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.soft(q.tint())), contentAlignment = Alignment.Center) {
                            Icon(q.icon(), contentDescription = null, tint = q.tint(), modifier = Modifier.size(20.dp))
                        }
                        Text(
                            stringResource(q.title),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            minLines = 2,
                            maxLines = 2,
                        )
                    }
                }
            }

            // Requests: the conversation with the ISP, open ones first.
            val list = support.list
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(stringResource(R.string.help_requests), Modifier.weight(1f))
                if (list?.canOpen == true) {
                    Row(
                        Modifier.clip(CircleShape).background(colors.brandSoft).clickable(role = Role.Button, onClick = onNew)
                            .heightIn(min = 40.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(AppIcons.Plus, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.help_new_request), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
            when {
                list == null && support.listError != null -> ErrorBox(support.listError!!, vm::load)
                list == null -> Loading()
                else -> {
                    if (!list.canOpen) Muted(stringResource(R.string.help_limit))
                    val (closed, active) = list.tickets.partition { it.status == "closed" }
                    if (list.tickets.isEmpty()) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(MaterialTheme.colorScheme.surfaceContainer).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(AppIcons.Help, contentDescription = null, tint = colors.muted, modifier = Modifier.size(28.dp))
                            Text(stringResource(R.string.help_none), style = MaterialTheme.typography.bodyMedium, color = colors.muted, textAlign = TextAlign.Center)
                        }
                    }
                    // A request's title is its topic's name: the topic, and so its icon, from that.
                    val topicOf = { t: Ticket -> list.categories.entries.firstOrNull { it.value == t.title }?.key }
                    active.forEach { t -> TicketRow(t, topicOf(t), dim = false) { onOpen(t.id) } }
                    if (closed.isNotEmpty()) {
                        TextButton(onClick = { showClosed = !showClosed }, modifier = Modifier.heightIn(min = 44.dp)) {
                            Text(
                                stringResource(if (showClosed) R.string.help_hide_closed else R.string.help_show_closed, closed.size),
                                color = colors.muted,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (showClosed) closed.forEach { t -> TicketRow(t, topicOf(t), dim = true) { onOpen(t.id) } }
                    }
                }
            }
            Spacer(Modifier.height(Space.l))
        }
    }

    fix?.let { q -> QuickFixSheet(q, onClose = { fix = null }, onAsk = { fix = null; onNew() }) }
}

/** The three things customers ask about most, each with steps to try first. */
private enum class QuickFix(val title: Int, val steps: Int) {
    NoInternet(R.string.fix_no_internet, R.array.fix_no_internet_steps),
    Slow(R.string.fix_slow, R.array.fix_slow_steps),
    PaidNotConnected(R.string.fix_paid, R.array.fix_paid_steps);

    fun icon(): ImageVector = when (this) {
        NoInternet -> AppIcons.Wifi
        Slow -> AppIcons.Usage
        PaidNotConnected -> AppIcons.Wallet
    }

    @Composable
    fun tint(): Color = when (this) {
        NoInternet -> AppTheme.colors.critical
        Slow -> AppTheme.colors.warning
        PaidNotConnected -> AppTheme.colors.good
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickFixSheet(fix: QuickFix, onClose: () -> Unit, onAsk: () -> Unit) {
    val colors = AppTheme.colors
    val steps = stringArrayResource(fix.steps)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(Space.l),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.soft(fix.tint())), contentAlignment = Alignment.Center) {
                    Icon(fix.icon(), contentDescription = null, tint = fix.tint(), modifier = Modifier.size(20.dp))
                }
                Text(stringResource(fix.title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            }
            steps.forEachIndexed { i, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(colors.brandSoft), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(top = 3.dp))
                }
            }
            PrimaryButton(stringResource(R.string.fix_still), onAsk)
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = AppTheme.colors.muted,
        letterSpacing = 1.sp,
        modifier = modifier.padding(start = 4.dp).semantics { heading() },
    )
}

@Composable
private fun TicketRow(t: Ticket, topic: String?, dim: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.m))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .alpha(if (dim) 0.7f else 1f)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.brandSoft), contentAlignment = Alignment.Center) {
            Icon(topicIcon(topic), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(t.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.help_last_message, agoIso(t.lastMessageAt)), style = MaterialTheme.typography.bodySmall, color = colors.muted, maxLines = 1)
        }
        StatusChip(t.status)
    }
}

@Composable
private fun HeroAction(label: String, icon: ImageVector, ink: Color, fill: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(fill).clickable(role = Role.Button, onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        Text("  $label", color = ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ContactButton(label: String, icon: ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.5.dp, AppTheme.colors.line),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text("  $label", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
