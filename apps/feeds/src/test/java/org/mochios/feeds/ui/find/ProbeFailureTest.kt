// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.find

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.feeds.api.FeedsApi
import org.mochios.feeds.api.MenuApi
import org.mochios.feeds.repository.FeedsRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A pasted link is asked about through the server's probe. Its 404 is an
 * answer, nothing found there; any other failure, a server that cannot be
 * reached included, is a failure to ask and is said, not shown as no feeds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProbeFailureTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var repository: FeedsRepository

    /** The status the probe answers with. */
    @Volatile private var status = 404

    private val probes = AtomicInteger()

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                return when (path) {
                    "/feeds/-/probe" -> {
                        probes.incrementAndGet()
                        MockResponse().setResponseCode(status).setBody("""{"error": "No feed"}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/feeds/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val session = SessionManager(context)
        runBlocking {
            session.setServerUrl(base)
            session.setBoundAccount("a1")
        }
        repository = FeedsRepository(
            retrofit.create(FeedsApi::class.java),
            retrofit.create(MenuApi::class.java),
            FileStore(context),
            context,
            session,
        )
    }

    @After
    fun end() = server.shutdown()

    /** Runs the main thread until [done]; an assertion fails if it never is. */
    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out")
            Thread.sleep(10)
        }
    }

    private fun probe(): FindFeedsViewModel {
        val model = FindFeedsViewModel(SavedStateHandle(), repository)
        model.setSearchQuery("https://example.com/feed")
        until { probes.get() == 1 && !model.isProbing.value }
        return model
    }

    @Test
    fun `nothing found at the address is no feed, not a failure`() {
        status = 404
        val model = probe()
        assertNull(model.probeResult.value)
        assertNull("THE POINT: the server's 404 is its answer", model.probeError.value)
    }

    @Test
    fun `a probe that cannot be asked is said, and the retry asks again`() {
        status = 502
        val model = probe()
        assertTrue("THE POINT: a failure to ask is not no feeds", model.probeError.value is MochiError.ServerError)

        status = 404
        model.retry()
        until { probes.get() == 2 && !model.isProbing.value }
        assertNull(model.probeError.value)
    }
}
