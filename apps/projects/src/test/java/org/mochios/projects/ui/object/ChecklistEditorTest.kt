// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.`object`

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.projects.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A checklist field's editor reads and writes the web's items, `{"id",
 * "text", "done"}`: a phone edit keeps every item's id and the keys another
 * client wrote, and a value that is not a list of items opens as no items.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChecklistEditorTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    /** Each value the editor wrote. */
    private val written = mutableListOf<String>()

    private fun show(value: String) {
        rule.setContent {
            ChecklistEditor(fieldName = null, value = value, isReadonly = false, onValueChange = { written += it })
        }
        rule.waitForIdle()
    }

    private fun ticks(): List<ToggleableState> =
        rule.onAllNodes(isToggleable()).fetchSemanticsNodes().map { node ->
            node.config[SemanticsProperties.ToggleableState]
        }

    private fun last(): JsonArray = JsonParser.parseString(written.last()).asJsonArray

    private val shared = """[{"id": "a", "text": "One", "done": true, "note": "kept"}, {"id": "b", "text": "Two", "done": false}]"""

    @Test
    fun `a web user's ticks show`() {
        show(shared)
        assertEquals(listOf(ToggleableState.On, ToggleableState.Off), ticks())
    }

    @Test
    fun `a tick lands on the item's done and keeps every id`() {
        show(shared)
        rule.onAllNodes(isToggleable())[1].performClick()
        val items = last()
        assertEquals(listOf("a", "b"), items.map { item -> item.asJsonObject["id"].asString })
        assertEquals("THE POINT: the tick lands on the item's own done", listOf(true, true), items.map { item -> item.asJsonObject["done"].asBoolean })
    }

    @Test
    fun `a tick keeps the keys another client wrote`() {
        show(shared)
        rule.onAllNodes(isToggleable())[1].performClick()
        assertEquals("kept", last()[0].asJsonObject["note"]?.asString)
    }

    @Test
    fun `a new item gets an id of its own`() {
        show("""[{"id": "a", "text": "One", "done": false}]""")
        rule.onNodeWithText(context.getString(R.string.projects_property_add_item)).performClick()
        val added = last()[1].asJsonObject
        assertTrue(added["id"].asString.isNotBlank())
        assertNotEquals("a", added["id"].asString)
        assertEquals(false, added["done"].asBoolean)
    }

    @Test
    fun `null opens as no items`() {
        show("null")
        assertEquals(emptyList<ToggleableState>(), ticks())
    }

    @Test
    fun `a list holding null opens as no items`() {
        show("[null]")
        assertEquals(emptyList<ToggleableState>(), ticks())
    }

    @Test
    fun `an item whose text is null opens with no text`() {
        show("""[{"id": "a", "text": null, "done": true}]""")
        assertEquals(listOf(ToggleableState.On), ticks())
        assertTrue(rule.onAllNodesWithText("null").fetchSemanticsNodes().isEmpty())
    }
}
