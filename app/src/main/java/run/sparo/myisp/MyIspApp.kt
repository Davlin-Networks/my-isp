package run.sparo.myisp

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.Cache
import okhttp3.OkHttpClient
import run.sparo.myisp.data.ApiException
import run.sparo.myisp.data.LocalStore
import run.sparo.myisp.data.SparoApi
import run.sparo.myisp.data.TokenStore
import java.io.File
import java.util.concurrent.TimeUnit

class MyIspApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Everything the app shares, made once. No DI framework: there are five things. */
class AppContainer(context: Context) {
    private val prefs = context.getSharedPreferences("myisp", Context.MODE_PRIVATE)

    val tokens = TokenStore(prefs)
    val store = LocalStore(prefs)

    /** Refusals that end the session, from whichever screen made the call. */
    val sessionEnds = MutableSharedFlow<ApiException>(extraBufferCapacity = 8)

    val http: OkHttpClient = OkHttpClient.Builder()
        // Honours the server's ETags: an unchanged account is an empty 304.
        .cache(Cache(File(context.cacheDir, "http"), 5L * 1024 * 1024))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Accept", "application/json")
                .header("X-Sparo-Client", AppConfig.clientHeader)
            // Only to Sparo, never to a logo host.
            if (chain.request().url.toString().startsWith(AppConfig.baseUrl)) {
                tokens.get()?.let { request.header("Authorization", "Bearer $it") }
            }
            chain.proceed(request.build())
        }
        .build()

    val api = SparoApi(http, AppConfig.baseUrl, sessionEnds)

    /** Logos, decoded once per run. */
    val images = LruCache<String, Bitmap>(8)
}

/**
 * Build-time settings (app/build.gradle.kts). None is secret: this app is
 * open source and holds no credential.
 */
object AppConfig {
    val baseUrl: String = BuildConfig.BASE_URL.trimEnd('/')

    /** A branded build's ISP. Null in the generic build, which asks. */
    val fixedSlug: String? = BuildConfig.TENANT_SLUG.ifBlank { null }

    val clientHeader = "android/${BuildConfig.VERSION_NAME}"

    val deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(64)

    private val SLUG = Regex("^[a-z0-9][a-z0-9-]{0,62}$")

    /**
     * The ISP a QR code or link names: https://{LINK_HOST}/isp/{slug}, on
     * that host only. Anything else is not ours and is ignored.
     */
    fun slugFromLink(uri: Uri?): String? {
        if (uri == null || uri.scheme != "https" || uri.host != BuildConfig.LINK_HOST) return null
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != "isp") return null
        return segments[1].lowercase().takeIf { SLUG.matches(it) }
    }

    /** What a customer types: the ISP's code, or the link itself pasted in. */
    fun slugFromInput(input: String): String? {
        val text = input.trim()
        if (text.startsWith("https://")) return slugFromLink(Uri.parse(text))
        return text.lowercase().takeIf { SLUG.matches(it) }
    }
}
