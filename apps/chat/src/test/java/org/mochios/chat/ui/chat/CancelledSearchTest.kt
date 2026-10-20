// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.chat

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.internal.http2.ErrorCode
import okhttp3.internal.http2.StreamResetException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.chat.api.ChatApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * A search the next keystroke cancels while its request is on the wire: the
 * catch sees a cancellation, not an IOException, so toMochiError() files it as
 * an unexpected error unless a CancellationException catch rethrows it first.
 * Every other failure, a reset stream among them, still reaches the error.
 */
class CancelledSearchTest {

    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun api(http2: Boolean, response: MockResponse): ChatApi {
        val client = OkHttpClient.Builder().apply {
            if (http2) {
                protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
            }
        }.build()
        if (http2) {
            server.protocols = listOf(Protocol.H2_PRIOR_KNOWLEDGE)
        }
        server.enqueue(response)
        server.start()
        return Retrofit.Builder()
            .baseUrl(server.url("/chat/"))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ChatApi::class.java)
    }

    private val slow = MockResponse()
        .setBody("""{"data": {"results": []}}""")
        .setHeadersDelay(5, TimeUnit.SECONDS)

    /** What the catch received, and whether it went on to show an error. */
    private fun search(api: ChatApi, rethrowCancel: Boolean, cancel: Boolean) = runBlocking {
        var caught: Throwable? = null
        var shown = false
        val job = launch(Dispatchers.IO) {
            try {
                api.search("chat", "dung")
            } catch (e: CancellationException) {
                caught = e
                if (rethrowCancel) {
                    throw e
                }
                shown = true
            } catch (e: Exception) {
                caught = e
                shown = true
            }
        }
        if (cancel) {
            server.takeRequest(5, TimeUnit.SECONDS)
            job.cancelAndJoin()
        } else {
            job.join()
        }
        caught to shown
    }

    @Test
    fun `a cancelled http1 search reaches the catch as a cancellation`() {
        val (caught, shown) = search(api(http2 = false, slow), rethrowCancel = false, cancel = true)
        assertTrue("caught $caught", caught is CancellationException)
        assertFalse("caught $caught", caught is IOException)
        assertTrue(shown)
    }

    @Test
    fun `a cancelled http2 search reaches the catch as a cancellation, not a reset stream`() {
        val (caught, shown) = search(api(http2 = true, slow), rethrowCancel = false, cancel = true)
        assertTrue("caught $caught", caught is CancellationException)
        assertFalse("caught $caught", caught is StreamResetException)
        assertTrue(shown)
    }

    @Test
    fun `rethrowing the cancellation shows nothing for a cancelled search`() {
        assertFalse(search(api(http2 = false, slow), rethrowCancel = true, cancel = true).second)
    }

    @Test
    fun `rethrowing the cancellation shows nothing for a cancelled http2 search`() {
        assertFalse(search(api(http2 = true, slow), rethrowCancel = true, cancel = true).second)
    }

    @Test
    fun `a stream the server resets is still shown as an error`() {
        val reset = MockResponse()
            .setSocketPolicy(SocketPolicy.RESET_STREAM_AT_START)
            .setHttp2ErrorCode(ErrorCode.CANCEL.httpCode)
        val (caught, shown) = search(api(http2 = true, reset), rethrowCancel = true, cancel = false)
        assertTrue("caught $caught", caught is StreamResetException)
        assertTrue(shown)
    }
}
