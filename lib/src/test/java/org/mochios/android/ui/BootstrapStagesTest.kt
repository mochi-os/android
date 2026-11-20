// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui

import android.accounts.AccountManager
import android.os.Looper
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.account.MochiAccount
import org.mochios.android.api.userMessage
import org.mochios.android.auth.AuthApi
import org.mochios.android.auth.AuthRepository
import org.mochios.android.auth.SessionManager
import org.mochios.android.auth.ShellApi
import org.mochios.android.auth.TokenApi
import org.mochios.android.i18n.AppContext
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
 * The start-up's stages for an account that needs something first: one with
 * no identity goes to making it, and a closing account whose reactivation
 * fails stays on the interstitial with the reason.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BootstrapStagesTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager
    private lateinit var model: AppBootstrapViewModel

    // What the server answers: the account's status and identity, and the
    // closure's cancel.
    @Volatile private var status = "active"
    @Volatile private var identity = """{"id": "i1", "name": "Ann", "fingerprint": "fp"}"""
    @Volatile private var cancel: MockResponse = MockResponse().setBody("{}")

    @Before
    fun begin() {
        FakeKeystore.install()
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/_/token" -> MockResponse().setBody("""{"token": "t"}""")
                "/_/identity" -> MockResponse().setBody(
                    """{"user": {"email": "ann@example.com", "status": "$status", "purge": 99}, "identity": $identity}"""
                )
                "/_/auth/close/cancel" -> cancel
                else -> MockResponse().setResponseCode(404).setBody("""{"error": "not here"}""")
            }
        }
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        session = SessionManager(context)
        runBlocking {
            session.clearAll()
            session.setServerUrl(base)
            session.saveSession("s1")
        }
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
        model = AppBootstrapViewModel(session, auth, preferences, socket, store, context)
    }

    @After
    fun end() {
        server.shutdown()
    }

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out at ${model.stage.value}" }
            Thread.sleep(10)
        }
    }

    @Test
    fun `a session with no identity goes to making one, not into the app`() {
        identity = "null"
        model.start("feeds")
        until { model.stage.value !is AuthStage.Booting && model.stage.value !is AuthStage.Bootstrapping }
        assertEquals(AuthStage.NeedsIdentity, model.stage.value)
    }

    @Test
    fun `abandoning the identity leaves the stage with the session`() {
        identity = "null"
        model.start("feeds")
        until { model.stage.value is AuthStage.NeedsIdentity }
        runBlocking { session.clearAll() }
        until { model.stage.value is AuthStage.NeedsLogin }
    }

    @Test
    fun `a reactivation that fails stays on the interstitial and says why`() {
        status = "closing"
        cancel = MockResponse().setResponseCode(500).setBody("""{"error": "x", "message": "Could not cancel the closure"}""")
        model.start("feeds")
        until { model.stage.value is AuthStage.NeedsReactivation }
        model.reactivate()
        until { (model.stage.value as? AuthStage.NeedsReactivation)?.failure != null }
        val stage = model.stage.value as AuthStage.NeedsReactivation
        assertEquals(99L, stage.purge)
        assertEquals("Could not cancel the closure", stage.failure!!.userMessage())
    }

    @Test
    fun `a reactivation that works goes on into the app`() {
        status = "closing"
        model.start("feeds")
        until { model.stage.value is AuthStage.NeedsReactivation }
        status = "active"
        model.reactivate()
        until { model.stage.value is AuthStage.Ready }
        assertTrue(model.stage.value is AuthStage.Ready)
    }

    @Test
    fun `an identity read that fails is not taken for none`() {
        identity = "null"
        // The read answers nothing usable.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/_/token" -> MockResponse().setBody("""{"token": "t"}""")
                else -> MockResponse().setResponseCode(500).setBody("""{"error": "down"}""")
            }
        }
        model.start("feeds")
        until { model.stage.value is AuthStage.Ready }
        assertTrue(model.stage.value !is AuthStage.NeedsIdentity)
    }
}
