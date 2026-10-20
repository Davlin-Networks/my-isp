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
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import run.sparo.myisp.AppConfig
import run.sparo.myisp.AppContainer
import run.sparo.myisp.R
import run.sparo.myisp.data.Account
import run.sparo.myisp.data.ApiException
import run.sparo.myisp.data.Provider
import run.sparo.myisp.ui.AppState
import run.sparo.myisp.ui.AppViewModel
import run.sparo.myisp.ui.ago
import run.sparo.myisp.ui.bytes
import run.sparo.myisp.ui.components.Avatar
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
import run.sparo.myisp.update.AboutRow
import run.sparo.myisp.update.JustUpdatedChip
import run.sparo.myisp.update.UpdateLogoBadge
import run.sparo.myisp.update.UpdateSheet
import run.sparo.myisp.update.UpdateStage
import run.sparo.myisp.update.UpdateState
import run.sparo.myisp.update.WhatsNewSheet
import kotlin.math.ceil

private enum class Tab(val label: Int, val icon: () -> ImageVector) {
    Home(R.string.nav_home, { AppIcons.Home }),
    Usage(R.string.nav_usage, { AppIcons.Usage }),
    Payments(R.string.nav_payments, { AppIcons.Receipt }),
    Help(R.string.nav_help, { AppIcons.Headset }),
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
    val promoVm: PromoVm = viewModel { PromoVm(app) }
    // The plan a promotion asked to renew on, if one opened the sheet.
    var renewPlan by rememberSaveable { mutableStateOf<Long?>(null) }
    val update by app.updates.state.collectAsStateWithLifecycle()
    var updateSheet by rememberSaveable { mutableStateOf(false) }
    var whatsNew by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { app.updates.checkIfDue() }

    // Push: this phone, for this sign-in. Replies that arrive refresh the Help list.
    LaunchedEffect(state.session) { app.push.register() }
    LaunchedEffect(Unit) { app.push.supportReplies.collect { supportVm.load() } }
    // A tapped notification: straight to that conversation.
    val openTicket by vm.openTicket.collectAsStateWithLifecycle()
    LaunchedEffect(openTicket) {
        openTicket?.let {
            tab = Tab.Help
            route = Route.ticket(it)
            vm.ticketOpened()
        }
    }

    // An update never comes before money: not over the Renew sheet, another
    // page, or a plan that has run out. It waits for a calm moment on Home.
    val calm = tab == Tab.Home && route == Route.NONE && !renewing &&
        state.account?.status == "active" && (state.account.daysRemaining ?: 0) > 0
    LaunchedEffect(update.offersItself, calm) {
        if (update.offersItself && calm) {
            delay(1200)
            updateSheet = true
        }
    }
    // Downloaded and verified while the customer carried on: the sheet returns.
    LaunchedEffect(update.stage is UpdateStage.Ready, calm) {
        if (update.stage is UpdateStage.Ready && calm) updateSheet = true
    }

    BackHandler(enabled = route != Route.NONE) { route = Route.NONE }
    BackHandler(enabled = route == Route.NONE && tab != Tab.Home) { tab = Tab.Home }

    when {
        route == Route.SETTINGS -> return SettingsScreen(
            state, app, vm, update,
            onPassword = { route = Route.PASSWORD },
            onUpdate = {
                route = Route.NONE
                updateSheet = true
            },
            onClose = { route = Route.NONE },
        )
        route == Route.PASSWORD -> return PasswordScreen(app, onClose = { route = Route.SETTINGS })
        route == Route.NEW_TICKET -> return NewTicketScreen(supportVm, state, app, onOpened = { route = Route.ticket(it) }, onClose = { route = Route.NONE })
        route.startsWith("ticket:") -> return TicketScreen(
            route.removePrefix("ticket:").toLong(), supportVm, state, app,
            onClose = { route = Route.NONE },
            onNew = { route = Route.NEW_TICKET },
        )
    }

    val provider = state.provider ?: return
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            val online = usageVm.state.collectAsStateWithLifecycle().value.value?.connection?.online
            TopBar(provider, state, app, update, online, onUpdate = { updateSheet = true }, onSettings = { route = Route.SETTINGS })
            Box(Modifier.weight(1f)) {
                when (tab) {
                    Tab.Home -> HomeTab(
                        state, usageVm, vm, update, promoVm, app,
                        onRenew = {
                            renewPlan = null
                            renewing = true
                        },
                        onRenewPlan = {
                            renewPlan = it
                            renewing = true
                        },
                        onUsage = { tab = Tab.Usage },
                        onWhatsNew = { whatsNew = true },
                        onUpdatedSeen = app.updates::acknowledgeUpdated,
                    )
                    Tab.Usage -> UsageTab(usageVm, state)
                    Tab.Payments -> PaymentsTab(app, provider, state.account)
                    Tab.Help -> HelpTab(state, supportVm, onNew = { route = Route.NEW_TICKET }, onOpen = { route = Route.ticket(it) })
                }
            }
            BottomBar(tab, onSelect = { tab = it })
        }
        if (renewing) {
            RenewSheet(state, app, vm, onClose = { renewing = false }, preferredPlanId = renewPlan)
        }
        if (updateSheet && !renewing) {
            UpdateSheet(app.updates, update, provider.name, logo = { ProviderLogo(provider, app, 40.dp) }, onClose = { updateSheet = false })
        }
        if (whatsNew) {
            (update.justUpdated ?: app.updates.state.value.latest)?.let { WhatsNewSheet(it, onClose = { whatsNew = false }) }
                ?: run { whatsNew = false }
        }
    }
}

/** The ISP's logo, or its initials on the brand gradient when it has none. */
@Composable
fun ProviderLogo(provider: Provider, app: AppContainer, size: Dp) {
    val colors = AppTheme.colors
    if (provider.branding.logoUrl != null) {
        RemoteLogo(provider.branding.logoUrl, app, size)
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(13.dp)).background(colors.hero), contentAlignment = Alignment.Center) {
            Text(initials(provider.name), color = colors.onHero, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun TopBar(
    provider: Provider,
    state: AppState,
    app: AppContainer,
    update: UpdateState,
    online: Boolean?,
    onUpdate: () -> Unit,
    onSettings: () -> Unit,
) {
    val colors = AppTheme.colors
    val first = state.account?.name?.substringBefore(' ')?.takeIf { it.isNotBlank() } ?: state.account?.username.orEmpty()
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        // Where an update lives: a dot when one waits, a ring while it downloads.
        UpdateLogoBadge(update, 42.dp, onOpen = onUpdate) { ProviderLogo(provider, app, 42.dp) }
        Column(Modifier.weight(1f)) {
            Text(provider.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            if (state.accountAt > 0) {
                Text(stringResource(R.string.home_greeting, first, ago(state.accountAt)), style = MaterialTheme.typography.bodySmall, color = colors.muted, maxLines = 1)
            }
        }
        // The customer's own avatar: their account and settings, with whether
        // their line is online on its edge.
        val label = stringResource(R.string.account_open) + when (online) {
            true -> " · " + stringResource(R.string.account_online)
            false -> " · " + stringResource(R.string.account_offline)
            null -> ""
        }
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = stringResource(R.string.account_open), onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Avatar(
                name = state.account?.name?.takeIf { it.isNotBlank() } ?: state.account?.username.orEmpty(),
                size = 40.dp,
                // The contrast-corrected brand, not the hero gradient: a dark
                // brand colour vanished against the dark theme's background.
                ring = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                fill = MaterialTheme.colorScheme.surfaceContainerHigh,
                ink = MaterialTheme.colorScheme.onSurface,
                label = label,
                online = online,
                presence = if (online == true) colors.good else colors.neutral,
                cutout = MaterialTheme.colorScheme.background,
            )
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
private fun HomeTab(
    state: AppState,
    usageVm: UsageVm,
    vm: AppViewModel,
    update: UpdateState,
    promoVm: PromoVm,
    app: AppContainer,
    onRenew: () -> Unit,
    onRenewPlan: (Long) -> Unit,
    onUsage: () -> Unit,
    onWhatsNew: () -> Unit,
    onUpdatedSeen: () -> Unit,
) {
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
            promoVm.load()
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        val scroll = rememberScrollState()
        var heroBottom by remember { mutableIntStateOf(Int.MAX_VALUE) }
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            if (state.refreshError != null && state.accountAt > 0) {
                Banner(stringResource(R.string.home_offline_banner, ago(state.accountAt)), "warning")
            }
            if (account == null) {
                Muted(stringResource(R.string.loading_account))
                return@Column
            }
            update.justUpdated?.let { JustUpdatedChip(it, onOpen = onWhatsNew, onDone = onUpdatedSeen) }

            // The ISP's promotions lead Home while the plan is healthy. When
            // money is due - renew soon, expired, suspended - the plan and
            // Renew come first and the promotions wait below the wallet.
            val promosFirst = account.status == "active" && (account.daysRemaining ?: 0) > 0 && !account.renewable
            val promos: @Composable () -> Unit = {
                val promos by promoVm.promos.collectAsStateWithLifecycle()
                val promosHidden by promoVm.hidden.collectAsStateWithLifecycle()
                if (!promosHidden) {
                    PromoCarousel(promos, provider.name, app, onHide = promoVm::hideForAWeek, onTap = { tap ->
                        when (tap) {
                            is PromoTap.Plan -> onRenewPlan(tap.packageId)
                            is PromoTap.Link -> openUrl(context, tap.url)
                            PromoTap.None -> Unit
                        }
                    })
                }
            }
            if (promosFirst) promos()

            Box(Modifier.onGloballyPositioned { heroBottom = it.boundsInParent().bottom.toInt() }) {
                Hero(account, onRenew)
            }

            QuickActions(
                provider = provider,
                accountRef = account.accountRef,
                renewable = account.renewable,
                onRenew = onRenew,
            )

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
                var hidden by rememberSaveable { mutableStateOf(app.store.walletHidden) }
                WalletCard(kes(wallet.balance), account.username, provider.name, hidden) {
                    hidden = !hidden
                    app.store.walletHidden = hidden
                }
            }
            if (paybill != null) {
                val copyLabel = stringResource(R.string.home_copy_account)
                StatTile(
                    stringResource(R.string.home_paybill), paybill, stringResource(R.string.home_account, account.accountRef!!), Modifier.fillMaxWidth(),
                    action = AppIcons.Copy to copyLabel,
                    onAction = { copy(context, copyLabel, account.accountRef) },
                )
            }

            if (!promosFirst) promos()

            provider.content.announcement?.let { Banner(it.text, it.tone) }
            // Room for the Renew pill, so it never covers the last card.
            Spacer(Modifier.height(if (account.renewable) 72.dp else Space.l))
        }

        // Once the plan card has scrolled away, Renew follows as a pill -
        // only while renewal is open.
        val showPill = account?.renewable == true && scroll.value > heroBottom
        AnimatedVisibility(
            visible = showPill,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        ) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(role = Role.Button, onClick = onRenew)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                Icon(AppIcons.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.home_renew_pill), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * One tap for what people open the app to do, beyond reading the plan:
 * renew, the paybill account number, and calling or messaging the ISP.
 * Each shows only when the ISP has set it up.
 */
@Composable
private fun QuickActions(provider: Provider, accountRef: String?, renewable: Boolean, onRenew: () -> Unit) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    val phone = provider.branding.contactPhone?.takeIf { it.isNotBlank() }
    val paybill = provider.content.payments.paybill?.takeIf { accountRef != null }
    val copyLabel = stringResource(R.string.home_copy_account)
    val copied = stringResource(R.string.copied)

    data class Action(val label: String, val icon: ImageVector, val tint: Color?, val enabled: Boolean, val onClick: () -> Unit)

    val actions = buildList {
        add(Action(stringResource(R.string.quick_renew), AppIcons.Refresh, null, renewable, onRenew))
        if (paybill != null && accountRef != null) {
            add(Action(stringResource(R.string.quick_paybill), AppIcons.Copy, null, true) {
                copy(context, copyLabel, accountRef)
                android.widget.Toast.makeText(context, copied, android.widget.Toast.LENGTH_SHORT).show()
            })
        }
        if (phone != null) {
            val digits = phone.filter(Char::isDigit)
            val wa = if (digits.startsWith("0")) "254" + digits.drop(1) else digits
            add(Action(stringResource(R.string.help_call), AppIcons.Phone, null, true) { openUrl(context, "tel:$phone") })
            // WhatsApp's mark keeps its own green: it is recognised by it.
            add(Action(stringResource(R.string.help_whatsapp), AppIcons.WhatsApp, Color(0xFF25D366), true) { openUrl(context, "https://wa.me/$wa") })
        }
    }
    if (actions.size < 2) return

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(MaterialTheme.colorScheme.surfaceContainer).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        actions.forEach { a ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.s))
                    .then(if (a.enabled) Modifier.clickable(role = Role.Button, onClick = a.onClick) else Modifier)
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val tint = a.tint ?: MaterialTheme.colorScheme.primary
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(if (a.enabled) colors.soft(tint) else colors.line),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(a.icon, contentDescription = null, tint = if (a.enabled) tint else colors.muted, modifier = Modifier.size(22.dp))
                }
                Text(
                    a.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (a.enabled) MaterialTheme.colorScheme.onSurface else colors.muted,
                    maxLines = 1,
                )
            }
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.l)).background(colors.hero).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProgressRing(
                fraction = days.toFloat() / total,
                color = on,
                track = on.copy(alpha = if (whiteText) 0.25f else 0.18f),
                size = 76.dp,
                stroke = 7.dp,
                label = stringResource(R.string.ring_label, days, total),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$days", color = on, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 26.sp)
                    Text(pluralStringResource(R.plurals.days_left, days), color = on.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(account.plan?.name ?: "—", color = on, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
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
                .height(44.dp)
                .clip(RoundedCornerShape(13.dp))
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
private fun WalletCard(amount: String, holder: String, issuer: String, hidden: Boolean, onToggle: () -> Unit) {
    val gold = Color(0xFFE8C77A)
    val goldDim = Color(0xFFCDB27A)
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF1A1712), Color(0xFF2B251B), Color(0xFF4A3F2B))))
            .border(1.dp, Brush.linearGradient(listOf(gold.copy(alpha = 0.55f), gold.copy(alpha = 0.12f), gold.copy(alpha = 0.4f))), shape)
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
            // Hide the balance, as M-Pesa does, for when someone is looking on.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Text(
                    if (hidden) "KES ••••" else amount,
                    color = gold,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 26.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                val toggleLabel = stringResource(if (hidden) R.string.wallet_show else R.string.wallet_hide)
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = toggleLabel, onClick = onToggle)
                        .semantics { contentDescription = toggleLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (hidden) AppIcons.Eye else AppIcons.EyeOff, contentDescription = null, tint = goldDim, modifier = Modifier.size(18.dp))
                }
            }
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
private fun SettingsScreen(
    state: AppState,
    app: AppContainer,
    vm: AppViewModel,
    update: UpdateState,
    onPassword: () -> Unit,
    onUpdate: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val account = state.account
    val provider = state.provider
    PageScaffold(stringResource(R.string.account_title), onClose) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(Space.l),
        ) {
            // Who is signed in, on the ISP's own gradient.
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.l)).background(colors.hero).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                    val name = account?.name?.takeIf { it.isNotBlank() } ?: account?.username.orEmpty()
                    Avatar(
                        name = name,
                        size = 68.dp,
                        ring = listOf(colors.onHero, colors.onHero.copy(alpha = 0.35f)),
                        fill = colors.onHero.copy(alpha = 0.16f),
                        ink = colors.onHero,
                        label = name,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(name, color = colors.onHero, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        Text("@" + account?.username.orEmpty(), color = colors.onHero.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        provider?.let { Text(it.name, color = colors.onHero.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    account?.plan?.let { HeroChip(it.name, colors.onHero) }
                    account?.accountRef?.let { ref ->
                        val copyLabel = stringResource(R.string.home_copy_account)
                        HeroChip(stringResource(R.string.home_account, ref), colors.onHero, AppIcons.Copy, copyLabel) { copy(context, copyLabel, ref) }
                    }
                }
            }

            GroupLabel(stringResource(R.string.appearance))
            ThemeSwitch(state.themeMode, vm::setThemeMode)

            GroupLabel(stringResource(R.string.account_security))
            Group {
                SettingRow(AppIcons.Key, stringResource(R.string.change_password), onPassword)
                if (AppConfig.fixedSlug == null) {
                    HorizontalDivider(Modifier.padding(start = 64.dp), color = colors.line)
                    SettingRow(AppIcons.Provider, stringResource(R.string.use_different_provider), { onClose(); vm.chooseAnotherProvider() })
                }
            }

            GroupLabel(stringResource(R.string.update_about))
            AboutRow(app.updates, update, onOpen = onUpdate)

            Group {
                SettingRow(AppIcons.SignOut, stringResource(R.string.sign_out), vm::signOut, tint = colors.critical, chevron = false)
            }
        }
    }
}

@Composable
private fun HeroChip(text: String, on: Color, icon: ImageVector? = null, iconLabel: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(on.copy(alpha = 0.16f))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = iconLabel, onClick = onClick) else Modifier)
            .heightIn(min = if (onClick != null) 36.dp else 0.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, color = on, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        if (icon != null) Icon(icon, contentDescription = null, tint = on, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = AppTheme.colors.muted,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp).semantics { heading() },
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(MaterialTheme.colorScheme.surfaceContainer)) {
        content()
    }
}

/** One row of the Account page: an icon in a tinted square, the words, a chevron. */
@Composable
private fun SettingRow(icon: ImageVector, text: String, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurface, chevron: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).heightIn(min = 60.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = if (chevron) 0.08f else 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = if (chevron) Color.Unspecified else tint, modifier = Modifier.weight(1f))
        if (chevron) Icon(AppIcons.ChevronRight, contentDescription = null, tint = AppTheme.colors.muted, modifier = Modifier.size(18.dp))
    }
}

/** Phone setting / Light / Dark as one segmented control, not three loose chips. */
@Composable
private fun ThemeSwitch(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(MaterialTheme.colorScheme.surfaceContainer).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ThemeMode.entries.forEach { mode ->
            val on = mode == selected
            val label = stringResource(
                when (mode) {
                    ThemeMode.System -> R.string.theme_system
                    ThemeMode.Light -> R.string.theme_light
                    ThemeMode.Dark -> R.string.theme_dark
                },
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                    .clickable(role = Role.RadioButton, onClickLabel = label) { onSelect(mode) }
                    .semantics { this.selected = on }
                    .heightIn(min = 44.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (on) MaterialTheme.colorScheme.onSurface else colors.muted,
                    maxLines = 1,
                )
            }
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

