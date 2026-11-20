// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** An option's colour can be taken away again, as on the web. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OptionFormTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a chosen colour can be cleared`() {
        val state = OptionFormState()
        state.seed(name = "Done", colour = "#22c55e", icon = "")
        rule.setContent {
            OptionForm(
                state = state,
                labels = OptionFormLabels(name = "Name", colour = "Colour", icon = "Icon", iconPlaceholder = "Icon name"),
            )
        }
        rule.waitForIdle()
        rule.onNodeWithText("Clear").performClick()
        rule.waitForIdle()
        assertEquals("", state.colour)
    }
}
