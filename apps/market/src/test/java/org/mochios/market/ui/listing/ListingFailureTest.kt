// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.listing

import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * A refused action on a listing is reported in the server's words, and the
 * page keeps the server's clock to count an auction down by.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ListingFailureTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager
    private lateinit var base: String

    /** The server's clock, an hour ahead of the phone's. */
    private val ahead = System.currentTimeMillis() / 1000L + 3_600

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/market/").substringBefore("?")
                return when (path) {
                    "-/listings/viewer/get" -> ok(
                        """{"listing": {"id": "l1", "title": "Teapot", "seller": "me", "status": "active"}, "now": $ahead}""",
                    )
                    "-/listings/delete", "-/listings/relist", "-/saved/add" ->
                        MockResponse().setResponseCode(400).setBody("""{"error": "Cannot remove a listing with active orders"}""")
                    "-/saved/list" -> ok("""{"saved": []}""")
                    else -> ok("null")
                }
            }
        }
        server.start()
        base = server.url("/").toString().trimEnd('/')
        session = SessionManager(context)
        runBlocking {
            session.setServerUrl(base)
            session.setBoundAccount("me")
        }
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

    private fun model(): ListingDetailViewModel {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/market/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MarketApi::class.java)
        val model = ListingDetailViewModel(
            MarketRepository(api, FileStore(context)),
            SavedRepository(api, session),
            RecentlyViewedStore(context, session),
            ReportedStore(context, session),
            context,
            session,
        )
        model.load("l1")
        until { model.state.value.listing != null && !model.state.value.isLoading }
        return model
    }

    private fun messages(model: ListingDetailViewModel): MutableList<String> {
        val messages = CopyOnWriteArrayList<String>()
        CoroutineScope(Dispatchers.Main).launch {
            model.events.collect { event -> if (event is ListingDetailEvent.Toast) messages += event.message }
        }
        shadowOf(Looper.getMainLooper()).idle()
        return messages
    }

    @Test
    fun `a refused removal says why`() {
        val model = model()
        val messages = messages(model)
        model.deleteListing()
        until { messages.isNotEmpty() }
        assertEquals("Cannot remove a listing with active orders", messages.single())
    }

    @Test
    fun `a refused relist says why`() {
        val model = model()
        val messages = messages(model)
        model.relistListing()
        until { messages.isNotEmpty() }
        assertEquals("Cannot remove a listing with active orders", messages.single())
    }

    @Test
    fun `a refused bookmark says why`() {
        val model = model()
        val messages = messages(model)
        model.toggleSave()
        until { messages.isNotEmpty() }
        assertEquals("Cannot remove a listing with active orders", messages.single())
    }

    @Test
    fun `the page keeps the server's clock`() {
        val model = model()
        val offset = model.state.value.offset
        assertTrue("$offset", offset in 3_590..3_610)
    }
}
