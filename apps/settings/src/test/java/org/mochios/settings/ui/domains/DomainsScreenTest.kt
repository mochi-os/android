// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.domains

import android.os.Looper
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.mochios.settings.R
import org.mochios.settings.api.DomainsApi
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.mochios.android.R as MochiR

/** The Domains screen over a server: what it says, and what it keeps on screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h1200dp")
class DomainsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private val lists = AtomicInteger()

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setBody(body).setHeader("Content-Type", "application/json")

    /** Runs the main looper, which the server's answers resume on, until [done]. */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.MILLISECONDS)
            rule.waitForIdle()
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun shown(text: String) = rule.onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty()

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path.orEmpty().substringBefore("?").removePrefix("/")) {
                    "settings/-/domains/data" -> {
                        lists.incrementAndGet()
                        json("""{"domains": [{"domain": "a.example", "verified": 1, "tls": 1}], "count": 1, "admin": true}""")
                    }
                    "settings/-/domains/get" -> json("""{"domain": {"domain": "a.example", "verified": 1, "tls": 1}, "routes": [], "delegations": [], "admin": true}""")
                    "settings/-/domains/update" -> json("""{"error": "Certificates are off on this server"}""", 400)
                    "settings/-/domains/create" -> json("{}")
                    else -> MockResponse().setResponseCode(404)
                }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    private fun open(): DomainsViewModel {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DomainsApi::class.java)
        val model = DomainsViewModel(api)
        rule.setContent { DomainsScreen(onBack = {}, viewModel = model) }
        until { shown("a.example") }
        return model
    }

    /**
     * A refused change used to change nothing, with no word, once a domain was
     * listed; the card it was made in stays open.
     */
    @Test
    fun `a refused change is said over the list, and the card it was made in stays open`() {
        open()
        rule.onNodeWithContentDescription(context.getString(MochiR.string.common_back)).assertExists()
        rule.onNodeWithContentDescription(context.getString(MochiR.string.common_expand)).performClick()
        until { shown(context.getString(R.string.route_section)) }
        rule.onNode(isToggleable()).performClick()
        until { shown("Certificates are off on this server") }
        rule.onNodeWithText(context.getString(R.string.route_section)).assertExists()
    }

    /** Every re-read after an action used to blank the list to a spinner and collapse the open card. */
    @Test
    fun `an open card stays open while the list is read again`() {
        val model = open()
        rule.onNodeWithContentDescription(context.getString(MochiR.string.common_expand)).performClick()
        until { shown(context.getString(R.string.route_section)) }
        val before = lists.get()
        model.createDomain("b.example")
        until { lists.get() > before }
        until { model.uiState.value.loadingDetails.isEmpty() }
        rule.onNodeWithText(context.getString(R.string.route_section)).assertExists()
    }
}
