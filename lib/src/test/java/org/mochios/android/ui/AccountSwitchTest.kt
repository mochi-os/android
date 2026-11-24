// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui

import android.accounts.AccountManager
import android.content.Context
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.account.MochiAccount
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
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * The phone's accounts as the signed-in app sees them: one whose session the
 * keystore cannot open is still there, and not a sign-out; and switching to
 * another account on the same server signs in with that account's session,
 * not one a response left in memory.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountSwitchTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager
    private lateinit var model: AppBootstrapViewModel
    private lateinit var base: String

    /** The session each identity read carried. */
    private val sent = CopyOnWriteArrayList<String>()

    @Before
    fun begin() {
        FakeKeystore.install()
        AppContext.set(context)
        server = MockWebServer()
        // Each session signs in its own identity; the identity read sets the
        // cookie it was sent again, as an older core did on every request.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val cookie = request.getHeader("Cookie").orEmpty()
                val value = cookie.substringAfter("session=", "").substringBefore(';')
                return when (request.path) {
                    "/_/token" -> MockResponse().setBody("""{"token": "t"}""")
                    "/_/identity" -> {
                        sent += value
                        val identity = if (value == "sb") "B" else "A"
                        MockResponse()
                            .addHeader("Set-Cookie", "session=$value; Path=/")
                            .setBody("""{"user": {"email": "", "status": "active"}, "identity": {"id": "$identity", "name": "$identity", "fingerprint": "fp"}}""")
                    }
                    else -> MockResponse().setResponseCode(404).setBody("""{"error": "not here"}""")
                }
            }
        }
        server.start()
        base = server.url("/").toString().trimEnd('/')
        session = SessionManager(context)
        runBlocking {
            session.clearAll()
            session.setServerUrl(base)
            session.saveSession("sa")
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().cookieJar(session.cookieJar).build())
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

    /** Let what is queued run: an accounts update and what it starts. */
    private fun settle() {
        repeat(30) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }

    private fun ready(epoch: Long) = (model.stage.value as? AuthStage.Ready)?.epoch == epoch

    @Test
    fun `an account whose session cannot be read is not a sign-out`() {
        model.start("feeds")
        until { ready(1) }
        assertNotNull("published", MochiAccount.byIdentity(context, "A"))
        // The keystore no longer opens A's sealed session.
        val garbled = "sealed:" + Base64.getEncoder().encodeToString(Random(5).nextBytes(48))
        context.getSharedPreferences("mochi_sessions", Context.MODE_PRIVATE).edit().putString("A", garbled).commit()
        // Any account change tells every app.
        MochiAccount.upsert(context, "C", "Cy", "", "https://elsewhere.example", null, "sc")
        settle()
        assertTrue("THE POINT: still signed in", model.stage.value is AuthStage.Ready)
        assertEquals("sa", runBlocking { session.currentToken.first() })
        val listed = MochiAccount.byIdentity(context, "A")
        assertNotNull("the account stays on the phone", listed)
        assertNull("needing sign-in", listed!!.session)
    }

    @Test
    fun `switching to another account on the same server signs in with its own session`() {
        model.start("feeds")
        until { ready(1) }
        assertEquals("sa", sent.last())
        MochiAccount.upsert(context, "B", "B", "", base, null, "sb")
        settle()
        model.pickAccount(MochiAccount.byIdentity(context, "B")!!)
        until { ready(2) }
        assertEquals("THE POINT: B's session, not A's held from a response", "sb", sent.last())
        assertEquals("B", runBlocking { session.getBoundIdentity() })
        assertEquals("sa", MochiAccount.byIdentity(context, "A")!!.session)
        assertEquals("sb", MochiAccount.byIdentity(context, "B")!!.session)
    }

    @Test
    fun `a session kept for one account is not bound to another`() {
        runBlocking {
            session.saveSession("sb", "B")
            assertFalse("THE POINT: kept for B", session.bindSession("sb", "A"))
            assertTrue(session.bindSession("sb", "B"))
            // A sign-in's session names no account until its identity is read.
            session.saveSession("sx")
            assertTrue(session.bindSession("sx", "A"))
            assertFalse("then it is A's", session.bindSession("sx", "B"))
            assertFalse("nor is a session replaced since it was read bound", session.bindSession("sb", "A"))
        }
    }
}
