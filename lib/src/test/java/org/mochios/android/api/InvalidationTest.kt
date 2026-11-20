// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.api

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Signing out takes the session, every app token and the phone's synced
 * contacts and calendars, so only a 401 about this session may do it: core's
 * own, or one core confirms. An app relaying a peer server's 401 must not.
 */
class InvalidationTest {
    private lateinit var server: MockWebServer
    private val answers = mutableMapOf<String, MockResponse>()
    private var held: String? = "s1"
    private var cleared = 0

    private fun json(code: Int, body: String) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Before
    fun start() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                answers[request.path!!.substringBefore('?')] ?: MockResponse().setResponseCode(404)
        }
        server.start()
    }

    @After
    fun stop() = server.shutdown()

    private fun client(session: () -> String? = { held }) = OkHttpClient.Builder()
        .addInterceptor(invalidationInterceptor(session = { session() }, clear = { cleared++; held = null }))
        .build()

    private fun call(path: String, session: () -> String? = { held }): Pair<Int, String> =
        client(session).newCall(
            Request.Builder().url(server.url(path)).header("Authorization", "Bearer app-token").build()
        ).execute().use { response -> response.code to response.body.string() }

    private fun requests(): List<RecordedRequest> =
        generateSequence { server.takeRequest(100, TimeUnit.MILLISECONDS) }.toList()

    @Test
    fun `a peer's 401 relayed by an app keeps the session, once core says it lives`() {
        answers["/forums/f1/-/view"] = json(401, """{"error": "Not signed in there"}""")
        answers["/_/identity"] = json(200, """{"user": {"status": "active"}}""")
        val (code, body) = call("/forums/f1/-/view")
        assertEquals(0, cleared)
        assertEquals("the request's own error reaches its caller", 401, code)
        assertEquals("""{"error": "Not signed in there"}""", body)
        val asked = requests()
        assertEquals(listOf("/forums/f1/-/view", "/_/identity"), asked.map { it.path })
        assertNull("core is asked about the session alone", asked[1].getHeader("Authorization"))
        assertEquals("GET", asked[1].method)
    }

    // An app relaying a peer's answer can carry core's code in its body.
    @Test
    fun `core's code in an app's answer is put to core too`() {
        answers["/forums/f1/-/view"] = json(401, """{"error": "authentication_required"}""")
        answers["/_/identity"] = json(200, """{"user": {"status": "active"}}""")
        call("/forums/f1/-/view")
        assertEquals(0, cleared)
    }

    @Test
    fun `core's own 401 signs out`() {
        answers["/_/token"] = json(401, """{"error": "authentication_required", "message": "Sign in"}""")
        call("/_/token")
        assertEquals(1, cleared)
        assertEquals("nothing more to ask", listOf("/_/token"), requests().map { it.path })
    }

    @Test
    fun `a 401 core confirms signs out`() {
        answers["/feeds/-/list"] = json(401, """{"error": "authentication_required"}""")
        answers["/_/identity"] = json(401, """{"error": "authentication_required", "message": "Sign in"}""")
        call("/feeds/-/list")
        assertEquals(1, cleared)
    }

    @Test
    fun `an identity read that does not answer keeps the session`() {
        answers["/feeds/-/list"] = json(401, """{"error": "x"}""")
        answers["/_/identity"] = MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        call("/feeds/-/list")
        assertEquals(0, cleared)
    }

    @Test
    fun `an HTML 401 is a portal's, not core's`() {
        answers["/feeds/-/list"] = MockResponse().setResponseCode(401).setHeader("Content-Type", "text/html").setBody("<html>")
        call("/feeds/-/list")
        assertEquals(0, cleared)
        assertEquals(listOf("/feeds/-/list"), requests().map { it.path })
    }

    @Test
    fun `a session that changed meanwhile is not the one cleared`() {
        answers["/_/token"] = json(401, """{"error": "authentication_required"}""")
        val sessions = ArrayDeque(listOf("s1", "s2"))
        call("/_/token") { sessions.removeFirstOrNull() ?: "s2" }
        assertEquals(0, cleared)
    }

    @Test
    fun `no 401, no question`() {
        answers["/feeds/-/list"] = json(200, """{"data": []}""")
        call("/feeds/-/list")
        assertEquals(listOf("/feeds/-/list"), requests().map { it.path })
    }
}
