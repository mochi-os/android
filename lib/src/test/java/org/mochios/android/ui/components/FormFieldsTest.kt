// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shared form pieces: their pickers are dropdown fields, not read-only
 * text editors; what is typed into them survives a rotation (or a theme or
 * font change) and a reload of what they edit; and a view's field that no
 * longer exists never shows its id.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class FormFieldsTest {

    @get:Rule
    val rule = createComposeRule()

    /** Nodes that edit text: a picker built on a read-only text field is one. */
    private val editors = SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)

    private val viewLabels = ViewListLabels(
        addAction = "Add",
        empty = "None",
        emptySubtitle = "",
        deleteTitle = "Delete",
        deleteMessage = { name -> name },
        byField = { name -> name },
        typeBoard = "Board",
        typeList = "List",
        dragRow = "Drag",
        moveUp = "Up",
        moveDown = "Down",
        nameLabel = "Name",
        typeLabel = "Type",
        columnsField = "Columns",
        rowsField = "Rows",
        borderField = "Border",
        filter = "Filter",
        sortBy = "Sort by",
        direction = "Direction",
        directionAsc = "Ascending",
        directionDesc = "Descending",
        filterClasses = "Classes",
        none = "Nothing",
    )

    private val saved = ViewListItem(
        id = "v1",
        name = "Board",
        viewtype = "board",
        filter = "",
        columns = "status",
        rows = "gone",
        fields = "",
        sort = "",
        direction = "asc",
        classes = listOf("task"),
        rank = 1,
        border = "",
    )

    private val fields = mapOf(
        "task" to listOf(ViewFieldOption(id = "status", name = "Status", fieldtype = "enumerated", isSortable = true)),
    )

    @Test
    fun `a field form's type picker is a dropdown, not a text field`() {
        rule.setContent {
            FieldForm(
                state = rememberFieldFormState(),
                labels = FieldFormLabels("Name", "Type", "Required", "Read-only", "Sortable", "Filterable"),
            )
        }
        rule.onAllNodes(editors).assertCountEquals(1)
        rule.onNodeWithText("Text").performClick()
        rule.onNodeWithText("Number").performClick()
        rule.onNodeWithText("Number").assertExists()
    }

    @Test
    fun `a view form's field pickers are dropdowns, and a field that is gone reads as none`() {
        rule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ViewForm(
                    state = rememberViewFormState(saved),
                    classes = emptyList(),
                    fields = fields,
                    labels = viewLabels,
                    sortOptions = emptyList(),
                )
            }
        }
        // The name and the filter; the four field pickers are not editors.
        rule.onAllNodes(editors).assertCountEquals(2)
        rule.onNodeWithText("Status").assertExists()
        rule.onAllNodes(hasText("gone")).assertCountEquals(0)
        rule.onAllNodes(hasText("Nothing")).assertCountEquals(3)
    }

    @Test
    fun `an AI choice and the prompt choice are dropdowns`() {
        var mode by mutableStateOf("a")
        rule.setContent {
            Column {
                ChoiceRow(
                    label = "Mode",
                    options = listOf("a" to "Automatic", "m" to "Manual"),
                    selected = mode,
                    fallbackLabel = "Default",
                    onSelect = { value -> mode = value },
                )
                AiPromptField(
                    label = "Prompt",
                    stored = "",
                    template = "Write {{posts}}",
                    defaultLabel = "Server default",
                    customLabel = "Own prompt",
                    variablesLabel = { names -> names },
                    onSaveCustom = {},
                    onResetDefault = {},
                )
            }
        }
        rule.onAllNodes(editors).assertCountEquals(0)
        rule.onNodeWithText("Automatic").performClick()
        rule.onNodeWithText("Manual").performClick()
        rule.waitForIdle()
        assertEquals("m", mode)
    }

    @Test
    fun `a view form keeps what was typed across a rotation`() {
        val tester = StateRestorationTester(rule)
        var form: ViewFormState? = null
        tester.setContent {
            val state = rememberViewFormState(saved)
            form = state
            ViewForm(state = state, classes = emptyList(), fields = fields, labels = viewLabels, sortOptions = emptyList())
        }
        rule.waitForIdle()
        form!!.name = "Roadmap"
        form!!.columns = ""
        rule.waitForIdle()
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        assertEquals("Roadmap", form!!.name)
        assertEquals("", form!!.columns)
        assertEquals(setOf("task"), form!!.classes)
        assertEquals("v1", form!!.toPreview().id)
    }

    @Test
    fun `a view form is filled once, so a reload of the view keeps what was typed`() {
        var view by mutableStateOf<ViewListItem?>(null)
        var form: ViewFormState? = null
        rule.setContent { form = rememberViewFormState(view) }
        view = saved
        rule.waitForIdle()
        assertEquals("THE POINT: a view that loads after the form fills it", "Board", form!!.name)
        form!!.name = "Roadmap"
        view = saved.copy(rank = 2, name = "Board renamed elsewhere")
        rule.waitForIdle()
        assertEquals("Roadmap", form!!.name)
        assertEquals(2, form!!.toPreview().rank)
    }

    @Test
    fun `a custom prompt being typed survives a rotation`() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            AiPromptField(
                label = "Prompt",
                stored = "",
                template = "Write {{posts}}",
                defaultLabel = "Server default",
                customLabel = "Own prompt",
                variablesLabel = { names -> names },
                onSaveCustom = {},
                onResetDefault = {},
            )
        }
        rule.onNodeWithText("Server default").performClick()
        rule.onNodeWithText("Own prompt").performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("Summarise {{posts}} briefly")
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.onNodeWithText("Summarise {{posts}} briefly").assertExists()
    }

    @Test
    fun `an inline edit under way survives a rotation`() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            InlineTextEditor(value = "Old name", onSave = {}, editLabel = "Rename")
        }
        rule.onNodeWithContentDescription("Rename").performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("New name")
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).assertExists()
        rule.onNodeWithText("New name").assertExists()
    }
}
