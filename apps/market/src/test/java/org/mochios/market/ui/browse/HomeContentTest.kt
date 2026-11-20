// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.browse

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R as MochiR
import org.mochios.android.api.MochiError
import org.mochios.android.i18n.AppContext
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.NumberFormat
import org.mochios.android.i18n.UserPreferences
import org.mochios.android.ui.components.LoadState
import org.mochios.android.util.Paging
import org.mochios.market.R
import org.mochios.market.model.Listing
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The front page as its state says: a failed first search in place of the
 * grid, a failed next page at its foot waiting for the retry, and each active
 * filter's chip in words.
 */
// Tall enough that the grid's foot is laid out under its one listing.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2000dp")
class HomeContentTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    private var loads = 0
    private var retries = 0

    private val european = Format(UserPreferences(numberFormat = NumberFormat.EUROPEAN_DOT_COMMA))

    private fun show(state: HomeUiState) {
        AppContext.set(context)
        rule.setContent {
            CompositionLocalProvider(LocalFormat provides european) {
                HomeContent(
                    padding = PaddingValues(),
                    state = state,
                    searchInput = state.query,
                    onSearchInput = {},
                    onClearSearch = {},
                    onOpenFilter = {},
                    onRemoveFilter = {},
                    onClearAll = {},
                    onLoadMore = { loads += 1 },
                    onRetry = { retries += 1 },
                    onListingClick = {},
                    onToggleSave = {},
                    onActivateAccount = {},
                    onDismissOnboarding = {},
                    onClearRecent = {},
                )
            }
        }
        rule.waitForIdle()
    }

    private fun text(id: Int) = context.getString(id)

    @Test
    fun `a first search that failed is shown in place of the grid`() {
        show(HomeUiState(query = "pot", load = LoadState.Failed(MochiError.Unknown("Comptroller is not available"))))
        rule.onNodeWithText("Comptroller is not available").assertExists()
        rule.onAllNodesWithText(text(R.string.market_empty_title)).assertCountEquals(0)
        rule.onNodeWithText(text(MochiR.string.common_retry)).performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `a next page that failed is shown at the foot and not asked for again`() {
        show(
            HomeUiState(
                listings = listOf(Listing(id = "l1", title = "Teapot")),
                load = LoadState.Failed(MochiError.Unknown("Too many requests")),
                paging = Paging().advance(count = 1, total = 5, next = "c1"),
            ),
        )
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        assertEquals(0, loads)
        rule.onNodeWithText("Too many requests").assertExists()
    }

    @Test
    fun `a page whose end is in view asks for the next`() {
        show(
            HomeUiState(
                listings = listOf(Listing(id = "l1", title = "Teapot")),
                paging = Paging().advance(count = 1, total = 5, next = "c1"),
            ),
        )
        assertEquals(1, loads)
    }

    @Test
    fun `each active filter's chip is in words, and the currency has none of its own`() {
        show(
            HomeUiState(
                filters = mapOf(
                    Filter.SORT to "price_low",
                    Filter.TYPE to "physical",
                    Filter.CURRENCY to "eur",
                    Filter.PRICE_MIN to "1234",
                    Filter.CATEGORY to "c9",
                ),
            ),
        )
        rule.onNodeWithText(text(R.string.market_filter_sort_price_low)).assertExists()
        rule.onNodeWithText(text(R.string.market_filter_type_physical)).assertExists()
        rule.onAllNodesWithText("Price_low").assertCountEquals(0)
        rule.onAllNodesWithText("Eur").assertCountEquals(0)
        rule.onAllNodesWithText("c9").assertCountEquals(0)
        // The bound is a price in the range's currency, in the user's digits.
        rule.onNodeWithText("1.234,00", substring = true).assertExists()
    }
}
