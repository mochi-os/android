// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market

import android.content.Context
import android.os.Looper
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.mochios.android.files.FileStore
import org.mochios.market.api.MarketApi
import org.mochios.market.repository.MarketRepository
import org.robolectric.Shadows.shadowOf
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/** One request the market's server was asked, its form or query decoded. */
data class Asked(val path: String, val fields: Map<String, String>)

/**
 * A market server for flow tests: each action path answers as a test sets it
 * (404 otherwise), and every request is kept, in order, with its fields.
 */
class FlowServer {

    private val server = MockWebServer()
    private val routes = ConcurrentHashMap<String, (RecordedRequest) -> MockResponse>()

    /** Every request, in the order they arrived. */
    val asked = ConcurrentLinkedQueue<Asked>()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/market/").substringBefore("?")
                val query = request.path.orEmpty().substringAfter('?', "")
                val body = request.body.readUtf8()
                asked.add(Asked(path, fields(query) + fields(body)))
                return routes[path]?.invoke(request) ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    /** Answer [path] with [answer]. */
    fun on(path: String, answer: (RecordedRequest) -> MockResponse) {
        routes[path] = answer
    }

    /** Answer [path] with `{"data": [json]}`. */
    fun data(path: String, json: String) = on(path) { MockResponse().setBody("""{"data": $json}""") }

    fun stop() = server.shutdown()

    fun api(): MarketApi = Retrofit.Builder()
        .baseUrl(server.url("/market/"))
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(MarketApi::class.java)

    fun repository(context: Context) = MarketRepository(api(), FileStore(context))

    /** The requests made of [path], in order. */
    fun of(path: String): List<Asked> = asked.filter { it.path == path }

    private fun fields(encoded: String): Map<String, String> =
        encoded.split('&').filter { it.isNotEmpty() }.associate { pair ->
            val name = URLDecoder.decode(pair.substringBefore('='), "UTF-8")
            val value = URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
            name to value
        }
}

/** Wait for [check], running the main looper the answers resume on. */
fun until(what: String, check: () -> Boolean) {
    repeat(400) {
        shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
        if (check()) return
        Thread.sleep(10)
    }
    throw AssertionError("timed out waiting for $what")
}

/** Run the main looper for [millis], for an answer that should not come. */
fun settle(millis: Long = 600) {
    val end = System.currentTimeMillis() + millis
    while (System.currentTimeMillis() < end) {
        shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS)
        Thread.sleep(10)
    }
}
