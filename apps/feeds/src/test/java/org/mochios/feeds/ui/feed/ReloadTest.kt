// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.mochios.feeds.model.Post

/** What a reload the reader did not ask for does to the list they are part-way through. */
class ReloadTest {
    private fun list(vararg ids: String) = ids.map { id -> Post(id = id) }

    private fun ids(posts: List<Post>?) = posts?.map { post -> post.id }

    private val loaded = list("a", "b", "c", "d", "e", "f", "g", "h", "i", "j")

    @Test
    fun `the posts reached stay though the fresh list has lost them, and what is unread follows`() {
        // Unread only: a to e were read on the way to e, so the server no longer lists them.
        val merged = mergeReload(loaded, list("f", "g", "h", "i", "j"), "e")
        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"), ids(merged))
    }

    @Test
    fun `a new post in the fresh list comes next, after the post reached`() {
        val merged = mergeReload(loaded, list("new", "f", "g"), "e")
        assertEquals(listOf("a", "b", "c", "d", "e", "new", "f", "g"), ids(merged))
    }

    @Test
    fun `a post reached is kept as the reader has it, and appears once`() {
        // Every post, read or not: the fresh list holds the kept posts too.
        val fresh = list("a", "b", "c", "d", "e", "f", "g", "h", "i", "j")
        val merged = mergeReload(loaded, fresh, "e")!!
        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"), ids(merged))
        for (index in 0..4) assertSame(loaded[index], merged[index])
        assertSame(fresh[5], merged[5])
    }

    @Test
    fun `loaded posts past the one reached give way to the fresh list`() {
        val merged = mergeReload(loaded, list("f", "h"), "e")
        assertEquals(listOf("a", "b", "c", "d", "e", "f", "h"), ids(merged))
    }

    @Test
    fun `with no post reached the fresh list replaces the old`() {
        val fresh = list("x", "y")
        assertSame(fresh, mergeReload(loaded, fresh, null))
    }

    @Test
    fun `a post reached that has since left the list counts as none`() {
        val fresh = list("x", "y")
        assertSame(fresh, mergeReload(loaded, fresh, "gone"))
    }

    @Test
    fun `a fresh list that adds nothing past the post reached leaves the list to stand`() {
        // The reader is further in than a first page goes.
        assertNull(mergeReload(loaded, list("a", "b", "c"), "e"))
        assertNull(mergeReload(loaded, emptyList(), "e"))
    }
}
