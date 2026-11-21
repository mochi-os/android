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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.android.i18n.AppContext
import org.mochios.people.R
import org.mochios.people.api.PeopleApi
import org.mochios.people.repository.PeopleRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * The profile's limit is counted as the server counts it, by character: a
 * profile of emoji up to the limit is sent, where counting UTF-16 units
 * refused one at half of it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileLengthTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager
    private lateinit var repository: PeopleRepository
    private val sent = CopyOnWriteArrayList<String>()

    @Before
    fun begin() {
        AppContext.set(context)
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path.orEmpty().substringBefore("?")) {
                    "/people/p1/-/information" -> ok("""{"id": "p1", "name": "Ada", "profile": ""}""")
                    "/people/p1/-/profile/set" -> {
                        sent += request.body.readUtf8()
                        ok("{}")
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
    fun end() = server.shutdown()

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
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
    fun `a profile of emoji up to the limit is sent`() {
        val model = model()
        var refused: MochiError? = null
        model.setBioDraft("😀".repeat(ProfileViewModel.PROFILE_MAXIMUM))
        model.saveBio(onError = { refused = it })
        until { sent.isNotEmpty() || refused != null }
        assertNull(refused)
        assertEquals(1, sent.size)
    }

    @Test
    fun `a profile one character past the limit is refused before it is sent`() {
        val model = model()
        var refused: MochiError? = null
        model.setBioDraft("😀".repeat(ProfileViewModel.PROFILE_MAXIMUM) + "a")
        model.saveBio(onError = { refused = it })
        assertEquals(MochiError.Local(R.string.people_profile_too_long), refused)
        assertTrue(sent.isEmpty())
    }
}
