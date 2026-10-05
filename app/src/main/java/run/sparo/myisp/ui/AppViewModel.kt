package run.sparo.myisp.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import run.sparo.myisp.AppConfig
import run.sparo.myisp.AppContainer
import run.sparo.myisp.data.Account
import run.sparo.myisp.data.ApiException
import run.sparo.myisp.data.Provider
import run.sparo.myisp.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where the app is: which screen family, never a detail of one. */
sealed interface Phase {
    data object Starting : Phase

    /** Generic build, no ISP yet: scan the ISP's QR code or type its code. */
    data class ChooseProvider(val busy: Boolean = false, val error: String? = null) : Phase

    /**
     * "Is this your provider?" - before any password, every time an ISP is
     * chosen. A QR sticker pasted over the real one cannot skip this.
     */
    data class ConfirmProvider(val provider: Provider, val switchingFrom: String?) : Phase

    data object SignIn : Phase

    data object Ready : Phase

    /** The ISP turned the app off, or this build is too old. */
    data class Unavailable(val message: String, val portalUrl: String?, val outdated: Boolean) : Phase
}

data class AppState(
    val phase: Phase = Phase.Starting,
    val provider: Provider? = null,
    val account: Account? = null,
    /** When [account] was last fetched - shown when it is a stored copy. */
    val accountAt: Long = 0,
    val refreshing: Boolean = false,
    val refreshError: String? = null,
    val signInBusy: Boolean = false,
    val signInError: String? = null,
    val notice: String? = null,
    val themeMode: ThemeMode = ThemeMode.System,
    /** Changes on every sign-in and sign-out: the screens' data belongs to one. */
    val session: Int = 0,
)

/**
 * One ISP and one signed-in account at a time, never mixed.
 * Switching ISP signs out of the old
 * one and forgets everything about it before the new one is shown.
 */
class AppViewModel(private val app: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(
        AppState(themeMode = runCatching { ThemeMode.valueOf(app.store.themeMode ?: "") }.getOrDefault(ThemeMode.System)),
    )
    val state: StateFlow<AppState> = _state

    private val signedIn: Boolean get() = app.tokens.get() != null

    /**
     * Where the signed-in screens keep their data (usage, payments, support).
     * One store per sign-in, cleared when it ends: what one customer loaded
     * can never show on the next one's screens - on a shared phone, or after
     * switching accounts. It lives here, not in the activity, so it survives
     * rotation but not a sign-out.
     */
    var sessionStore = ViewModelStore()
        private set

    private fun newSession() {
        sessionStore.clear()
        sessionStore = ViewModelStore()
        _state.update { it.copy(session = it.session + 1) }
    }

    override fun onCleared() {
        sessionStore.clear()
    }

    init {
        viewModelScope.launch {
            app.sessionEnds.collect(::onSessionEnded)
        }
    }

    /** First launch of this process, with the link that opened it if any. */
    fun start(link: Uri?) {
        if (_state.value.phase != Phase.Starting || starting) return
        starting = true
        // The Keystore decrypt behind `signedIn` stays off the main thread:
        // the first frame never waits on it.
        viewModelScope.launch {
            withContext(Dispatchers.IO) { app.tokens.get() }
            begin(link)
        }
    }

    private var starting = false

    private fun begin(link: Uri?) {

        val slug = AppConfig.fixedSlug ?: app.store.slug
        val cached = app.store.provider?.takeIf { it.slug == slug }

        when {
            slug == null -> _state.update { it.copy(phase = Phase.ChooseProvider()) }
            cached != null && signedIn -> {
                // The stored account first, then fresh behind it: the first
                // frame never waits on the network.
                _state.update {
                    it.copy(phase = Phase.Ready, provider = cached, account = app.store.account, accountAt = app.store.accountAt)
                }
                refreshProvider(slug)
                refreshAccount()
            }
            cached != null -> {
                _state.update { it.copy(phase = Phase.SignIn, provider = cached) }
                refreshProvider(slug)
            }
            else -> lookUp(slug, confirm = AppConfig.fixedSlug == null)
        }

        link?.let(::onLink)
    }

    /** A scanned QR code or tapped link while the app is open. */
    fun onLink(uri: Uri) {
        if (AppConfig.fixedSlug != null) return
        val slug = AppConfig.slugFromLink(uri) ?: return
        if (slug == _state.value.provider?.slug && _state.value.phase !is Phase.ChooseProvider) return
        lookUp(slug, confirm = true)
    }

    fun lookUpTyped(input: String) {
        val slug = AppConfig.slugFromInput(input)
        if (slug == null) {
            _state.update { it.copy(phase = Phase.ChooseProvider(error = "That doesn't look like a provider code. Check it and try again.")) }
            return
        }
        lookUp(slug, confirm = true)
    }

    private fun lookUp(slug: String, confirm: Boolean) {
        val switchingFrom = _state.value.provider?.takeIf { it.slug != slug && signedIn }?.name
        _state.update { it.copy(phase = Phase.ChooseProvider(busy = true)) }

        viewModelScope.launch {
            try {
                val provider = app.api.provider(slug)
                if (confirm) {
                    _state.update { it.copy(phase = Phase.ConfirmProvider(provider, switchingFrom)) }
                } else {
                    adopt(provider)
                }
            } catch (e: ApiException) {
                when {
                    e.code == "app_disabled" || e.status == 426 -> _state.update { it.copy(phase = unavailable(e)) }
                    e.status == 404 -> chooseError("We couldn't find that provider. Check the code, or scan their QR code again.")
                    else -> chooseError(e.message)
                }
            }
        }
    }

    fun confirmProvider() {
        val phase = _state.value.phase as? Phase.ConfirmProvider ?: return
        viewModelScope.launch {
            if (phase.provider.slug != app.store.slug) forgetEverything()
            adopt(phase.provider)
        }
    }

    fun cancelConfirm() {
        val provider = _state.value.provider
        _state.update {
            it.copy(phase = when {
                provider == null -> Phase.ChooseProvider()
                signedIn -> Phase.Ready
                else -> Phase.SignIn
            })
        }
    }

    /** From settings, generic build only. Nothing changes until the new ISP is confirmed. */
    fun chooseAnotherProvider() {
        if (AppConfig.fixedSlug != null) return
        _state.update { it.copy(phase = Phase.ChooseProvider()) }
    }

    private fun adopt(provider: Provider) {
        app.store.slug = provider.slug
        app.store.provider = provider
        _state.update {
            it.copy(provider = provider, phase = if (signedIn) Phase.Ready else Phase.SignIn, signInError = null)
        }
        if (signedIn) refreshAccount()
    }

    private fun refreshProvider(slug: String) {
        viewModelScope.launch {
            try {
                val provider = app.api.provider(slug)
                app.store.provider = provider
                _state.update { it.copy(provider = provider) }
            } catch (e: ApiException) {
                if (e.code == "app_disabled" || e.status == 426) _state.update { it.copy(phase = unavailable(e)) }
                // Anything else: keep the stored look; the next launch retries.
            }
        }
    }

    // ── Signing in and out ──────────────────────────────────────────────────

    fun signIn(username: String, password: String) {
        val provider = _state.value.provider ?: return
        if (username.isBlank() || password.isEmpty()) {
            _state.update { it.copy(signInError = "Enter your username and password.") }
            return
        }
        _state.update { it.copy(signInBusy = true, signInError = null) }

        viewModelScope.launch {
            try {
                val login = app.api.login(provider.slug, username.trim(), password, AppConfig.deviceName)
                app.tokens.set(login.token)
                newSession()
                app.store.lastUsername = login.account.username
                app.store.account = login.account
                _state.update {
                    it.copy(phase = Phase.Ready, account = login.account, accountAt = System.currentTimeMillis(), signInBusy = false, notice = null)
                }
            } catch (e: ApiException) {
                if (e.code == "app_disabled" || e.status == 426) {
                    _state.update { it.copy(phase = unavailable(e), signInBusy = false) }
                } else if (e.status == 404 && AppConfig.fixedSlug == null) {
                    // The ISP itself is gone (closed, or a stale code): choose again.
                    app.store.forgetAll()
                    _state.update { AppState(phase = Phase.ChooseProvider(error = "${provider.name} isn't on the app any more. Choose your provider again.")) }
                } else {
                    _state.update { it.copy(signInBusy = false, signInError = e.message) }
                }
            }
        }
    }

    suspend fun forgotPassword(username: String): String {
        val slug = _state.value.provider?.slug ?: return ""
        return try {
            app.api.forgot(slug, username.trim())
        } catch (e: ApiException) {
            e.message
        }
    }

    fun signOut() {
        viewModelScope.launch {
            // Revoke on the server first, but sign out here whatever it says.
            runCatching { app.api.logout() }
            forgetAccount()
            _state.update { it.copy(phase = Phase.SignIn, notice = null) }
        }
    }

    // ── The account ─────────────────────────────────────────────────────────

    fun refreshAccount() {
        if (!signedIn) return
        _state.update { it.copy(refreshing = true, refreshError = null) }
        viewModelScope.launch {
            try {
                applyAccount(app.api.account())
                _state.update { it.copy(refreshing = false) }
            } catch (e: ApiException) {
                _state.update { it.copy(refreshing = false, refreshError = if (e.offline) "Offline - showing what we had." else e.message) }
            }
        }
    }

    /** A fresher account from anywhere: a refresh, or a payment that went through. */
    fun applyAccount(account: Account) {
        app.store.account = account
        _state.update { it.copy(account = account, accountAt = System.currentTimeMillis()) }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    fun setThemeMode(mode: ThemeMode) {
        app.store.themeMode = mode.name
        _state.update { it.copy(themeMode = mode) }
    }

    private fun onSessionEnded(e: ApiException) {
        when {
            e.status == 426 || e.code == "app_disabled" || e.code == "tenant_inactive" ->
                _state.update { it.copy(phase = unavailable(e)) }
            e.status == 401 && signedIn -> {
                forgetAccount()
                _state.update { it.copy(phase = Phase.SignIn, notice = "You were signed out. Sign in again to carry on.") }
            }
        }
    }

    private fun unavailable(e: ApiException) = Phase.Unavailable(
        message = if (e.status == 426) "This version of the app is too old. Update it to carry on." else e.message,
        portalUrl = e.portalUrl ?: _state.value.provider?.portalUrl,
        outdated = e.status == 426,
    )

    private fun chooseError(message: String) =
        _state.update { it.copy(phase = Phase.ChooseProvider(error = message)) }

    private fun forgetAccount() {
        app.tokens.set(null)
        newSession()
        app.store.forgetAccount()
        _state.update { it.copy(account = null, accountAt = 0) }
    }

    private suspend fun forgetEverything() {
        if (signedIn) runCatching { app.api.logout() }
        app.tokens.set(null)
        newSession()
        app.store.forgetAll()
        _state.update { AppState(phase = it.phase) }
    }
}
