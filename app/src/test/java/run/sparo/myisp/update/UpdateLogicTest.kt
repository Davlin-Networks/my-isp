package run.sparo.myisp.update

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The updater's decisions, without a phone (docs/self-update-plan.md). */
class UpdateLogicTest {
    private fun update(code: Int, minSdk: Int? = null) =
        AppUpdate(code, "1.$code.0", minSdk, "https://example.test/$code.apk", "ab", 1_600_000)

    // ── What to offer ───────────────────────────────────────────────────────

    @Test
    fun offersOnlyANewerBuild() {
        assertTrue(UpdatePolicy.isNewer(update(3), currentVersionCode = 2, sdk = 34))
        assertFalse(UpdatePolicy.isNewer(update(2), currentVersionCode = 2, sdk = 34))
        assertFalse(UpdatePolicy.isNewer(update(1), currentVersionCode = 2, sdk = 34))
    }

    @Test
    fun neverOffersABuildThePhoneCannotInstall() {
        assertFalse(UpdatePolicy.isNewer(update(3, minSdk = 26), currentVersionCode = 2, sdk = 24))
        assertTrue(UpdatePolicy.isNewer(update(3, minSdk = 26), currentVersionCode = 2, sdk = 26))
        assertTrue(UpdatePolicy.isNewer(update(3, minSdk = null), currentVersionCode = 2, sdk = 24))
    }

    @Test
    fun notNowStopsTheSheetForThatVersionOnly() {
        assertTrue(UpdatePolicy.offersItself(update(3), 2, 34, dismissedVersionCode = 0))
        assertFalse(UpdatePolicy.offersItself(update(3), 2, 34, dismissedVersionCode = 3))
        assertTrue(UpdatePolicy.offersItself(update(4), 2, 34, dismissedVersionCode = 3))
    }

    // ── When to check ───────────────────────────────────────────────────────

    @Test
    fun checksAtMostOnceADay() {
        val day = UpdatePolicy.CHECK_INTERVAL_MS
        assertTrue(UpdatePolicy.dueForCheck(lastCheckedAt = 0, now = 1_000))
        assertFalse(UpdatePolicy.dueForCheck(lastCheckedAt = 10 * day, now = 10 * day + day - 1))
        assertTrue(UpdatePolicy.dueForCheck(lastCheckedAt = 10 * day, now = 11 * day))
        // A clock set back must not silence checks for good.
        assertTrue(UpdatePolicy.dueForCheck(lastCheckedAt = 10 * day, now = 9 * day))
    }

    // ── What the server says ────────────────────────────────────────────────

    @Test
    fun readsTheServersAnswerAndIgnoresWhatItAddsLater() {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        val parsed = json.decodeFromString(
            AppUpdate.serializer(),
            """{"version_code":3,"version_name":"1.2.0","min_sdk":24,"apk_url":"https://app.example/releases/run.sparo.myisp/3.apk",
               "apk_sha256":"9f2c","apk_size":1612345,"published_at":"2026-10-12T09:00:00+03:00","notes":"## New\n- A","channel":"later"}""",
        )
        assertEquals(3, parsed.versionCode)
        assertEquals(24, parsed.minSdk)
        assertEquals(1_612_345L, parsed.apkSize)

        val bare = json.decodeFromString(
            AppUpdate.serializer(),
            """{"version_code":4,"version_name":"1.3.0","min_sdk":null,"apk_url":"u","apk_sha256":"x","apk_size":1,"notes":null}""",
        )
        assertNull(bare.minSdk)
        assertNull(bare.notes)
    }

    // ── Release notes ───────────────────────────────────────────────────────

    @Test
    fun splitsNotesIntoSectionsWithTheirIcons() {
        val sections = ReleaseNotes.parse(
            """
            Smoother updates.
            ## New
            - Updates arrive in the app
            - A ring on the logo
            ## Fixed
            - Receipts on small phones
            ## Haraka zaidi
            - Home opens faster
            ## Other
            Thanks for the reports.
            """.trimIndent(),
        )
        assertEquals(5, sections.size)
        assertNull(sections[0].heading)
        assertEquals(listOf("Smoother updates."), sections[0].paragraphs)
        assertEquals(NotesKind.New, sections[1].kind)
        assertEquals(listOf("Updates arrive in the app", "A ring on the logo"), sections[1].bullets)
        assertEquals(NotesKind.Fixed, sections[2].kind)
        assertEquals(NotesKind.Faster, sections[3].kind)
        assertEquals(NotesKind.Other, sections[4].kind)
    }

    @Test
    fun showsAnythingElseExactlyAsTyped() {
        val sections = ReleaseNotes.parse("- **bold** and [a link](x)\n# not a heading")
        assertEquals(listOf("**bold** and [a link](x)"), sections.single().bullets)
        assertEquals(listOf("# not a heading"), sections.single().paragraphs)
    }

    @Test
    fun noNotesIsNoSections() {
        assertTrue(ReleaseNotes.parse(null).isEmpty())
        assertTrue(ReleaseNotes.parse("  \n ").isEmpty())
    }

    // ── The download is the file published ──────────────────────────────────

    @Test
    fun hashesAFileTheWayTheServerDoes() {
        val file = File.createTempFile("update", ".apk").apply { writeText("abc") }
        try {
            // sha256("abc"), as PHP's hash_file('sha256', ...) gives it.
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", UpdateVerifier.sha256(file))
            assertEquals(UpdateVerifier.sha256("abc".toByteArray()), UpdateVerifier.sha256(file))
        } finally {
            file.delete()
        }
    }
}
