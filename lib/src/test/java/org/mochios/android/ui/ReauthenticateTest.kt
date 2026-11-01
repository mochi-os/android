// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui

import android.accounts.AccountManager
import android.os.Looper
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.account.MochiAccount
import org.mochios.android.auth.AuthApi
import org.mochios.android.auth.AuthRepository
import org.mochios.android.auth.SessionManager
import org.mochios.android.auth.ShellApi
import org.mochios.android.auth.TokenApi
import org.mochios.android.i18n.FakeKeystore
import org.mochios.android.i18n.PreferencesApi
import org.mochios.android.i18n.PreferencesManager
import org.mochios.android.notifications.NotificationsApi
import org.mochios.android.notifications.NotificationsRepository
import org.mochios.android.notifications.NotificationsUnreadStore
import org.mochios.android.websocket.MochiWebSocket
import org.mochios.android.websocket.SocketSession
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Tapping the sign-in prompt for an account whose session is dead leads to
 * signing that account in: the app starts it, finds its session dead, and
 * opens the sign-in screen on its server - whether the tap launched the app or
 * found it already started.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReauthenticateTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager
    private lateinit var model: AppBootstrapViewModel
    private lateinit var base: String

    @Before
    fun begin() {
        FakeKeystore.install()
        server = MockWebServer()
        // The dead account's server: every session refused.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setResponseCode(401).setBody("""{"error": "signed out"}""")
        }
        server.start()
        base = server.url("/").toString().trimEnd('/')
        session = SessionManager(context)
        runBlocking { session.clearAll() }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val auth = AuthRepository(retrofit.create(AuthApi::class.java), retrofit.create(TokenApi::class.java), session)
        val preferences = PreferencesManager(context, session, retrofit.create(PreferencesApi::class.java), auth, retrofit.create(ShellApi::class.java))
        val socket = MochiWebSocket(OkHttpClient(), Gson(), object : SocketSession {
            override suspend fun serverUrl(): String = base

            override suspend fun token(app: String): String? = null

            override suspend fun invalidate(app: String) {
            }
        })
        val store = NotificationsUnreadStore(NotificationsRepository(retrofit.create(NotificationsApi::class.java)), socket, session, context)
        shadowOf(AccountManager.get(context)).addAuthenticator(MochiAccount.TYPE)
        MochiAccount.upsert(context, "a2", "Dead", "", base, null, "dead-session")
        model = AppBootstrapViewModel(session, auth, preferences, socket, store, context)
    }

    @After
    fun end() {
        server.shutdown()
    }

    /** Runs the main looper until [done]. */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out at ${model.stage.value}" }
            Thread.sleep(10)
        }
    }

    private fun shown(): String = runBlocking { session.serverUrl.first() }.trimEnd('/')

    @Test
    fun `a prompt that launches the app leads to signing its account in`() {
        model.reauthenticate("a2")
        model.start("feeds")
        until { model.stage.value is AuthStage.NeedsLogin }
        assertEquals("the sign-in screen's server", base, shown())
    }

    @Test
    fun `a prompt tapped once the app has started leads to signing its account in`() {
        model.start("feeds")
        until { model.stage.value is AuthStage.NeedsAccountChoice }
        model.reauthenticate("a2")
        until { model.stage.value is AuthStage.NeedsLogin }
        assertEquals("the sign-in screen's server", base, shown())
    }
}
