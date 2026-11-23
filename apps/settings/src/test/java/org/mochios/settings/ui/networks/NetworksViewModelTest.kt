// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.networks

import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
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
import org.mochios.android.auth.PasskeyManager
import org.mochios.android.auth.SessionManager
import org.mochios.android.auth.TokenApi
import org.mochios.android.i18n.AppContext
import org.mochios.settings.R
import org.mochios.settings.api.AccountApi
import org.mochios.settings.api.Handle
import org.mochios.settings.api.NetworkAccount
import org.mochios.settings.api.NetworksApi
import org.mochios.settings.ui.login.SettingsStepUpClient
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
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

    /** Holds an account test's answer back until a test lets it go. */
    @Volatile private var testGate: CountDownLatch? = null

    /** When the server began and finished each change, in order. */
    private val order = CopyOnWriteArrayList<String>()

    /** Whether the server refuses a home feed. */
    @Volatile private var homeRefused = false

    /** Holds an account's making or removal back while it is set, and whether the making is refused. */
    @Volatile private var accountGate: CountDownLatch? = null
    @Volatile private var createRefused = false

    /** Holds the list back while it is set. */
    @Volatile private var dataGate: CountDownLatch? = null

    /** Whether the server refuses a password, an account's keys, or a name's check. */
    @Volatile private var passwordRefused = false
    @Volatile private var keysRefused = false
    @Volatile private var availableRefused = false

    private fun refused(status: Int, error: String) =
        MockResponse().setResponseCode(status).setBody("""{"error": "$error"}""").setHeader("Content-Type", "application/json")

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?").removePrefix("/")
                val body = request.body.readUtf8()
                requests += path to body + "|" + request.getHeader("Authorization").orEmpty()
                return when (path) {
                    "settings/-/user/networks/data" -> {
                        dataGate?.await(5, TimeUnit.SECONDS)
                        json("""{"offered": true, "networks": ["fediverse"]}""")
                    }
                    "settings/-/user/networks/handle/available" ->
                        if (availableRefused) refused(400, "Checks are paused") else json("""{"available": true, "name": "alice"}""")
                    "settings/-/user/networks/account/keys" ->
                        if (keysRefused) refused(400, "No keys here") else json("""[{"kind": "signing", "algorithm": "p256", "created": 1}]""")
                    "settings/-/user/networks/account/keys/recovery" -> {
                        order += "recovery"
                        json("""{"public": "did:key:z", "private": "deadbeef"}""")
                    }
                    "settings/-/user/networks/account/link" ->
                        if (body.contains("challenge=")) {
                            json("""{"url": "https://m.example/oauth/authorize", "nonce": "n1"}""")
                        } else {
                            json("""{"id": "l1"}""")
                        }
                    "settings/-/user/networks/account/aliases" -> json("""{"id": "f1"}""")
                    "settings/-/user/networks/account/test" -> {
                        order += "test"
                        testGate?.await(5, TimeUnit.SECONDS)
                        order += "tested"
                        json("""{"success": true, "message": "Connected"}""")
                    }
                    "settings/-/user/networks/account/update" -> {
                        order += "update"
                        json("""{"id": "b1", "label": "Work"}""")
                    }
                    "settings/-/user/networks/account/home" -> {
                        order += "home"
                        if (homeRefused) {
                            MockResponse().setResponseCode(400).setBody("""{"error": "The feed is not public"}""").setHeader("Content-Type", "application/json")
                        } else {
                            json("""{"id": "b1", "home": "n"}""")
                        }
                    }
                    "settings/-/user/networks/account/password" -> {
                        order += "password"
                        if (passwordRefused) refused(500, "Not now") else json("""{"password": "s3cret"}""")
                    }
                    "settings/-/user/networks/account/create" -> {
                        accountGate?.await(5, TimeUnit.SECONDS)
                        if (createRefused) {
                            MockResponse().setResponseCode(500).setBody("""{"error": "Not now"}""").setHeader("Content-Type", "application/json")
                        } else {
                            json("""{"id": "b1", "type": "bluesky", "identifier": "alice.a.example"}""")
                        }
                    }
                    "settings/-/user/networks/account/remove" -> {
                        accountGate?.await(5, TimeUnit.SECONDS)
                        json("""{}""")
                    }
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

    // Each test's view model is cleared when it ends. A model left running
    // keeps collecting the link return, and when a later test changes it the
    // model's edit waits on the main thread that test's setup is holding.
    private val models = ViewModelStore()

    @After
    fun end() {
        models.clear()
        server.shutdown()
    }

    private fun json(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    private fun model(): NetworksViewModel {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val auth = AuthRepository(retrofit.create(AuthApi::class.java), retrofit.create(TokenApi::class.java), session)
        val stepUp = SettingsStepUpClient(retrofit.create(AccountApi::class.java), PasskeyManager(context))
        val made = NetworksViewModel(retrofit.create(NetworksApi::class.java), session, auth, stepUp)
        return ViewModelProvider(models, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = made as T
        })[NetworksViewModel::class.java]
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

    // A network switch moves when tapped, or for a removal once that is
    // confirmed, not once the server has answered and the list been read
    // again; it goes back when the server refuses.
    @Test
    fun `a network switch moves at once and back when the account cannot be made`() {
        accountGate = CountDownLatch(1)
        createRefused = true
        val model = model()
        until { model.uiState.value.data != null }
        val handle = Handle(name = "alice", domain = "a.example")
        model.create(handle, "bluesky")
        assertEquals(mapOf("alice/bluesky" to true), model.uiState.value.moving)
        accountGate!!.countDown()
        until { model.uiState.value.moving.isEmpty() }
        assertTrue(model.uiState.value.busy.isEmpty())
    }

    @Test
    fun `a moved switch holds where it was moved until the list has been read again`() {
        val model = model()
        until { model.uiState.value.data != null }
        dataGate = CountDownLatch(1)
        model.create(Handle(name = "alice", domain = "a.example"), "fediverse")
        until { requests.count { it.first == "settings/-/user/networks/data" } == 2 && model.uiState.value.busy.isEmpty() }
        settle()
        // THE POINT: the account is made, but the list the switch falls back
        // to has not landed, so the switch stays where it was moved.
        assertEquals(mapOf("alice/fediverse" to true), model.uiState.value.moving)
        dataGate!!.countDown()
        until { model.uiState.value.moving.isEmpty() }
    }

    @Test
    fun `a new Bluesky account opens its keys on the recovery key's question`() {
        val model = model()
        until { model.uiState.value.data != null }
        model.create(Handle(name = "alice", domain = "a.example"), "bluesky")
        until { model.keysDialog.value?.keys != null }
        assertEquals("b1", model.keysDialog.value!!.account.id)
        assertTrue(model.keysDialog.value!!.offer)
    }

    @Test
    fun `a recovery key is made only with a step-up's proof, and held in the view model until its dialog closes`() {
        val model = model()
        until { model.uiState.value.data != null }
        val account = NetworkAccount(id = "b1", type = "bluesky", hosted = true)
        model.openKeys(account, offer = true)
        until { model.keysDialog.value?.keys != null }
        model.recovery(account)
        // The key can take the account away, so nothing is sent before the step-up.
        assertTrue(model.stepUp.visible.value)
        assertTrue(sent("settings/-/user/networks/account/keys/recovery").isEmpty())
        model.stepUp.onVerified("proof")
        until { model.keysDialog.value?.secret?.isNotEmpty() == true }
        assertEquals(mapOf("id" to "b1", "token" to "proof"), sent("settings/-/user/networks/account/keys/recovery").single())
        // THE POINT: the key is the view model's, which outlives the screen,
        // and goes only when the dialog is closed.
        assertEquals("deadbeef", model.keysDialog.value!!.secret)
        assertFalse(model.keysDialog.value!!.offer)
        model.closeKeys()
        assertNull(model.keysDialog.value)
    }

    @Test
    fun `keys that cannot be read say why, and are read again on a retry`() {
        keysRefused = true
        val model = model()
        until { model.uiState.value.data != null }
        model.openKeys(NetworkAccount(id = "f1", type = "fediverse", hosted = true))
        until { model.keysDialog.value?.failure != null }
        assertEquals("No keys here", model.keysDialog.value!!.failure)
        assertNull(model.keysDialog.value!!.keys)
        keysRefused = false
        model.retryKeys()
        until { model.keysDialog.value?.keys != null }
        assertNull(model.keysDialog.value!!.failure)
        assertEquals(listOf("signing"), model.keysDialog.value!!.keys!!.map { it.kind })
    }

    @Test
    fun `a name whose availability cannot be read is a failure, with the server's reason, not an answer`() {
        val model = model()
        until { model.uiState.value.data != null }
        assertTrue(runBlocking { model.available("alice", "a.example") }.getOrThrow().available)
        availableRefused = true
        val result = runBlocking { model.available("alice", "a.example") }
        assertTrue(result.isFailure)
        assertEquals("Checks are paused", failureText(result.exceptionOrNull()!!, R.string.networks_check_failed))
    }

    @Test
    fun `a network switch moves off at once when its removal is confirmed`() {
        accountGate = CountDownLatch(1)
        val model = model()
        until { model.uiState.value.data != null }
        model.remove(NetworkAccount(id = "f1", type = "fediverse", hosted = true, identifier = "@alice@a.example"))
        assertEquals(mapOf("f1" to false), model.uiState.value.moving)
        accountGate!!.countDown()
        until { model.uiState.value.moving.isEmpty() }
    }

    @Test
    fun `an OAuth link names the app's scheme and challenge, keeps the verifier with the nonce, and opens the consent`() {
        val model = model()
        until { model.uiState.value.data != null }
        var linked = false
        model.link("mastodon", mapOf("server" to "mastodon.social"), oauth = true) { linked = true }
        // A Mastodon account becomes a way to sign in, so nothing is sent
        // before the step-up, and the link carries its proof.
        assertTrue(model.stepUp.visible.value)
        assertTrue(sent("settings/-/user/networks/account/link").isEmpty())
        model.stepUp.onVerified("proof")
        until { model.launch.value != null }
        val begun = sent("settings/-/user/networks/account/link").single()
        assertEquals("proof", begun["token"])
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
        // A Lemmy account is no way to sign in, so it links with no step-up.
        assertFalse(model.stepUp.visible.value)
        until { said.isNotEmpty() }
        collecting.cancel()
        val begun = sent("settings/-/user/networks/account/link").single()
        assertNull(begun["token"])
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

    @Test
    fun `a password asked with a home feed saves the home feed first, and a refused one makes no password`() {
        val model = model()
        until { model.uiState.value.data != null }
        val account = NetworkAccount(id = "b1", type = "bluesky", hosted = true)
        model.openPassword(account)
        model.password(account, " Phone ", "n")
        // The password never expires, so nothing is sent before the step-up.
        assertTrue(model.stepUp.visible.value)
        settle()
        assertTrue(order.isEmpty())
        model.stepUp.onVerified("proof")
        until { model.passwordDialog.value?.password?.isNotEmpty() == true }
        // THE POINT: the home feed is saved, then the password made with the proof.
        assertEquals(listOf("home", "password"), order.toList())
        assertEquals(mapOf("id" to "b1", "feed" to "n"), sent("settings/-/user/networks/account/home").single())
        assertEquals(
            mapOf("id" to "b1", "name" to "Phone", "token" to "proof"),
            sent("settings/-/user/networks/account/password").single(),
        )
        // The password is the view model's, which outlives the screen, until the dialog closes.
        assertEquals("s3cret", model.passwordDialog.value!!.password)
        model.closePassword()
        assertNull(model.passwordDialog.value)

        order.clear()
        homeRefused = true
        model.openPassword(account)
        model.password(account, "Phone", NetworksViewModel.HOME_NEW)
        model.stepUp.onVerified("again")
        until { "password" !in model.uiState.value.busy && order.isNotEmpty() }
        settle()
        // THE POINT: a refused home feed stops there; no password is made.
        assertEquals(listOf("home"), order.toList())
        assertEquals("", model.passwordDialog.value!!.password)
    }

    @Test
    fun `a password retried after a failed mint saves no second home feed`() {
        passwordRefused = true
        val model = model()
        until { model.uiState.value.data != null }
        val account = NetworkAccount(id = "b1", type = "bluesky", hosted = true)
        model.openPassword(account)
        model.password(account, "Phone", NetworksViewModel.HOME_NEW)
        model.stepUp.onVerified("one")
        until { "password" in order && "password" !in model.uiState.value.busy }
        // A new feed names the home it replaces, none, so the server keeps a
        // home an earlier attempt made rather than making another.
        assertEquals(
            mapOf("id" to "b1", "feed" to "", "create" to "true", "previous" to ""),
            sent("settings/-/user/networks/account/home").single(),
        )
        // THE POINT: the dialog holds the account as the home feed's save
        // answered it, so the retry is not asked for a home feed again.
        val open = model.passwordDialog.value!!
        assertEquals("n", open.account.home)
        passwordRefused = false
        model.password(open.account, "Phone", null)
        model.stepUp.onVerified("two")
        until { model.passwordDialog.value?.password?.isNotEmpty() == true }
        assertEquals(1, sent("settings/-/user/networks/account/home").size)
        assertEquals(listOf("one", "two"), sent("settings/-/user/networks/account/password").map { it["token"] })
    }

    @Test
    fun `a new home feed names the home it replaces`() {
        val model = model()
        until { model.uiState.value.data != null }
        var saved = false
        model.home(NetworkAccount(id = "f1", type = "fediverse", hosted = true, home = "old"), NetworksViewModel.HOME_NEW) { saved = true }
        until { saved }
        assertEquals(
            mapOf("id" to "f1", "feed" to "", "create" to "true", "previous" to "old"),
            sent("settings/-/user/networks/account/home").single(),
        )
    }

    /** Lets a request already on its way reach the server: one sent alongside would have by now. */
    private fun settle() = repeat(30) {
        shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.MILLISECONDS)
        Thread.sleep(10)
    }

    @Test
    fun `a change made while another account's test runs waits for it, both showing busy, instead of being dropped`() {
        val gate = CountDownLatch(1)
        testGate = gate
        val model = model()
        until { model.uiState.value.data != null }
        val account = NetworkAccount(id = "b1", type = "bluesky", hosted = true)
        model.test(account)
        until { requests.any { it.first == "settings/-/user/networks/account/test" } }
        var saved = 0
        model.rename(account, "Work") { saved++ }
        model.rename(account, "Work") { saved++ }
        assertEquals(setOf("b1", "profile"), model.uiState.value.busy)
        settle()
        assertTrue(sent("settings/-/user/networks/account/update").isEmpty())
        gate.countDown()
        until { saved > 0 && model.uiState.value.busy.isEmpty() }
        assertEquals(listOf("Work"), sent("settings/-/user/networks/account/update").map { it["label"] })
        assertEquals(1, saved)
        // One at a time, in the order tapped: the change waited for the test's answer.
        assertEquals(listOf("test", "tested", "update"), order)
    }

    private fun kotlinx.coroutines.CoroutineScope.launchCollect(model: NetworksViewModel, into: MutableList<String>) =
        launch { model.messages.collect { into += it } }
}
