package run.sparo.myisp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import run.sparo.myisp.push.clearSupportReply
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import run.sparo.myisp.AppContainer
import run.sparo.myisp.R
import run.sparo.myisp.data.Message
import run.sparo.myisp.data.Provider
import run.sparo.myisp.ui.AppState
import run.sparo.myisp.ui.components.Banner
import run.sparo.myisp.ui.components.ErrorBox
import run.sparo.myisp.ui.components.Loading
import run.sparo.myisp.ui.components.Muted
import run.sparo.myisp.ui.components.PrimaryButton
import run.sparo.myisp.ui.components.RemoteLogo
import run.sparo.myisp.ui.components.StatusChip
import run.sparo.myisp.ui.parseIso
import run.sparo.myisp.ui.theme.AppIcons
import run.sparo.myisp.ui.theme.AppTheme
import run.sparo.myisp.ui.theme.Space
import run.sparo.myisp.ui.theme.StatusColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** How often the open chat asks for new messages, besides on coming back to the app. */
private const val REFRESH_MS = 15_000L

private enum class Delivery { Sending, Sent, Seen, Failed }

/** One line of the chat: a day divider, or a message bubble. */
private sealed interface ChatRow {
    val key: String

    data class Day(val label: String) : ChatRow {
        override val key get() = "day:$label"
    }

    data class Bubble(
        val id: Long,
        val localId: Long?,
        val body: String,
        val mine: Boolean,
        /** First and last of a run of messages from one side, for the corners. */
        val first: Boolean,
        val last: Boolean,
        val time: String?,
        val delivery: Delivery?,
        val avatar: Boolean,
    ) : ChatRow {
        override val key get() = if (localId != null) "pending:$localId" else "message:$id"
    }
}

private data class Entry(val id: Long, val localId: Long?, val body: String, val mine: Boolean, val at: Date?, val seen: Boolean, val failed: Boolean?)

/**
 * The thread as rows, newest first for a reversed list. Messages from one side
 * on one day form a run: rounded as a group, the time under the last one, the
 * ISP's logo beside it. Only your newest message says Sending, Sent or Seen;
 * every failed one says so, so each can be retried.
 */
private fun chatRows(messages: List<Message>, pending: List<PendingMessage>, today: String, yesterday: String): List<ChatRow> {
    val now = Date()
    val entries = messages.map { Entry(it.id, null, it.body, it.from == "you", parseIso(it.at), it.seenAt != null, null) } +
        pending.map { Entry(it.localId, it.localId, it.body, true, now, false, it.failed) }

    val dayKey = SimpleDateFormat("yyyyMMdd", Locale.US)
    val clock = SimpleDateFormat("HH:mm", Locale.UK)
    val todayKey = dayKey.format(now)
    val yesterdayKey = dayKey.format(Date(now.time - 86_400_000))
    fun label(d: Date): String = when (dayKey.format(d)) {
        todayKey -> today
        yesterdayKey -> yesterday
        else -> SimpleDateFormat(if (dayKey.format(d).take(4) == todayKey.take(4)) "EEE d MMM" else "d MMM yyyy", Locale.UK).format(d)
    }

    val lastMine = entries.indexOfLast { it.mine }
    val rows = mutableListOf<ChatRow>()
    entries.forEachIndexed { i, e ->
        val day = e.at?.let(dayKey::format)
        val prev = entries.getOrNull(i - 1)
        val next = entries.getOrNull(i + 1)
        if (e.at != null && day != prev?.at?.let(dayKey::format)) rows += ChatRow.Day(label(e.at))
        val sameAsPrev = prev != null && prev.mine == e.mine && prev.at?.let(dayKey::format) == day
        val sameAsNext = next != null && next.mine == e.mine && next.at?.let(dayKey::format) == day
        val delivery = when {
            e.failed == true -> Delivery.Failed
            i != lastMine -> null
            e.failed == false -> Delivery.Sending
            e.seen -> Delivery.Seen
            else -> Delivery.Sent
        }
        rows += ChatRow.Bubble(
            id = e.id,
            localId = e.localId,
            body = e.body,
            mine = e.mine,
            first = !sameAsPrev,
            last = !sameAsNext,
            time = if (!sameAsNext && e.localId == null) e.at?.let(clock::format) else null,
            delivery = delivery,
            avatar = !e.mine && !sameAsNext,
        )
    }

    return rows.asReversed()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketScreen(id: Long, vm: SupportVm, state: AppState, app: AppContainer, onClose: () -> Unit, onNew: () -> Unit) {
    val support by vm.state.collectAsStateWithLifecycle()
    val ticket = support.thread?.takeIf { it.id == id }
    val provider = state.provider
    // The ISP by name, as its notifications are titled - not the portal's title.
    val ispName = provider?.name.orEmpty()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var draft by rememberSaveable(id) { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }

    LaunchedEffect(id) { vm.open(id) }
    // New replies while the chat is on screen: at once when their push lands,
    // every few seconds besides, and straight away on coming back to it.
    LaunchedEffect(id) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            app.push.openTicket.value = id
            clearSupportReply(context, id)
            try {
                coroutineScope {
                    launch { app.push.supportReplies.collect { if (it == id) vm.refresh(id) } }
                    while (true) {
                        delay(REFRESH_MS)
                        vm.refresh(id)
                    }
                }
            } finally {
                if (app.push.openTicket.value == id) app.push.openTicket.value = null
            }
        }
    }

    // Notifications are asked for here, once: right after the customer has
    // written to their ISP, when a reply is what they are waiting for.
    var askPush by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(ticket != null) {
        if (ticket == null || !app.push.enabled || app.store.notificationsAsked || Build.VERSION.SDK_INT < 33) return@LaunchedEffect
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return@LaunchedEffect
        delay(800)
        askPush = true
    }

    // A gentle tick when the ISP's reply lands while the chat is open.
    val fromIsp = ticket?.messages?.count { it.from != "you" }
    var known by remember(id) { mutableIntStateOf(-1) }
    LaunchedEffect(fromIsp) {
        if (fromIsp == null) return@LaunchedEffect
        if (known in 0 until fromIsp) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        known = fromIsp
    }

    val today = stringResource(R.string.chat_today)
    val yesterday = stringResource(R.string.chat_yesterday)
    val rows = remember(ticket, support.pending, today) { chatRows(ticket?.messages.orEmpty(), support.pending, today, yesterday) }
    val listState = rememberLazyListState()
    val newest = rows.firstOrNull()
    // Follow new messages when already at the bottom, and always after sending.
    LaunchedEffect(newest?.key) {
        if (newest == null) return@LaunchedEffect
        if ((newest as? ChatRow.Bubble)?.mine == true || listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        Text(
                            ticket?.title ?: stringResource(R.string.help_title),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        ticket?.let { StatusChip(it.status) }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(AppIcons.Back, contentDescription = stringResource(R.string.back)) }
                },
                actions = {
                    val phone = provider?.branding?.contactPhone
                    val canClose = ticket != null && ticket.status != "closed"
                    if (canClose || !phone.isNullOrBlank()) {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(AppIcons.More, contentDescription = stringResource(R.string.chat_more)) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                if (canClose) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.chat_close_request)) },
                                        onClick = {
                                            menu = false
                                            confirmClose = true
                                        },
                                    )
                                }
                                if (!phone.isNullOrBlank()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.chat_call, ispName)) },
                                        leadingIcon = { Icon(AppIcons.Phone, contentDescription = null) },
                                        onClick = {
                                            menu = false
                                            openUrl(context, "tel:$phone")
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
        ) {
            when {
                ticket == null && support.threadError != null -> Box(Modifier.weight(1f).padding(Space.l)) {
                    ErrorBox(support.threadError!!, { vm.open(id) })
                }
                ticket == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Loading() }
                else -> {
                    LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        contentPadding = PaddingValues(horizontal = Space.m, vertical = Space.m),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .semantics { contentDescription = context.getString(R.string.chat_messages, ispName) },
                    ) {
                        items(rows, key = { it.key }) { row ->
                            when (row) {
                                is ChatRow.Day -> DayDivider(row.label)
                                is ChatRow.Bubble -> ChatBubble(
                                    row, provider, app, ispName,
                                    onRetry = { row.localId?.let { vm.retry(id, it) } },
                                    onCopy = {
                                        copy(context, ispName, row.body)
                                        Toast.makeText(context, context.getString(R.string.chat_copied), Toast.LENGTH_SHORT).show()
                                    },
                                )
                            }
                        }
                    }

                    support.actionError?.let { Banner(it, "error", Modifier.padding(horizontal = Space.m, vertical = Space.xs)) }
                    HorizontalDivider(color = AppTheme.colors.line)

                    if (ticket.status == "closed") {
                        Column(Modifier.fillMaxWidth().padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                            Muted(stringResource(R.string.chat_closed))
                            // The one thing left to do here, so it reads as the action.
                            PrimaryButton(stringResource(R.string.help_new_request), onNew)
                        }
                    } else {
                        Composer(
                            draft = draft,
                            onDraft = { draft = it.take(2000) },
                            placeholder = stringResource(R.string.chat_placeholder, ispName),
                            onSend = {
                                val body = draft.trim()
                                if (body.isNotEmpty()) {
                                    vm.send(id, body)
                                    draft = ""
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (askPush) {
        AlertDialog(
            onDismissRequest = {
                askPush = false
                app.store.notificationsAsked = true
            },
            title = { Text(stringResource(R.string.push_ask_title, ispName)) },
            text = { Text(stringResource(R.string.push_ask_body)) },
            confirmButton = {
                TextButton(onClick = {
                    askPush = false
                    app.store.notificationsAsked = true
                    if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text(stringResource(R.string.push_ask_yes)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    askPush = false
                    app.store.notificationsAsked = true
                }) { Text(stringResource(R.string.push_ask_no)) }
            },
        )
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text(stringResource(R.string.chat_close_confirm_title)) },
            text = { Text(stringResource(R.string.chat_close_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClose = false
                    vm.close(id)
                }) { Text(stringResource(R.string.chat_close_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClose = false }) { Text(stringResource(R.string.chat_keep_open)) }
            },
        )
    }
}

@Composable
private fun DayDivider(label: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        HorizontalDivider(Modifier.weight(1f), color = AppTheme.colors.line)
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.muted)
        HorizontalDivider(Modifier.weight(1f), color = AppTheme.colors.line)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    row: ChatRow.Bubble,
    provider: Provider?,
    app: AppContainer,
    ispName: String,
    onRetry: () -> Unit,
    onCopy: () -> Unit,
) {
    val round = 18.dp
    val joined = 4.dp
    val shape = if (row.mine) {
        RoundedCornerShape(topStart = round, topEnd = if (row.first) round else joined, bottomEnd = if (row.last) round else joined, bottomStart = round)
    } else {
        RoundedCornerShape(topStart = if (row.first) round else joined, topEnd = round, bottomEnd = round, bottomStart = if (row.last) round else joined)
    }
    val fill = if (row.mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val ink = if (row.mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Row(
        Modifier.fillMaxWidth().padding(top = if (row.first) Space.s else 2.dp),
        horizontalArrangement = if (row.mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (row.mine) {
            Spacer(Modifier.width(48.dp))
        } else {
            // Level with the bubble's bottom edge, above the time under it. The
            // padding sits outside the 32dp box: inside, it squashed the logo.
            Box(Modifier.padding(bottom = if (row.time != null) 22.dp else 0.dp)) {
                Box(Modifier.size(32.dp)) {
                    if (row.avatar) IspAvatar(provider, app, ispName)
                }
            }
            Spacer(Modifier.width(Space.s))
        }

        Column(Modifier.weight(1f, fill = false), horizontalAlignment = if (row.mine) Alignment.End else Alignment.Start) {
            Text(
                row.body,
                color = ink,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .alpha(if (row.delivery == Delivery.Failed || row.delivery == Delivery.Sending) 0.7f else 1f)
                    .clip(shape)
                    .background(fill)
                    .combinedClickable(onClick = { if (row.delivery == Delivery.Failed) onRetry() }, onLongClick = onCopy)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
            if (row.time != null || row.delivery != null) {
                Row(
                    Modifier.padding(top = Space.xs, start = Space.xs, end = Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    row.time?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.muted) }
                    if (row.time != null && row.delivery != null) Text("·", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.muted)
                    when (row.delivery) {
                        Delivery.Sending -> Text(stringResource(R.string.chat_sending), style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.muted)
                        Delivery.Sent -> Text(stringResource(R.string.chat_sent), style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.muted)
                        Delivery.Seen -> {
                            Icon(AppIcons.Check, contentDescription = null, tint = StatusColors.good, modifier = Modifier.size(14.dp))
                            Text(stringResource(R.string.chat_seen), style = MaterialTheme.typography.labelSmall, color = StatusColors.good)
                        }
                        Delivery.Failed -> Text(
                            stringResource(R.string.chat_not_sent),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = StatusColors.critical,
                            modifier = Modifier.heightIn(min = 24.dp).combinedClickable(onClick = onRetry),
                        )
                        null -> Unit
                    }
                }
            }
        }

        if (!row.mine) Spacer(Modifier.width(48.dp))
    }
}

/** The ISP's logo in a circle, or its first letter when it has none. */
@Composable
private fun IspAvatar(provider: Provider?, app: AppContainer, ispName: String) {
    val logo = provider?.branding?.logoUrl
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(AppTheme.colors.brandSoft),
        contentAlignment = Alignment.Center,
    ) {
        if (logo.isNullOrBlank()) {
            Text(ispName.trim().take(1).uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        } else {
            RemoteLogo(logo, app, 22.dp)
        }
    }
}

@Composable
private fun Composer(draft: String, onDraft: (String) -> Unit, placeholder: String, onSend: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        TextField(
            value = draft,
            onValueChange = onDraft,
            placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            maxLines = 5,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        )
        FilledIconButton(onClick = onSend, enabled = draft.isNotBlank(), modifier = Modifier.size(48.dp)) {
            Icon(AppIcons.Send, contentDescription = stringResource(R.string.chat_send), modifier = Modifier.size(20.dp))
        }
    }
}
