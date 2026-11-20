// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.buying

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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.AppContext
import org.mochios.market.api.MarketApi
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
 * The Saved page says why its list could not be read instead of claiming
 * nothing is saved, and says why a change was refused.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SavedFailuresTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager

    @Volatile private var listed = true

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path.orEmpty().substringBefore("?").removePrefix("/market/")) {
                    "-/saved/list" -> if (listed) {
                        MockResponse().setBody("""{"data": {"saved": [{"id": "l1", "title": "Pot"}], "total": 1}}""")
                    } else {
                        MockResponse().setResponseCode(502).setBody("""{"error": "Comptroller is not available"}""")
                    }
                    else -> MockResponse().setResponseCode(400).setBody("""{"error": "Saved list is full"}""")
                }
        }
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        session = SessionManager(context)
        runBlocking {
            session.clearAll()
            session.setServerUrl(base)
            session.setBoundAccount("me")
        }
    }

    @After
    fun end() = server.shutdown()

    private fun model(): SavedListingsViewModel {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/market/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MarketApi::class.java)
        return SavedListingsViewModel(SavedRepository(api, session))
    }

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    @Test
    fun `a list that could not be read says why`() {
        listed = false
        val model = model()
        until { !model.state.value.isLoading && model.state.value.error != null }
        assertEquals("Comptroller is not available", model.state.value.error!!.userMessage())
    }

    @Test
    fun `a refused clear says why`() {
        val model = model()
        until { !model.state.value.isLoading && model.state.value.listings.isNotEmpty() }
        val failures = CopyOnWriteArrayList<MochiError>()
        CoroutineScope(Dispatchers.Main).launch { model.failures.collect { failure -> failures += failure } }
        shadowOf(Looper.getMainLooper()).idle()
        model.clearAll()
        until { failures.isNotEmpty() }
        assertEquals("Saved list is full", failures.single().userMessage())
        assertEquals(listOf("l1"), model.state.value.listings.map { listing -> listing.id })
    }
}
