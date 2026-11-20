// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.browse

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.android.ui.components.LoadState
import org.mochios.market.api.MarketApi
import org.mochios.market.lib.RecentlyViewedStore
import org.mochios.market.repository.MarketRepository
import org.mochios.market.repository.SavedRepository
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
 * The market's front page as its reads succeed and fail: a failed search is
 * shown and tried again on request, pages follow the server's cursor, an
 * account that could not be read is not taken for a new one, a price is
 * searched for once typing pauses, a refused activation says why, and the
 * recently viewed strip is read in one call that forgets what is gone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeLoadTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager
    private lateinit var base: String
    private lateinit var api: MarketApi

    /** Each search's query, in order. */
    private val searches = CopyOnWriteArrayList<String>()
    private val batches = AtomicInteger()

    /** The searches still to fail, each with a 502. */
    private val failing = AtomicInteger()

    /** The answer to a search, by the cursor it asked after ("" for none). */
    @Volatile private var pages: Map<String, String> = mapOf(
        "" to """{"listings": [{"id": "l1", "title": "Teapot", "status": "active"}], "total": 1, "next": ""}""",
    )

    /** The account's answer: its status, or null to refuse the read. */
    @Volatile private var account: String? = "active"

    @Volatile private var batch = """{"listings": []}"""

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/market/").substringBefore("?")
                return when (path) {
                    "-/listings/viewer/search" -> {
                        val url = request.requestUrl!!
                        searches += url.query.orEmpty()
                        if (failing.getAndUpdate { left -> if (left > 0) left - 1 else 0 } > 0) {
                            refuse(502, "Comptroller is not available")
                        } else {
                            ok(pages[url.queryParameter("after").orEmpty()] ?: """{"listings": [], "total": 0, "next": ""}""")
                        }
                    }
                    "-/accounts/get" -> account?.let { status -> ok("""{"id": "me", "status": "$status"}""") }
                        ?: refuse(502, "Comptroller is not available")
                    "-/accounts/activate" -> refuse(400, "Your account is suspended")
                    "-/listings/viewer/batch" -> {
                        batches.incrementAndGet()
                        ok(batch)
                    }
                    "-/categories/list" -> ok("[]")
                    "-/saved/list" -> ok("""{"saved": []}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        base = server.url("/").toString().trimEnd('/')
        session = SessionManager(context)
        runBlocking {
            session.clearAll()
            session.setServerUrl(base)
            session.setBoundAccount("me", base)
            // The store outlives a test; start each with nothing viewed.
            recent.clear()
        }
        api = Retrofit.Builder()
            .baseUrl(server.url("/market/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MarketApi::class.java)
    }

    @After
    fun end() = server.shutdown()

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun refuse(code: Int, message: String) =
        MockResponse().setResponseCode(code).setBody("""{"error": "$message"}""")

    private val recent by lazy { RecentlyViewedStore(context, session) }

    private fun model() = HomeViewModel(
        MarketRepository(api, FileStore(context), session),
        SavedRepository(api, session),
        recent,
        SavedStateHandle(),
    )

    /** Runs the main looper, its clock included, until [done]. */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    /** Lets everything under way finish, a second of the main looper's clock included. */
    private fun settle() {
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }

    private fun HomeViewModel.idle() = state.value.load == LoadState.Idle

    @Test
    fun `a failed search is the page's error, and retry searches again`() {
        failing.set(1)
        val model = model()
        until { model.state.value.load is LoadState.Failed }
        val failure = (model.state.value.load as LoadState.Failed).error
        assertEquals("Comptroller is not available", failure.userMessage())
        model.retry()
        until { model.idle() && model.state.value.listings.isNotEmpty() }
        assertEquals(listOf("l1"), model.state.value.listings.map { listing -> listing.id })
    }

    @Test
    fun `the next page is asked for after the cursor, and a listing it repeats is listed once`() {
        pages = mapOf(
            "" to """{"listings": [{"id": "l1"}, {"id": "l2"}], "total": 4, "next": "c2"}""",
            "c2" to """{"listings": [{"id": "l2"}, {"id": "l3"}], "total": 4, "next": ""}""",
        )
        val model = model()
        until { model.idle() && model.state.value.listings.size == 2 }
        model.loadMore()
        until { model.idle() && model.state.value.listings.size == 3 }
        assertEquals(listOf("l1", "l2", "l3"), model.state.value.listings.map { listing -> listing.id })
        assertTrue(searches.last(), "after=c2" in searches.last() && "page=2" in searches.last())
        assertTrue(!model.state.value.paging.more)
    }

    @Test
    fun `a next page that failed waits for the retry`() {
        pages = mapOf(
            "" to """{"listings": [{"id": "l1"}], "total": 2, "next": "c1"}""",
            "c1" to """{"listings": [{"id": "l2"}], "total": 2, "next": ""}""",
        )
        val model = model()
        until { model.idle() && model.state.value.listings.size == 1 }
        failing.set(1)
        model.loadMore()
        until { model.state.value.load is LoadState.Failed }
        settle()
        assertEquals(2, searches.size)
        assertEquals(listOf("l1"), model.state.value.listings.map { listing -> listing.id })
        model.retry()
        until { model.idle() && model.state.value.listings.size == 2 }
        assertEquals(3, searches.size)
    }

    @Test
    fun `an account that could not be read is not asked to activate`() {
        account = null
        val model = model()
        until { model.idle() }
        settle()
        assertNull(model.state.value.accountActive)
    }

    @Test
    fun `an inactive account is asked to activate`() {
        account = "inactive"
        val model = model()
        until { model.state.value.accountActive == false }
    }

    @Test
    fun `a refused activation says why`() {
        account = "inactive"
        val model = model()
        until { model.idle() }
        val failures = CopyOnWriteArrayList<MochiError>()
        CoroutineScope(Dispatchers.Main).launch { model.failures.collect { failure -> failures += failure } }
        settle()
        model.activateAccount()
        until { failures.isNotEmpty() }
        assertEquals("Your account is suspended", failures.single().userMessage())
    }

    @Test
    fun `a price is searched for once typing pauses`() {
        val model = model()
        until { model.idle() }
        settle()
        val before = searches.size
        // A keystroke every tenth of a second, with real time behind it for
        // any search it starts to reach the server.
        for (typed in listOf("1", "12", "125")) {
            model.setFilter(Filter.PRICE_MIN, typed)
            shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
            Thread.sleep(200)
        }
        until { searches.size > before && model.idle() }
        settle()
        assertEquals(before + 1, searches.size)
        assertTrue(searches.last(), "min=12500" in searches.last())
    }

    @Test
    fun `choosing a currency with no price set searches for nothing`() {
        val model = model()
        until { model.idle() }
        settle()
        val before = searches.size
        model.setFilter(Filter.CURRENCY, "eur")
        settle()
        assertEquals(before, searches.size)
    }

    @Test
    fun `the strip is read in one call that forgets listings gone or no longer on sale`() {
        runBlocking {
            recent.push("l1")
            recent.push("l2")
            recent.push("l3")
        }
        batch = """{"listings": [{"id": "l1", "status": "active"}, {"id": "l2", "status": "sold"}]}"""
        val model = model()
        until { model.state.value.recentListings.isNotEmpty() }
        settle()
        assertEquals(listOf("l1"), model.state.value.recentListings.map { listing -> listing.id })
        assertEquals(1, batches.get())
        assertEquals(listOf("l1"), runBlocking { recent.observe().first() })
    }
}
