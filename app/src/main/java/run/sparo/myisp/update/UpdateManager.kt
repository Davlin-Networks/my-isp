package run.sparo.myisp.update

import android.app.DownloadManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import run.sparo.myisp.BuildConfig
import run.sparo.myisp.data.SparoApi

/** Where an update stands. One at a time, for the whole app. */
sealed interface UpdateStage {
    /** Nothing newer, or not checked yet. */
    data object None : UpdateStage
    data object Checking : UpdateStage
    data class Available(val update: AppUpdate) : UpdateStage
    data class Downloading(val update: AppUpdate, val bytes: Long, val total: Long) : UpdateStage {
        val fraction: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
    }
    data class NeedsPermission(val update: AppUpdate) : UpdateStage
    data class Ready(val update: AppUpdate) : UpdateStage
    data class Installing(val update: AppUpdate) : UpdateStage
    data class Failed(val update: AppUpdate?, val reason: Reason) : UpdateStage

    enum class Reason { Offline, Download, Damaged, NotOurs, Install }
}

data class UpdateState(
    val stage: UpdateStage = UpdateStage.None,
    /** When the last check finished, for "Checked today at 09:14". */
    val checkedAt: Long = 0,
    /** The newest version known, whatever stage this is at. */
    val latest: AppUpdate? = null,
    /** Versions dismissed with "Not now" no longer open the sheet by themselves. */
    val dismissedVersionCode: Int = 0,
    /** Set once after updating: the version now running, with its notes. */
    val justUpdated: AppUpdate? = null,
) {
    val offersItself: Boolean
        get() = stage is UpdateStage.Available &&
            UpdatePolicy.offersItself(stage.update, BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT, dismissedVersionCode)

    /** Anything to show on the logo: a dot when waiting, a ring while it moves. */
    val pending: Boolean get() = stage !is UpdateStage.None && stage !is UpdateStage.Checking &&
        !(stage is UpdateStage.Failed && stage.update == null)
}

/**
 * Finds, downloads, verifies and installs a newer build of this app
 * (docs/self-update-plan.md). Lives as long as the process, not a screen:
 * a download carries on while the customer uses the app, and is picked up
 * again if the app was closed mid-way.
 */
class UpdateManager(
    context: Context,
    private val api: SparoApi,
    private val store: UpdateStore,
) {
    private val appContext = context.applicationContext
    private val downloader = UpdateDownloader(appContext)
    private val verifier = UpdateVerifier(appContext)
    val installer = UpdateInstaller(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var polling: Job? = null

    private val _state = MutableStateFlow(UpdateState(checkedAt = store.lastCheckedAt, dismissedVersionCode = store.dismissedVersionCode))
    val state: StateFlow<UpdateState> = _state

    val enabled: Boolean get() = BuildConfig.SELF_UPDATE

    init {
        if (enabled) {
            noteVersionRunning()
            resumeDownload()
            scope.launch {
                InstallResults.outcomes.collect(::onInstallOutcome)
            }
        }
    }

    /** The quiet launch check: at most once a day, and silent if it fails. */
    fun checkIfDue() {
        if (!enabled || _state.value.stage !is UpdateStage.None) return
        if (!UpdatePolicy.dueForCheck(store.lastCheckedAt, System.currentTimeMillis())) {
            // Between checks, still offer what was found last time.
            store.known?.let { known ->
                if (UpdatePolicy.isNewer(known, BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT)) {
                    _state.update { it.copy(stage = UpdateStage.Available(known), latest = known) }
                }
            }
            return
        }
        check(manual = false)
    }

    /** Settings' "Check for updates", and the too-old screen: now, whatever the clock says. */
    fun check(manual: Boolean = true) {
        if (!enabled) return
        val busy = _state.value.stage
        if (busy is UpdateStage.Downloading || busy is UpdateStage.Installing || busy is UpdateStage.Checking) return
        _state.update { it.copy(stage = UpdateStage.Checking) }
        scope.launch {
            // A timeout is no answer, not "nothing newer": it is not counted as a check.
            val answer = try {
                withTimeoutOrNull(10_000) { Result.success(api.latestRelease(appContext.packageName)) }
            } catch (e: Exception) {
                Result.failure(e)
            }
            if (answer == null || answer.isFailure) {
                _state.update { it.copy(stage = if (manual) UpdateStage.Failed(null, UpdateStage.Reason.Offline) else UpdateStage.None) }
                return@launch
            }
            val found = answer.getOrNull()
            val now = System.currentTimeMillis()
            store.lastCheckedAt = now
            if (found != null && UpdatePolicy.isNewer(found, BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT)) {
                store.known = found
                _state.update { it.copy(stage = UpdateStage.Available(found), latest = found, checkedAt = now) }
            } else {
                _state.update { it.copy(stage = UpdateStage.None, latest = null, checkedAt = now) }
            }
        }
    }

    /** "Not now": the dot stays on the logo, the sheet stops opening by itself. */
    fun dismiss(update: AppUpdate) {
        store.dismissedVersionCode = update.versionCode
        _state.update { it.copy(dismissedVersionCode = update.versionCode) }
    }

    fun download(update: AppUpdate, title: String) {
        if (!enabled) return
        polling?.cancel()
        val id = runCatching { downloader.start(update, title) }.getOrElse {
            fail(update, UpdateStage.Reason.Download)
            return
        }
        store.downloadId = id
        store.known = update
        _state.update { it.copy(stage = UpdateStage.Downloading(update, 0, update.apkSize)) }
        poll(update, id)
    }

    fun cancelDownload() {
        polling?.cancel()
        downloader.cancel(store.downloadId)
        store.downloadId = -1
        val update = (_state.value.stage as? UpdateStage.Downloading)?.update ?: return
        _state.update { it.copy(stage = UpdateStage.Available(update)) }
    }

    /** Back from the system page: if installs were allowed, carry straight on. */
    fun continueAfterPermission() {
        val update = (_state.value.stage as? UpdateStage.NeedsPermission)?.update ?: return
        if (!installer.canInstall()) return
        _state.update { it.copy(stage = UpdateStage.Ready(update)) }
        install()
    }

    fun install() {
        val update = when (val s = _state.value.stage) {
            is UpdateStage.Ready -> s.update
            is UpdateStage.NeedsPermission -> s.update
            else -> return
        }
        if (!installer.canInstall()) {
            _state.update { it.copy(stage = UpdateStage.NeedsPermission(update)) }
            return
        }
        _state.update { it.copy(stage = UpdateStage.Installing(update)) }
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { installer.install(downloader.fileFor(update)) }.isSuccess }
            if (!ok) fail(update, UpdateStage.Reason.Install)
        }
    }

    /** Try again from wherever it failed: re-download unless the file is already good. */
    fun retry() {
        val failed = _state.value.stage as? UpdateStage.Failed ?: return
        val update = failed.update ?: return check()
        _state.update { it.copy(stage = UpdateStage.Available(update)) }
    }

    fun clearFailure() {
        val failed = _state.value.stage as? UpdateStage.Failed ?: return
        _state.update { it.copy(stage = failed.update?.let { u -> UpdateStage.Available(u) } ?: UpdateStage.None) }
    }

    fun acknowledgeUpdated() = _state.update { it.copy(justUpdated = null) }

    // ── Internals ───────────────────────────────────────────────────────────

    private fun poll(update: AppUpdate, id: Long) {
        polling = scope.launch {
            while (true) {
                val p = withContext(Dispatchers.IO) { downloader.progress(id) }
                when (p?.status) {
                    null -> {
                        // Removed from under us (cancelled from the notification).
                        store.downloadId = -1
                        _state.update { it.copy(stage = UpdateStage.Available(update)) }
                        return@launch
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        store.downloadId = -1
                        verify(update)
                        return@launch
                    }
                    DownloadManager.STATUS_FAILED -> {
                        store.downloadId = -1
                        downloader.cancel(id)
                        fail(update, UpdateStage.Reason.Download)
                        return@launch
                    }
                    else -> _state.update {
                        it.copy(stage = UpdateStage.Downloading(update, p.bytes.coerceAtLeast(0), if (p.total > 0) p.total else update.apkSize))
                    }
                }
                delay(400)
            }
        }
    }

    private suspend fun verify(update: AppUpdate) {
        val file = downloader.fileFor(update)
        when (withContext(Dispatchers.IO) { verifier.verify(file, update.apkSha256) }) {
            UpdateVerifier.Result.Ok -> _state.update {
                it.copy(stage = if (installer.canInstall()) UpdateStage.Ready(update) else UpdateStage.NeedsPermission(update))
            }
            UpdateVerifier.Result.Damaged -> {
                file.delete()
                fail(update, UpdateStage.Reason.Damaged)
            }
            UpdateVerifier.Result.NotOurs -> {
                file.delete()
                fail(update, UpdateStage.Reason.NotOurs)
            }
        }
    }

    private fun onInstallOutcome(outcome: InstallOutcome) {
        val update = (_state.value.stage as? UpdateStage.Installing)?.update ?: return
        when (outcome) {
            // Android replaces this process on success; this is rarely seen.
            InstallOutcome.Done -> _state.update { it.copy(stage = UpdateStage.None) }
            InstallOutcome.Cancelled -> _state.update { it.copy(stage = UpdateStage.Ready(update)) }
            is InstallOutcome.Failed -> fail(update, UpdateStage.Reason.Install)
        }
    }

    private fun fail(update: AppUpdate?, reason: UpdateStage.Reason) =
        _state.update { it.copy(stage = UpdateStage.Failed(update, reason)) }

    /** First start of a new version: say so once, and tidy the old download away. */
    private fun noteVersionRunning() {
        val current = BuildConfig.VERSION_CODE
        val lastSeen = store.lastSeenVersionCode
        if (lastSeen in 1 until current) {
            val known = store.known?.takeIf { it.versionCode == current }
            _state.update { it.copy(justUpdated = known ?: AppUpdate(current, BuildConfig.VERSION_NAME, apkUrl = "", apkSha256 = "", apkSize = 0)) }
        }
        if (lastSeen != current) store.lastSeenVersionCode = current
        if (store.known?.let { it.versionCode <= current } == true) store.known = null
        if (store.downloadId < 0) scope.launch(Dispatchers.IO) { downloader.clean() }
    }

    private fun resumeDownload() {
        val id = store.downloadId
        val known = store.known
        if (id < 0 || known == null) return
        _state.update { it.copy(stage = UpdateStage.Downloading(known, 0, known.apkSize), latest = known) }
        poll(known, id)
    }
}
