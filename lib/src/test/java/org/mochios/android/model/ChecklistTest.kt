// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.model

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A checklist value is shared with the web, which finds items by id. */
class ChecklistTest {

    @Test
    fun `reads the web's items with their ids and done marks`() {
        val items = Checklist.parse("""[{"id":"a1","text":"Write","done":true},{"id":"b2","text":"Test","done":false}]""")
        assertEquals(listOf("a1", "b2"), items.map { it.id })
        assertEquals(listOf(true, false), items.map { it.done })
        assertEquals("Write", items[0].text)
    }

    @Test
    fun `writes every item with its id, text and done mark`() {
        val written = JsonParser.parseString(
            Checklist.write(listOf(ChecklistItem("a1", "Write", true)))
        ).asJsonArray[0].asJsonObject
        assertEquals("a1", written["id"].asString)
        assertEquals("Write", written["text"].asString)
        assertTrue(written["done"].asBoolean)
        assertFalse(written.has("checked"))
    }

    @Test
    fun `an edit keeps keys another client wrote`() {
        val items = Checklist.parse("""[{"id":"a1","text":"Write","done":false,"due":"2026-12-01"}]""")
        val written = JsonParser.parseString(Checklist.write(listOf(items[0].copy(done = true)))).asJsonArray[0].asJsonObject
        assertEquals("2026-12-01", written["due"].asString)
        assertTrue(written["done"].asBoolean)
    }

    @Test
    fun `an item an older phone wrote reads checked as done and gets a stable id`() {
        val value = """[{"text":"Old","checked":true},{"text":"Older","checked":false}]"""
        val first = Checklist.parse(value)
        assertEquals(listOf("item-0", "item-1"), first.map { it.id })
        assertEquals(listOf(true, false), first.map { it.done })
        assertEquals(first.map { it.id }, Checklist.parse(value).map { it.id })
        val rewritten = JsonParser.parseString(Checklist.write(first)).asJsonArray[0].asJsonObject
        assertFalse(rewritten.has("checked"))
        assertTrue(rewritten["done"].asBoolean)
    }

    @Test
    fun `a repeated id is made unique so one tick does not tick both`() {
        val items = Checklist.parse("""[{"id":"x","text":"One","done":false},{"id":"x","text":"Two","done":false}]""")
        assertNotEquals(items[0].id, items[1].id)
    }

    @Test
    fun `a repaired id never repeats an earlier one`() {
        val items = Checklist.parse("""[{"id":"a-2","text":"One","done":false},{"id":"a","text":"Two","done":false},{"id":"a","text":"Three","done":false}]""")
        assertEquals(listOf("a-2", "a", "a-2-2"), items.map { it.id })
    }

    @Test
    fun `null, null items, null text and non-arrays read without crashing`() {
        assertEquals(emptyList<ChecklistItem>(), Checklist.parse("null"))
        assertEquals(emptyList<ChecklistItem>(), Checklist.parse("[null]"))
        assertEquals(emptyList<ChecklistItem>(), Checklist.parse("5"))
        assertEquals(emptyList<ChecklistItem>(), Checklist.parse("{\"id\":\"a\"}"))
        assertEquals(emptyList<ChecklistItem>(), Checklist.parse("not json"))
        assertEquals("", Checklist.parse("""[{"id":"a","text":null,"done":false}]""")[0].text)
    }

    @Test
    fun `a new item has an id of its own`() {
        val one = Checklist.item("A")
        val two = Checklist.item("B")
        assertTrue(one.id.isNotEmpty())
        assertNotEquals(one.id, two.id)
        assertFalse(one.done)
    }
}
