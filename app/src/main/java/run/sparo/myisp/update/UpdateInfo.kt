package run.sparo.myisp.update

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A published build of this app, as GET /api/v1/app/latest?package=…
 * describes it. Asked for by this build's own package name, so a branded
 * build is only ever told about its own releases.
 */
@Immutable
@Serializable
data class AppUpdate(
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    @SerialName("min_sdk") val minSdk: Int? = null,
    /** This exact version, never "latest": the file checked is the file installed. */
    @SerialName("apk_url") val apkUrl: String,
    @SerialName("apk_sha256") val apkSha256: String,
    @SerialName("apk_size") val apkSize: Long,
    @SerialName("published_at") val publishedAt: String? = null,
    val notes: String? = null,
)

/** When to look for, and when to offer, an update. No Android in here, so it is unit-tested. */
object UpdatePolicy {
    /** At most one quiet check a day. */
    const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    fun dueForCheck(lastCheckedAt: Long, now: Long): Boolean =
        lastCheckedAt <= 0 || now - lastCheckedAt >= CHECK_INTERVAL_MS || now < lastCheckedAt

    /** Newer than this build, and installable on this phone. */
    fun isNewer(update: AppUpdate, currentVersionCode: Int, sdk: Int): Boolean =
        update.versionCode > currentVersionCode && (update.minSdk == null || sdk >= update.minSdk)

    /**
     * Whether the What's new sheet opens by itself. Only once per version:
     * "Not now" leaves the dot on the logo, and the next version asks afresh.
     */
    fun offersItself(update: AppUpdate, currentVersionCode: Int, sdk: Int, dismissedVersionCode: Int): Boolean =
        isNewer(update, currentVersionCode, sdk) && update.versionCode > dismissedVersionCode
}

/** One `##` section of the release notes: its heading, and the lines under it. */
@Immutable
data class NotesSection(val heading: String?, val kind: NotesKind, val bullets: List<String>, val paragraphs: List<String>)

enum class NotesKind { New, Fixed, Faster, Other }

/**
 * The release notes' Markdown subset: `## ` headings, `- ` bullets and
 * plain paragraphs. Nothing else is interpreted, so a stray `*` or `[`
 * shows as typed rather than as broken formatting.
 */
object ReleaseNotes {
    fun parse(notes: String?): List<NotesSection> {
        if (notes.isNullOrBlank()) return emptyList()
        val sections = mutableListOf<NotesSection>()
        var heading: String? = null
        var bullets = mutableListOf<String>()
        var paragraphs = mutableListOf<String>()

        fun close() {
            if (heading != null || bullets.isNotEmpty() || paragraphs.isNotEmpty()) {
                sections += NotesSection(heading, kindOf(heading), bullets, paragraphs)
            }
            bullets = mutableListOf()
            paragraphs = mutableListOf()
        }

        notes.lineSequence().map(String::trim).filter(String::isNotEmpty).forEach { line ->
            when {
                line.startsWith("## ") -> {
                    close()
                    heading = line.removePrefix("## ").trim()
                }
                line.startsWith("- ") -> bullets += line.removePrefix("- ").trim()
                else -> paragraphs += line
            }
        }
        close()
        return sections
    }

    /** Which icon a heading earns, in English or Kiswahili. */
    fun kindOf(heading: String?): NotesKind {
        val h = heading?.lowercase() ?: return NotesKind.Other
        return when {
            listOf("new", "mpya", "added").any { it in h } -> NotesKind.New
            listOf("fix", "rekebish", "imarish").any { it in h } -> NotesKind.Fixed
            listOf("fast", "speed", "haraka", "perform").any { it in h } -> NotesKind.Faster
            else -> NotesKind.Other
        }
    }
}
