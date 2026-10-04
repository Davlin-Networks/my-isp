package run.sparo.myisp.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import run.sparo.myisp.AppConfig
import run.sparo.myisp.AppContainer
import run.sparo.myisp.BuildConfig
import run.sparo.myisp.data.ApiException
import run.sparo.myisp.ui.AppState
import run.sparo.myisp.ui.AppViewModel
import run.sparo.myisp.ui.ago
import run.sparo.myisp.ui.agoIso
import run.sparo.myisp.ui.components.Banner
import run.sparo.myisp.ui.components.Gap
import run.sparo.myisp.ui.components.Muted
import run.sparo.myisp.ui.components.PrimaryButton
import run.sparo.myisp.ui.components.RemoteLogo
import run.sparo.myisp.ui.components.SectionCard
import run.sparo.myisp.ui.components.StatusChip
import run.sparo.myisp.ui.dateTime
import run.sparo.myisp.ui.duration
import run.sparo.myisp.ui.kes
import run.sparo.myisp.ui.theme.StatusColors

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Usage("Usage", Icons.Filled.DateRange),
    Payments("Payments", Icons.AutoMirrored.Filled.List),
    Help("Help", Icons.Filled.Email),
}

/** Full-screen pages over the tabs. Saved as a string so it survives rotation. */
private object Route {
    const val NONE = ""
    const val RENEW = "renew"
    const val SETTINGS = "settings"
    const val PASSWORD = "password"
    const val NEW_TICKET = "ticket:new"
    fun ticket(id: Long) = "ticket:$id"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignedInScreen(state: AppState, app: AppContainer, vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var route by rememberSaveable { mutableStateOf(Route.NONE) }
    val usageVm: UsageVm = viewModel { UsageVm(app) }
    val supportVm: SupportVm = viewModel { SupportVm(app) }

    BackHandler(enabled = route != Route.NONE) { route = Route.NONE }
    BackHandler(enabled = route == Route.NONE && tab != Tab.Home) { tab = Tab.Home }

    when {
        route == Route.RENEW -> return RenewScreen(state, app, vm, onClose = { route = Route.NONE })
        route == Route.SETTINGS -> return SettingsScreen(state, vm, onPassword = { route = Route.PASSWORD }, onClose = { route = Route.NONE })
        route == Route.PASSWORD -> return PasswordScreen(app, onClose = { route = Route.SETTINGS })
        route == Route.NEW_TICKET -> return NewTicketScreen(supportVm, onOpened = { route = Route.ticket(it) }, onClose = { route = Route.NONE })
        route.startsWith("ticket:") -> return TicketScreen(route.removePrefix("ticket:").toLong(), supportVm, onClose = { route = Route.NONE })
    }

    val provider = state.provider ?: return
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RemoteLogo(provider.branding.logoUrl, app, 32.dp)
                        Text(provider.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                },
                actions = {
                    IconButton(onClick = { route = Route.SETTINGS }) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.Home -> HomeTab(state, usageVm, vm, onRenew = { route = Route.RENEW })
                Tab.Usage -> UsageTab(usageVm)
                Tab.Payments -> PaymentsTab(app, provider.name)
                Tab.Help -> HelpTab(state, supportVm, onNew = { route = Route.NEW_TICKET }, onOpen = { route = Route.ticket(it) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTab(state: AppState, usageVm: UsageVm, vm: AppViewModel, onRenew: () -> Unit) {
    val usage by usageVm.state.collectAsStateWithLifecycle()
    val account = state.account
    val provider = state.provider ?: return
    val context = LocalContext.current

    LaunchedEffect(Unit) { usageVm.loadIfStale() }

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = {
            vm.refreshAccount()
            usageVm.load()
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            provider.content.announcement?.let { Banner(it.text, it.tone) }
            state.refreshError?.let { Banner("$it${if (state.accountAt > 0) " Updated ${ago(state.accountAt)}." else ""}", "warning") }

            if (account == null) {
                Muted("Loading your account…")
                return@Column
            }

            SectionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Hi ${account.name?.substringBefore(' ') ?: account.username}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    StatusChip(account.status)
                }
                account.plan?.let { plan ->
                    Text(plan.name + (plan.rateLimit?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyLarge)
                }
                if (account.expiresAt != null) {
                    val days = account.daysRemaining
                    Text(
                        if (account.status == "active" && days != null && days > 0) {
                            "Expires ${dateTime(account.expiresAt)} · $days day${if (days == 1) "" else "s"} left"
                        } else {
                            "Expired ${dateTime(account.expiresAt)}"
                        },
                        color = if ((days ?: 0) <= 3) StatusColors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                account.wallet?.takeIf { it.enabled && provider.content.payments.showWallet }?.let {
                    Text("Wallet credit: ${kes(it.balance)}", style = MaterialTheme.typography.bodyMedium)
                }
                Gap(4.dp)
                if (account.renewable) {
                    PrimaryButton("Renew with M-Pesa", onRenew)
                } else {
                    PrimaryButton("Renewal opens ${dateTime(account.renewOpensAt)}", {}, enabled = false)
                }
                Muted("Username: ${account.username} · updated ${ago(state.accountAt)}")
            }

            usage.value?.connection?.let { c ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(if (c.online) "online" else "offline")
                    }
                    Text(
                        if (c.online) "Connected ${agoIso(c.since)} · ${duration(c.since, null)}"
                        else c.lastSeen?.let { "Last connected ${agoIso(it)}" } ?: "No connection seen yet.",
                    )
                }
            }

            val paybill = provider.content.payments.paybill
            if (paybill != null && account.accountRef != null) {
                SectionCard {
                    Text("Or pay from the M-Pesa menu", fontWeight = FontWeight.SemiBold)
                    Text("Paybill $paybill · Account ${account.accountRef}")
                    TextButton(onClick = { copy(context, "Account number", account.accountRef) }) { Text("Copy account number") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(state: AppState, vm: AppViewModel, onPassword: () -> Unit, onClose: () -> Unit) {
    PageScaffold("Settings", onClose) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard {
                Text(state.provider?.name.orEmpty(), fontWeight = FontWeight.SemiBold)
                Muted("Signed in as ${state.account?.username.orEmpty()}")
            }
            OutlinedButton(onClick = onPassword, modifier = Modifier.fillMaxWidth()) { Text("Change password") }
            if (AppConfig.fixedSlug == null) {
                OutlinedButton(onClick = { onClose(); vm.chooseAnotherProvider() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Use a different provider")
                }
            }
            OutlinedButton(onClick = vm::signOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
            HorizontalDivider()
            Muted("Version ${BuildConfig.VERSION_NAME}")
        }
    }
}

@Composable
private fun PasswordScreen(app: AppContainer, onClose: () -> Unit) {
    var current by rememberSaveable { mutableStateOf("") }
    var new by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var done by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    PageScaffold("Change password", onClose) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Muted("Your other devices will be signed out. This one stays signed in.")
            OutlinedTextField(current, { current = it }, label = { Text("Current password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(new, { new = it }, label = { Text("New password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = { Text("At least 4 characters") }, modifier = Modifier.fillMaxWidth())
            result?.let { Banner(it, if (done) "info" else "error") }
            PrimaryButton("Change password", {
                busy = true
                scope.launch {
                    try {
                        app.api.changePassword(current, new)
                        done = true
                        result = "Password changed."
                        current = ""
                        new = ""
                    } catch (e: ApiException) {
                        result = e.message
                    } finally {
                        busy = false
                    }
                }
            }, enabled = current.isNotEmpty() && new.length >= 4, busy = busy)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScaffold(title: String, onClose: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        content = content,
    )
}

fun copy(context: Context, label: String, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
}

fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
