// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.feed

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `post/create` frames arrive on the websocket's own thread, so the pill's
 * counter is incremented off the main thread and has to survive that.
 */
class NewPostsTest {
    private val nothingLoaded: (String) -> Boolean = { false }

    @Test
    fun `every frame counts once, and a reload clears the pill`() {
        val posts = NewPosts()
        posts.record("p1", nothingLoaded)
        posts.record("p1", nothingLoaded)
        posts.record("p2", nothingLoaded)
        posts.record(null, nothingLoaded)
        posts.record("", nothingLoaded)
        assertEquals(4, posts.count.value)
        posts.clear()
        assertEquals(0, posts.count.value)
    }

    @Test
    fun `a post already on screen raises no pill`() {
        val posts = NewPosts()
        posts.record("p1") { it == "p1" }
        assertEquals(0, posts.count.value)
    }

    @Test
    fun `concurrent frames all count`() {
        val posts = NewPosts()
        val threads = (0 until 8).map { thread ->
            Thread { repeat(500) { posts.record("$thread-$it", nothingLoaded) } }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(4000, posts.count.value)
    }
}
