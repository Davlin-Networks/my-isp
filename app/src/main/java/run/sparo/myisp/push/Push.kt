package run.sparo.myisp.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import run.sparo.myisp.BuildConfig
import run.sparo.myisp.R
import run.sparo.myisp.data.SparoApi
import run.sparo.myisp.data.TokenStore
import kotlin.coroutines.resume

/**
 * Push notifications through Firebase Cloud Messaging. Only messages: no
 * analytics, no other Firebase product.
 *
 * Firebase is started here from BuildConfig rather than by the Google
 * Services plugin, so a branded build whose package is not in Firebase yet
 * simply has [enabled] false and carries on without push.
 */
class Push(private val context: Context, private val api: SparoApi, private val tokens: TokenStore) {
    val enabled: Boolean = BuildConfig.FCM_APP_ID.isNotBlank()

    /** Support replies that arrived while the app was open: the ticket id. */
    val supportReplies = MutableSharedFlow<Long>(extraBufferCapacity = 8)

    /** The conversation on screen right now, if any: its push refreshes it rather than notify. */
    val openTicket = MutableStateFlow<Long?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("push", Context.MODE_PRIVATE)

    fun start() {
        if (!enabled) return
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FCM_APP_ID)
                    .setApiKey(BuildConfig.FCM_API_KEY)
                    .setProjectId(BuildConfig.FCM_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FCM_SENDER_ID)
                    .build(),
            )
        }
        createChannels()
    }

    /** Signed in: tell Sparo where to reach this phone. Quietly does nothing offline. */
    suspend fun register() {
        if (!enabled || tokens.get() == null) return
        val token = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull() ?: return
        runCatching { api.registerDevice(token) }.onSuccess { prefs.edit().putString("token", token).apply() }
    }

    /** FCM rotated the token. Registered at once if someone is signed in. */
    fun onNewToken(token: String) {
        if (tokens.get() == null) return
        scope.launch { runCatching { api.registerDevice(token) }.onSuccess { prefs.edit().putString("token", token).apply() } }
    }

    /**
     * Signing out: this phone must not show the next person anything meant
     * for the last. Before the session is revoked, while the call is still
     * allowed; ending the session removes it on Sparo's side anyway.
     */
    suspend fun unregister() {
        if (!enabled) return
        prefs.getString("token", null)?.let { token -> runCatching { api.unregisterDevice(token) } }
        prefs.edit().remove("token").apply()
        runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SUPPORT, context.getString(R.string.push_channel_support), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.push_channel_support_about)
            },
        )
    }

    companion object {
        /** Matches the server's FcmMessage::CHANNEL_SUPPORT. */
        const val CHANNEL_SUPPORT = "support"

        /** What a notification tap carries into MainActivity. */
        const val EXTRA_TICKET = "ticket_id"

        /** FCM's own notifications use this tag (and id 0); ours do too, so either is replaced or cleared. */
        fun supportTag(ticketId: Long) = "support-$ticketId"
    }
}

private suspend fun <T> Task<T>.await(): T? = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task -> cont.resume(if (task.isSuccessful) task.result else null) }
}
