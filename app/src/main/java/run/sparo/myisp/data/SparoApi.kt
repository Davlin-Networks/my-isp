package run.sparo.myisp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * A refusal from the API, or no answer at all (status 0).
 *
 * `message` is written for the customer and safe to show as it is. `code`
 * is the stable reason to branch on (see the collection's Errors section).
 */
class ApiException(
    val status: Int,
    val code: String?,
    override val message: String,
    val retryAfter: Int? = null,
    val portalUrl: String? = null,
) : Exception(message) {
    val offline: Boolean get() = status == 0

    /** Worth trying the same request again: no answer, or the server's fault. */
    val retryable: Boolean get() = status == 0 || status >= 500
}

/**
 * The Sparo subscriber API: /api/v1/portal/{slug}... and /api/v1/my/...
 *
 * The token is the signed-in customer's own and sees their account only; the
 * app holds nothing else. Refusals that end the session - signed out, the ISP
 * turned the app off, this build too old - are also published on [sessionEnds]
 * so the app reacts in one place, whichever screen made the call.
 */
class SparoApi(
    private val http: OkHttpClient,
    private val baseUrl: String,
    private val sessionEnds: MutableSharedFlow<ApiException>,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    // ── Before sign-in ──────────────────────────────────────────────────────

    suspend fun provider(slug: String): Provider =
        request("GET", "/api/v1/portal/${enc(slug)}").data(Provider.serializer())

    suspend fun login(slug: String, username: String, password: String, device: String): Login =
        request("POST", "/api/v1/portal/${enc(slug)}/login", buildJsonObject {
            put("username", username)
            put("password", password)
            put("device_name", device.take(64))
        }).data(Login.serializer())

    suspend fun forgot(slug: String, username: String): String =
        request("POST", "/api/v1/portal/${enc(slug)}/forgot", buildJsonObject { put("username", username) })
            .string("message").orEmpty()

    // ── The signed-in account ───────────────────────────────────────────────

    suspend fun account(): Account = request("GET", "/api/v1/my/account").data(Account.serializer())

    suspend fun usage(): Usage = request("GET", "/api/v1/my/usage").data(Usage.serializer())

    suspend fun sessions(page: Int): SessionPage =
        json.decodeFromJsonElement(SessionPage.serializer(), request("GET", "/api/v1/my/sessions?page=$page"))

    suspend fun plans(): Plans {
        val body = request("GET", "/api/v1/my/packages")
        val meta = body.meta(PlansMeta.serializer()) ?: PlansMeta()
        return Plans(body.data(ListSerializer(Plan.serializer())), meta.currentPlanId, meta.currentOnly)
    }

    suspend fun payments(before: Long?): PaymentPage {
        val body = request("GET", "/api/v1/my/payments" + (before?.let { "?before=$it" } ?: ""))
        return PaymentPage(body.data(ListSerializer(Payment.serializer())), body.meta(PaymentsMeta.serializer())?.nextBefore)
    }

    /** `key` is new per tap of Pay, and the same on a retry of that tap. */
    suspend fun checkout(planId: Long, phone: String?, key: String): Checkout =
        request("POST", "/api/v1/my/checkouts", buildJsonObject {
            put("package_id", planId)
            put("phone", phone?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull)
        }, headers = mapOf("Idempotency-Key" to key)).data(Checkout.serializer())

    suspend fun checkoutStatus(id: String): CheckoutStatus =
        request("GET", "/api/v1/my/checkouts/${enc(id)}").data(CheckoutStatus.serializer())

    suspend fun tickets(): Tickets {
        val body = request("GET", "/api/v1/my/support")
        val meta = body.meta(TicketsMeta.serializer()) ?: TicketsMeta()
        return Tickets(body.data(ListSerializer(Ticket.serializer())), meta.categories, meta.canOpen)
    }

    suspend fun ticket(id: Long): Ticket = request("GET", "/api/v1/my/support/$id").data(Ticket.serializer())

    suspend fun openTicket(category: String, body: String): Ticket =
        request("POST", "/api/v1/my/support", buildJsonObject {
            put("category", category)
            put("body", body)
        }).data(Ticket.serializer())

    suspend fun reply(id: Long, body: String): Ticket =
        request("POST", "/api/v1/my/support/$id/reply", buildJsonObject { put("body", body) }).data(Ticket.serializer())

    suspend fun closeTicket(id: Long): Ticket =
        request("POST", "/api/v1/my/support/$id/close").data(Ticket.serializer())

    suspend fun changePassword(current: String, new: String) {
        request("POST", "/api/v1/my/password", buildJsonObject {
            put("current", current)
            put("password", new)
        })
    }

    suspend fun logout() {
        request("POST", "/api/v1/my/logout")
    }

    // ── Plumbing ────────────────────────────────────────────────────────────

    private suspend fun request(
        method: String,
        path: String,
        body: JsonObject? = null,
        headers: Map<String, String> = emptyMap(),
    ): JsonObject = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(baseUrl + path)
        headers.forEach { (k, v) -> builder.header(k, v) }
        when (method) {
            "GET" -> builder.get()
            else -> builder.method(method, (body ?: JsonObject(emptyMap())).toString().toRequestBody(JSON))
        }

        val response = try {
            http.newCall(builder.build()).await()
        } catch (e: IOException) {
            throw ApiException(0, "offline", "No connection. Check your internet and try again.")
        }

        response.use { res ->
            val text = res.body?.string().orEmpty()
            val parsed = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()

            if (res.isSuccessful) return@withContext parsed ?: JsonObject(emptyMap())

            val error = ApiException(
                status = res.code,
                code = parsed?.string("code"),
                message = parsed?.firstFieldError() ?: parsed?.string("message") ?: fallback(res.code),
                retryAfter = res.header("Retry-After")?.toIntOrNull(),
                portalUrl = parsed?.string("portal_url"),
            )

            val endsSession = res.code == 426 ||
                (path.startsWith("/api/v1/my/") && (res.code == 401 || error.code == "app_disabled" || error.code == "tenant_inactive"))
            if (endsSession) sessionEnds.tryEmit(error)

            throw error
        }
    }

    private fun <T> JsonObject.data(serializer: KSerializer<T>): T =
        json.decodeFromJsonElement(serializer, this["data"] ?: throw ApiException(500, null, fallback(500)))

    private fun <T> JsonObject.meta(serializer: KSerializer<T>): T? =
        this["meta"]?.let { json.decodeFromJsonElement(serializer, it) }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** Laravel's validation reply: the first message, which names the field. */
    private fun JsonObject.firstFieldError(): String? =
        (this["errors"] as? JsonObject)?.values?.firstNotNullOfOrNull { field ->
            (field as? JsonArray)?.firstOrNull()?.jsonPrimitive?.content
        }

    private fun fallback(status: Int): String = when {
        status == 429 -> "Too many tries. Wait a moment and try again."
        status >= 500 -> "Something went wrong on our side. Try again in a moment."
        else -> "That didn't work. Try again."
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}

/** OkHttp's call as a cancellable suspend: leaving the screen cancels it. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
}

