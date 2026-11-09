// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LabeledSelectFieldTest {

    @get:Rule
    val rule = createComposeRule()

    private fun field(selected: String) {
        rule.setContent {
            LabeledSelectField(
                label = "Decision",
                options = listOf("approve" to "Approve", "reject" to "Reject"),
                selected = selected,
                onSelect = {},
            )
        }
    }

    @Test
    fun `an unset select is named by its caption, though it shows nothing`() {
        // THE POINT: with no placeholder, the button's own text is empty.
        field("")
        rule.onNode(hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Decision")))
            .assertIsDisplayed()
    }

    @Test
    fun `a set select says its choice as its state`() {
        field("reject")
        rule.onNodeWithContentDescription("Decision")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Reject"))
    }
}
