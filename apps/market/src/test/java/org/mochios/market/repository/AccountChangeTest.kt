// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.repository

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
import org.mochios.market.api.MarketApi
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * The market account held for the screens opening together is one account's:
 * the next account to sign in is given its own, not the previous one's
 * address and company.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountChangeTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var session: SessionManager
    private lateinit var repository: MarketRepository
    private lateinit var base: String

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                // The account answered is whoever is signed in.
                val identity = runBlocking { session.getBoundIdentity() }
                return MockResponse().setBody("""{"data": {"id": "$identity", "company": "$identity Ltd"}}""")
            }
        }
        server.start()
        base = server.url("/").toString().trimEnd('/')
        session = SessionManager(context)
        runBlocking {
            session.clearAll()
            session.setServerUrl(base)
            session.setBoundAccount("first", base)
        }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/market/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MarketApi::class.java)
        repository = MarketRepository(api, FileStore(context), session)
    }

    @After
    fun end() = server.shutdown()

    @Test
    fun `another account signing in is given its own market account`() {
        // Let the repository see the first account before the change.
        Thread.sleep(300)
        assertEquals("first", runBlocking { repository.getAccount() }.id)
        runBlocking { session.setBoundAccount("second", base) }
        val deadline = System.currentTimeMillis() + 5_000
        var held = runBlocking { repository.getAccount() }.id
        while (held != "second" && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
            held = runBlocking { repository.getAccount() }.id
        }
        assertEquals("second", held)
    }
}
