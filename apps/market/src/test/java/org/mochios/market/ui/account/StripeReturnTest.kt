// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.account

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.i18n.AppContext
import org.mochios.market.FlowServer
import org.mochios.market.R
import org.mochios.market.settle
import org.mochios.market.until
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Stripe's return to the seller settings: the Comptroller's "1" and "pending"
 * are told apart from a failure, the account is read again whatever the
 * answer, and the single-use return is completed once, not again when the
 * screen is made anew from its saved state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StripeReturnTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: FlowServer

    @Before
    fun begin() {
        AppContext.set(context)
        server = FlowServer()
        server.data("-/accounts/get", """{"id": "a1", "seller": 1, "onboarded": 1, "stripe": "acct_1"}""")
        server.data("-/accounts/fees", """{"platform": 5}""")
        server.data("-/accounts/stripe/status", """{"charges_enabled": true, "payouts_enabled": true}""")
    }

    @After
    fun end() = server.stop()

    private fun returned(): SavedStateHandle = SavedStateHandle(mapOf("code" to "c1", "state" to "s1"))

    /** The events the model emits after a return completes, and the model. */
    private fun complete(answer: String, handle: SavedStateHandle = returned()): List<SellerSettingsEvent> {
        server.data("-/accounts/stripe/oauth/complete", answer)
        val model = SellerSettingsViewModel(server.repository(context), handle)
        val events = CopyOnWriteArrayList<SellerSettingsEvent>()
        val job = CoroutineScope(Dispatchers.Main).launch { model.events.collect { events.add(it) } }
        until("the account read after the return") { events.isNotEmpty() && model.state.value.account != null }
        job.cancel()
        return events
    }

    @Test
    fun `a connected account says so`() {
        val events = complete("""{"redirect_url": "x", "connected": "1"}""")
        assertEquals(listOf(SellerSettingsEvent.Notice(R.string.market_account_stripe_connected)), events)
        assertEquals(1, server.of("-/accounts/get").size)
    }

    // The Comptroller keeps Stripe's code; the exchange is completed from the
    // state alone, and a code the return still carries is not sent.
    @Test
    fun `the return is completed from its state alone`() {
        complete("""{"redirect_url": "x", "connected": "1"}""")
        val sent = server.of("-/accounts/stripe/oauth/complete").single().fields
        assertEquals("s1", sent["state"])
        assertFalse("no code is sent", sent.containsKey("code"))
    }

    @Test
    fun `an account Stripe wants more from says that, not failure`() {
        val events = complete("""{"redirect_url": "x", "connected": "pending"}""")
        assertEquals(listOf(SellerSettingsEvent.Notice(R.string.market_account_stripe_pending)), events)
        assertEquals(1, server.of("-/accounts/get").size)
    }

    @Test
    fun `a refused exchange is a failure, and the account is still read`() {
        val events = complete("""{"redirect_url": "x", "error": "Invalid or expired state"}""")
        assertEquals(
            listOf(SellerSettingsEvent.Error(MochiError.Local(R.string.market_account_stripe_connect_failed))),
            events,
        )
        assertEquals(1, server.of("-/accounts/get").size)
    }

    @Test
    fun `a return is completed once, not again from the saved state`() {
        val handle = returned()
        complete("""{"redirect_url": "x", "connected": "1"}""", handle)
        assertNull("the return is taken out of the saved state", handle.get<String>("state"))
        // The screen made anew (a rotation, the process restored) from the same state.
        SellerSettingsViewModel(server.repository(context), handle)
        settle()
        assertEquals(1, server.of("-/accounts/stripe/oauth/complete").size)
    }
}
