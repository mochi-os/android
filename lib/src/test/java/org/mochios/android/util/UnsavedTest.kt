// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a read of an object shows over its answer: the edits waiting when it
 * was sent or when it answers, and nothing else.
 */
class UnsavedTest {

    private val edits = UnsavedEdits()
    private val server = mapOf<String, Any?>("title" to "Old", "status" to "open")

    @Test
    fun `an edit waiting on its save stands over the answer, the other fields follow it`() {
        edits.value("o1", "title", "New")
        val sent = edits.waiting("o1")
        assertEquals(
            mapOf("title" to "New", "status" to "done"),
            edits.values("o1", mapOf("title" to "Old", "status" to "done"), server, sent),
        )
    }

    @Test
    fun `an edit saved while the read was out stands, as the read may have been served before it`() {
        val edit = edits.value("o1", "title", "New")
        val sent = edits.waiting("o1")
        edits.saved(edit)
        assertEquals("New", edits.values("o1", server, mapOf("title" to "New"), sent)["title"])
    }

    @Test
    fun `an edit refused while the read was out shows what the refusal put back`() {
        val edit = edits.value("o1", "status", "done")
        val sent = edits.waiting("o1")
        edits.refused(edit)
        assertEquals("open", edits.values("o1", mapOf("status" to "closed"), mapOf("status" to "open"), sent)["status"])
        assertEquals(
            "a field the refusal emptied stays empty",
            null,
            edits.values("o1", mapOf("status" to "closed"), emptyMap(), sent)["status"],
        )
    }

    @Test
    fun `an edit answered before the read was sent leaves the answer alone`() {
        edits.saved(edits.value("o1", "title", "New"))
        val sent = edits.waiting("o1")
        assertEquals(server, edits.values("o1", server, mapOf("title" to "New"), sent))
    }

    @Test
    fun `a field emptied elsewhere shows emptied`() {
        assertEquals(emptyMap<String, Any?>(), edits.values("o1", emptyMap(), server, edits.waiting("o1")))
    }

    @Test
    fun `an edit made while the read is out stands too`() {
        val sent = edits.waiting("o1")
        edits.value("o1", "title", "New")
        assertEquals("New", edits.values("o1", server, server, sent)["title"])
    }

    @Test
    fun `a later edit of the field is the one that shows`() {
        edits.value("o1", "title", "First")
        val sent = edits.waiting("o1")
        edits.saved(edits.value("o1", "title", "Second"))
        assertEquals("Second", edits.values("o1", server, server, sent)["title"])
    }

    @Test
    fun `a copy opened from shows the edits still waiting, with nothing sent`() {
        edits.value("o1", "title", "New")
        assertEquals("New", edits.values("o1", server, null, emptySet())["title"])
    }

    @Test
    fun `another object's edits stay with it`() {
        edits.value("o2", "title", "New")
        edits.parent("o2", "p9")
        assertEquals(server, edits.values("o1", server, server, edits.waiting("o1")))
        assertEquals("p1", edits.parent("o1", "p1", "p1", edits.waiting("o1")))
    }

    @Test
    fun `the parent is kept as the values are`() {
        val edit = edits.parent("o1", "p2")
        val sent = edits.waiting("o1")
        assertEquals("waiting", "p2", edits.parent("o1", "p1", "p2", sent))
        edits.saved(edit)
        assertEquals("saved while the read was out", "p2", edits.parent("o1", "p1", "p2", sent))
        assertEquals("read after it answered", "p1", edits.parent("o1", "p1", "p2", edits.waiting("o1")))
        val refused = edits.parent("o1", "p3")
        val later = edits.waiting("o1")
        edits.refused(refused)
        assertEquals("refused while the read was out", "p2", edits.parent("o1", "p1", "p2", later))
    }
}
