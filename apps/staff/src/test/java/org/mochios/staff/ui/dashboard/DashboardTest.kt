// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.dashboard

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R as MochiR
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.LoadState
import org.mochios.staff.R
import org.mochios.staff.StaffServer
import org.mochios.staff.StaffServer.Companion.ok
import org.mochios.staff.StaffServer.Companion.refused
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * The dashboard's overview and each of its tabs read, fail and page on their
 * own: a failure says so with a retry where the rows would be, is not asked for
 * again by itself, and a tab pages by the cursor its last page gave.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DashboardTest {

    @get:Rule
    val rule = createComposeRule()

    @Volatile private var overviewRefused = false
    @Volatile private var ordersRefused = false
    @Volatile private var slow = false

    private val overview = """{"listings": 1, "orders": 2, "revenue": [{"currency": "gbp", "total": 1500}], "sellers": 3, "buyers": 4, "disputes": 5, "pending_moderation": 6}"""

    private val server = StaffServer { request ->
        val url = request.requestUrl!!
        when {
            url.encodedPath.endsWith("-/metrics/overview") -> if (overviewRefused) refused("Overview is not available") else ok(overview)
            url.encodedPath.endsWith("-/metrics/activity") -> when {
                ordersRefused -> refused("Orders are not available")
                url.queryParameter("after") == "c2" -> ok("""{"orders": [{"id": "o2"}, {"id": "o3"}], "orders_total": 3, "next": ""}""")
                else -> ok("""{"orders": [{"id": "o1"}, {"id": "o2"}], "orders_total": 3, "next": "c2"}""")
                    .apply { if (slow) setBodyDelay(400, TimeUnit.MILLISECONDS) }
            }
            url.encodedPath.endsWith("-/audit/list") -> ok("""{"audit": [], "total": 0, "next": ""}""")
            else -> null
        }
    }

    @Before
    fun begin() {
        server.start()
    }

    @After
    fun end() = server.stop()

    private fun model(): DashboardViewModel {
        val model = DashboardViewModel(server.repository, SavedStateHandle())
        server.until { !model.state.value.overviewLoading && !model.state.value.isLoading(DashboardTab.ORDERS) }
        return model
    }

    private fun activity() = server.made("-/metrics/activity")

    @Test
    fun `a tab pages by the cursor its last page gave and lists each row once`() {
        val model = model()
        assertEquals(listOf("o1", "o2"), model.state.value.orders.map { order -> order.id })
        assertTrue(model.state.value.hasMore(DashboardTab.ORDERS))
        model.loadMore()
        server.until { model.state.value.orders.size == 3 }
        assertEquals("c2", activity().last().requestUrl!!.queryParameter("after"))
        assertEquals(listOf("o1", "o2", "o3"), model.state.value.orders.map { order -> order.id })
        assertFalse(model.state.value.hasMore(DashboardTab.ORDERS))
    }

    @Test
    fun `a tab's failed first page is held until retried and asks for nothing more`() {
        ordersRefused = true
        val model = model()
        assertEquals("Orders are not available", model.state.value.errors[DashboardTab.ORDERS]?.userMessage())
        assertFalse(model.state.value.hasMore(DashboardTab.ORDERS))
        model.loadMore()
        server.idle()
        assertEquals(1, activity().size)
        ordersRefused = false
        model.retryTab()
        server.until { model.state.value.orders.size == 2 }
        assertNull(model.state.value.errors[DashboardTab.ORDERS])
    }

    @Test
    fun `a failed next page is held as failed with its reason`() {
        val model = model()
        ordersRefused = true
        model.loadMore()
        server.until { model.state.value.loadOf(DashboardTab.ORDERS) is LoadState.Failed }
        ordersRefused = false
        model.loadMore()
        server.until { model.state.value.orders.size == 3 }
        assertEquals(LoadState.Idle, model.state.value.loadOf(DashboardTab.ORDERS))
    }

    @Test
    fun `the overview failing leaves the tabs as they are`() {
        overviewRefused = true
        val model = model()
        assertNotNull(model.state.value.overviewError)
        assertNull(model.state.value.overview)
        assertEquals(2, model.state.value.orders.size)
        assertNull(model.state.value.errors[DashboardTab.ORDERS])
    }

    @Test
    fun `a read the tab's retry replaces records no failure, not even for a moment`() {
        slow = true
        val model = DashboardViewModel(server.repository, SavedStateHandle())
        val seen = CopyOnWriteArrayList<DashboardUiState>()
        val watch = CoroutineScope(Dispatchers.Unconfined).launch { model.state.collect { state -> seen += state } }
        model.retryTab()
        server.until { !model.state.value.isLoading(DashboardTab.ORDERS) && model.state.value.loaded(DashboardTab.ORDERS) }
        server.idle()
        watch.cancel()
        assertTrue(seen.none { state -> state.errors[DashboardTab.ORDERS] != null })
        assertNull(model.state.value.errors[DashboardTab.ORDERS])
        assertEquals(2, model.state.value.orders.size)
    }

    @Test
    fun `a cancelled read is a cancellation, not a failure`() {
        slow = true
        val caught = CopyOnWriteArrayList<Throwable>()
        val read = CoroutineScope(Dispatchers.Main).launch {
            try {
                server.repository.getMetricsActivity(tab = DashboardTab.ORDERS)
            } catch (e: Throwable) {
                caught += e
                throw e
            }
        }
        server.until { activity().isNotEmpty() }
        read.cancel()
        server.until { caught.isNotEmpty() }
        assertTrue(caught.single().toString(), caught.single() is CancellationException)
    }

    @Test
    fun `the screen shows a failed tab's reason with a retry and does not ask again by itself`() {
        ordersRefused = true
        val model = model()
        rule.setContent { DashboardScreen(viewModel = model) }
        rule.waitForIdle()
        // At the end of the list, where a list that has more asks for it.
        val retry = hasText(server.context.getString(MochiR.string.common_retry)) and hasClickAction()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(retry)
        rule.onNodeWithText("Orders are not available").assertExists()
        server.idle(600)
        rule.waitForIdle()
        server.idle(600)
        assertEquals(1, activity().size)
        ordersRefused = false
        rule.onNode(retry).performClick()
        server.until { model.state.value.orders.size == 2 }
    }

    @Test
    fun `the screen shows a failed overview's reason with a retry`() {
        overviewRefused = true
        val model = model()
        rule.setContent { DashboardScreen(viewModel = model) }
        rule.onNodeWithText("Overview is not available").assertExists()
        overviewRefused = false
        rule.onNode(hasText(server.context.getString(MochiR.string.common_retry)) and hasClickAction()).performClick()
        server.until { model.state.value.overview != null }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun `every summary card is drawn on a narrow phone`() {
        val model = model()
        rule.setContent { DashboardScreen(viewModel = model) }
        rule.waitForIdle()
        for (label in listOf(
            R.string.staff_dashboard_kpi_active_listings,
            R.string.staff_dashboard_kpi_total_orders,
            R.string.staff_dashboard_kpi_revenue,
            R.string.staff_dashboard_kpi_sellers,
            R.string.staff_dashboard_kpi_buyers,
            R.string.staff_dashboard_kpi_open_disputes,
            R.string.staff_dashboard_kpi_pending_moderation,
        )) {
            rule.onNodeWithText(server.context.getString(label)).assertExists()
        }
    }

    @Test
    fun `summary cards fit as many to a row as the width and font scale allow`() {
        assertEquals(1, kpiColumns(328.dp, 1f))
        assertEquals(2, kpiColumns(380.dp, 1f))
        assertEquals(1, kpiColumns(380.dp, 1.5f))
        assertEquals(4, kpiColumns(768.dp, 1f))
        assertEquals(1, kpiColumns(100.dp, 1f))
    }
}
