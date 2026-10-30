// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.i18n

import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * The account's preferences are read again each time the app comes back to
 * the foreground, so one changed on the web applies without a restart, the
 * theme with the rest. The first start is the bootstrap's, and a signed-out
 * app reads nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreferencesWatchTest {

    private val context = RuntimeEnvironment.getApplication()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var session: SessionManager
    private lateinit var manager: PreferencesManager

    /** The app's own lifecycle, as the process's would run it. */
    private val owner = object : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    /** What the server holds for the account. */
    @Volatile private var served = mapOf("time_format" to "12h")

    /** How many times the preferences have been read. */
    private val reads = AtomicInteger()

    /** How many times the device's zone has been reported, ahead of a read. */
    private val boots = AtomicInteger()

    private val api = object : PreferencesApi {
        override suspend fun getPreferences(token: String): Response<FullPrefsResponse> {
            reads.incrementAndGet()
            return Response.success(
                FullPrefsResponse(
                    preferences = served,
                    themes = listOf(RawTheme("a", "A", 10.0, 0.1, 20.0), RawTheme("b", "B", 200.0, 0.2, 210.0)),
                    default_theme = "a",
                ),
            )
        }

        override suspend fun setPreferences(token: String, fields: Map<String, String>) = Response.success(mapOf<String, Any>())

        override suspend fun unsetPreferences(token: String, keys: List<String>) = Response.success(mapOf<String, Any>())

        override suspend fun resetPreferences(token: String) = Response.success(mapOf<String, Any>())
    }

    private val shell = object : ShellApi {
        override suspend fun boot(body: ShellRequest): Response<Map<String, Any>> {
            boots.incrementAndGet()
            return Response.success(mapOf<String, Any>())
        }
    }

    @Before
    fun begin() {
        FakeKeystore.install()
        session = SessionManager(context)
        // The store outlives a test in this process: each starts signed out.
        runBlocking { session.clearAll() }
        // Never reached: the settings token is already held.
        val retrofit = Retrofit.Builder()
            .baseUrl("http://localhost/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val auth = AuthRepository(retrofit.create(AuthApi::class.java), retrofit.create(TokenApi::class.java), session)
        manager = PreferencesManager(context, session, api, auth, shell)
        manager.watch(owner.lifecycle, scope)
    }

    @After
    fun end() {
        scope.cancel()
    }

    private fun signIn() = runBlocking {
        session.saveSession("cookie")
        session.saveToken("settings", "token")
    }

    /** The app leaves the foreground and comes back. */
    private fun comeBack() {
        owner.registry.currentState = Lifecycle.State.CREATED
        owner.registry.currentState = Lifecycle.State.STARTED
    }

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "timed out after ${reads.get()} reads" }
            Thread.sleep(10)
        }
    }

    /** Lets a read that was going to start, start. */
    private fun settle() {
        repeat(10) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    private fun theme() = runBlocking { session.themeAnchors.first() }

    @Test
    fun `the first start is left to the bootstrap`() {
        signIn()
        owner.registry.currentState = Lifecycle.State.STARTED
        settle()
        assertEquals(0, reads.get())
    }

    @Test
    fun `coming back reads the preferences changed elsewhere, and applies them`() {
        signIn()
        owner.registry.currentState = Lifecycle.State.STARTED
        // The device's own clock, en-US here, is 12-hour until the server says otherwise.
        assertEquals(TimeFormat.H12, manager.preferences.value.timeFormat)
        served = mapOf("time_format" to "24h")
        comeBack()
        until { manager.preferences.value.timeFormat == TimeFormat.H24 }
        assertEquals(1, reads.get())
    }

    @Test
    fun `a theme chosen elsewhere is the one drawn on coming back`() {
        signIn()
        owner.registry.currentState = Lifecycle.State.STARTED
        served = mapOf("theme" to "b")
        comeBack()
        until { theme() == SessionManager.ThemeAnchors(200f, 0.2f, 210f) }
    }

    @Test
    fun `each return reads them again`() {
        signIn()
        owner.registry.currentState = Lifecycle.State.STARTED
        comeBack()
        until { reads.get() == 1 }
        comeBack()
        until { reads.get() == 2 }
    }

    @Test
    fun `a signed-out app asks the server nothing on coming back`() {
        owner.registry.currentState = Lifecycle.State.STARTED
        comeBack()
        settle()
        assertEquals(0, boots.get())
        assertEquals(0, reads.get())
    }
}
