// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.domains

import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.mochios.settings.api.DomainsApi
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * What the Domains screen says when something fails once domains are listed,
 * and what "Verify DNS" found, as the web's toasts say it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DomainsFailuresTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer

    /** The route create's answer. */
    @Volatile private var route: MockResponse = json("{}")

    /** Whether the verification record is found. */
    @Volatile private var verified = false

    /** Holds the verify's answer back until released, when set. */
    @Volatile private var verifyGate: CountDownLatch? = null

    /** Whether the domain's details are refused. */
    @Volatile private var detailsRefused = false

    /** Holds each read of the list after the first back until released, when set. */
    @Volatile private var listGate: CountDownLatch? = null
    private val lists = AtomicInteger()

    private val messages = CopyOnWriteArrayList<String>()
    private var collector: Job? = null

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setBody(body).setHeader("Content-Type", "application/json")

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path.orEmpty().substringBefore("?").removePrefix("/")) {
                    "settings/-/domains/data" -> {
                        if (lists.incrementAndGet() > 1) listGate?.await(5, TimeUnit.SECONDS)
                        json("""{"domains": [{"domain": "a.example", "verified": 0, "token": "t0k3n"}], "count": 1, "admin": true}""")
                    }
                    "settings/-/domains/get" -> if (detailsRefused) {
                        json("""{"error": "Database locked"}""", 500)
                    } else {
                        json("""{"domain": {"domain": "a.example"}, "routes": [], "delegations": [], "admin": true}""")
                    }
                    "settings/-/domains/route/create" -> route
                    "settings/-/domains/create" -> json("{}")
                    "settings/-/domains/verify" -> {
                        verifyGate?.await(5, TimeUnit.SECONDS)
                        json("""{"verified": $verified}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
        }
        server.start()
    }

    @After
    fun end() {
        listGate?.countDown()
        verifyGate?.countDown()
        collector?.cancel()
        server.shutdown()
    }

    private fun model(): DomainsViewModel {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DomainsApi::class.java)
        val made = DomainsViewModel(api)
        collector = CoroutineScope(Dispatchers.Unconfined).launch { made.messages.collect { messages += it } }
        until { !made.uiState.value.isLoading && made.uiState.value.domains.isNotEmpty() }
        return made
    }

    /** A refused route used to close its dialog and change nothing, with no word, once a domain was listed. */
    @Test
    fun `a refused route says the server's reason over the listed domains`() {
        route = json("""{"error": "Path is taken"}""", 400)
        val model = model()
        model.createRoute("a.example", "/x", "app", "feeds", 0)
        until { messages.isNotEmpty() }
        assertEquals(listOf("Path is taken"), messages)
        assertEquals(1, model.uiState.value.domains.size)
        assertNull("the list's own error is for a failed read alone", model.uiState.value.error)
    }

    @Test
    fun `a failure the server gives no reason for is said in the action's own words`() {
        route = json("{}", 500)
        val model = model()
        model.createRoute("a.example", "/x", "app", "feeds", 0)
        until { messages.isNotEmpty() }
        assertEquals(listOf("Failed to create route"), messages)
    }

    /** "Verify DNS" used to drop the answer, so a record not there yet looked like nothing happening. */
    @Test
    fun `verifying says the record was not found, and shows the check running until it answers`() {
        verifyGate = CountDownLatch(1)
        val model = model()
        model.verifyDomain("a.example")
        assertTrue(model.uiState.value.verifying.contains("a.example"))
        verifyGate!!.countDown()
        until { messages.isNotEmpty() }
        assertEquals(listOf("TXT record not found"), messages)
        until { model.uiState.value.verifying.isEmpty() }
    }

    @Test
    fun `verifying says the domain is verified when the record is found`() {
        verified = true
        val model = model()
        model.verifyDomain("a.example")
        until { messages.isNotEmpty() }
        assertEquals(listOf("Domain verified"), messages)
    }

    /** A domain whose details failed to load used to expand to an empty card. */
    @Test
    fun `a domain whose details cannot be read keeps why`() {
        detailsRefused = true
        val model = model()
        model.loadDetails("a.example")
        until { model.uiState.value.detailsErrors.containsKey("a.example") }
        assertNotNull(model.uiState.value.detailsErrors["a.example"])
        assertFalse(model.uiState.value.loadingDetails.contains("a.example"))
    }

    /** Every action used to blank the list to a spinner while it was read again. */
    @Test
    fun `the list stays while it is read again after an action`() {
        val model = model()
        listGate = CountDownLatch(1)
        model.createDomain("b.example")
        until { lists.get() >= 2 }
        assertFalse(model.uiState.value.isLoading)
        assertEquals(1, model.uiState.value.domains.size)
        listGate!!.countDown()
    }
}
