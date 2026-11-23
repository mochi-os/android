// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.chat.ui.chat.EditComposerPreview
import org.mochios.chat.ui.chat.SelectionBar
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.mochios.android.R as MochiR

/** The chat screen's icon-only buttons have names a screen reader can say. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatBarLabelsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun text(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    /** The button that leaves the selection was announced as "button", with no name. */
    @Test
    fun `the selection's close button is named`() {
        var closed = 0
        rule.setContent { SelectionBar(count = 2, onClose = { closed++ }, onCopy = {}, onForward = {}, onDelete = {}) }
        rule.onNodeWithContentDescription(text(MochiR.string.common_close)).performClick()
        rule.waitForIdle()
        assertEquals(1, closed)
    }

    @Test
    fun `the edit's cancel button is named`() {
        var cancelled = 0
        rule.setContent { EditComposerPreview(onCancel = { cancelled++ }) }
        rule.onNodeWithContentDescription(text(MochiR.string.common_cancel)).performClick()
        rule.waitForIdle()
        assertEquals(1, cancelled)
    }
}
