// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.buying

import android.os.Looper
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.userMessage
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.android.ui.components.LoadState
import org.mochios.market.api.MarketApi
import org.mochios.market.repository.MarketRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A list's next page that failed is held as failed, with the server's
 * reason, until it is asked for again; pages follow the server's cursor, and
 * an order a page repeats is listed once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PurchasesPagingTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer

    private val queries = CopyOnWriteArrayList<String>()
    private val failing = AtomicInteger()

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                return when (url.encodedPath.substringAfter("/market/")) {
                    "-/orders/purchases" -> {
                        queries += url.query.orEmpty()
                        when {
                            failing.getAndUpdate { left -> if (left > 0) left - 1 else 0 } > 0 ->
                                MockResponse().setResponseCode(429).setBody("""{"error": "Too many requests"}""")
                            url.queryParameter("after") == "c2" ->
                                ok("""{"orders": [{"id": "o2"}, {"id": "o3"}], "total": 4, "next": ""}""")
                            else -> ok("""{"orders": [{"id": "o1"}, {"id": "o2"}], "total": 4, "next": "c2"}""")
                        }
                    }
                    "-/bids/mine" -> ok("""{"bids": [], "total": 0}""")
                    else -> ok("null")
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

    private fun model(): MyPurchasesViewModel {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/market/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MarketApi::class.java)
        return MyPurchasesViewModel(MarketRepository(api, FileStore(context)))
    }

    @Test
    fun `a failed next page is held with its reason until asked for again`() {
        val model = model()
        until { !model.uiState.value.isInitialLoading }
        failing.set(1)
        model.loadMore()
        until { model.uiState.value.load is LoadState.Failed }
        assertEquals("Too many requests", (model.uiState.value.load as LoadState.Failed).error.userMessage())
        assertEquals(2, queries.size)
        model.loadMore()
        until { model.uiState.value.load == LoadState.Idle && model.uiState.value.orders.size == 3 }
        assertEquals(listOf("o1", "o2", "o3"), model.uiState.value.orders.map { order -> order.id })
        assertTrue(queries.last(), "after=c2" in queries.last())
        assertFalse(model.uiState.value.paging.more)
    }
}
