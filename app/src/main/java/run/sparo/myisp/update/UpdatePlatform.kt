package run.sparo.myisp.update

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * What the updater remembers. In the app's preferences beside, but apart
 * from, the account: switching provider or signing out never resets it.
 */
class UpdateStore(private val prefs: SharedPreferences) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    var lastCheckedAt: Long
        get() = prefs.getLong("update_checked_at", 0)
        set(value) = prefs.edit().putLong("update_checked_at", value).apply()

    /** "Not now" on this version: the sheet no longer opens by itself for it. */
    var dismissedVersionCode: Int
        get() = prefs.getInt("update_dismissed", 0)
        set(value) = prefs.edit().putInt("update_dismissed", value).apply()

    /** The last version this phone ran, to say "You're on 1.2.0" once after updating. */
    var lastSeenVersionCode: Int
        get() = prefs.getInt("update_last_seen", 0)
        set(value) = prefs.edit().putInt("update_last_seen", value).apply()

    /** The newest release known, kept so its notes are there after installing. */
    var known: AppUpdate?
        get() = prefs.getString("update_known", null)?.let { runCatching { json.decodeFromString(AppUpdate.serializer(), it) }.getOrNull() }
        set(value) = prefs.edit().apply {
            if (value == null) remove("update_known") else putString("update_known", json.encodeToString(AppUpdate.serializer(), value))
        }.apply()

    /** A download still running when the app was closed, picked up on the next start. */
    var downloadId: Long
        get() = prefs.getLong("update_download_id", -1)
        set(value) = prefs.edit().putLong("update_download_id", value).apply()
}

/** Bytes so far, the total when known, and how it ended. */
data class DownloadProgress(val bytes: Long, val total: Long, val status: Int, val reason: Int)

/**
 * Android's DownloadManager: it keeps going when the app is left or killed,
 * follows the redirect to storage, resumes on a flaky connection and shows
 * its own notification. Into the app's own folder, so no storage permission.
 */
class UpdateDownloader(private val context: Context) {
    private val manager = context.getSystemService(DownloadManager::class.java)

    fun fileFor(update: AppUpdate): File = File(folder(), "update-${update.versionCode}.apk")

    fun start(update: AppUpdate, title: String): Long {
        val file = fileFor(update)
        file.delete()
        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle(title)
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(file))
        return manager.enqueue(request)
    }

    fun progress(id: Long): DownloadProgress? =
        manager.query(DownloadManager.Query().setFilterById(id))?.use { c: Cursor ->
            if (!c.moveToFirst()) return null
            DownloadProgress(
                bytes = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
            )
        }

    fun cancel(id: Long) {
        if (id >= 0) manager.remove(id)
    }

    /** Old downloads go: after installing, and on every start. */
    fun clean(keep: File? = null) {
        folder().listFiles()?.filter { it != keep }?.forEach(File::delete)
    }

    private fun folder(): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "updates").apply { mkdirs() }
}

/**
 * The checks before Android's installer sees the file: the bytes are the ones
 * published, and the APK is this app, signed with this app's key. Android
 * would refuse a different key itself; checking first means a clear message.
 */
class UpdateVerifier(private val context: Context) {
    sealed interface Result {
        data object Ok : Result
        data object Damaged : Result
        data object NotOurs : Result
    }

    fun verify(file: File, expectedSha256: String): Result {
        if (!file.isFile || !sha256(file).equals(expectedSha256, ignoreCase = true)) return Result.Damaged
        val archive = archiveInfo(file) ?: return Result.Damaged
        if (archive.packageName != context.packageName) return Result.NotOurs
        val theirs = signers(archive)
        val ours = signers(installedInfo())
        return if (theirs.isNotEmpty() && theirs == ours) Result.Ok else Result.NotOurs
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(file: File): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        return if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            context.packageManager.getPackageArchiveInfo(file.path, flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun installedInfo(): PackageInfo {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        return if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            context.packageManager.getPackageInfo(context.packageName, flags)
        }
    }

    /** The signing certificates, as SHA-256 hex, so two sets compare by value. */
    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val certs = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else {
            info.signatures
        }
        return certs.orEmpty().map { sha256(it.toByteArray()) }.toSet()
    }

    companion object {
        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

/**
 * Hands a verified APK to Android's installer through a PackageInstaller
 * session, which reports back - unlike the deprecated ACTION_VIEW intent.
 * Android always shows its own confirmation; nothing installs silently.
 */
class UpdateInstaller(private val context: Context) {
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** The system page where the customer allows installs from this app. */
    fun permissionIntent(): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun install(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("update.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            session.commit(InstallResults.replyTo(context, id).intentSender)
        }
    }
}

/** What the installer said, for whichever screen is listening. */
sealed interface InstallOutcome {
    data object Done : InstallOutcome
    data object Cancelled : InstallOutcome
    data class Failed(val message: String?) : InstallOutcome
}

/**
 * The installer's answer to a session commit, delivered to the app's own
 * screen rather than a background receiver. While the app is open it
 * arrives as a new intent. After a successful update Android has closed the
 * app, and on Android 10-13 this answer opens it again on the new version.
 * Android 14 blocks that (seen on a Galaxy A23: BAL_BLOCK), so there the
 * customer taps the icon - the panel says so before installing.
 */
object InstallResults {
    const val ACTION = "run.sparo.myisp.action.INSTALL_STATUS"

    val outcomes = MutableSharedFlow<InstallOutcome>(extraBufferCapacity = 4)

    fun isResult(intent: Intent?): Boolean = intent?.action == ACTION

    /** Where the installer replies: this app's screen, for this session. */
    fun replyTo(context: Context, sessionId: Int): PendingIntent {
        val intent = Intent(ACTION).setClassName(context, "run.sparo.myisp.MainActivity").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getActivity(context, sessionId, intent, flags)
    }

    fun handle(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.let { context.startActivity(it) }
            }
            PackageInstaller.STATUS_SUCCESS -> outcomes.tryEmit(InstallOutcome.Done)
            PackageInstaller.STATUS_FAILURE_ABORTED -> outcomes.tryEmit(InstallOutcome.Cancelled)
            else -> outcomes.tryEmit(InstallOutcome.Failed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)))
        }
    }
}
