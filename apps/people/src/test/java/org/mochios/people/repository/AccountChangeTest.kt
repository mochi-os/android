// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.repository

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
import org.mochios.android.auth.SessionManager
import org.mochios.android.files.FileStore
import org.mochios.people.api.PeopleApi
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * The address books held for every screen's drawer are one account's: the
 * next account to sign in on the phone is shown its own, not the previous
 * one's names, nor offered their ids.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountChangeTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager
    private lateinit var repository: PeopleRepository

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                // The books answered are whoever is signed in.
                val identity = runBlocking { session.getBoundIdentity() }
                return MockResponse().setBody("""{"data": {"books": [{"id": "$identity-book", "name": "$identity's book"}]}}""")
            }
        }
        server.start()
        session = SessionManager(context)
        runBlocking {
            session.clearAll()
            session.setServerUrl(server.url("/").toString().trimEnd('/'))
            session.setBoundAccount("first")
        }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/people/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PeopleApi::class.java)
        repository = PeopleRepository(api, FileStore(context), session)
    }

    @After
    fun end() = server.shutdown()

    @Test
    fun `another account signing in is shown its own address books`() {
        // Let the repository see the first account before the change.
        Thread.sleep(300)
        assertEquals(listOf("first-book"), runBlocking { repository.loadBooks() }.map { it.id })
        runBlocking { session.setBoundAccount("second") }
        val deadline = System.currentTimeMillis() + 5_000
        while (repository.books.value.isNotEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        // THE POINT: the drawer no longer lists the first account's books.
        assertEquals(emptyList<String>(), repository.books.value.map { it.id })
        // And the next read fetches the second account's, not the ones held.
        assertEquals(listOf("second-book"), runBlocking { repository.loadBooks() }.map { it.id })
    }
}
