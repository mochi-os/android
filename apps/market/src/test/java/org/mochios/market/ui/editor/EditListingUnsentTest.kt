// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.editor

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
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
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.market.api.MarketApi
import org.mochios.market.repository.MarketRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/**
 * A change to a live listing reaches its copies on other networks, and a copy
 * that went without its photo says so, a line each as the server words it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EditListingUnsentTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private val requests = ConcurrentLinkedQueue<Pair<String, String>>()

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/market/").substringBefore("?")
                requests.add(path to request.body.readUtf8())
                return when (path) {
                    "-/photos/delete" -> MockResponse().setBody("""{"data": {"ok": true}, "unsent": ["Deleted, without its photo"]}""")
                    "-/photos/reorder" -> MockResponse().setBody("""{"data": {"ok": true}, "unsent": ["Reordered, without its photo"]}""")
                    "-/listings/publish" -> MockResponse().setBody("""{"data": {"id": "l1"}, "unsent": ["Published, without its photo"]}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun end() {
        server.shutdown()
    }

    private fun repository(): MarketRepository {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/market/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MarketApi::class.java)
        return MarketRepository(api, FileStore(context))
    }

    private fun until(what: String, check: () -> Boolean) {
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
            if (check()) return
            Thread.sleep(10)
        }
        throw AssertionError("timed out waiting for $what")
    }

    @Test
    fun `a photo deleted names its listing, and what the copies went without is said`() {
        val model = EditListingViewModel(SavedStateHandle(mapOf("id" to "l1")), repository())
        val events = mutableListOf<EditListingEvent>()
        val job = CoroutineScope(Dispatchers.Main).launch { model.events.collect { events.add(it) } }
        model.deletePhoto("p1")
        until("the delete's answer") { events.any { it is EditListingEvent.Unsent } }
        assertEquals(EditListingEvent.Unsent("Deleted, without its photo"), events.first { it is EditListingEvent.Unsent })
        val body = requests.first { it.first == "-/photos/delete" }.second
        assertTrue("the delete names the listing, so its copies follow: $body", body.contains("listing=l1"))
        job.cancel()
    }

    @Test
    fun `a reorder and a publish say what the copies went without`() {
        val repository = repository()
        assertEquals(listOf("Reordered, without its photo"), runBlocking { repository.reorderPhotos("l1", listOf("p1")) })
        val published = runBlocking { repository.publishListing(mapOf("id" to "l1")) }
        assertEquals("l1", published.data.id)
        assertEquals(listOf("Published, without its photo"), published.unsent)
    }
}
