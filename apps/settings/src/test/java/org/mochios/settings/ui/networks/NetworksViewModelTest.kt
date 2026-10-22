// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.networks

import android.os.Looper
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.AuthApi
import org.mochios.android.auth.AuthRepository
import org.mochios.android.auth.SessionManager
import org.mochios.android.auth.TokenApi
import org.mochios.android.i18n.AppContext
import org.mochios.settings.R
import org.mochios.settings.api.NetworkAccount
import org.mochios.settings.api.NetworksApi
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * The Networks view model against a server: an OAuth link begun from the app
 * names the app's scheme and its PKCE challenge, keeps the verifier for the
 * return, and lands the link at the exchange; lists go up as one JSON list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NetworksViewModelTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager

    /** Each request the server took: its path and its body, decoded. */
    private val requests = CopyOnWriteArrayList<Pair<String, String>>()

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?").removePrefix("/")
                val body = request.body.readUtf8()
                requests += path to body + "|" + request.getHeader("Authorization").orEmpty()
                return when (path) {
                    "settings/-/user/networks/data" -> json("""{"offered": true, "networks": ["fediverse"]}""")
                    "settings/-/user/networks/account/link" ->
                        if (body.contains("challenge=")) {
                            json("""{"url": "https://m.example/oauth/authorize", "nonce": "n1"}""")
                        } else {
                            json("""{"id": "l1"}""")
                        }
                    "settings/-/user/networks/account/aliases" -> json("""{"id": "f1"}""")
                    "_/auth/oauth/exchange" -> json("""{"linked": "l2"}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        AppContext.set(context)
        FakeKeystore.install()
        session = SessionManager(context)
        runBlocking { session.clearAll() }
    }

    @After
    fun end() = server.shutdown()

    private fun json(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    private fun model(): NetworksViewModel {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val auth = AuthRepository(retrofit.create(AuthApi::class.java), retrofit.create(TokenApi::class.java), session)
        return NetworksViewModel(retrofit.create(NetworksApi::class.java), session, auth)
    }

    /** Waits for [done], running the main looper the server's answers resume on. */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun sent(path: String): List<Map<String, String>> = requests.filter { it.first == path }.map { (_, raw) ->
        raw.substringBefore("|").split("&").filter { it.contains("=") }.associate {
            val (k, v) = it.split("=", limit = 2)
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }
    }

    @Test
    fun `an OAuth link names the app's scheme and challenge, keeps the verifier with the nonce, and opens the consent`() {
        val model = model()
        until { model.uiState.value.data != null }
        var linked = false
        model.link("mastodon", mapOf("server" to "mastodon.social"), oauth = true) { linked = true }
        until { model.launch.value != null }
        val begun = sent("settings/-/user/networks/account/link").single()
        assertEquals("mastodon", begun["type"])
        assertEquals("mastodon.social", begun["server"])
        assertEquals("mochi", begun["scheme"])
        assertTrue(begun["challenge"].orEmpty().length >= 43)
        assertEquals("https://m.example/oauth/authorize", model.launch.value)
        val ceremony = runBlocking { session.oauthLinkCeremony() }
        assertTrue(ceremony.hasVerifier)
        assertEquals("n1", ceremony.nonce)
        assertTrue(linked)
    }

    @Test
    fun `a form link names no scheme and says the account is linked`() {
        val model = model()
        until { model.uiState.value.data != null }
        val said = CopyOnWriteArrayList<String>()
        val collecting = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launchCollect(model, said)
        model.link("lemmy", mapOf("server" to "lemmy.world", "username" to "bob", "password" to "pw"), oauth = false) {}
        until { said.isNotEmpty() }
        collecting.cancel()
        val begun = sent("settings/-/user/networks/account/link").single()
        assertNull(begun["scheme"])
        assertNull(begun["challenge"])
        assertEquals(context.getString(R.string.networks_linked), said.single())
        assertNull(model.launch.value)
    }

    @Test
    fun `the consent's return is landed at the exchange with the verifier and the settings token`() {
        runBlocking {
            session.saveToken("settings", "settings-token")
            session.saveOAuthLinkVerifier("v".repeat(43), "n1")
        }
        val model = model()
        until { model.uiState.value.data != null }
        runBlocking { session.setOAuthLinkReturn("code1", null) }
        until { requests.any { it.first == "_/auth/oauth/exchange" } }
        val (_, raw) = requests.first { it.first == "_/auth/oauth/exchange" }
        assertTrue(raw.contains("\"code\":\"code1\""))
        assertTrue(raw.contains("\"verifier\":\"" + "v".repeat(43) + "\""))
        assertTrue(raw.endsWith("|Bearer settings-token"))
        until { runBlocking { !session.oauthLinkCeremony().hasVerifier } }
        assertFalse(model.uiState.value.isLoading)
    }

    @Test
    fun `a refused consent forgets the verifier and lands nothing`() {
        runBlocking {
            session.saveToken("settings", "settings-token")
            session.saveOAuthLinkVerifier("v".repeat(43), "n1")
        }
        val model = model()
        until { model.uiState.value.data != null }
        runBlocking { session.setOAuthLinkReturn(null, "access_denied") }
        until { runBlocking { !session.oauthLinkCeremony().hasVerifier } }
        assertFalse(requests.any { it.first == "_/auth/oauth/exchange" })
    }

    @Test
    fun `aliases go up as one JSON list`() {
        val model = model()
        until { model.uiState.value.data != null }
        var saved = false
        model.aliases(NetworkAccount(id = "f1", type = "fediverse", hosted = true), listOf("@a@b.example", "@c@d.example")) { saved = true }
        until { saved }
        val body = sent("settings/-/user/networks/account/aliases").single()
        assertEquals("f1", body["id"])
        assertEquals("""["@a@b.example","@c@d.example"]""", body["aliases"])
    }

    private fun kotlinx.coroutines.CoroutineScope.launchCollect(model: NetworksViewModel, into: MutableList<String>) =
        launch { model.messages.collect { into += it } }
}
