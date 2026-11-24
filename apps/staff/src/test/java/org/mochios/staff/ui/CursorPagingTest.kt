// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui

import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.staff.StaffServer
import org.mochios.staff.StaffServer.Companion.ok
import org.mochios.staff.ui.accounts.AccountsViewModel
import org.mochios.staff.ui.appeals.AppealsViewModel
import org.mochios.staff.ui.disputes.DisputesViewModel
import org.mochios.staff.ui.listings.ListingsViewModel
import org.mochios.staff.ui.moderation.ModerationLogViewModel
import org.mochios.staff.ui.reports.ReportsViewModel
import org.mochios.staff.ui.reviews.ReviewsViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every staff list reads its next page after the cursor the last page gave,
 * so rows added at the top meanwhile cannot shift it; a row two pages both
 * hold is listed once, and the list ends when the server gives no cursor,
 * whatever its total says.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CursorPagingTest {

    /** Each list action and the key its rows come under. */
    private val lists = mapOf(
        "-/accounts/list" to "accounts",
        "-/listings/pending" to "listings",
        "-/reports/list" to "reports",
        "-/disputes/list" to "disputes",
        "-/appeals/list" to "appeals",
        "-/moderation/log" to "log",
        "-/reviews/list" to "reviews",
    )

    private val server = StaffServer { request ->
        val url = request.requestUrl!!
        val key = lists.entries.firstOrNull { (path, _) -> url.encodedPath.endsWith(path) }?.value ?: return@StaffServer null
        if (url.queryParameter("after") == "c2") {
            ok("""{"$key": [{"id": "x2"}, {"id": "x3"}], "total": 99, "next": ""}""")
        } else {
            ok("""{"$key": [{"id": "x1"}, {"id": "x2"}], "total": 99, "next": "c2"}""")
        }
    }

    @Before
    fun begin() {
        server.start()
    }

    @After
    fun end() = server.stop()

    /** Reads [path]'s first page, then its next, through [more], and checks what was asked and listed. */
    private fun pages(path: String, loaded: () -> Boolean, more: () -> Unit, rows: () -> List<String>) {
        server.until(loaded)
        assertNull(server.made(path).single().requestUrl!!.queryParameter("after"))
        more()
        server.until { rows().size == 3 && loaded() }
        assertEquals("c2", server.made(path).last().requestUrl!!.queryParameter("after"))
        assertEquals(listOf("x1", "x2", "x3"), rows())
        more()
        server.idle(200)
        assertEquals(2, server.made(path).size)
    }

    @Test
    fun accounts() {
        val model = AccountsViewModel(server.repository, SavedStateHandle())
        pages("-/accounts/list", { !model.state.value.isLoading && model.state.value.load is org.mochios.android.ui.components.LoadState.Idle }, model::loadMore) {
            model.state.value.accounts.map { row -> row.id }
        }
    }

    @Test
    fun listings() {
        val model = ListingsViewModel(server.repository, server.socket, server.session, SavedStateHandle())
        pages("-/listings/pending", { !model.state.value.isLoading }, model::loadMore) {
            model.state.value.listings.map { row -> row.id }
        }
    }

    @Test
    fun reports() {
        val model = ReportsViewModel(server.repository, server.socket, server.session, SavedStateHandle())
        pages("-/reports/list", { !model.state.value.isLoading }, model::loadMore) {
            model.state.value.reports.map { row -> row.id }
        }
    }

    @Test
    fun disputes() {
        val model = DisputesViewModel(server.repository, server.socket, server.session, SavedStateHandle())
        pages("-/disputes/list", { !model.state.value.isLoading }, model::loadMore) {
            model.state.value.disputes.map { row -> row.id }
        }
    }

    @Test
    fun appeals() {
        val model = AppealsViewModel(server.repository, server.socket, server.session)
        pages("-/appeals/list", { !model.state.value.isLoading }, model::loadMore) {
            model.state.value.appeals.map { row -> row.id }
        }
    }

    @Test
    fun moderation() {
        val model = ModerationLogViewModel(server.repository, server.socket, server.session, SavedStateHandle())
        pages("-/moderation/log", { !model.state.value.isLoading }, model::loadMore) {
            model.state.value.entries.map { row -> row.id }
        }
    }

    @Test
    fun reviews() {
        val model = ReviewsViewModel(server.repository, SavedStateHandle())
        pages("-/reviews/list", { !model.state.value.isLoading }, model::loadMore) {
            model.state.value.reviews.map { row -> row.id }
        }
    }
}
