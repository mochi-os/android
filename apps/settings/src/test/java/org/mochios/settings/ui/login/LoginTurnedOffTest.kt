// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.login

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
import org.mochios.settings.ui.networks.FakeKeystore
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * A login method the server administrator turned off is not offered: its add
 * button is disabled with a line saying why, and no step-up is spent asking
 * for what the server would refuse. A failure is said over the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp")
class LoginTurnedOffTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager

    // Each test's view model is cleared when it ends. A model left running
    // keeps collecting the link return, and when a later test changes it the
    // model's edit waits on the main thread that test's setup is holding.
    private val models = ViewModelStore()

    /** The operator's policy for passkeys, the authenticator and recovery codes. */
    @Volatile private var system = "disabled"

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path.orEmpty().substringBefore("?").removePrefix("/")) {
                    "settings/-/user/account/methods" -> json(
                        """{"methods": {
                            "email": {"state": "allowed", "system": "allowed", "available": true},
                            "passkey": {"state": "disabled", "system": "$system", "available": false},
                            "totp": {"state": "disabled", "system": "$system", "available": false},
                            "recovery": {"state": "disabled", "system": "$system", "available": false}}}""",
                    )
                    "settings/-/user/account/passkeys" -> json("""{"passkeys": []}""")
                    "settings/-/user/account/totp" -> json("""{"enabled": false}""")
                    "settings/-/user/account/recovery" -> json("""{"count": 0}""")
                    "settings/-/user/account/oauth" -> json("""{"identities": []}""")
                    "settings/-/user/account/methods/set" -> MockResponse().setResponseCode(400)
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"error": "You must keep at least one way to sign in"}""")
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

    private fun json(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun model(): LoginViewModel {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api = retrofit.create(AccountApi::class.java)
        val auth = AuthRepository(retrofit.create(AuthApi::class.java), retrofit.create(TokenApi::class.java), session)
        val made = LoginViewModel(api, PasskeyManager(context), auth, session, SettingsStepUpClient(api, PasskeyManager(context)))
        val model = ViewModelProvider(models, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = made as T
        })[LoginViewModel::class.java]
        until { !model.uiState.value.isLoading && model.uiState.value.methods.isNotEmpty() }
        return model
    }

    private fun text(id: Int): String = context.getString(id)

    @Test
    fun `a turned-off method starts no step-up`() {
        val model = model()
        model.registerPasskey("Work key", "Passkey")
        model.beginTotpSetup()
        model.generateRecovery()
        assertFalse("THE POINT: no step-up for what the server refuses", model.stepUp.visible.value)
    }

    @Test
    fun `an allowed method starts its step-up`() {
        system = "allowed"
        val model = model()
        for (ask in listOf({ model.registerPasskey("Work key", "Passkey") }, { model.beginTotpSetup() }, { model.generateRecovery() })) {
            ask()
            assertTrue(model.stepUp.visible.value)
            model.stepUp.cancel()
        }
    }

    @Test
    fun `a turned-off method's button is disabled, with a line saying why`() {
        val model = model()
        rule.setContent { LoginScreen(onBack = {}, viewModel = model) }
        for (note in listOf(
            org.mochios.settings.R.string.account_passkeys_turned_off,
            org.mochios.settings.R.string.account_totp_turned_off,
            org.mochios.settings.R.string.account_recovery_turned_off,
        )) {
            rule.onNodeWithText(text(note)).performScrollTo().assertExists()
        }
        for (button in listOf(
            org.mochios.settings.R.string.account_passkey_register,
            org.mochios.settings.R.string.account_totp_setup,
            org.mochios.settings.R.string.account_recovery_generate,
        )) {
            rule.onNodeWithText(text(button)).performScrollTo().assertIsNotEnabled()
        }
    }

    @Test
    fun `an allowed method's button is enabled, with no line`() {
        system = "allowed"
        val model = model()
        rule.setContent { LoginScreen(onBack = {}, viewModel = model) }
        rule.onNodeWithText(text(org.mochios.settings.R.string.account_passkey_register)).performScrollTo().assertIsEnabled()
        rule.onNodeWithText(text(org.mochios.settings.R.string.account_totp_setup)).performScrollTo().assertIsEnabled()
        rule.onNodeWithText(text(org.mochios.settings.R.string.account_recovery_generate)).performScrollTo().assertIsEnabled()
        assertEquals(0, rule.onAllNodesWithText(text(org.mochios.settings.R.string.account_passkeys_turned_off)).fetchSemanticsNodes().size)
    }

    @Test
    fun `a refused change is said over the screen and cleared once said`() {
        val model = model()
        rule.setContent { LoginScreen(onBack = {}, viewModel = model) }
        model.setMethodState("email", "disabled")
        model.stepUp.onVerified("proof")
        // In a snackbar, which announces itself, not a line at the column's foot.
        val snackbar = hasText("You must keep at least one way to sign in") and
            hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        rule.waitUntil(5_000) { rule.onAllNodes(snackbar).fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(10_000)
        rule.waitForIdle()
        assertEquals("cleared once said", null, model.uiState.value.error)
    }
}
