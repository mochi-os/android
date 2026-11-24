// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.reports

import androidx.lifecycle.SavedStateHandle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.LoadState
import org.mochios.staff.StaffServer
import org.mochios.staff.StaffServer.Companion.ok
import org.mochios.staff.StaffServer.Companion.refused
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * A staff list's next page that failed is held as failed, with the reason,
 * until it is asked for again; a report a page repeats is listed once, and a
 * page with nothing on it ends the list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReportsPagingTest {

    private val failing = AtomicInteger()
    private val server = StaffServer { request ->
        val url = request.requestUrl!!
        when {
            !url.encodedPath.endsWith("-/reports/list") -> ok("null")
            failing.getAndUpdate { left -> if (left > 0) left - 1 else 0 } > 0 -> refused()
            else -> when (url.queryParameter("page")) {
                "2" -> ok("""{"reports": [{"id": "r2"}, {"id": "r3"}], "total": 5}""")
                "3" -> ok("""{"reports": [], "total": 5}""")
                else -> ok("""{"reports": [{"id": "r1"}, {"id": "r2"}], "total": 5}""")
            }
        }
    }

    @Before
    fun begin() {
        server.start()
    }

    @After
    fun end() = server.stop()

    private fun model(): ReportsViewModel {
        val model = ReportsViewModel(server.repository, server.socket, server.session, SavedStateHandle())
        server.until { !model.state.value.isLoading }
        return model
    }

    private fun reads() = server.made("-/reports/list").size

    @Test
    fun `a failed next page is held with its reason until asked for again`() {
        val model = model()
        failing.set(1)
        model.loadMore()
        server.until { model.state.value.load is LoadState.Failed }
        assertEquals("Comptroller is not available", (model.state.value.load as LoadState.Failed).error.userMessage())
        model.loadMore()
        server.until { model.state.value.load == LoadState.Idle && model.state.value.reports.size == 3 }
        assertEquals(listOf("r1", "r2", "r3"), model.state.value.reports.map { report -> report.id })
    }

    @Test
    fun `a page with nothing on it ends the list`() {
        val model = model()
        model.loadMore()
        server.until { model.state.value.load == LoadState.Idle && model.state.value.reports.size == 3 }
        model.loadMore()
        server.until { model.state.value.load == LoadState.Idle && reads() == 3 }
        model.loadMore()
        server.idle(200)
        assertEquals(3, reads())
    }
}
