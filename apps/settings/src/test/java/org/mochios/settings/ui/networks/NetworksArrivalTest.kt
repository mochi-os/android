// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.networks

import android.os.Looper
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.AuthApi
import org.mochios.android.auth.AuthRepository
import org.mochios.android.auth.PasskeyManager
import org.mochios.android.auth.SessionManager
import org.mochios.android.auth.TokenApi
import org.mochios.android.i18n.AppContext
import org.mochios.settings.api.AccountApi
import org.mochios.settings.api.NetworkAccount
import org.mochios.settings.api.NetworksApi
import org.mochios.settings.ui.login.SettingsStepUpClient
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Networks opened by an app's Set up: a network the server only links opens
 * the link dialog on its provider once the screen has loaded; one it hosts,
 * or none asked for, opens the screen as usual. And the screen recreated,
 * with a secret shown once on it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NetworksArrivalTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager
    private val models = ViewModelStore()

    /** The networks the server hosts accounts on. */
    @Volatile private var networks = "[]"

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path.orEmpty().substringBefore("?").removePrefix("/")) {
                    "settings/-/user/networks/data" -> MockResponse().setHeader("Content-Type", "application/json").setBody(
                        """{"offered": true, "networks": $networks, "providers": [
                            {"type": "lemmy", "link": {"flow": "form", "fields": [{"name": "server", "label": "Lemmy server", "required": true}]}},
                            {"type": "mastodon", "link": {"flow": "oauth", "fields": [{"name": "server", "label": "Mastodon server", "required": true}]}}]}""",
                    )
                    "settings/-/user/networks/account/keys" -> json("[]")
                    "settings/-/user/networks/account/keys/recovery" -> json("""{"public": "did:key:z", "private": "deadbeef"}""")
                    "settings/-/user/networks/account/password" -> json("""{"password": "s3cret"}""")
                    else -> MockResponse().setResponseCode(404)
                }
        }
        server.start()
        AppContext.set(context)
        FakeKeystore.install()
        session = SessionManager(context)
        runBlocking { session.clearAll() }
    }

    @After
    fun end() {
        models.clear()
        server.shutdown()
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun model(): NetworksViewModel {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val auth = AuthRepository(retrofit.create(AuthApi::class.java), retrofit.create(TokenApi::class.java), session)
        val stepUp = SettingsStepUpClient(retrofit.create(AccountApi::class.java), PasskeyManager(context))
        val made = NetworksViewModel(retrofit.create(NetworksApi::class.java), session, auth, stepUp)
        val model = ViewModelProvider(models, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = made as T
        })[NetworksViewModel::class.java]
        val deadline = System.currentTimeMillis() + 5_000
        while (model.uiState.value.data == null) {
            shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
        return model
    }

    private fun open(network: String?) {
        val model = model()
        rule.setContent { NetworksScreen(onBack = {}, network = network, viewModel = model) }
        rule.waitForIdle()
    }

    @Test
    fun `sent to set up the fediverse where the server only links it, the link dialog opens on Mastodon`() {
        open("fediverse")
        rule.onNodeWithText("Mastodon server").assertExists()
    }

    @Test
    fun `sent to set up a network the server hosts, the screen opens as usual`() {
        networks = """["fediverse"]"""
        open("fediverse")
        rule.onNodeWithText("Mastodon server").assertDoesNotExist()
        rule.onNodeWithText("Lemmy server").assertDoesNotExist()
    }

    @Test
    fun `opened with no network, the screen opens as usual`() {
        open(null)
        rule.onNodeWithText("Mastodon server").assertDoesNotExist()
        rule.onNodeWithText("Lemmy server").assertDoesNotExist()
    }

    // Turning the phone, or a theme, font or language change, recreates the
    // screen; a secret shown once must still be there afterwards.
    @Test
    fun `a recovery key and a minted password outlive the screen being recreated`() {
        val model = model()
        val restoration = StateRestorationTester(rule)
        restoration.setContent { NetworksScreen(onBack = {}, viewModel = model) }
        val bluesky = NetworkAccount(id = "b1", type = "bluesky", hosted = true, identifier = "alice.a.example")
        model.openKeys(bluesky)
        model.recovery(bluesky)
        model.stepUp.onVerified("proof")
        rule.waitUntil(5000) { rule.onAllNodesWithText("deadbeef").fetchSemanticsNodes().isNotEmpty() }
        restoration.emulateSavedInstanceStateRestore()
        // THE POINT: the dialog is open again on the same key.
        rule.onNodeWithText("deadbeef").assertExists()
        model.closeKeys()

        val xmpp = NetworkAccount(id = "x1", type = "xmpp", hosted = true, identifier = "alice@a.example", domain = "a.example")
        model.openPassword(xmpp)
        model.password(xmpp, "Phone", null)
        model.stepUp.onVerified("proof")
        rule.waitUntil(5000) { rule.onAllNodesWithText("s3cret").fetchSemanticsNodes().isNotEmpty() }
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("s3cret").assertExists()
    }
}
