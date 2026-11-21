// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.person

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.people.R
import org.mochios.people.api.PeopleApi
import org.mochios.people.repository.PeopleRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** A person's id, and the fingerprint core derives from it. */
private const val ADA = "12C83B6Phg9Ap8bK58uqrzWKBYRXAo9pSzsHzSJnEsAnCnRpdob"
private const val PRINT = "6MFgfL93a"

/**
 * A person's page asks where the viewer stands with that person beside the
 * person's information, not after it: the information is a relay to the
 * person's server when they live elsewhere, about half a second. It asks for
 * that one person, falling back to the whole list on a server that predates
 * the question. The page is the person asked for: an answer naming someone
 * else is a failed load.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PersonLoadTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var repository: PeopleRepository
    private lateinit var session: SessionManager

    /** Counted down once the information has been answered. */
    private val answered = CountDownLatch(1)

    /** Whether where the viewer stands was asked while the information was still unanswered. */
    @Volatile private var overlapped: Boolean? = null

    /** The status the standing and the list answer with; null answers them. */
    @Volatile private var refusal: Int? = null

    /** The server predates the standing action. */
    @Volatile private var older = false

    /** The id the person's server answers with. */
    @Volatile private var answer = ADA

    private val paths = Collections.synchronizedList(mutableListOf<String>())

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/people/").substringBefore("?")
                paths.add(path)
                return when {
                    path.endsWith("/-/information") -> {
                        // The relay to the person's server.
                        Thread.sleep(500)
                        answered.countDown()
                        ok("""{"id": "$answer", "name": "Ada", "privacy": "public"}""")
                    }
                    path == "-/friends/standing" -> {
                        overlapped = answered.count == 1L
                        val status = refusal
                        when {
                            older -> MockResponse().setResponseCode(404)
                            status != null -> MockResponse().setResponseCode(status).setBody("""{"error": "down"}""")
                            else -> ok("""{"contact": "c1", "friend": true, "sent": false, "received": false}""")
                        }
                    }
                    path == "-/contacts" -> {
                        val status = refusal
                        if (status != null) {
                            MockResponse().setResponseCode(status).setBody("""{"error": "down"}""")
                        } else {
                            ok("""{"contacts": [{"id": "c1", "person": "$ADA", "friend": true, "name": "Ada"}], "received": [], "sent": []}""")
                        }
                    }
                    path == "-/contacts/photos" -> ok("{}")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/people/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        repository = PeopleRepository(retrofit.create(PeopleApi::class.java), FileStore(context))
        session = SessionManager(context)
        runBlocking {
            session.setServerUrl(base)
            session.setBoundAccount("me")
        }
    }

    @After
    fun end() {
        answered.countDown()
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

    private fun open(route: String = ADA): PersonViewModel {
        val model = PersonViewModel(SavedStateHandle(mapOf("id" to route)), repository, session)
        until { !model.uiState.value.isLoading }
        return model
    }

    @Test
    fun `where the viewer stands is asked while the person's information is on its way`() {
        val state = open().uiState.value
        assertEquals("THE POINT: the standing read went out beside the information read", true, overlapped)
        assertEquals("Ada", state.info?.name)
        assertEquals(FriendState.Friend, state.friendState)
    }

    @Test
    fun `the page asks for this person alone, listing no contacts`() {
        open()
        assertTrue(paths.toString(), "-/friends/standing" in paths)
        assertTrue("THE POINT: no list of every contact for one person", "-/contacts" !in paths)
    }

    @Test
    fun `a server that predates the question is read from the list`() {
        older = true
        val state = open().uiState.value
        assertTrue("-/contacts" in paths)
        assertEquals(FriendState.Friend, state.friendState)
    }

    @Test
    fun `a failed read of where the viewer stands still shows the person`() {
        refusal = 500
        val state = open().uiState.value
        assertNull("THE POINT: the page is the person, not an error", state.error)
        assertEquals("Ada", state.info?.name)
        assertTrue("where the viewer stands is not known", state.friendState == FriendState.Unknown)
    }

    @Test
    fun `an answer naming someone other than the person asked for is a failed load`() {
        answer = "1".repeat(50)
        val state = open().uiState.value
        // THE POINT: no page, avatar or button for another person under this one's address.
        assertNull(state.info)
        assertEquals(MochiError.Local(R.string.people_person_unavailable), state.error)
    }

    @Test
    fun `a page opened by fingerprint is the person whose id derives it`() {
        val state = open(PRINT).uiState.value
        assertEquals(ADA, state.info?.id)
        assertEquals(FriendState.Friend, state.friendState)
        answer = "1".repeat(50)
        val other = open(PRINT).uiState.value
        assertNull(other.info)
        assertEquals(MochiError.Local(R.string.people_person_unavailable), other.error)
    }

    @Test
    fun `a fingerprint is derived as core derives it`() {
        assertEquals(PRINT, fingerprintOf(ADA))
        assertTrue(isFingerprint(PRINT))
        assertTrue(!isFingerprint(ADA))
    }
}
