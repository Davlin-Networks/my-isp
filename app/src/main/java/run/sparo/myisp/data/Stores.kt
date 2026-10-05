package run.sparo.myisp.data

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The sign-in token, encrypted with an AES key that lives in this phone's
 * Android Keystore and never leaves it. Only the ciphertext is stored.
 *
 * SharedPreferences rather than DataStore: one small value, and no library
 * added to the APK for it. If the key is ever lost (a factory reset of the
 * keystore, a restored backup) the token cannot be read, so it is dropped
 * and the customer signs in again - never an error.
 */
class TokenStore(private val prefs: SharedPreferences) {
    @Volatile private var cached: String? = null
    @Volatile private var loaded = false

    fun get(): String? {
        if (!loaded) {
            cached = prefs.getString(KEY, null)?.let(::decrypt)
            loaded = true
        }
        return cached
    }

    fun set(token: String?) {
        cached = token
        loaded = true
        prefs.edit().apply {
            if (token == null) remove(KEY) else putString(KEY, encrypt(token))
        }.apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + sealed, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String? = try {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_BYTES))
        }
        String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    } catch (e: Exception) {
        prefs.edit().remove(KEY).apply()
        null
    }

    private companion object {
        const val KEY = "token"
        const val ALIAS = "myisp_token"
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}

/**
 * What the app remembers between launches, so it opens on the last known
 * account at once and refreshes behind it: which ISP, how it looks, and the
 * account as last seen. Nothing here is secret; the token is TokenStore's.
 */
class LocalStore(private val prefs: SharedPreferences) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    var slug: String?
        get() = prefs.getString("slug", null)
        set(value) = prefs.edit().putString("slug", value).apply()

    var provider: Provider?
        get() = read("provider", Provider.serializer())
        set(value) = write("provider", value, Provider.serializer())

    var account: Account?
        get() = read("account", Account.serializer())
        set(value) {
            write("account", value, Account.serializer())
            prefs.edit().putLong("account_at", if (value == null) 0 else System.currentTimeMillis()).apply()
        }

    val accountAt: Long get() = prefs.getLong("account_at", 0)

    /** "System", "Light" or "Dark" - the customer's choice in Settings. */
    var themeMode: String?
        get() = prefs.getString("theme_mode", null)
        set(value) = prefs.edit().putString("theme_mode", value).apply()

    var lastUsername: String?
        get() = prefs.getString("last_username", null)
        set(value) = prefs.edit().putString("last_username", value).apply()

    /** Everything about the signed-in customer. The ISP stays. */
    fun forgetAccount() {
        prefs.edit().remove("account").remove("account_at").apply()
    }

    /** Switching ISP: nothing of the old one may show under the new name. */
    fun forgetAll() {
        prefs.edit().remove("slug").remove("provider").remove("account").remove("account_at").remove("last_username").apply()
    }

    private fun <T> read(key: String, serializer: KSerializer<T>): T? =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }

    private fun <T> write(key: String, value: T?, serializer: KSerializer<T>) {
        prefs.edit().apply {
            if (value == null) remove(key) else putString(key, json.encodeToString(serializer, value))
        }.apply()
    }
}
