// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.i18n.AppContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The @mention suggestions: read from the text the field holds, whoever set
 * it, and kept on screen while the next search is out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MentionTextFieldTest {

    @get:Rule
    val rule = createComposeRule()

    private val alice = MentionSuggestion("1", "Alice")
    private val albert = MentionSuggestion("2", "Albert")

    @Before
    fun context() {
        AppContext.set(RuntimeEnvironment.getApplication())
    }

    /** Past the search's debounce, and whatever it set going. */
    private fun settle() {
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
    }

    private fun type(text: String) {
        rule.onNode(hasSetTextAction()).performTextInput(text)
        rule.waitForIdle()
    }

    @Test
    fun `the mention at the cursor is read from the text`() {
        assertEquals(Mention(6, "al"), mentionAt(TextFieldValue("hello @al", TextRange(9))))
        assertEquals(Mention(0, ""), mentionAt(TextFieldValue("@", TextRange(1))))
        assertEquals(Mention(3, "b"), mentionAt(TextFieldValue("hi\n@b", TextRange(5))))
        // Up to the cursor, not the end of the word.
        assertEquals(Mention(0, "a"), mentionAt(TextFieldValue("@al", TextRange(2))))
        // An @ inside a word is an address, not a mention.
        assertNull(mentionAt(TextFieldValue("mail@al", TextRange(7))))
        assertNull(mentionAt(TextFieldValue("@al bo", TextRange(6))))
        assertNull(mentionAt(TextFieldValue("hello @al", TextRange(0))))
        assertNull(mentionAt(TextFieldValue("")))
    }

    @Test
    fun `a pick replaces the mention it was read from`() {
        val value = TextFieldValue("hello @al and more", TextRange(9))
        val picked = mentioned(value, mentionAt(value)!!, "Alice")
        assertEquals("hello @[Alice]  and more", picked.text)
        assertEquals(TextRange(15), picked.selection)
    }

    @Test
    fun `a draft cleared from outside takes its suggestions with it`() {
        var text by mutableStateOf("")
        rule.setContent {
            MentionTextField(value = text, onValueChange = { text = it }, onSearch = { listOf(alice) })
        }
        type("hello @al")
        settle()
        rule.onNodeWithText("Alice").assertExists()
        // ComposeBar, after Send.
        text = ""
        rule.waitForIdle()
        rule.onNodeWithText("Alice").assertDoesNotExist()
    }

    @Test
    fun `text replaced from outside takes its suggestions with it`() {
        var value by mutableStateOf(TextFieldValue(""))
        rule.setContent {
            MentionTextField(value = value, onValueChange = { value = it }, onSearch = { listOf(alice) })
        }
        type("hello @al")
        settle()
        rule.onNodeWithText("Alice").assertExists()
        value = TextFieldValue("hi", TextRange(2))
        rule.waitForIdle()
        rule.onNodeWithText("Alice").assertDoesNotExist()
    }

    @Test
    fun `a pick after the text moved indexes the text the field holds`() {
        var value by mutableStateOf(TextFieldValue(""))
        rule.setContent {
            MentionTextField(value = value, onValueChange = { value = it }, onSearch = { listOf(alice) })
        }
        type("hello @al")
        settle()
        // A quote marker put in front from outside moves the mention along:
        // what was found for it where it was is not shown for it here.
        value = TextFieldValue("> hello @al", TextRange(11))
        rule.waitForIdle()
        rule.onNodeWithText("Alice").assertDoesNotExist()
        settle()
        rule.onNodeWithText("Alice").performClick()
        rule.waitForIdle()
        assertEquals("> hello @[Alice] ", value.text)
    }

    @Test
    fun `suggestions stay while the next search is out`() {
        val pending = CompletableDeferred<List<MentionSuggestion>>()
        var text by mutableStateOf("")
        rule.setContent {
            MentionTextField(
                value = text,
                onValueChange = { text = it },
                onSearch = { query -> if (query == "a") listOf(alice) else pending.await() },
            )
        }
        type("@a")
        settle()
        rule.onNodeWithText("Alice").assertExists()
        type("l")
        settle()
        // The search for "al" is out; the next keystroke cancels it.
        type("b")
        rule.onNodeWithText("Alice").assertExists()
    }

    @Test
    fun `a search that swallows its own cancellation does not blank the suggestions`() {
        val pending = CompletableDeferred<List<MentionSuggestion>>()
        var text by mutableStateOf("")
        rule.setContent {
            MentionTextField(
                value = text,
                onValueChange = { text = it },
                onSearch = { query ->
                    if (query == "a") {
                        listOf(alice)
                    } else {
                        // As the apps' view models search: every failure, the
                        // cancellation among them, becomes "nobody".
                        try {
                            pending.await()
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                },
            )
        }
        type("@a")
        settle()
        type("l")
        settle()
        type("b")
        settle()
        rule.onNodeWithText("Alice").assertExists()
    }

    @Test
    fun `new results replace the ones shown`() {
        var text by mutableStateOf("")
        rule.setContent {
            MentionTextField(
                value = text,
                onValueChange = { text = it },
                onSearch = { query -> if (query == "a") listOf(alice) else listOf(albert) },
            )
        }
        type("@a")
        settle()
        type("lb")
        settle()
        rule.onNodeWithText("Albert").assertExists()
        rule.onNodeWithText("Alice").assertDoesNotExist()
    }

    @Test
    fun `a failed search says why`() {
        var text by mutableStateOf("")
        rule.setContent {
            MentionTextField(
                value = text,
                onValueChange = { text = it },
                onSearch = { throw MochiError.ServerError(502, "Directory unavailable") },
            )
        }
        type("@al")
        settle()
        rule.onNodeWithText("Directory unavailable").assertExists()
    }
}
