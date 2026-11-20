// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.repository

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.market.api.MarketApi
import org.mochios.market.model.Listing
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * A bookmark set while the saved list is being read stays set, and the list a
 * save answers is the list the phone then holds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SavedLockTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var repository: SavedRepository

    /** Holds the list's answer while it is set. */
    @Volatile private var hold: CountDownLatch? = null
    private val lists = AtomicInteger()
    private val adds = AtomicInteger()

    /** What `saved/add` answers. */
    @Volatile private var added = """{"saved": [{"id": "l2", "title": "Kettle"}, {"id": "l1", "title": "Pot"}], "total": 2}"""

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path.orEmpty().substringBefore("?").removePrefix("/market/")) {
                    "-/saved/list" -> {
                        lists.incrementAndGet()
                        hold?.await(5, TimeUnit.SECONDS)
                        // As it was when the read began, before the save.
                        MockResponse().setBody("""{"data": {"saved": [{"id": "l1", "title": "Pot"}], "total": 1}}""")
                    }
                    "-/saved/add" -> {
                        adds.incrementAndGet()
                        MockResponse().setBody("""{"data": $added}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
        }
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        val session = SessionManager(context)
        runBlocking {
            session.clearAll()
            session.setServerUrl(base)
            session.setBoundAccount("first")
        }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/market/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MarketApi::class.java)
        repository = SavedRepository(api, session)
        Thread.sleep(300)
    }

    @After
    fun end() {
        hold?.countDown()
        server.shutdown()
    }

    @Test
    fun `a bookmark set while the list is read stays set`() {
        hold = CountDownLatch(1)
        val read = thread { runBlocking { repository.refresh() } }
        val deadline = System.currentTimeMillis() + 5_000
        while (lists.get() == 0) {
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
        val save = thread { runBlocking { repository.toggle(Listing(id = "l2", title = "Kettle")) } }
        // The save waits for the read, or, without the lock, finishes first.
        Thread.sleep(300)
        hold!!.countDown()
        read.join(5_000)
        save.join(5_000)
        assertEquals(1, adds.get())
        assertEquals(setOf("l1", "l2"), repository.saved.value.map { listing -> listing.id }.toSet())
    }

    @Test
    fun `the saved list a save answers is the list held`() {
        added = """{"saved": [{"id": "l2", "title": "Kettle"}, {"id": "l7", "title": "Tray"}], "total": 2}"""
        runBlocking { repository.toggle(Listing(id = "l2", title = "Kettle")) }
        assertEquals(listOf("l2", "l7"), repository.saved.value.map { listing -> listing.id })
    }

    @Test
    fun `a save answered by an older server keeps the change made`() {
        added = """{"saved": true}"""
        runBlocking { repository.toggle(Listing(id = "l2", title = "Kettle")) }
        assertEquals(listOf("l2"), repository.saved.value.map { listing -> listing.id })
    }
}
