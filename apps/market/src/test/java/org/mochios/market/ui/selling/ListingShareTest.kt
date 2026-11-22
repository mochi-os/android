// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.selling

import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.content.IntentCompat
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
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.UserPreferences
import org.mochios.android.share.EXTRA_ADDRESS
import org.mochios.android.share.EXTRA_WORDS
import org.mochios.android.share.ShareSheet
import org.mochios.market.api.MarketApi
import org.mochios.market.lib.formatPrice
import org.mochios.market.model.Currency
import org.mochios.market.model.Listing
import org.mochios.market.model.ListingStatus
import org.mochios.market.repository.MarketRepository
import org.mochios.market.ui.components.listingShare
import org.mochios.market.ui.components.shareable
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Share on a listing live for sale, from the seller's listings, through the
 * system share sheet: its title and its price, and its page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class ListingShareTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val format = Format(UserPreferences())
    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager
    private lateinit var base: String

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path.orEmpty().substringAfter("/market/").substringBefore("?")) {
                    "-/accounts/get" -> ok("""{"id": "me", "seller": 1, "onboarded": 1}""")
                    "-/listings/mine" -> ok(
                        """{"listings": [{"id": "l1", "title": "Teapot", "description": " A blue pot. ", "price": 1200,
                        "currency": "gbp", "status": "active"}], "total": 1}""",
                    )
                    else -> ok("null")
                }
        }
        server.start()
        base = server.url("/").toString().trimEnd('/')
        session = SessionManager(context)
        runBlocking { session.setServerUrl(base) }
    }

    @After
    fun end() = server.shutdown()

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun model(): MyListingsViewModel {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/market/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MarketApi::class.java)
        return MyListingsViewModel(MarketRepository(api, FileStore(context)), session)
    }

    /**
     * What the system share sheet opened for, once it opens; the launch of
     * the test's own host activity is passed over.
     */
    private fun sheet(): Intent {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
            val started = shadowOf(context).nextStartedActivity
            if (started == null) {
                Thread.sleep(10)
            } else if (started.action == Intent.ACTION_CHOOSER) {
                return IntentCompat.getParcelableExtra(started, Intent.EXTRA_INTENT, Intent::class.java)!!
            }
        }
        throw AssertionError("the share sheet did not open")
    }

    @Test
    fun `a listing is shared by its title and its price, a blank line between, and its page`() {
        val model = model()
        rule.setContent { ShareSheet(model.shares) }
        rule.waitUntil(5_000) {
            rule.onAllNodes(isRoot()).fetchSemanticsNodes()
            model.state.value.listings.isNotEmpty()
        }
        rule.runOnIdle { model.share(model.state.value.listings.single(), format) }
        val send = sheet()
        // The description is left to the listing's page.
        val words = "Teapot\n\n" + formatPrice(1200, Currency.GBP, format)
        assertEquals("$words\n\n$base/market/listings/l1", send.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("Teapot", send.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals(words, send.getStringExtra(EXTRA_WORDS))
        assertEquals("$base/market/listings/l1", send.getStringExtra(EXTRA_ADDRESS))
    }

    @Test
    fun `a listing with no price is shared by its title alone`() {
        val shared = listingShare(Listing(id = "l2", title = "Free chair"), "$base/", format)
        assertEquals("Free chair", shared.text)
        assertEquals("$base/market/listings/l2", shared.url)
    }

    @Test
    fun `only a listing live for sale is shared`() {
        assertTrue(Listing(status = ListingStatus.ACTIVE).shareable())
        assertFalse(Listing(status = ListingStatus.ACTIVE, moderation = "hold").shareable())
        for (status in listOf(ListingStatus.DRAFT, ListingStatus.SOLD, ListingStatus.EXPIRED, ListingStatus.REMOVED)) {
            assertFalse("$status", Listing(status = status).shareable())
        }
    }
}
