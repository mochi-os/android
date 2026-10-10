// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.home

import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.AppContext
import org.mochios.android.notifications.MochiNotification
import org.mochios.android.notifications.NotificationCategory
import org.mochios.android.notifications.NotificationTopic
import org.mochios.home.repository.MenuSource
import org.mochios.home.repository.Person
import org.mochios.home.ui.HomeMenuViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The user menu's view model over a source that answers as a test tells it to. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeMenuViewModelTest {

    private val chat = MochiNotification(id = "1", app = "chat", topic = "message", `object` = "c", title = "Hello")
    private val feeds = MochiNotification(id = "2", app = "feeds", topic = "post", `object` = "f", title = "Post")

    private class Source : MenuSource {
        override val count: StateFlow<Int> = MutableStateFlow(0)
        var followed = false
        var recounts = 0
        var unread = listOf<MochiNotification>()
        val read = mutableListOf<String>()
        var readAll = 0
        val moved = mutableListOf<Pair<String, String?>>()
        var fail = false
        var categories: CompletableDeferred<List<NotificationCategory>>? = null
        var topics = mapOf<String, NotificationTopic?>()

        override fun follow() {
            followed = true
        }
        override suspend fun recount() {
            recounts++
        }
        override suspend fun person() = Person("p1", "Ada")
        override suspend fun unread(): List<MochiNotification> {
            if (fail) throw RuntimeException("HTTP 500")
            return unread
        }
        override suspend fun read(id: String) {
            if (fail) throw RuntimeException("HTTP 500")
            read += id
        }
        override suspend fun readAll() {
            if (fail) throw RuntimeException("HTTP 500")
            readAll++
        }
        override suspend fun categories(): List<NotificationCategory> =
            categories?.await() ?: listOf(NotificationCategory(id = "a", label = "Normal"))
        override suspend fun topic(notification: MochiNotification) = topics[notification.id]
        override suspend fun categorise(notification: MochiNotification, category: String?) {
            moved += notification.id to category
        }
    }

    private fun model(source: Source): HomeMenuViewModel {
        // A failure's message is read through the app's context.
        AppContext.set(RuntimeEnvironment.getApplication())
        return HomeMenuViewModel(source).also { settle() }
    }

    private fun settle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `it follows the count and knows who is signed in`() {
        val source = Source()
        val model = model(source)
        assertTrue(source.followed)
        assertEquals("Ada", model.state.value.name)
        assertEquals("p1", model.state.value.identity)
    }

    @Test
    fun `it lists the unread notifications`() {
        val source = Source().apply { unread = listOf(chat, feeds) }
        val model = model(source)
        model.refresh()
        settle()
        assertEquals(listOf(chat, feeds), model.state.value.unread)
        assertEquals(false, model.state.value.loading)
    }

    @Test
    fun `a failed fetch is reported`() {
        val source = Source().apply { fail = true }
        val model = model(source)
        model.refresh()
        settle()
        assertNotNull(model.state.value.error)
    }

    @Test
    fun `a notification read leaves the list and the count is fetched again`() {
        val source = Source().apply { unread = listOf(chat, feeds) }
        val model = model(source)
        model.refresh()
        settle()
        model.read(chat)
        settle()
        assertEquals(listOf(feeds), model.state.value.unread)
        assertEquals(listOf("1"), source.read)
        assertEquals(1, source.recounts)
    }

    @Test
    fun `a notification already read is not read again`() {
        val source = Source()
        val model = model(source)
        model.read(chat.copy(read = 5))
        settle()
        assertEquals(emptyList<String>(), source.read)
    }

    @Test
    fun `reading them all empties the list, and a failure puts them back`() {
        val source = Source().apply { unread = listOf(chat, feeds) }
        val model = model(source)
        model.refresh()
        settle()
        model.readAll()
        settle()
        assertEquals(emptyList<MochiNotification>(), model.state.value.unread)
        assertEquals(1, source.readAll)

        model.refresh()
        settle()
        source.fail = true
        model.readAll()
        settle()
        assertEquals(listOf(chat, feeds), model.state.value.unread)
        assertNotNull(model.state.value.error)
    }

    @Test
    fun `a picker loads the categories and the notification's topic`() {
        val source = Source().apply { topics = mapOf("1" to NotificationTopic(app = "chat", category = "a")) }
        val model = model(source)
        model.pick(chat)
        settle()
        val picker = model.state.value.picker!!
        assertEquals("1", picker.notification)
        assertEquals(listOf("a"), picker.categories?.map { it.id })
        assertEquals("a", picker.topic?.category)
    }

    @Test
    fun `a picker's late answer is dropped once another row's picker has opened`() {
        val slow = CompletableDeferred<List<NotificationCategory>>()
        val source = Source().apply { categories = slow }
        val model = model(source)
        model.pick(chat)
        settle()
        source.categories = null
        model.pick(feeds)
        settle()
        slow.complete(listOf(NotificationCategory(id = "late")))
        settle()
        val picker = model.state.value.picker!!
        assertEquals("2", picker.notification)
        assertEquals(listOf("a"), picker.categories?.map { it.id })
    }

    @Test
    fun `choosing a category moves the topic and closes the picker`() {
        val source = Source()
        val model = model(source)
        model.pick(chat)
        settle()
        model.categorise(chat, "a")
        model.categorise(feeds, null)
        settle()
        assertNull(model.state.value.picker)
        assertEquals(listOf("1" to "a", "2" to null), source.moved)
    }
}
