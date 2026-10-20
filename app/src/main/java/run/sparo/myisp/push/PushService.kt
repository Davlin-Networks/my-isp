package run.sparo.myisp.push

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import run.sparo.myisp.MainActivity
import run.sparo.myisp.MyIspApp
import run.sparo.myisp.R

/**
 * Pushes from Sparo. With the app closed, Android shows them itself from the
 * message's notification part, and a tap opens MainActivity with the data as
 * extras. This only runs with the app open: refresh the chat if it is the one
 * on screen, otherwise show the notification, which Android does not then.
 */
class PushService : FirebaseMessagingService() {
    private val push get() = (application as MyIspApp).container.push

    override fun onNewToken(token: String) = push.onNewToken(token)

    override fun onMessageReceived(message: RemoteMessage) {
        val ticketId = message.data[Push.EXTRA_TICKET]?.toLongOrNull()
        if (message.data["type"] != "support_reply" || ticketId == null) return

        push.supportReplies.tryEmit(ticketId)
        if (push.openTicket.value == ticketId) return

        showSupportReply(
            this,
            ticketId = ticketId,
            isp = message.notification?.title ?: getString(R.string.app_name),
            body = message.notification?.body.orEmpty(),
            conversation = message.data["title"],
        )
    }
}

/** The ISP's reply as a chat notification, opening that conversation on tap. */
fun showSupportReply(context: Context, ticketId: Long, isp: String, body: String, conversation: String?) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    val open = PendingIntent.getActivity(
        context,
        ticketId.toInt(),
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(Push.EXTRA_TICKET, ticketId.toString()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val you = Person.Builder().setName(context.getString(R.string.chat_you)).build()
    val sender = Person.Builder().setName(isp).build()
    val style = NotificationCompat.MessagingStyle(you)
        .setConversationTitle(conversation)
        .addMessage(body, System.currentTimeMillis(), sender)

    val notification = NotificationCompat.Builder(context, Push.CHANNEL_SUPPORT)
        .setSmallIcon(R.drawable.ic_stat_myisp)
        .setContentTitle(isp)
        .setContentText(body)
        .setStyle(style)
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(open)
        .build()

    runCatching { NotificationManagerCompat.from(context).notify(Push.supportTag(ticketId), 0, notification) }
}

/** The conversation is open: its notification has done its job. */
fun clearSupportReply(context: Context, ticketId: Long) {
    NotificationManagerCompat.from(context).cancel(Push.supportTag(ticketId), 0)
}
