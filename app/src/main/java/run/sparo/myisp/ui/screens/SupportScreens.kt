package run.sparo.myisp.ui.screens

import androidx.compose.foundation.background
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
)

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
        if (_state.value.thread?.id != id) _state.update { it.copy(thread = null) }
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

    fun reply(id: Long, body: String, onSent: () -> Unit) = act({ app.api.reply(id, body) }) { onSent() }

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
    LaunchedEffect(Unit) { vm.load() }

    PullToRefreshBox(isRefreshing = support.loading && support.list != null, onRefresh = vm::load, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                Text("Contact ${provider.name}", fontWeight = FontWeight.SemiBold)
                provider.content.supportHours?.let { Muted("Open $it") }
                provider.branding.contactPhone?.let { phone ->
                    val digits = phone.filter(Char::isDigit)
                    val wa = if (digits.startsWith("0")) "254" + digits.drop(1) else digits
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { openUrl(context, "tel:$phone") }) { Text("Call") }
                        OutlinedButton(onClick = { openUrl(context, "https://wa.me/$wa") }) { Text("WhatsApp") }
                    }
                }
            }

            val list = support.list
            when {
                list == null && support.listError != null -> ErrorBox(support.listError!!, vm::load)
                list == null -> Loading()
                else -> {
                    if (list.canOpen) {
                        PrimaryButton("New request", onNew)
                    } else {
                        Muted("You have the most open requests allowed. Reply on one of them, or close one first.")
                    }
                    if (list.tickets.isEmpty()) {
                        Muted("No requests yet. Something wrong with your connection? Tell us here.")
                    }
                    list.tickets.forEach { t ->
                        SectionCard(Modifier.clickable { onOpen(t.id) }) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(t.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                StatusChip(t.status)
                            }
                            Muted("Last message ${agoIso(t.lastMessageAt)}")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewTicketScreen(vm: SupportVm, onOpened: (Long) -> Unit, onClose: () -> Unit) {
    val support by vm.state.collectAsStateWithLifecycle()
    val categories = support.list?.categories.orEmpty()
    var category by rememberSaveable { mutableStateOf(categories.keys.firstOrNull().orEmpty()) }
    var body by rememberSaveable { mutableStateOf("") }

    PageScaffold("New request", onClose) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("What is it about?", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { (key, label) ->
                    FilterChip(selected = category == key, onClick = { category = key }, label = { Text(label) })
                }
            }
            OutlinedTextField(
                value = body,
                onValueChange = { body = it.take(2000) },
                label = { Text("Tell us what's happening") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            support.actionError?.let { Banner(it, "error") }
            PrimaryButton(
                "Send",
                { vm.create(category, body.trim(), onOpened) },
                enabled = category.isNotEmpty() && body.trim().length >= 5,
                busy = support.busy,
            )
        }
    }
}

@Composable
fun TicketScreen(id: Long, vm: SupportVm, onClose: () -> Unit) {
    val support by vm.state.collectAsStateWithLifecycle()
    var reply by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(id) { vm.open(id) }
    val ticket = support.thread?.takeIf { it.id == id }

    PageScaffold(ticket?.title ?: "Request", onClose) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                ticket == null && support.threadError != null -> ErrorBox(support.threadError!!, { vm.open(id) })
                ticket == null -> Loading()
                else -> {
                    StatusChip(ticket.status)
                    ticket.messages.forEach { m ->
                        val mine = m.from == "you"
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                            Text(
                                m.body,
                                modifier = Modifier
                                    .widthIn(max = 320.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(12.dp),
                            )
                            Muted((if (mine) "You" else "Your provider") + " · " + dateTime(m.at))
                        }
                    }
                    support.actionError?.let { Banner(it, "error") }
                    if (ticket.status != "closed") {
                        OutlinedTextField(
                            value = reply,
                            onValueChange = { reply = it.take(2000) },
                            label = { Text("Reply") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PrimaryButton("Send", { vm.reply(id, reply.trim()) { reply = "" } }, enabled = reply.isNotBlank(), busy = support.busy)
                        TextButton(onClick = { vm.close(id) }, enabled = !support.busy) { Text("My problem is solved - close this request") }
                    } else {
                        Muted("This request is closed. Open a new one if you still need help.")
                    }
                }
            }
        }
    }
}
