// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.i18n

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.AuthApi
import org.mochios.android.auth.AuthRepository
import org.mochios.android.auth.SessionManager
import org.mochios.android.auth.ShellApi
import org.mochios.android.auth.ShellRequest
import org.mochios.android.auth.TokenApi
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * A preference picked in Settings, as the web has it: it shows, theme and
 * appearance included, before the server answers, and the server's value
 * comes back if the server refuses. Of quick picks of one setting the newest
 * shows, and the language applies to the app only once its pick is answered.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreferencesPickTest {

    private val context = RuntimeEnvironment.getApplication()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var session: SessionManager
    private lateinit var manager: PreferencesManager

    /** What the server holds for the account. */
    @Volatile private var served = mapOf("time_format" to "12h", "maps" to "google", "language" to "en")

    /** One answer per save, in the order the saves arrive: true accepts it. */
    private val answers = ConcurrentLinkedQueue<CompletableDeferred<Boolean>>()

    /** How many saves the server has been sent. */
    private val sets = AtomicInteger()

    /** Whether a read of the preferences fails. */
    @Volatile private var down = false

    private val api = object : PreferencesApi {
        override suspend fun getPreferences(token: String): Response<FullPrefsResponse> = if (down) {
            Response.error(500, """{"error": "Down"}""".toResponseBody())
        } else Response.success(
            FullPrefsResponse(
                preferences = served,
                themes = listOf(RawTheme("a", "A", 10.0, 0.1, 20.0), RawTheme("b", "B", 200.0, 0.2, 210.0)),
                default_theme = "a",
            ),
        )

        override suspend fun setPreferences(token: String, fields: Map<String, String>): Response<Map<String, Any>> {
            val answer = CompletableDeferred<Boolean>()
            answers.add(answer)
            sets.incrementAndGet()
            if (!answer.await()) return Response.error(500, """{"error": "Refused"}""".toResponseBody())
            served = served + fields
            return Response.success(mapOf())
        }

        override suspend fun unsetPreferences(token: String, keys: List<String>) = Response.success(mapOf<String, Any>())

        override suspend fun resetPreferences(token: String) = Response.success(mapOf<String, Any>())
    }

    private val shell = object : ShellApi {
        override suspend fun boot(body: ShellRequest): Response<Map<String, Any>> = Response.success(mapOf())
    }

    @Before
    fun begin() {
        FakeKeystore.install()
        Locale.setDefault(Locale.US)
        LanguageStore.set(context, "en")
        session = SessionManager(context)
        runBlocking {
            session.clearAll()
            session.saveSession("cookie")
            session.saveToken("settings", "token")
        }
        val retrofit = Retrofit.Builder()
            .baseUrl("http://localhost/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val auth = AuthRepository(retrofit.create(AuthApi::class.java), retrofit.create(TokenApi::class.java), session)
        manager = PreferencesManager(context, session, api, auth, shell)
        runBlocking { manager.refresh() }
    }

    @After
    fun end() {
        answers.forEach { it.complete(false) }
        scope.cancel()
        LanguageStore.set(context, null)
        Locale.setDefault(Locale.US)
    }

    /** Sends a pick, and waits for the server to have it in hand. */
    private fun pick(key: String, value: String): Deferred<Result<Unit>> {
        val before = sets.get()
        val save = scope.async { runCatching { manager.setPreference(key, value) } }
        until { sets.get() > before }
        return save
    }

    /** Answers the oldest save not yet answered. */
    private fun answer(accept: Boolean) = answers.first { !it.isCompleted }.complete(accept)

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun theme() = runBlocking { session.themeAnchors.first() }

    private fun raw(key: String) = manager.rawPreferences()[key]

    @Test
    fun `a pick shows before the server answers, and the server's comes back when refused`() {
        val save = pick("time_format", "24h")
        assertEquals("24h", raw("time_format"))
        assertEquals(TimeFormat.H24, manager.preferences.value.timeFormat)

        answer(false)
        val result = runBlocking { save.await() }
        assertTrue(result.isFailure)
        assertEquals("12h", raw("time_format"))
        assertEquals(TimeFormat.H12, manager.preferences.value.timeFormat)
    }

    @Test
    fun `an accepted pick stays`() {
        val save = pick("time_format", "24h")
        answer(true)
        assertTrue(runBlocking { save.await() }.isSuccess)
        assertEquals("24h", raw("time_format"))
        assertEquals(TimeFormat.H24, manager.preferences.value.timeFormat)
    }

    @Test
    fun `an accepted pick stays when the read after it fails`() {
        val save = pick("time_format", "24h")
        down = true
        answer(true)
        assertTrue(runBlocking { save.await() }.isSuccess)
        assertEquals("24h", raw("time_format"))
    }

    @Test
    fun `a theme picked draws at once, and the old one comes back when refused`() {
        until { theme() == SessionManager.ThemeAnchors(10f, 0.1f, 20f) }
        val save = pick("theme", "b")
        until { theme() == SessionManager.ThemeAnchors(200f, 0.2f, 210f) }

        answer(false)
        runBlocking { save.await() }
        assertEquals(SessionManager.ThemeAnchors(10f, 0.1f, 20f), theme())
    }

    @Test
    fun `of quick picks the newest shows, and a refusal of it shows the older one the server took`() {
        val older = pick("maps", "osm")
        val newer = pick("maps", "apple")
        assertEquals("apple", raw("maps"))

        answer(true)
        runBlocking { older.await() }
        // The server read after the older answer still has the newer pick over it.
        assertEquals("apple", raw("maps"))

        answer(false)
        runBlocking { newer.await() }
        assertEquals("osm", raw("maps"))
    }

    @Test
    fun `a language applies to the app once its pick is answered, and a refused one never does`() {
        val save = pick("language", "fr")
        assertEquals("fr", raw("language"))
        assertNotEquals("fr", LanguageStore.get(context))
        answer(true)
        runBlocking { save.await() }
        assertEquals("fr", LanguageStore.get(context))

        val refused = pick("language", "de")
        answer(false)
        runBlocking { refused.await() }
        assertEquals("fr", LanguageStore.get(context))
        assertEquals("fr", raw("language"))
    }

    @Test
    fun `quick language picks apply only once the last is answered`() {
        val first = pick("language", "fr")
        val second = pick("language", "de")
        answer(true)
        runBlocking { first.await() }
        assertEquals("en", LanguageStore.get(context))
        answer(true)
        runBlocking { second.await() }
        assertEquals("de", LanguageStore.get(context))
    }
}
