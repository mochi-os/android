// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.text.Selection
import android.text.Spannable
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A Reply that quotes what is selected on the page, as the web's quotes
 * `window.getSelection()`: the quote's shape, a second Reply replacing the
 * first's quote rather than stacking another, the selection read from
 * whichever body holds it, and the shared comment row offering Reply in its
 * body's selection menu.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuoteReplyTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a selection is trimmed and quoted, a blank line after`() {
        assertEquals("> quick brown\n\n", quoteInto("", "  quick brown \n"))
    }

    @Test
    fun `each line is marked, a blank one too, as the web marks them`() {
        assertEquals("> one\n> \n> three\n\n", quoteInto("", "one\n\nthree"))
    }

    @Test
    fun `the quote goes ahead of what is already typed`() {
        assertEquals("> quick\n\nmy words", quoteInto("my words", "quick"))
    }

    @Test
    fun `with nothing selected the draft is left as it is`() {
        assertEquals("my words", quoteInto("my words", null))
        assertEquals("my words", quoteInto("my words", " \n "))
    }

    @Test
    fun `a second Reply replaces the first's quote, keeping what was typed after it`() {
        assertEquals("> fox\n\nmy words", quoteInto("> quick\n\nmy words", "fox", "> quick\n\n"))
    }

    @Test
    fun `a second Reply with nothing selected takes the first's quote out, as the web's leaves none`() {
        assertEquals("my words", quoteInto("> quick\n\nmy words", null, "> quick\n\n"))
    }

    @Test
    fun `a quote edited since, or one cited by Quote, stays`() {
        assertEquals("> fox\n\n> quick, edited\n\nmy words", quoteInto("> quick, edited\n\nmy words", "fox", "> quick\n\n"))
    }

    @Test
    fun `Reply twice with the same selection quotes it once`() {
        val drafts = CommentDrafts()
        drafts.update("ticket") { it.copy(text = "my words") }
        drafts.reply("ticket", "c1", "quick")
        drafts.reply("ticket", "c2", "quick")
        assertEquals(CommentDraft(text = "> quick\n\nmy words", reply = "c2", quote = "> quick\n\n"), drafts.draft("ticket"))
    }

    private var root: View? = null

    private fun bodies(view: View): List<ClickableLinkTextView> = when (view) {
        is ClickableLinkTextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { bodies(view.getChildAt(it)) }
        else -> emptyList()
    }

    @Test
    fun `the page's selection is read from whichever body was selected in last`() {
        var second by mutableStateOf(true)
        rule.setContent {
            root = LocalView.current
            Column {
                HtmlContent(html = "The quick brown fox")
                if (second) HtmlContent(html = "jumps over the lazy dog")
            }
        }
        rule.waitForIdle()
        val first = bodies(root!!).single { body -> body.text.startsWith("The") }
        val other = bodies(root!!).single { body -> body.text.startsWith("jumps") }
        var read: String? = "unread"
        rule.runOnUiThread { read = windowSelection(root!!) }
        assertNull("nothing selected yet", read)
        rule.runOnUiThread {
            Selection.setSelection(first.text as Spannable, 4, 15)
            Selection.setSelection(other.text as Spannable, 0, 5)
            read = windowSelection(root!!)
        }
        assertEquals("THE POINT: the body selected in last, wherever it is", "jumps", read)
        rule.runOnUiThread {
            Selection.setSelection(first.text as Spannable, 10, 19)
            read = windowSelection(root!!)
        }
        assertEquals("brown fox", read)
        rule.runOnUiThread {
            Selection.setSelection(first.text as Spannable, 0)
            read = windowSelection(root!!)
        }
        assertNull("a selection let go of is not read", read)
        rule.runOnUiThread { Selection.setSelection(other.text as Spannable, 5, 9) }
        second = false
        rule.waitForIdle()
        rule.runOnUiThread { read = windowSelection(root!!) }
        assertNull("nor is one in a body no longer drawn", read)
    }

    @Test
    fun `the comment row's body offers the action it is given in its selection menu`() {
        val action = SelectionAction("Reply") {}
        rule.setContent {
            root = LocalView.current
            CommentItem(
                name = "Ann",
                body = "The quick brown fox",
                created = 0,
                depth = 0,
                seed = "ann",
                attachments = emptyList(),
                attachmentUrl = { null },
                attachmentThumbnailUrl = { null },
                selectionAction = action,
            )
        }
        rule.waitForIdle()
        assertSame(action, bodies(root!!).single().selectionAction)
    }
}
