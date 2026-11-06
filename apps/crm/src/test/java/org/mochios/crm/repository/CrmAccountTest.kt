// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.repository

import android.os.Looper
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.crm.api.CrmsApi
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
 * The CRM caches hold one account's answers: another account signing in
 * on the same phone is not shown them, nor a read begun before it signed in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CrmAccountTest {
    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager
    private lateinit var repository: CrmsRepository
    private lateinit var base: String
    private lateinit var api: CrmsApi

    /** Holds the information's answer while it is set. */
    @Volatile private var hold: CountDownLatch? = null
    private val informations = AtomicInteger()

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path.orEmpty().substringBefore("?")) {
                    "/crm/x1/-/information" -> {
                        informations.incrementAndGet()
                        hold?.await(5, TimeUnit.SECONDS)
                        ok("""{"crm": {"id": "x1", "name": "One"}}""")
                    }
                    "/crm/x1/-/objects" -> ok("""{"objects": [{"id": "o1", "crm": "x1"}], "watched": ["o1"]}""")
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
            .baseUrl(server.url("/crm/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CrmsApi::class.java)
        repository = CrmsRepository(api, FileStore(context), context, session)
    }

    @After
    fun end() {
        hold?.countDown()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    /** Read the CRM as the first account, until the caches hold it. */
    private fun held() = until {
        runBlocking {
            repository.getCrmInfo("x1")
            repository.getObjects("x1")
        }
        repository.getCachedCrmInfo("x1") != null && repository.getCachedObjects("x1") != null
    }

    @Test
    fun `another account signing in is not shown the previous one's answers`() {
        held()
        assertEquals(listOf("o1"), repository.getWatched("x1"))
        runBlocking { session.setBoundAccount("second", base) }
        until { repository.getCachedCrmInfo("x1") == null }
        assertNull(repository.getCachedObjects("x1"))
        assertEquals(emptyList<String>(), repository.getWatched("x1"))
    }

    @Test
    fun `the first read as an account signs in is kept`() {
        // Signed out, then the account is bound while the first read is under
        // way: there was nothing to clear, and the read is the new account's.
        runBlocking {
            session.clearAll()
            session.setServerUrl(base)
        }
        val fresh = CrmsRepository(api, FileStore(context), context, session)
        Thread.sleep(300)
        hold = CountDownLatch(1)
        val before = informations.get()
        val read = thread { runBlocking { fresh.getCrmInfo("x1") } }
        until { informations.get() > before }
        runBlocking { session.setBoundAccount("first", base) }
        Thread.sleep(300)
        hold!!.countDown()
        read.join(5_000)
        assertNotNull(fresh.getCachedCrmInfo("x1"))
    }

    @Test
    fun `a read begun for the previous account is not kept for the next`() {
        held()
        repository.invalidateCache("x1")
        runBlocking { repository.getObjects("x1") }
        hold = CountDownLatch(1)
        val before = informations.get()
        val read = thread { runBlocking { repository.getCrmInfo("x1") } }
        until { informations.get() > before }
        runBlocking { session.setBoundAccount("second", base) }
        // The switch has reached the caches once the objects are gone.
        until { repository.getCachedObjects("x1") == null }
        hold!!.countDown()
        read.join(5_000)
        assertNull(repository.getCachedCrmInfo("x1"))
    }
}
