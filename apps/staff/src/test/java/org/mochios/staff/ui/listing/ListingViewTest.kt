// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.listing

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.owned
import org.mochios.android.format.formatPrice
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.NumberFormat
import org.mochios.android.i18n.UserPreferences
import org.mochios.staff.R
import org.mochios.staff.StaffServer
import org.mochios.staff.StaffServer.Companion.ok
import org.mochios.staff.model.listingPhotoAddress
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A listing under review shows in the console whatever its status: its
 * description, condition, price, the factors behind its score, its seller by
 * name and fingerprint, its photos from the staff app's own route and its
 * moderation history, which opens the log for the listing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ListingViewTest {

    @get:Rule
    val rule = createComposeRule()

    private val view = """{"listing": {"id": "l1", "title": "Teapot", "description": "A brown teapot", "condition": "used",
        "price": 1250, "currency": "gbp", "status": "active", "moderation": "hold", "score": 72,
        "factors": "[{\"name\": \"price\", \"score\": 80, \"reason\": \"Price far below similar items\"}]"},
        "seller": {"id": "s1", "name": "Ann", "fingerprint": "abc123defXYZ"},
        "photos": [{"id": "p1", "position": 0}],
        "history": [{"id": "m1", "listing": "l1", "action": "hold", "actor": "system", "reason": "Held for review", "created": 100}]}"""

    private val server = StaffServer { request ->
        if (request.requestUrl!!.encodedPath.endsWith("-/listings/get")) ok(view) else null
    }

    private val format = Format(UserPreferences(numberFormat = NumberFormat.EUROPEAN_DOT_COMMA))

    @Before
    fun begin() {
        server.start()
    }

    @After
    fun end() = server.stop()

    private fun model(): ListingViewModel {
        val model = ListingViewModel(server.repository, SavedStateHandle(mapOf("id" to "l1")))
        server.until { model.state.value.view != null }
        return model
    }

    @Test
    fun `the view reads the listing by its id`() {
        model()
        assertEquals("l1", server.made("-/listings/get").single().requestUrl!!.queryParameter("id"))
    }

    @Test
    fun `the view shows the listing as staff see it`() {
        val model = model()
        var history: String? = null
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides format) {
                ListingScreen(onBack = {}, onOpenHistory = { listing -> history = listing }, viewModel = model)
            }
        }
        rule.onNodeWithText("Teapot").assertExists()
        rule.onNodeWithText("A brown teapot").assertExists()
        rule.onNodeWithText(server.context.getString(R.string.staff_listing_condition_used)).assertExists()
        rule.onNodeWithText(formatPrice(1250, "gbp", format)).assertExists()
        rule.onNodeWithText("Price far below similar items").assertExists()
        rule.onNodeWithText("Ann").assertExists()
        rule.onNodeWithText("abc-123-def").assertExists()
        rule.onNodeWithText("Held for review").performScrollTo().performClick()
        assertEquals("l1", history)
    }

    @Test
    fun `a held listing offers its moderator actions`() {
        val model = model()
        rule.setContent { ListingScreen(onBack = {}, onOpenHistory = {}, viewModel = model) }
        for (action in listOf(R.string.staff_listings_approve, R.string.staff_listings_reject, R.string.staff_listings_remove)) {
            rule.onNode(hasText(server.context.getString(action)) and hasClickAction()).performScrollTo().assertExists()
        }
    }

    @Test
    fun `a photo comes from the staff app's own route, fetched with its credential`() {
        val thumbnail = listingPhotoAddress("l1", "p1", thumbnail = true)!!
        assertEquals("/staff/-/listings/photo?listing=l1&photo=p1&size=thumbnail", thumbnail.address)
        assertEquals(true, owned(thumbnail.address))
        assertEquals("/staff/-/listings/photo?listing=l1&photo=p1", listingPhotoAddress("l1", "p1")!!.address)
        assertNull(listingPhotoAddress("l1", "../p1"))
    }
}
