// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import org.mochios.android.files.FileStore
import org.mochios.market.api.MarketApi
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * The reads several market screens make as they open together: the user's own
 * account, the categories and a listing just opened are each asked for once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MarketReadsTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var server: MockWebServer
    private lateinit var repository: MarketRepository

    /** How many times each path has been asked for. */
    private val asked = ConcurrentHashMap<String, AtomicInteger>()

    @Before
    fun begin() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringAfter("/market/").substringBefore("?")
                asked.getOrPut(path) { AtomicInteger() }.incrementAndGet()
                Thread.sleep(100)
                return when (path) {
                    "-/accounts/get" -> ok("""{"id": "a1", "seller": 1}""")
                    "-/accounts/update" -> ok(
                        """{"id": "a1", "seller": 1, "location": "Hanoi"}""",
                    )
                    "-/categories/list" -> ok("""[{"id": "c1", "name": "Pots"}]""")
                    "-/listings/viewer/get" -> ok(
                        """{"listing": {"id": "l1", "title": "Teapot"}}""",
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/market/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MarketApi::class.java)
        repository = MarketRepository(api, FileStore(context))
    }

    @After
    fun end() {
        server.shutdown()
    }

    private fun ok(data: String) = MockResponse().setBody("""{"data": $data}""")

    private fun count(path: String): Int = asked[path]?.get() ?: 0

    @Test
    fun `screens opening together read the account once`() = runBlocking {
        val accounts = (1..3).map { async { repository.getAccount() } }.awaitAll()
        repository.getAccount()
        assertEquals(1, count("-/accounts/get"))
        assertEquals(listOf("a1", "a1", "a1"), accounts.map { account -> account.id })
    }

    @Test
    fun `a check the user asked for reads the account again`() = runBlocking {
        repository.getAccount()
        repository.getAccount(fresh = true)
        assertEquals(2, count("-/accounts/get"))
    }

    @Test
    fun `a saved account is what the next screen reads`() = runBlocking {
        repository.getAccount()
        repository.updateAccount(mapOf("location" to "Hanoi"))
        assertEquals("Hanoi", repository.getAccount().location)
        assertEquals(1, count("-/accounts/get"))
    }

    @Test
    fun `the categories are read once for every screen`() = runBlocking {
        (1..2).map { async { repository.listCategories() } }.awaitAll()
        repository.listCategories()
        assertEquals(1, count("-/categories/list"))
    }

    @Test
    fun `a listing just opened is not read again for the recently viewed strip`() = runBlocking {
        repository.getListing("l1")
        val recent = repository.getListingsByIds(listOf("l1"))
        assertEquals(listOf("l1"), recent.map { listing -> listing.id })
        assertEquals(1, count("-/listings/viewer/get"))
    }

    @Test
    fun `the strip waits on a listing page's read under way`() = runBlocking {
        val page = async { repository.getListing("l1") }
        val recent = async { repository.getListingsByIds(listOf("l1")) }
        page.await()
        assertEquals(listOf("l1"), recent.await().map { listing -> listing.id })
        assertEquals(1, count("-/listings/viewer/get"))
    }
}
