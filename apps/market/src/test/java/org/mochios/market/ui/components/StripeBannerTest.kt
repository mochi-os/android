// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.market.R
import org.mochios.market.api.MarketApi
import org.mochios.market.repository.MarketRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * The payment setup banner asks Stripe only when the seller asks it to, says
 * in the server's words why it could not, and hands over once payments are
 * set up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StripeBannerTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private val checks = AtomicInteger()

    @Volatile private var status: MockResponse? = null

    /** The method of the last status check. */
    @Volatile private var method: String? = null

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path.orEmpty().substringBefore("?").removePrefix("/market/")) {
                    "-/accounts/stripe/status" -> {
                        method = request.method
                        checks.incrementAndGet()
                        status ?: MockResponse().setResponseCode(502).setBody("""{"error": "Stripe is not answering"}""")
                    }
                    "-/accounts/stripe/onboarding" ->
                        MockResponse().setResponseCode(400).setBody("""{"error": "No Stripe account"}""")
                    else -> MockResponse().setResponseCode(404)
                }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    private var ready = 0

    private fun show() {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/market/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MarketApi::class.java)
        val repository = MarketRepository(api, FileStore(context))
        rule.setContent {
            StripeOnboardingBanner(repository = repository, returnUrl = "mochi://market/account", onReady = { ready += 1 })
        }
        rule.waitForIdle()
    }

    private fun waitFor(text: String) = rule.waitUntil(5_000) {
        rule.onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun `Stripe is not asked until the seller checks`() {
        show()
        Thread.sleep(300)
        rule.waitForIdle()
        assertEquals(0, checks.get())
    }

    @Test
    fun `a check that failed says why`() {
        show()
        rule.onNodeWithText(context.getString(R.string.market_stripe_onboarding_check)).performClick()
        waitFor("Stripe is not answering")
    }

    @Test
    fun `setup that could not start says why`() {
        show()
        rule.onNodeWithText(context.getString(R.string.market_stripe_onboarding_action)).performClick()
        waitFor("No Stripe account")
    }

    @Test
    fun `a check that finds payments set up hands over`() {
        status = MockResponse().setBody("""{"data": {"charges_enabled": true, "payouts_enabled": true}}""")
        show()
        rule.onNodeWithText(context.getString(R.string.market_stripe_onboarding_check)).performClick()
        rule.waitUntil(5_000) { ready == 1 }
    }

    // The check asks Stripe, records the seller's onboarding state and spends
    // the allowance the Stripe connection draws on, so it is a POST: a GET
    // would let a link or an image spend it for the seller.
    @Test
    fun `the check is a POST`() {
        status = MockResponse().setBody("""{"data": {"charges_enabled": true, "payouts_enabled": true}}""")
        show()
        rule.onNodeWithText(context.getString(R.string.market_stripe_onboarding_check)).performClick()
        rule.waitUntil(5_000) { checks.get() == 1 }
        assertEquals("POST", method)
    }
}
