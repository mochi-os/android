// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.data

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.auth.SessionManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins and marks of unread are kept per account on the device: another
 * account signed in sees none of the first one's, which come back with it,
 * and a mark kept before they were per account goes to the first account to
 * read it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatStoresTest {

    private val context = RuntimeEnvironment.getApplication()
    private lateinit var session: SessionManager

    @Before
    fun begin() {
        session = SessionManager(context)
    }

    private fun until(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done()) {
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    @Test
    fun `pins are the signed-in account's own`() {
        runBlocking { session.setBoundAccount("alice") }
        val pins = PinnedChatsStore(context, session)
        pins.toggle("c1")
        assertEquals(setOf("c1"), pins.pinned.value)
        runBlocking { session.setBoundAccount("bob") }
        Thread.sleep(500)
        assertEquals("another account sees none of the first one's", emptySet<String>(), pins.pinned.value)
        pins.toggle("c2")
        runBlocking { session.setBoundAccount("alice") }
        until { pins.pinned.value == setOf("c1") }
        assertEquals("kept for the next start", setOf("c1"), PinnedChatsStore(context, session).pinned.value)
    }

    @Test
    fun `marks of unread are the signed-in account's own`() {
        runBlocking { session.setBoundAccount("alice") }
        val marks = UnreadChatsStore(context, session)
        marks.mark("c1")
        runBlocking { session.setBoundAccount("bob") }
        until { marks.marked.value.isEmpty() }
        runBlocking { session.setBoundAccount("alice") }
        until { marks.marked.value == setOf("c1") }
    }

    @Test
    fun `pins kept before they were per account go to the first account to read them`() {
        context.getSharedPreferences("mochi_chat_pins", Context.MODE_PRIVATE)
            .edit().putStringSet("pinned_chat_ids", setOf("old")).commit()
        runBlocking { session.setBoundAccount("alice") }
        assertEquals(setOf("old"), PinnedChatsStore(context, session).pinned.value)
        assertFalse(
            "taken over, not left for the next account",
            context.getSharedPreferences("mochi_chat_pins", Context.MODE_PRIVATE).contains("pinned_chat_ids"),
        )
    }

    @Test
    fun `pins and marks of chats the list no longer has are dropped`() {
        runBlocking { session.setBoundAccount("alice") }
        val pins = PinnedChatsStore(context, session)
        val marks = UnreadChatsStore(context, session)
        pins.toggle("c1")
        pins.toggle("gone")
        marks.mark("c1")
        marks.mark("gone")
        pins.prune(setOf("c1", "c2"))
        marks.prune(setOf("c1", "c2"))
        assertEquals(setOf("c1"), pins.pinned.value)
        assertEquals(setOf("c1"), marks.marked.value)
        assertEquals("kept so", setOf("c1"), PinnedChatsStore(context, session).pinned.value)
    }
}
