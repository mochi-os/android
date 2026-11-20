// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.profile

import android.os.Looper
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.people.api.PeopleApi
import org.mochios.people.repository.PeopleRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The profile's directory switch moves on the tap, takes no second tap while
 * its change goes, and goes back when the server refuses, where it used to
 * stay at the refused position.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfilePrivacyTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager
    private lateinit var repository: PeopleRepository
    private val changes = CopyOnWriteArrayList<String>()

    /** Holds the change's answer back until released. */
    private val gate = CountDownLatch(1)

    /** Whether the server refuses the change. */
    @Volatile private var refuse = false

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path.orEmpty().substringBefore("?")) {
                    "/people/p1/-/information" -> ok("""{"id": "p1", "name": "Ada", "privacy": "public"}""")
                    "/people/p1/-/privacy/set" -> {
                        changes += request.body.readUtf8()
                        gate.await(5, TimeUnit.SECONDS)
                        if (refuse) MockResponse().setResponseCode(500).setBody("""{"error": "Not now"}""") else ok("{}")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
        }
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        session = SessionManager(context)
        runBlocking {
            session.setServerUrl(base)
            session.setBoundAccount("p1")
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/people/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        repository = PeopleRepository(retrofit.create(PeopleApi::class.java), FileStore(context))
    }

    @After
    fun end() {
        gate.countDown()
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.MILLISECONDS)
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    private fun model(): ProfileViewModel {
        val made = ProfileViewModel(context, repository, session)
        until { made.uiState.value.info != null }
        return made
    }

    @Test
    fun `the directory switch moves at once and takes no second tap while it goes`() {
        val model = model()
        assertEquals("public", model.uiState.value.privacyDraft)
        model.savePrivacy("private")
        assertEquals("private", model.uiState.value.privacyDraft)
        until { changes.isNotEmpty() }
        model.savePrivacy("public")
        assertEquals("private", model.uiState.value.privacyDraft)
        gate.countDown()
        until { model.uiState.value.savingField == null && model.uiState.value.info?.privacy == "private" }
        assertEquals(1, changes.size)
    }

    @Test
    fun `the directory switch goes back when the server refuses, saying why`() {
        refuse = true
        val model = model()
        model.savePrivacy("private")
        gate.countDown()
        until { model.uiState.value.error != null }
        assertEquals("public", model.uiState.value.privacyDraft)
        assertNotNull(model.uiState.value.error)
    }
}
