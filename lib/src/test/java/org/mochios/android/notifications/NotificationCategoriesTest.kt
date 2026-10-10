// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.notifications

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * The notifications app's own category calls, which a notification's
 * category picker uses outside settings: the categories, the topic a
 * notification belongs to, and moving that topic to another category.
 */
class NotificationCategoriesTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: NotificationsRepository

    private val post = MochiNotification(id = "n", app = "feeds", topic = "post", `object` = "f1")

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/notifications/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NotificationsApi::class.java)
        repository = NotificationsRepository(api)
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun answer(body: String) {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body))
    }

    @Test
    fun `the categories come from the notifications app with their shown names`() = runBlocking {
        answer("""{"data":[{"id":"a","label":"Normal","display":"Wichtig","default":1,"destinations":[{"type":"device","target":"d"}]}]}""")
        val categories = repository.categories()
        assertEquals("/notifications/-/categories/list", server.takeRequest().path)
        assertEquals(listOf("Wichtig"), categories.map { it.shown })
        assertEquals(listOf(DestinationRow("device", "d")), categories.single().destinations)
    }

    @Test
    fun `a notification's topic is looked up by its app, topic and object`() = runBlocking {
        answer("""{"data":{"app":"feeds","topic":"post","object":"f1","label":"Posts","name":"","category":"a"}}""")
        val topic = repository.topic(post)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/notifications/-/topics/lookup", url.encodedPath)
        assertEquals("feeds", url.queryParameter("app"))
        assertEquals("post", url.queryParameter("topic"))
        assertEquals("f1", url.queryParameter("object"))
        assertEquals("a", topic?.category)
    }

    @Test
    fun `a notification with no topic row yet has none`() = runBlocking {
        answer("""{"data":null}""")
        assertNull(repository.topic(post))
    }

    @Test
    fun `moving a topic posts its key and the category`() = runBlocking {
        answer("""{"data":{}}""")
        repository.setCategory(post, "a")
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/notifications/-/topics/category/set", request.path)
        assertEquals("app=feeds&topic=post&object=f1&category=a", request.body.readUtf8())
    }

    @Test
    fun `moving a topic to unassigned sends an empty category`() = runBlocking {
        answer("""{"data":{}}""")
        repository.setCategory(post, null)
        assertEquals("app=feeds&topic=post&object=f1&category=", server.takeRequest().body.readUtf8())
    }
}
