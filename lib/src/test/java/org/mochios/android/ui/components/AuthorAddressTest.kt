// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** One avatar address per author, as the web's addressByAuthor builds it. */
class AuthorAddressTest {
    private data class Row(
        val id: String,
        val author: String,
        val approved: Boolean = true,
        val children: List<Row> = emptyList(),
    )

    private fun addresses(rows: List<Row>, found: MutableMap<String, String> = mutableMapOf()) =
        addressByAuthor(
            rows,
            { it.author },
            { if (it.approved) "/rows/${it.id}/asset/avatar" else null },
            { it.children },
            found,
        )

    @Test
    fun `every author gets the address of their first row`() {
        val rows = listOf(Row("a1", "ann"), Row("b1", "bob"), Row("a2", "ann"), Row("b2", "bob"))
        assertEquals(
            mapOf("ann" to "/rows/a1/asset/avatar", "bob" to "/rows/b1/asset/avatar"),
            addresses(rows),
        )
    }

    @Test
    fun `replies come before the next row, in reading order`() {
        val rows = listOf(Row("a1", "ann", children = listOf(Row("c1", "cat"))), Row("c2", "cat"))
        assertEquals("/rows/c1/asset/avatar", addresses(rows)["cat"])
    }

    @Test
    fun `a row that may not stand in is passed over`() {
        val rows = listOf(Row("a1", "ann", approved = false), Row("a2", "ann"), Row("b1", "bob", approved = false))
        val found = addresses(rows)
        assertEquals("/rows/a2/asset/avatar", found["ann"])
        assertEquals(null, found["bob"])
    }

    @Test
    fun `a seeded row stays ahead of the list, and a blank author is left out`() {
        val found = addresses(
            listOf(Row("a1", "ann"), Row("x1", "")),
            mutableMapOf("ann" to "/posts/p1/asset/avatar"),
        )
        assertEquals(mapOf("ann" to "/posts/p1/asset/avatar"), found)
    }
}
