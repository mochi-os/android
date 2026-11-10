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
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.people.api.PeopleApi
import org.mochios.people.repository.PeopleRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A person's page asks for the viewer's contacts beside the person's
 * information, not after it: the information is a relay to the person's server
 * when they live elsewhere, about half a second, and the contacts waited behind
 * it on every person page. A failed read of the contacts still shows the person.
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

    /** Whether the contacts were asked for while the information was still unanswered. */
    @Volatile private var overlapped: Boolean? = null

    /** The status the contacts answer with; null answers them. */
    @Volatile private var refusal: Int? = null

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path.orEmpty().substringAfter("/people/").substringBefore("?")) {
                    "p1/-/information" -> {
                        // The relay to the person's server.
                        Thread.sleep(500)
                        answered.countDown()
                        ok("""{"id": "e1", "name": "Ada", "privacy": "public"}""")
                    }
                    "-/contacts" -> {
                        overlapped = answered.count == 1L
                        val status = refusal
                        if (status != null) {
                            MockResponse().setResponseCode(status).setBody("""{"error": "down"}""")
                        } else {
                            ok("""{"contacts": [{"id": "c1", "person": "e1", "friend": true, "name": "Ada"}], "received": [], "sent": []}""")
                        }
                    }
                    "-/contacts/photos" -> ok("{}")
                    else -> MockResponse().setResponseCode(404)
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
            session.setBoundAccount("me", base)
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

    private fun open(): PersonViewModel {
        val model = PersonViewModel(SavedStateHandle(mapOf("id" to "p1")), repository, session)
        until { !model.uiState.value.isLoading }
        return model
    }

    @Test
    fun `the contacts are asked for while the person's information is on its way`() {
        val state = open().uiState.value
        assertEquals("THE POINT: the contacts read went out beside the information read", true, overlapped)
        assertEquals("Ada", state.info?.name)
        assertEquals(FriendState.Friend, state.friendState)
    }

    @Test
    fun `a failed read of the contacts still shows the person`() {
        refusal = 500
        val state = open().uiState.value
        assertNull("THE POINT: the page is the person, not an error", state.error)
        assertEquals("Ada", state.info?.name)
        assertTrue("where the viewer stands is not known", state.friendState == FriendState.Unknown)
    }
}
