// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** What a reload the reader did not ask for does to the list they are part-way through. */
class ReloadTest {
    private data class Row(val id: String)

    private fun list(vararg ids: String) = ids.map { id -> Row(id) }

    private fun ids(rows: List<Row>?) = rows?.map { row -> row.id }

    private fun merge(current: List<Row>, fresh: List<Row>, reached: String?) =
        mergeReload(current, fresh, reached) { row -> row.id }

    private val loaded = list("a", "b", "c", "d", "e", "f", "g", "h", "i", "j")

    @Test
    fun `the rows reached stay though the fresh list has lost them, and what is unread follows`() {
        // Unread only: a to e were read on the way to e, so the server no longer lists them.
        val merged = merge(loaded, list("f", "g", "h", "i", "j"), "e")
        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"), ids(merged))
    }

    @Test
    fun `a new row in the fresh list comes next, after the row reached`() {
        val merged = merge(loaded, list("new", "f", "g"), "e")
        assertEquals(listOf("a", "b", "c", "d", "e", "new", "f", "g"), ids(merged))
    }

    @Test
    fun `a row reached is kept as the reader has it, and appears once`() {
        // Every row, read or not: the fresh list holds the kept rows too.
        val fresh = list("a", "b", "c", "d", "e", "f", "g", "h", "i", "j")
        val merged = merge(loaded, fresh, "e")!!
        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"), ids(merged))
        for (index in 0..4) assertSame(loaded[index], merged[index])
        assertSame(fresh[5], merged[5])
    }

    @Test
    fun `loaded rows past the one reached give way to the fresh list`() {
        val merged = merge(loaded, list("f", "h"), "e")
        assertEquals(listOf("a", "b", "c", "d", "e", "f", "h"), ids(merged))
    }

    @Test
    fun `with no row reached the fresh list replaces the old`() {
        val fresh = list("x", "y")
        assertSame(fresh, merge(loaded, fresh, null))
    }

    @Test
    fun `a row reached that has since left the list counts as none`() {
        val fresh = list("x", "y")
        assertSame(fresh, merge(loaded, fresh, "gone"))
    }

    @Test
    fun `a fresh list that adds nothing past the row reached leaves the list to stand`() {
        // The reader is further in than a first page goes.
        assertNull(merge(loaded, list("a", "b", "c"), "e"))
        assertNull(merge(loaded, emptyList(), "e"))
    }
}
