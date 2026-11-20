// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.reports

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.AppContext
import org.mochios.android.ui.components.LoadState
import org.mochios.staff.api.StaffApi
import org.mochios.staff.repository.StaffRepository
import org.mochios.staff.ws.StaffEventsBus
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A staff list's next page that failed is held as failed, with the reason,
 * until it is asked for again; a report a page repeats is listed once, and a
 * page with nothing on it ends the list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReportsPagingTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private val failing = AtomicInteger()
    private val reads = AtomicInteger()

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                if (!url.encodedPath.endsWith("-/reports/list")) return ok("null")
                reads.incrementAndGet()
                if (failing.getAndUpdate { left -> if (left > 0) left - 1 else 0 } > 0) {
                    return MockResponse().setResponseCode(502).setBody("""{"error": "Comptroller is not available"}""")
                }
                return when (url.queryParameter("page")) {
                    "2" -> ok("""{"reports": [{"id": "r2"}, {"id": "r3"}], "total": 5}""")
                    "3" -> ok("""{"reports": [], "total": 5}""")
                    else -> ok("""{"reports": [{"id": "r1"}, {"id": "r2"}], "total": 5}""")
                }
            }
        }
        server.start()
    }

    @After
    fun end() = server.shutdown()

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun model(): ReportsViewModel {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/staff/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val model = ReportsViewModel(
            StaffRepository(retrofit.create(StaffApi::class.java)),
            StaffEventsBus(),
            SavedStateHandle(),
        )
        until { !model.state.value.isLoading }
        return model
    }

    @Test
    fun `a failed next page is held with its reason until asked for again`() {
        val model = model()
        failing.set(1)
        model.loadMore()
        until { model.state.value.load is LoadState.Failed }
        assertEquals("Comptroller is not available", (model.state.value.load as LoadState.Failed).error.userMessage())
        model.loadMore()
        until { model.state.value.load == LoadState.Idle && model.state.value.reports.size == 3 }
        assertEquals(listOf("r1", "r2", "r3"), model.state.value.reports.map { report -> report.id })
    }

    @Test
    fun `a page with nothing on it ends the list`() {
        val model = model()
        model.loadMore()
        until { model.state.value.load == LoadState.Idle && model.state.value.reports.size == 3 }
        model.loadMore()
        until { model.state.value.load == LoadState.Idle && reads.get() == 3 }
        model.loadMore()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)
        assertEquals(3, reads.get())
    }
}
