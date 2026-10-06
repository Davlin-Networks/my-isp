package run.sparo.myisp.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import run.sparo.myisp.AppConfig
import run.sparo.myisp.AppContainer
import run.sparo.myisp.BuildConfig
import run.sparo.myisp.R
import run.sparo.myisp.data.Account
import run.sparo.myisp.data.ApiException
import run.sparo.myisp.data.Provider
import run.sparo.myisp.ui.AppState
import run.sparo.myisp.ui.AppViewModel
import run.sparo.myisp.ui.ago
import run.sparo.myisp.ui.bytes
import run.sparo.myisp.ui.components.Banner
import run.sparo.myisp.ui.components.Muted
import run.sparo.myisp.ui.components.PrimaryButton
import run.sparo.myisp.ui.components.ProgressRing
import run.sparo.myisp.ui.components.PulseDot
import run.sparo.myisp.ui.components.RemoteLogo
import run.sparo.myisp.ui.components.SectionCard
import run.sparo.myisp.ui.dateTime
import run.sparo.myisp.ui.duration
import run.sparo.myisp.ui.kes
import run.sparo.myisp.ui.theme.AppIcons
import run.sparo.myisp.ui.theme.AppTheme
import run.sparo.myisp.ui.theme.Radius
import run.sparo.myisp.ui.theme.Space
import run.sparo.myisp.ui.theme.ThemeMode
import run.sparo.myisp.ui.theme.textOn
import kotlin.math.ceil

private enum class Tab(val label: Int, val icon: () -> ImageVector) {
    Home(R.string.nav_home, { AppIcons.Home }),
    Usage(R.string.nav_usage, { AppIcons.Usage }),
    Payments(R.string.nav_payments, { AppIcons.Receipt }),
    Help(R.string.nav_help, { AppIcons.Help }),
}

/** Full-screen pages over the tabs. Saved as a string so it survives rotation. */
private object Route {
    const val NONE = ""
    const val SETTINGS = "settings"
    const val PASSWORD = "password"
    const val NEW_TICKET = "ticket:new"
    fun ticket(id: Long) = "ticket:$id"
}

@Composable
fun SignedInScreen(state: AppState, app: AppContainer, vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var route by rememberSaveable { mutableStateOf(Route.NONE) }
    var renewing by rememberSaveable { mutableStateOf(false) }
    val usageVm: UsageVm = viewModel { UsageVm(app) }
    val supportVm: SupportVm = viewModel { SupportVm(app) }

    BackHandler(enabled = route != Route.NONE) { route = Route.NONE }
    BackHandler(enabled = route == Route.NONE && tab != Tab.Home) { tab = Tab.Home }

    when {
        route == Route.SETTINGS -> return SettingsScreen(state, vm, onPassword = { route = Route.PASSWORD }, onClose = { route = Route.NONE })
        route == Route.PASSWORD -> return PasswordScreen(app, onClose = { route = Route.SETTINGS })
        route == Route.NEW_TICKET -> return NewTicketScreen(supportVm, onOpened = { route = Route.ticket(it) }, onClose = { route = Route.NONE })
        route.startsWith("ticket:") -> return TicketScreen(route.removePrefix("ticket:").toLong(), supportVm, onClose = { route = Route.NONE })
    }

    val provider = state.provider ?: return
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(provider, state, app, onSettings = { route = Route.SETTINGS })
            Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.Home -> HomeTab(state, usageVm, vm, onRenew = { renewing = true }, onUsage = { tab = Tab.Usage })
                    Tab.Usage -> UsageTab(usageVm, state)
                    Tab.Payments -> PaymentsTab(app, provider)
                    Tab.Help -> HelpTab(state, supportVm, onNew = { route = Route.NEW_TICKET }, onOpen = { route = Route.ticket(it) })
                }
            }
            BottomBar(tab, onSelect = { tab = it })
        }
        if (renewing) {
            RenewSheet(state, app, vm, onClose = { renewing = false })
        }
    }
}

@Composable
private fun TopBar(provider: Provider, state: AppState, app: AppContainer, onSettings: () -> Unit) {
    val colors = AppTheme.colors
    val first = state.account?.name?.substringBefore(' ')?.takeIf { it.isNotBlank() } ?: state.account?.username.orEmpty()
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        if (provider.branding.logoUrl != null) {
            RemoteLogo(provider.branding.logoUrl, app, 42.dp)
        } else {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(colors.hero), contentAlignment = Alignment.Center) {
                Text(initials(provider.name), color = colors.onHero, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(provider.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            if (state.accountAt > 0) {
                Text(stringResource(R.string.home_greeting, first, ago(state.accountAt)), style = MaterialTheme.typography.bodySmall, color = colors.muted, maxLines = 1)
            }
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
            Icon(AppIcons.Settings, contentDescription = stringResource(R.string.settings))
        }
    }
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val colors = AppTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.glass)) {
        HorizontalDivider(color = colors.line)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp)) {
            Tab.entries.forEach { t ->
                val on = t == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.m))
                        .clickable(role = Role.Tab, onClickLabel = stringResource(t.label)) { onSelect(t) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier.clip(CircleShape).background(if (on) colors.brandSoft else Color.Transparent).padding(horizontal = 18.dp, vertical = 4.dp),
                    ) {
                        Icon(t.icon(), contentDescription = null, tint = if (on) MaterialTheme.colorScheme.primary else colors.muted, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        stringResource(t.label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (on) MaterialTheme.colorScheme.primary else colors.muted,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTab(state: AppState, usageVm: UsageVm, vm: AppViewModel, onRenew: () -> Unit, onUsage: () -> Unit) {
    val usage by usageVm.state.collectAsStateWithLifecycle()
    val account = state.account
    val provider = state.provider ?: return
    val colors = AppTheme.colors
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
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            if (state.refreshError != null && state.accountAt > 0) {
                Banner(stringResource(R.string.home_offline_banner, ago(state.accountAt)), "warning")
            }
            if (account == null) {
                Muted(stringResource(R.string.loading_account))
                return@Column
            }

            Hero(account, onRenew)

            // The live connection: the one thing customers open the app to check.
            val c = usage.value?.connection
            if (c != null) {
                val today = usage.value?.daily?.lastOrNull()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable(role = Role.Button, onClick = onUsage)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    if (c.online) {
                        PulseDot(colors.good)
                    } else {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(colors.neutral))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            when {
                                c.online -> stringResource(R.string.home_online, duration(c.since, null))
                                c.lastSeen != null -> stringResource(R.string.home_last_seen, ago(run.sparo.myisp.ui.parseIso(c.lastSeen)?.time ?: 0))
                                else -> stringResource(R.string.home_never_seen)
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (today != null && today.down + today.up > 0) {
                            Text(stringResource(R.string.home_today_data, bytes(today.down), bytes(today.up)), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                        } else if (!c.online) {
                            Text(stringResource(R.string.home_offline), style = MaterialTheme.typography.bodySmall, color = colors.muted)
                        }
                    }
                    Icon(AppIcons.ChevronRight, contentDescription = null, tint = colors.muted, modifier = Modifier.size(18.dp))
                }
            }

            val wallet = account.wallet?.takeIf { it.enabled && provider.content.payments.showWallet }
            val paybill = provider.content.payments.paybill?.takeIf { account.accountRef != null }
            if (wallet != null) {
                WalletCard(kes(wallet.balance), account.username, provider.name)
            }
            if (paybill != null) {
                val copyLabel = stringResource(R.string.home_copy_account)
                StatTile(
                    stringResource(R.string.home_paybill), paybill, stringResource(R.string.home_account, account.accountRef!!), Modifier.fillMaxWidth(),
                    action = AppIcons.Copy to copyLabel,
                    onAction = { copy(context, copyLabel, account.accountRef) },
                )
            }

            provider.content.announcement?.let { Banner(it.text, it.tone) }
            Spacer(Modifier.height(Space.l))
        }
    }
}

/** The plan at a glance: days left, status, end date, and the one action. */
@Composable
private fun Hero(account: Account, onRenew: () -> Unit) {
    val colors = AppTheme.colors
    val on = colors.onHero
    val whiteText = on == Color.White
    val days = (account.daysRemaining ?: 0).coerceAtLeast(0)
    // A renewal stacks onto the running plan, so days left can exceed one
    // plan's length; the ring is then simply full.
    val total = maxOf(1, ceil(account.plan?.validityDays ?: 30.0).toInt(), days)
    val (statusText, dot) = when {
        // "Renew soon" follows the server's renewal window, which is shorter
        // than three days on short plans; a fixed 1..3 days said it right
        // after a daily plan was bought, beside a greyed-out button.
        account.status == "active" && days > 0 && account.renewable -> stringResource(R.string.status_renew_soon) to Color(0xFFFBBF24)
        account.status == "active" && days > 0 -> stringResource(R.string.status_active) to Color(0xFF4ADE80)
        account.status == "suspended" -> stringResource(R.string.status_suspended) to Color(0xFFF87171)
        account.status == "inactive" -> stringResource(R.string.status_inactive) to Color(0xFF94A3B8)
        else -> stringResource(R.string.status_expired) to Color(0xFFF87171)
    }
    val buttonText = textOn(colors.heroStart, on)

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.l)).background(colors.hero).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ProgressRing(
                fraction = days.toFloat() / total,
                color = on,
                track = on.copy(alpha = if (whiteText) 0.25f else 0.18f),
                size = 104.dp,
                stroke = 9.dp,
                label = stringResource(R.string.ring_label, days, total),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$days", color = on, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 36.sp)
                    Text(pluralStringResource(R.plurals.days_left, days), color = on.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(account.plan?.name ?: "—", color = on, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                account.plan?.let { plan ->
                    Text(
                        stringResource(R.string.plan_meta, plan.rateLimit ?: "", run.sparo.myisp.ui.validity(plan.validityDays)),
                        color = on.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    Modifier.clip(CircleShape).background(if (whiteText) Color.Black.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.45f)).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                    Text(statusText, color = on, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (account.expiresAt != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(if (days > 0) R.string.home_ends else R.string.home_ended), color = on.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                Text(dateTime(account.expiresAt), color = on, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }
        val label = if (account.renewable) stringResource(R.string.home_renew) else stringResource(R.string.home_renew_opens, dateTime(account.renewOpensAt))
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (account.renewable) on else on.copy(alpha = 0.18f))
                .then(if (account.renewable) Modifier.clickable(role = Role.Button, onClick = onRenew) else Modifier)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = if (account.renewable) buttonText else on.copy(alpha = 0.8f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    hint: String,
    modifier: Modifier = Modifier,
    action: Pair<ImageVector, String>? = null,
    onAction: () -> Unit = {},
) {
    val colors = AppTheme.colors
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.muted)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.muted, maxLines = 1)
        }
        if (action != null) {
            IconButton(onClick = onAction, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(44.dp)) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colors.brandSoft), contentAlignment = Alignment.Center) {
                    Icon(action.first, contentDescription = action.second, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * Wallet credit as a card: dark, with gold, like the physical card it
 * stands for - the same in both themes, as a real card would be. Gold
 * here is decoration, not state, so it never stands in for a status colour.
 */
@Composable
private fun WalletCard(amount: String, holder: String, issuer: String) {
    val gold = Color(0xFFE8C77A)
    val goldDim = Color(0xFFCDB27A)
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF1A1712), Color(0xFF2B251B), Color(0xFF4A3F2B))))
            .border(1.dp, Brush.linearGradient(listOf(gold.copy(alpha = 0.55f), gold.copy(alpha = 0.12f), gold.copy(alpha = 0.4f))), shape)
            .semantics(mergeDescendants = true) {},
    ) {
        // A soft diagonal sheen, as light catches a card.
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.linearGradient(0f to Color.Transparent, 0.55f to Color.White.copy(alpha = 0.06f), 0.75f to Color.Transparent)),
        )
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(AppIcons.Diamond, contentDescription = null, tint = gold, modifier = Modifier.size(13.dp))
                Text(stringResource(R.string.home_wallet_label), color = gold, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.4.sp)
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.home_wallet_available), color = goldDim, style = MaterialTheme.typography.labelSmall)
            }
            Text(amount, color = gold, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 26.sp, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(holder.uppercase(), color = Color(0xFFF1E6CF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp, maxLines = 1)
                Text(issuer.uppercase(), color = goldDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp, maxLines = 1)
            }
            Text(stringResource(R.string.home_wallet_hint), color = goldDim.copy(alpha = 0.8f), fontSize = 10.5.sp)
        }
    }
}

private fun initials(name: String) =
    name.split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }

@Composable
private fun SettingsScreen(state: AppState, vm: AppViewModel, onPassword: () -> Unit, onClose: () -> Unit) {
    PageScaffold(stringResource(R.string.settings), onClose) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                Text(state.provider?.name.orEmpty(), fontWeight = FontWeight.SemiBold)
                Muted(stringResource(R.string.signed_in_as, state.account?.username.orEmpty()))
            }
            SectionCard {
                Text(stringResource(R.string.appearance), fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.themeMode == mode,
                            onClick = { vm.setThemeMode(mode) },
                            label = {
                                Text(
                                    stringResource(
                                        when (mode) {
                                            ThemeMode.System -> R.string.theme_system
                                            ThemeMode.Light -> R.string.theme_light
                                            ThemeMode.Dark -> R.string.theme_dark
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
            }
            OutlinedButton(onClick = onPassword, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(stringResource(R.string.change_password)) }
            if (AppConfig.fixedSlug == null) {
                OutlinedButton(onClick = { onClose(); vm.chooseAnotherProvider() }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text(stringResource(R.string.use_different_provider))
                }
            }
            OutlinedButton(onClick = vm::signOut, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(stringResource(R.string.sign_out)) }
            HorizontalDivider()
            Muted(stringResource(R.string.version, BuildConfig.VERSION_NAME))
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

    PageScaffold(stringResource(R.string.change_password), onClose) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Muted("Your other devices will be signed out. This one stays signed in.")
            OutlinedTextField(current, { current = it }, label = { Text("Current password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(new, { new = it }, label = { Text("New password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = { Text("At least 4 characters") }, modifier = Modifier.fillMaxWidth())
            result?.let { Banner(it, if (done) "info" else "error") }
            PrimaryButton(stringResource(R.string.change_password), {
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
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(AppIcons.Back, contentDescription = stringResource(R.string.back)) }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
        content = { padding -> Box(Modifier.navigationBarsPadding()) { content(padding) } },
    )
}

fun copy(context: Context, label: String, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
}

fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

