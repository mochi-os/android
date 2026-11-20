// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.`object`

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.crm.R
import org.mochios.crm.model.CrmField
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A checklist field holds the web's `[{"id", "text", "done"}]`: the phone
 * shows the web's ticks, keeps every item's id and any other key through an
 * edit, gives a new item an id of its own, and opens whatever else is
 * stored without crashing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChecklistEditorTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val field = CrmField(id = "todo", name = "Todo", fieldtype = "checklist")

    /** The last value the editor wrote. */
    private var stored: String? = null

    private fun show(vararg values: String) {
        rule.setContent {
            Column {
                values.forEach { initial ->
                    var value by remember { mutableStateOf(initial) }
                    FieldEditor(
                        field = field,
                        value = value,
                        options = emptyList(),
                        canWrite = true,
                        people = emptyList(),
                        onValueChange = { changed ->
                            value = changed
                            stored = changed
                        },
                        onSearchUsers = { emptyList() },
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun items(): JsonArray = JsonParser.parseString(stored).asJsonArray

    private fun boxes() = rule.onAllNodes(isToggleable())

    @Test
    fun `a stored null, a null item or a null text opens without crashing`() {
        show("null", "[null]", """[{"text": null}]""", """{"id": "x"}""", """[null, {"id": "a", "text": "Milk", "done": true}]""")
        assertTrue(rule.onAllNodesWithText("Milk").fetchSemanticsNodes().isNotEmpty())
        // [{"text": null}] is one empty item; Milk is the other.
        assertEquals(2, boxes().fetchSemanticsNodes().size)
        boxes()[1].assertIsOn()
    }

    @Test
    fun `the web's ticks show, and a tick keeps every id and key`() {
        show("""[{"id": "a", "text": "Milk", "done": false, "note": "semi"}, {"id": "b", "text": "Eggs", "done": true}]""")
        boxes()[0].assertIsOff()
        boxes()[1].assertIsOn()
        boxes()[0].performClick()
        rule.waitForIdle()
        val written = items()
        assertEquals(2, written.size())
        val milk = written[0].asJsonObject
        assertEquals("a", milk.get("id").asString)
        assertEquals("Milk", milk.get("text").asString)
        assertTrue(milk.get("done").asBoolean)
        assertEquals("semi", milk.get("note").asString)
        val eggs = written[1].asJsonObject
        assertEquals("b", eggs.get("id").asString)
        assertTrue(eggs.get("done").asBoolean)
    }

    @Test
    fun `an older phone's ticks read as done, and the next edit writes the web's shape`() {
        show("""[{"text": "Milk", "checked": true}, {"text": "Eggs", "checked": false}]""")
        boxes()[0].assertIsOn()
        boxes()[1].performClick()
        rule.waitForIdle()
        val written = items()
        written.forEach { element ->
            val item = element.asJsonObject
            assertTrue(item.toString(), item.get("id").asString.isNotEmpty())
            assertTrue(item.toString(), item.get("done").asBoolean)
            assertFalse(item.toString(), item.has("checked"))
        }
        assertNotEquals(written[0].asJsonObject.get("id"), written[1].asJsonObject.get("id"))
    }

    @Test
    fun `a new item has an id of its own and is not done`() {
        show("""[{"id": "a", "text": "Milk", "done": true}]""")
        rule.onNodeWithText(context.getString(R.string.crm_property_add_item)).performClick()
        rule.waitForIdle()
        val written = items()
        assertEquals(2, written.size())
        val added = written[1].asJsonObject
        assertTrue(added.get("id").asString.isNotEmpty())
        assertNotEquals("a", added.get("id").asString)
        assertFalse(added.get("done").asBoolean)
        assertEquals("", added.get("text").asString)
    }
}
