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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * A refused notifications call throws the server's own reason, so the screen
 * shows why, not "An unexpected error occurred" for every status alike; and
 * the calls whose answer may be empty still take an empty one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationsRefusalTest {

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

    private fun refuse() {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"forbidden","message":"Nicht erlaubt"}"""),
        )
    }

    private fun answer(body: String) {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body))
    }

    /** Runs [call] against a refusal and returns what it threw. */
    private fun refused(call: suspend () -> Unit): MochiError {
        refuse()
        try {
            runBlocking { call() }
        } catch (e: MochiError) {
            return e
        }
        fail("the refusal should throw")
        throw AssertionError()
    }

    private fun assertServerReason(error: MochiError) {
        assertEquals(MochiError.ForbiddenError("Nicht erlaubt", "forbidden"), error)
    }

    @Test
    fun `a refused list carries the server's reason`() {
        assertServerReason(refused { repository.list() })
    }

    @Test
    fun `a refused count carries the server's reason`() {
        assertServerReason(refused { repository.count() })
    }

    @Test
    fun `a refused mark read carries the server's reason`() {
        assertServerReason(refused { repository.markRead("n") })
    }

    @Test
    fun `a refused mark all read carries the server's reason`() {
        assertServerReason(refused { repository.markAllRead() })
    }

    @Test
    fun `a refused categories list carries the server's reason`() {
        assertServerReason(refused { repository.categories() })
    }

    @Test
    fun `a refused topic lookup carries the server's reason`() {
        assertServerReason(refused { repository.topic(post) })
    }

    @Test
    fun `a refused category move carries the server's reason`() {
        assertServerReason(refused { repository.setCategory(post, "a") })
    }

    @Test
    fun `a refused clear carries the server's reason`() {
        assertServerReason(refused { repository.clearAll() })
    }

    @Test
    fun `a refused account removal carries the server's reason`() {
        assertServerReason(refused { repository.removeAccount("a") })
    }

    @Test
    fun `an empty answer is no notifications, no count, no categories and no topic`() = runBlocking {
        answer("""{"data":null}""")
        assertEquals(NotificationsCount(), repository.count())
        answer("""{"data":null}""")
        assertEquals(emptyList<NotificationCategory>(), repository.categories())
        answer("""{"data":null}""")
        assertNull(repository.topic(post))
    }
}
