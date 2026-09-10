// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mochios.android.push.VisibleEntity.Cover.NONE
import org.mochios.android.push.VisibleEntity.Cover.READ
import org.mochios.android.push.VisibleEntity.Cover.SUPPRESS

/**
 * A push is dropped only when it is about the entity actually on screen.
 * Getting this wrong in the permissive direction loses notifications
 * silently, so every near miss is pinned here.
 */
class VisibleEntityTest {

    private val feedScreen = Any()
    private val postScreen = Any()

    // The socket a registration depends on. `live` says this one is up; a
    // screen keyed to anything else is registered but not carrying events.
    private val LIVE = "live-socket"

    private fun live(key: String): Boolean = key == LIVE

    @After
    fun clear() {
        VisibleEntity.hide(feedScreen)
        VisibleEntity.hide(postScreen)
    }


    @Test
    fun `nothing on screen posts everything`() {
        assertEquals(NONE, VisibleEntity.coverFor("/feeds/qM8KdfhEt", ::live))
    }

    @Test
    fun `the feed on screen is covered`() {
        VisibleEntity.show(feedScreen, "feeds", "qM8KdfhEt", socketKey = LIVE)
        assertEquals(SUPPRESS, VisibleEntity.coverFor("/feeds/qM8KdfhEt", ::live))
    }

    @Test
    fun `the chat on screen is covered`() {
        VisibleEntity.show(feedScreen, "chat", "019ede9c575a774da4a51bea035a3600", socketKey = LIVE)
        assertEquals(SUPPRESS, VisibleEntity.coverFor("/chat/019ede9c575a774da4a51bea035a3600", ::live))
    }

    @Test
    fun `a link deeper into the open entity is covered`() {
        VisibleEntity.show(feedScreen, "wikis", "16i2sSiYtMDvTqw7qSbZLs26ntgBY7nGrWwjtQznjfrCkKowU3", socketKey = LIVE)
        assertEquals(
            SUPPRESS,
            VisibleEntity.coverFor("/wikis/16i2sSiYtMDvTqw7qSbZLs26ntgBY7nGrWwjtQznjfrCkKowU3/api-world", ::live),
        )
    }

    @Test
    fun `another entity in the same app still posts`() {
        VisibleEntity.show(feedScreen, "feeds", "qM8KdfhEt", socketKey = LIVE)
        assertEquals(NONE, VisibleEntity.coverFor("/feeds/6HaN7LwYq", ::live))
    }

    @Test
    fun `another app still posts`() {
        VisibleEntity.show(feedScreen, "feeds", "qM8KdfhEt", socketKey = LIVE)
        assertEquals(NONE, VisibleEntity.coverFor("/chat/qM8KdfhEt", ::live))
    }

    @Test
    fun `a link naming no entity posts`() {
        VisibleEntity.show(feedScreen, "feeds", "qM8KdfhEt", socketKey = LIVE)
        assertEquals(NONE, VisibleEntity.coverFor("/feeds", ::live))
        assertEquals(NONE, VisibleEntity.coverFor("", ::live))
    }

    @Test
    fun `a game on screen is covered, though its object is empty`() {
        VisibleEntity.show(feedScreen, "go", "01a06673725375c1a70537a01ba820f9", socketKey = LIVE)
        assertEquals(SUPPRESS, VisibleEntity.coverFor("/go/01a06673725375c1a70537a01ba820f9", ::live))
        assertEquals(NONE, VisibleEntity.coverFor("/go/01a06b44f95a7013b3817eec59973607", ::live))
    }

    @Test
    fun `the screen left behind on a swap does not clear its successor`() {
        VisibleEntity.show(feedScreen, "feeds", "qM8KdfhEt", socketKey = LIVE)
        VisibleEntity.show(postScreen, "feeds", "6HaN7LwYq", socketKey = LIVE)
        VisibleEntity.hide(feedScreen)
        assertEquals(SUPPRESS, VisibleEntity.coverFor("/feeds/6HaN7LwYq", ::live))
        VisibleEntity.hide(postScreen)
        assertEquals(NONE, VisibleEntity.coverFor("/feeds/6HaN7LwYq", ::live))
    }

    @Test
    fun `a post of the feed keeps the cover when the feed behind it pauses`() {
        VisibleEntity.show(feedScreen, "feeds", "qM8KdfhEt", socketKey = LIVE)
        VisibleEntity.show(postScreen, "feeds", "qM8KdfhEt", socketKey = LIVE)
        VisibleEntity.hide(feedScreen)
        assertEquals(SUPPRESS, VisibleEntity.coverFor("/feeds/qM8KdfhEt", ::live))
    }

    @Test
    fun `a one to one screen reads what arrives for it`() {
        VisibleEntity.show(feedScreen, "chat", "019ede9c575a774da4a51bea035a3600", marksRead = true, socketKey = LIVE)
        assertEquals(READ, VisibleEntity.coverFor("/chat/019ede9c575a774da4a51bea035a3600", ::live))
    }

    @Test
    fun `a container screen suppresses but does not read`() {
        VisibleEntity.show(feedScreen, "feeds", "qM8KdfhEt", socketKey = LIVE)
        assertEquals(SUPPRESS, VisibleEntity.coverFor("/feeds/qM8KdfhEt", ::live))
    }

    @Test
    fun `the one to one screen wins when both cover the entity`() {
        VisibleEntity.show(feedScreen, "go", "01a0", socketKey = LIVE)
        VisibleEntity.show(postScreen, "go", "01a0", marksRead = true, socketKey = LIVE)
        assertEquals(READ, VisibleEntity.coverFor("/go/01a0", ::live))
    }

    @Test
    fun `a market thread is named by its whole path`() {
        VisibleEntity.show(feedScreen, "market", "messages/L1/T1", marksRead = true, socketKey = LIVE)
        assertEquals(READ, VisibleEntity.coverFor("/market/messages/L1/T1", ::live))
    }

    @Test
    fun `another thread under the same category still posts`() {
        VisibleEntity.show(feedScreen, "market", "messages/L1/T1", marksRead = true, socketKey = LIVE)
        assertEquals(NONE, VisibleEntity.coverFor("/market/messages/L1/T2", ::live))
        assertEquals(NONE, VisibleEntity.coverFor("/market/messages/L2/T1", ::live))
    }

    @Test
    fun `a link shorter than the path on screen posts`() {
        VisibleEntity.show(feedScreen, "market", "messages/L1/T1", marksRead = true, socketKey = LIVE)
        assertEquals(NONE, VisibleEntity.coverFor("/market/messages", ::live))
    }

    @Test
    fun `a screen whose socket is down covers nothing`() {
        VisibleEntity.show(feedScreen, "feeds", "qM8KdfhEt", socketKey = "some-other-key")
        assertEquals(NONE, VisibleEntity.coverFor("/feeds/qM8KdfhEt", ::live))
    }

    @Test
    fun `a live screen still covers when a dead one is registered too`() {
        VisibleEntity.show(feedScreen, "feeds", "qM8KdfhEt", socketKey = "some-other-key")
        VisibleEntity.show(postScreen, "feeds", "qM8KdfhEt", socketKey = LIVE)
        assertEquals(SUPPRESS, VisibleEntity.coverFor("/feeds/qM8KdfhEt", ::live))
    }
}
