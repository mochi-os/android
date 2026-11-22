// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A card and a row draw a field's value as [fieldDisplay] reads it, and nothing when it reads as nothing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class FieldValueTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `values are drawn by type on a card and a row`() {
        rule.setContent {
            Column {
                FieldValue(type = "checklist", value = """[{"text":"a","done":true},{"text":"b"}]""", compact = true)
                FieldValue(type = "checkbox", value = "1", name = "Billable")
                FieldValue(type = "checkbox", value = "0", name = "Urgent", compact = true)
                FieldValue(type = "number", value = "1234.5")
                FieldValue(type = "enumerated", value = "open", choice = { FieldChoice("Open", "#22c55e") })
            }
        }
        rule.onNodeWithText("1/2").assertExists()
        rule.onNodeWithText("Billable").assertExists()
        rule.onNodeWithText("Urgent").assertDoesNotExist()
        rule.onNodeWithText("0").assertDoesNotExist()
        rule.onNodeWithText("1,234.5").assertExists()
        rule.onNodeWithText("Open").assertExists()
        rule.onNodeWithText("open").assertDoesNotExist()
    }
}
