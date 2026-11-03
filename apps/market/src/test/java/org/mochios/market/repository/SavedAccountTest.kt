// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.repository

import android.os.Looper
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * The saved listings are one account's: another account signing in on the
 * same phone is not shown them, nor a list read before it signed in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SavedAccountTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager
    private lateinit var repository: SavedRepository
    private lateinit var base: String
    private lateinit var api: MarketApi

    /** Holds the list's answer while it is set. */
    @Volatile private var hold: CountDownLatch? = null
    private val lists = AtomicInteger()

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path.orEmpty().substringBefore("?").removePrefix("/market/")) {
                    "-/saved/list" -> {
                        lists.incrementAndGet()
                        hold?.await(5, TimeUnit.SECONDS)
                        MockResponse().setBody("""{"data": {"saved": [{"id": "l1", "title": "Pot"}]}}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
        }
        server.start()
        base = server.url("/").toString().trimEnd('/')
        session = SessionManager(context)
        runBlocking {
            session.clearAll()
            session.setServerUrl(base)
            session.setBoundAccount("first", base)
        }
        api = Retrofit.Builder()
            .baseUrl(server.url("/market/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MarketApi::class.java)
        repository = SavedRepository(api, session)
    }

    @After
    fun end() {
        hold?.countDown()
        server.shutdown()
    }

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    /** Read the first account's saved listings, until the mirror holds them. */
    private fun held() = until {
        runBlocking { repository.refresh() }
        repository.saved.value.isNotEmpty()
    }

    @Test
    fun `another account signing in is not shown the previous one's saved listings`() {
        held()
        assertEquals(listOf("l1"), repository.saved.value.map { it.id })
        runBlocking { session.setBoundAccount("second", base) }
        until { repository.saved.value.isEmpty() }
    }

    @Test
    fun `the first read as an account signs in is kept`() {
        // Signed out, then the account is bound while the first read is under
        // way: there was nothing to clear, and the read is the new account's.
        runBlocking {
            session.clearAll()
            session.setServerUrl(base)
        }
        val fresh = SavedRepository(api, session)
        Thread.sleep(300)
        hold = CountDownLatch(1)
        val before = lists.get()
        val read = thread { runBlocking { fresh.refresh() } }
        until { lists.get() > before }
        runBlocking { session.setBoundAccount("first", base) }
        Thread.sleep(300)
        hold!!.countDown()
        read.join(5_000)
        assertEquals(listOf("l1"), fresh.saved.value.map { it.id })
    }

    @Test
    fun `a list read for the previous account is not kept for the next`() {
        held()
        hold = CountDownLatch(1)
        val before = lists.get()
        val read = thread { runBlocking { repository.refresh() } }
        until { lists.get() > before }
        runBlocking { session.setBoundAccount("second", base) }
        until { repository.saved.value.isEmpty() }
        hold!!.countDown()
        read.join(5_000)
        assertEquals(emptyList<String>(), repository.saved.value.map { it.id })
    }
}
