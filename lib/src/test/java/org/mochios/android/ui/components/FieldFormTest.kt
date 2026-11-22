// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The field types both apps offer are one list with one set of names, the
 * checkbox among them, and a new field has no multiple-values switch, which
 * no client reads.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class FieldFormTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `every field type is offered with its name`() {
        var options = emptyList<FieldTypeOption>()
        var unknown = ""
        rule.setContent {
            options = fieldTypeOptions()
            unknown = fieldTypeLabel("object")
        }
        rule.waitForIdle()
        assertEquals(
            listOf(
                "text" to "Text",
                "number" to "Number",
                "enumerated" to "Enumerated",
                "user" to "User",
                "date" to "Date",
                "checkbox" to "Checkbox",
                "checklist" to "Checklist",
            ),
            options.map { option -> option.key to option.label },
        )
        assertEquals("object", unknown)
    }

    @Test
    fun `an enumerated field has only the four flags`() {
        val state = FieldFormState().apply { fieldtype = "enumerated" }
        rule.setContent {
            FieldForm(
                state = state,
                labels = FieldFormLabels(
                    name = "Name",
                    type = "Type",
                    required = "Required",
                    readonly = "Read-only",
                    sortable = "Sortable",
                    filterable = "Filterable",
                ),
            )
        }
        rule.onAllNodes(isToggleable()).assertCountEquals(4)
    }
}
