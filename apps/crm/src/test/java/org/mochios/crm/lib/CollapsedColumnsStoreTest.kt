// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.lib

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CollapsedColumnsStoreTest {

    private fun store() = CollapsedColumnsStore(RuntimeEnvironment.getApplication())

    @Test
    fun `toggling collapses a column, and toggling again expands it`() {
        val store = store()
        assertEquals(setOf("done"), store.toggle("c1", "status", "done"))
        assertEquals(setOf("done", "review"), store.toggle("c1", "status", "review"))
        assertEquals(setOf("review"), store.toggle("c1", "status", "done"))
        assertEquals(emptySet<String>(), store.toggle("c1", "status", "review"))
    }

    @Test
    fun `collapsed columns outlive the store, per CRM and column field`() {
        store().toggle("c1", "status", "done")
        store().toggle("c1", "stage", "won")
        store().toggle("c2", "status", "todo")
        assertEquals(mapOf("status" to setOf("done"), "stage" to setOf("won")), store().get("c1"))
        assertEquals(mapOf("status" to setOf("todo")), store().get("c2"))
    }

    @Test
    fun `a field with nothing collapsed is not kept`() {
        val store = store()
        store.toggle("c1", "status", "done")
        store.toggle("c1", "status", "done")
        assertEquals(emptyMap<String, Set<String>>(), store.get("c1"))
    }
}
