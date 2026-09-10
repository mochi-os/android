// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A push is dropped only when it is about the entity actually on screen.
 * Getting this wrong in the permissive direction loses notifications
 * silently, so every near miss is pinned here.
 */
class VisibleEntityTest {

    private val feedScreen = Any()
    private val postScreen = Any()

    @After
    fun clear() {
        VisibleEntity.hide(feedScreen)
        VisibleEntity.hide(postScreen)
    }

    @Test
    fun `nothing on screen posts everything`() {
        assertFalse(VisibleEntity.covers("/feeds/qM8KdfhEt"))
    }

    @Test
    fun `the feed on screen is covered`() {
        VisibleEntity.show(feedScreen, "feeds", "qM8KdfhEt")
        assertTrue(VisibleEntity.covers("/feeds/qM8KdfhEt"))
    }

    @Test
    fun `the chat on screen is covered`() {
        VisibleEntity.show(feedScreen, "chat", "019ede9c575a774da4a51bea035a3600")
        assertTrue(VisibleEntity.covers("/chat/019ede9c575a774da4a51bea035a3600"))
    }

    @Test
    fun `a link deeper into the open entity is covered`() {
        VisibleEntity.show(feedScreen, "wikis", "16i2sSiYtMDvTqw7qSbZLs26ntgBY7nGrWwjtQznjfrCkKowU3")
        assertTrue(
            VisibleEntity.covers("/wikis/16i2sSiYtMDvTqw7qSbZLs26ntgBY7nGrWwjtQznjfrCkKowU3/api-world")
        )
    }

    @Test
    fun `another entity in the same app still posts`() {
        VisibleEntity.show(feedScreen, "feeds", "qM8KdfhEt")
        assertFalse(VisibleEntity.covers("/feeds/6HaN7LwYq"))
    }

    @Test
    fun `another app still posts`() {
        VisibleEntity.show(feedScreen, "feeds", "qM8KdfhEt")
        assertFalse(VisibleEntity.covers("/chat/qM8KdfhEt"))
    }

    @Test
    fun `a link naming no entity posts`() {
        VisibleEntity.show(feedScreen, "feeds", "qM8KdfhEt")
        assertFalse(VisibleEntity.covers("/feeds"))
        assertFalse(VisibleEntity.covers(""))
    }

    @Test
    fun `a game on screen is covered, though its object is empty`() {
        VisibleEntity.show(feedScreen, "go", "01a06673725375c1a70537a01ba820f9")
        assertTrue(VisibleEntity.covers("/go/01a06673725375c1a70537a01ba820f9"))
        assertFalse(VisibleEntity.covers("/go/01a06b44f95a7013b3817eec59973607"))
    }

    @Test
    fun `the screen left behind on a swap does not clear its successor`() {
        VisibleEntity.show(feedScreen, "feeds", "qM8KdfhEt")
        VisibleEntity.show(postScreen, "feeds", "6HaN7LwYq")
        VisibleEntity.hide(feedScreen)
        assertTrue(VisibleEntity.covers("/feeds/6HaN7LwYq"))
        VisibleEntity.hide(postScreen)
        assertFalse(VisibleEntity.covers("/feeds/6HaN7LwYq"))
    }

    @Test
    fun `a post of the feed keeps the cover when the feed behind it pauses`() {
        VisibleEntity.show(feedScreen, "feeds", "qM8KdfhEt")
        VisibleEntity.show(postScreen, "feeds", "qM8KdfhEt")
        VisibleEntity.hide(feedScreen)
        assertTrue(VisibleEntity.covers("/feeds/qM8KdfhEt"))
    }
}
