// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.listing

import android.os.Looper
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.market.api.MarketApi
import org.mochios.market.lib.RecentlyViewedStore
import org.mochios.market.lib.ReportedStore
import org.mochios.market.repository.MarketRepository
import org.mochios.market.repository.SavedRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A listing as it opens: shown as soon as it is read, its photos asked for
 * beside it and its seller's reviews after it, and its timeline asked for only
 * by a party to it, whom the comptroller lets read it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ListingLoadTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager
    private lateinit var base: String

    /** Counted down as the photos are asked for. */
    private val photographed = CountDownLatch(1)

    /** Whether the photos had been asked for while the listing was still unanswered. */
    @Volatile private var overlapped = false

    /** How many times the listing's timeline has been asked for. */
    private val audits = AtomicInteger()

    /** Holds the seller's reviews until released. */
    @Volatile private var keep: CountDownLatch? = null

    /** Whether the viewer has an order on the listing. */
    @Volatile private var ordered = false

    /** Holds the photos' answer until released. */
    @Volatile private var late: CountDownLatch? = null

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/market/").substringBefore("?")
                return when (path) {
                    "-/listings/viewer/get" -> {
                        overlapped = photographed.await(2, TimeUnit.SECONDS)
                        val order = if (ordered) """{"id": "o1", "status": "paid"}""" else "null"
                        ok(
                            """{"listing": {"id": "l1", "title": "Teapot", "seller": "s1", "price": 100, "currency": "jpy", "status": "active"},
                               "seller": {"id": "s1", "name": "Seller"}, "my_order": $order}""",
                        )
                    }
                    "-/photos/list" -> {
                        photographed.countDown()
                        late?.await(10, TimeUnit.SECONDS)
                        ok("""[{"id": "ph2", "rank": 1}, {"id": "ph1", "rank": 0}]""")
                    }
                    "-/audit/object" -> {
                        audits.incrementAndGet()
                        ok("""{"audit": [{"id": "e1"}], "total": 1}""")
                    }
                    "-/reviews/account" -> {
                        keep?.await(10, TimeUnit.SECONDS)
                        ok("""{"reviews": [{"id": "r1"}], "total": 1}""")
                    }
                    "-/categories/list" -> ok("""[{"id": "c1", "name": "Pots"}]""")
                    "-/saved/list" -> ok("""{"saved": []}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        base = server.url("/").toString().trimEnd('/')
        session = SessionManager(context)
        runBlocking { session.setServerUrl(base) }
    }

    @After
    fun end() {
        photographed.countDown()
        keep?.countDown()
        late?.countDown()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    /** The listing's screen as [viewer] opens it. */
    private fun open(viewer: String): ListingDetailViewModel {
        runBlocking { session.setBoundAccount(viewer, base) }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/market/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api = retrofit.create(MarketApi::class.java)
        val model = ListingDetailViewModel(
            MarketRepository(api, FileStore(context)),
            SavedRepository(api),
            RecentlyViewedStore(context),
            ReportedStore(context),
            context,
            session,
        )
        model.load("l1")
        return model
    }

    /** Runs the main looper the network's answers resume on until [done]. */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    /** Lets everything already under way finish. */
    private fun settle() {
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    @Test
    fun `the listing shows as soon as it is read, its photos asked for beside it and its reviews after`() {
        val held = CountDownLatch(1)
        keep = held
        val model = open("buyer")
        until { model.state.value.listing != null && !model.state.value.isLoading }
        // The reviews are still unanswered.
        assertEquals(emptyList<Any>(), model.state.value.sellerReviews)
        assertTrue(overlapped)
        until { model.state.value.photos.size == 2 }
        assertEquals(listOf("ph1", "ph2"), model.state.value.photos.map { it.id })
        held.countDown()
        until { model.state.value.sellerReviews.isNotEmpty() }
    }

    @Test
    fun `a buyer with no order is not asked for the listing's timeline`() {
        val model = open("buyer")
        until { model.state.value.listing != null && !model.state.value.isLoading }
        settle()
        assertEquals(0, audits.get())
        assertEquals(emptyList<Any>(), model.state.value.audit)
    }

    @Test
    fun `the seller and a buyer with an order are asked for the listing's timeline`() {
        val selling = open("s1")
        until { selling.state.value.audit.isNotEmpty() }
        assertEquals(1, audits.get())
        ordered = true
        val bought = open("buyer")
        until { bought.state.value.audit.isNotEmpty() }
        assertEquals(2, audits.get())
    }

    @Test
    fun `photos answered after the timeline and reviews leave them standing`() {
        val held = CountDownLatch(1)
        late = held
        val model = open("s1")
        until {
            val state = model.state.value
            state.audit.isNotEmpty() && state.sellerReviews.isNotEmpty() && state.categories.isNotEmpty()
        }
        held.countDown()
        until { model.state.value.photos.size == 2 }
        settle()
        val state = model.state.value
        assertEquals(listOf("e1"), state.audit.map { it.id })
        assertEquals(listOf("r1"), state.sellerReviews.map { it.id })
        assertEquals(listOf("c1"), state.categories.map { it.id })
        assertEquals("l1", state.listing?.listing?.id)
    }
}
