package run.sparo.myisp.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Immutable: the UI may skip redrawing anything built from an unchanged one.
 *
 * The Sparo subscriber API's shapes. Unknown fields are ignored, so the server can add
 * to any of these without breaking an installed app.
 */

@Immutable
@Serializable
data class Provider(
    val slug: String,
    val name: String,
    val branding: Branding,
    val content: Content = Content(),
    @SerialName("portal_url") val portalUrl: String? = null,
    @SerialName("min_app_version") val minAppVersion: String? = null,
)

@Immutable
@Serializable
data class Branding(
    @SerialName("portal_title") val portalTitle: String,
    val tagline: String? = null,
    @SerialName("primary_color") val primaryColor: String = "#3B82F6",
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("contact_phone") val contactPhone: String? = null,
)

@Immutable
@Serializable
data class Content(
    val announcement: Announcement? = null,
    @SerialName("support_hours") val supportHours: String? = null,
    val payments: PaymentOptions = PaymentOptions(),
)

@Immutable
@Serializable
data class Announcement(val text: String, val tone: String = "info")

@Immutable
@Serializable
data class PaymentOptions(
    val paybill: String? = null,
    @SerialName("show_wallet") val showWallet: Boolean = true,
)

@Immutable
@Serializable
data class Login(
    val token: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    val account: Account,
)

@Immutable
@Serializable
data class Account(
    val username: String,
    @SerialName("subscriber_name") val name: String? = null,
    val status: String,
    @SerialName("current_plan") val plan: Plan? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("days_remaining") val daysRemaining: Int? = null,
    val renewable: Boolean = true,
    @SerialName("renew_opens_at") val renewOpensAt: String? = null,
    @SerialName("account_ref") val accountRef: String? = null,
    val wallet: Wallet? = null,
)

@Immutable
@Serializable
data class Plan(
    val id: Long,
    val name: String,
    val price: Int,
    @SerialName("validity_days") val validityDays: Double = 0.0,
    @SerialName("rate_limit") val rateLimit: String? = null,
    val description: String? = null,
)

@Immutable
@Serializable
data class Wallet(val enabled: Boolean = false, val balance: Int = 0)

@Immutable
@Serializable
data class Usage(
    val connection: Connection,
    val usage: UsageNow = UsageNow(),
    val daily: List<Day> = emptyList(),
)

@Immutable
@Serializable
data class Connection(
    val online: Boolean = false,
    val since: String? = null,
    @SerialName("last_seen") val lastSeen: String? = null,
    val ip: String? = null,
    @SerialName("download_bytes") val down: Long = 0,
    @SerialName("upload_bytes") val up: Long = 0,
)

@Immutable
@Serializable
data class UsageNow(
    @SerialName("fair_usage") val fair: FairUsage? = null,
    val period: Period? = null,
)

@Immutable
@Serializable
data class FairUsage(
    @SerialName("resets_at") val resetsAt: String? = null,
    @SerialName("download_bytes") val down: Long = 0,
    @SerialName("upload_bytes") val up: Long = 0,
    val tier: Int = 0,
    @SerialName("base_rate") val baseRate: String? = null,
    val tiers: List<Tier> = emptyList(),
) {
    /** As the web portal counts it: both directions. */
    val used: Long get() = down + up
}

@Immutable
@Serializable
data class Tier(
    @SerialName("threshold_bytes") val threshold: Long,
    @SerialName("rate_limit") val rateLimit: String,
)

@Immutable
@Serializable
data class Period(
    val since: String,
    @SerialName("download_bytes") val down: Long = 0,
    @SerialName("upload_bytes") val up: Long = 0,
)

@Immutable
@Serializable
data class Day(
    val day: String,
    @SerialName("download_bytes") val down: Long = 0,
    @SerialName("upload_bytes") val up: Long = 0,
)

@Immutable
@Serializable
data class SessionRow(
    val id: Long,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    val online: Boolean = false,
    @SerialName("download_bytes") val down: Long = 0,
    @SerialName("upload_bytes") val up: Long = 0,
    @SerialName("ended_by") val endedBy: String? = null,
)

@Immutable
@Serializable
data class SessionPage(
    val data: List<SessionRow> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Immutable
data class Plans(val plans: List<Plan>, val currentPlanId: Long?, val currentOnly: Boolean)

@Immutable
@Serializable
data class PlansMeta(
    @SerialName("current_plan_id") val currentPlanId: Long? = null,
    @SerialName("current_plan_only") val currentOnly: Boolean = false,
)

@Immutable
@Serializable
data class Payment(
    val id: Long,
    val at: String? = null,
    val amount: Int = 0,
    val method: String? = null,
    val reference: String? = null,
    val plan: String? = null,
)

@Immutable
data class PaymentPage(val payments: List<Payment>, val nextBefore: Long?)

@Immutable
@Serializable
data class PaymentsMeta(@SerialName("next_before") val nextBefore: Long? = null)

@Immutable
@Serializable
data class Checkout(
    val id: String,
    val status: String,
    @SerialName("paid_from_credit") val paidFromCredit: Boolean = false,
    val amount: Int = 0,
    @SerialName("credit_used") val creditUsed: Int = 0,
    val message: String? = null,
    val phone: String? = null,
    val account: Account? = null,
)

@Immutable
@Serializable
data class CheckoutStatus(
    val id: String,
    val status: String,
    val message: String? = null,
    @SerialName("mpesa_receipt") val receipt: String? = null,
    val account: Account? = null,
)

@Immutable
@Serializable
data class Ticket(
    val id: Long,
    val title: String,
    val status: String,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    val messages: List<Message> = emptyList(),
)

@Immutable
@Serializable
data class Message(val id: Long, val from: String, val body: String, val at: String? = null)

@Immutable
data class Tickets(val tickets: List<Ticket>, val categories: Map<String, String>, val canOpen: Boolean)

@Immutable
@Serializable
data class TicketsMeta(
    val categories: Map<String, String> = emptyMap(),
    @SerialName("can_open") val canOpen: Boolean = true,
)
