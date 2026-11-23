// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.account

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import org.mochios.android.auth.PasskeyManager
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.Downloads
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.settings.R
import org.mochios.settings.api.AccountApi
import org.mochios.settings.ui.login.SettingsStepUpClient
import org.mochios.settings.ui.networks.FakeKeystore
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** The Account screen says a refused change over the screen, and names the name's edit button. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h1600dp")
class AccountScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path.orEmpty().substringBefore("?").removePrefix("/")) {
                    "settings/-/user/account/identity" ->
                        json("""{"entity": "e1", "fingerprint": "f1", "username": "ada@example.com", "name": "Ada", "privacy": "public", "role": "user"}""")
                    "settings/-/user/account/identity/update" -> MockResponse().setResponseCode(500)
                        .setHeader("Content-Type", "application/json").setBody("""{"error": "Not now"}""")
                    "settings/-/user/account/export/status" -> json("""{"state": "none", "file": ""}""")
                    else -> MockResponse().setResponseCode(404)
                }
        }
        server.start()
        AppContext.set(context)
        FakeKeystore.install()
    }

    @After
    fun end() {
        server.shutdown()
    }

    private fun json(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    private fun text(id: Int): String = context.getString(id)

    private fun model(): AccountViewModel {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api = retrofit.create(AccountApi::class.java)
        val session = SessionManager(context)
        runBlocking { session.clearAll() }
        val made = AccountViewModel(api, SettingsStepUpClient(api, PasskeyManager(context)), Downloads(context, FileStore(context)))
        val deadline = System.currentTimeMillis() + 5_000
        while (made.uiState.value.identity.username.isEmpty()) {
            shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
        return made
    }

    @Test
    fun `a refused change is said over the screen`() {
        val model = model()
        rule.setContent { AccountScreen(onBack = {}, viewModel = model) }
        model.setPrivacy("private")
        val snackbar = hasText("Not now") and hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        rule.waitUntil(5_000) { rule.onAllNodes(snackbar).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `the name's edit button is named`() {
        val model = model()
        rule.setContent { AccountScreen(onBack = {}, viewModel = model) }
        rule.onNodeWithContentDescription(text(R.string.account_rename)).assertExists()
    }
}
