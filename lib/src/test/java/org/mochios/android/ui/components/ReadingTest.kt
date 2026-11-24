// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A screen drawn from one part of a state is left alone while the rest of it
 * changes, as a conversation is while its composer's text does, and follows
 * that part when it changes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReadingTest {
    @get:Rule
    val compose = createComposeRule()

    private data class Conversation(val messages: List<String>, val draft: String)

    @Test
    fun `a part is left alone while the rest changes, and follows its own changes`() {
        val flow = MutableStateFlow(Conversation(listOf("Hello"), ""))
        var draws = 0
        var shown = emptyList<String>()
        compose.setContent {
            val messages by flow.collectPartAsState { conversation -> conversation.messages }
            draws++
            shown = messages
        }
        compose.waitForIdle()
        val drawn = draws
        compose.runOnIdle { flow.value = flow.value.copy(draft = "H") }
        compose.runOnIdle { flow.value = flow.value.copy(draft = "Hi") }
        compose.waitForIdle()
        assertEquals("THE POINT: typing draws the conversation no more", drawn, draws)
        compose.runOnIdle { flow.value = flow.value.copy(messages = listOf("Hello", "Hi"), draft = "") }
        compose.waitForIdle()
        assertEquals("a new message is drawn", listOf("Hello", "Hi"), shown)
    }
}
