// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.userMessage
import org.mochios.staff.R
import org.mochios.staff.StaffServer
import org.mochios.staff.StaffServer.Companion.ok
import org.mochios.staff.model.Account
import org.mochios.staff.model.PendingListing
import org.mochios.staff.model.Report
import org.mochios.staff.ui.accounts.AccountActionType
import org.mochios.staff.ui.accounts.AccountsEvent
import org.mochios.staff.ui.accounts.AccountsViewModel
import org.mochios.staff.ui.listings.ListingActionType
import org.mochios.staff.ui.listings.ListingsEvent
import org.mochios.staff.ui.listings.ListingsViewModel
import org.mochios.staff.ui.reports.ReportsEvent
import org.mochios.staff.ui.reports.ReportsViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * An action that succeeds says so with its own message, and one the server
 * refuses says why in the server's words: two kinds of event, so no server
 * sentence can pass for a success, and no success can reach the screen as a
 * marker.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActionEventsTest {

    @Volatile private var refuse = false

    private val server = StaffServer { request ->
        val path = request.requestUrl!!.encodedPath
        when {
            refuse && request.method == "POST" ->
                MockResponse().setResponseCode(400).setBody("""{"error": "Not allowed here"}""")
            path.endsWith("-/accounts/list") -> ok("""{"accounts": [{"id": "a1", "name": "Ann", "status": "active"}], "total": 1}""")
            path.endsWith("-/accounts/suspend") -> ok("""{"id": "a1", "status": "suspended"}""")
            path.endsWith("-/listings/pending") -> ok("""{"listings": [{"id": "l1", "title": "Teapot"}], "total": 1}""")
            path.endsWith("-/listings/approve") -> ok("""{"ok": true}""")
            path.endsWith("-/reports/list") -> ok("""{"reports": [{"id": "r1", "status": "pending"}], "total": 1}""")
            path.endsWith("-/reports/action") -> ok("""{"id": "r1", "status": "dismissed"}""")
            else -> null
        }
    }

    @Before
    fun begin() {
        server.start()
    }

    @After
    fun end() = server.stop()

    @Test
    fun `an account action says what it did, or why not`() {
        val model = AccountsViewModel(server.repository, SavedStateHandle())
        server.until { !model.state.value.isLoading }
        val seen = CopyOnWriteArrayList<AccountsEvent>()
        model.viewModelScope.launch { model.events.collect { event -> seen += event } }
        model.openAction(AccountActionType.SUSPEND, Account(id = "a1"))
        model.submitAction("Fraud", "")
        server.until { seen.isNotEmpty() }
        assertEquals(AccountsEvent.Toast(R.string.staff_accounts_suspended_toast), seen.single())

        refuse = true
        model.openAction(AccountActionType.SUSPEND, Account(id = "a1"))
        model.submitAction("Fraud", "")
        server.until { seen.size == 2 }
        assertEquals("Not allowed here", (seen.last() as AccountsEvent.Error).error.userMessage())
    }

    @Test
    fun `a listing action says what it did, or why not`() {
        val model = ListingsViewModel(server.repository, server.socket, server.session, SavedStateHandle())
        server.until { !model.state.value.isLoading }
        val seen = CopyOnWriteArrayList<ListingsEvent>()
        model.viewModelScope.launch { model.events.collect { event -> seen += event } }
        model.openAction(ListingActionType.APPROVE, PendingListing(id = "l1"))
        model.submitAction("", "")
        server.until { seen.isNotEmpty() }
        assertEquals(ListingsEvent.Toast(R.string.staff_listings_approved_toast), seen.single())

        refuse = true
        model.openAction(ListingActionType.APPROVE, PendingListing(id = "l1"))
        model.submitAction("", "")
        server.until { seen.size == 2 }
        assertEquals("Not allowed here", (seen.last() as ListingsEvent.Error).error.userMessage())
    }

    @Test
    fun `a report action says what it did, or why not`() {
        val model = ReportsViewModel(server.repository, server.socket, server.session, SavedStateHandle())
        server.until { !model.state.value.isLoading }
        val seen = CopyOnWriteArrayList<ReportsEvent>()
        model.viewModelScope.launch { model.events.collect { event -> seen += event } }
        model.openAction(Report(id = "r1", status = "pending"))
        model.actionReport("dismiss", "")
        server.until { seen.isNotEmpty() }
        assertEquals(ReportsEvent.Toast(R.string.staff_reports_action_taken_toast), seen.single())

        refuse = true
        model.openAction(Report(id = "r1", status = "pending"))
        model.actionReport("dismiss", "")
        server.until { seen.size == 2 }
        assertEquals("Not allowed here", (seen.last() as ReportsEvent.Error).error.userMessage())
    }
}
