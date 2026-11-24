// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.text.Selection
import android.text.Spannable
import android.view.ActionMode
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboMenu
import org.robolectric.RobolectricTestRunner

/**
 * The item a body's text selection menu offers beside the platform's own: it
 * is there only when asked for, and hands over the text selected.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SelectionActionTest {

    @get:Rule
    val rule = createComposeRule()

    /** An action mode that only records that it was finished. */
    private class Mode : ActionMode() {
        var finished = false
        override fun setTitle(title: CharSequence?) {}
        override fun setTitle(resId: Int) {}
        override fun setSubtitle(subtitle: CharSequence?) {}
        override fun setSubtitle(resId: Int) {}
        override fun setCustomView(view: View?) {}
        override fun invalidate() {}
        override fun finish() {
            finished = true
        }
        override fun getMenu(): Menu = throw UnsupportedOperationException()
        override fun getTitle(): CharSequence? = null
        override fun getSubtitle(): CharSequence? = null
        override fun getCustomView(): View? = null
        override fun getMenuInflater(): MenuInflater = throw UnsupportedOperationException()
    }

    private val picked = mutableListOf<String>()
    private val mode = Mode()

    private fun show(action: SelectionAction?, html: String = "The quick brown fox"): TextView {
        var view: TextView? = null
        rule.setContent {
            Box(Modifier.width(320.dp)) {
                HtmlContent(
                    html = html,
                    modifier = Modifier.fillMaxWidth(),
                    onTextViewReady = { view = it },
                    selectionAction = action,
                )
            }
        }
        rule.waitForIdle()
        return view!!
    }

    private fun reply() = SelectionAction("Reply") { picked += it }

    /** The selection menu [view] builds with [from]..[to] selected. */
    private fun menu(view: TextView, from: Int, to: Int): Menu {
        val menu = RoboMenu(view.context)
        rule.runOnUiThread {
            Selection.setSelection(view.text as Spannable, from, to)
            assertTrue(view.customSelectionActionModeCallback.onCreateActionMode(mode, menu))
        }
        return menu
    }

    private fun items(menu: Menu): List<MenuItem> = (0 until menu.size()).map { menu.getItem(it) }

    /** Choose [item] from [view]'s selection menu, answering whether the menu took it. */
    private fun choose(view: TextView, item: MenuItem): Boolean {
        var taken = false
        rule.runOnUiThread { taken = view.customSelectionActionModeCallback.onActionItemClicked(mode, item) }
        return taken
    }

    @Test
    fun `the menu holds the action asked for`() {
        val view = show(reply())
        assertEquals(listOf("Reply"), items(menu(view, 4, 15)).map { it.title.toString() })
    }

    @Test
    fun `choosing it hands over the selected text`() {
        val view = show(reply())
        choose(view, items(menu(view, 4, 15)).single())
        assertEquals(listOf("quick brown"), picked)
    }

    @Test
    fun `an inline image in the selection is left out of the text handed over`() {
        val view = show(reply(), html = """The quick <img src="https://example.org/a.png"> brown fox""")
        val text = view.text.toString()
        assertTrue("the image stands on a replacement character", text.contains('￼'))
        choose(view, items(menu(view, text.indexOf("quick"), text.indexOf("brown") + 5)).single())
        assertEquals(listOf("quick  brown"), picked)
    }

    @Test
    fun `choosing it closes the menu`() {
        val view = show(reply())
        choose(view, items(menu(view, 4, 15)).single())
        assertTrue(mode.finished)
    }

    @Test
    fun `without an action the menu is the platform's own`() {
        val view = show(null)
        assertEquals(emptyList<String>(), items(menu(view, 4, 15)).map { it.title.toString() })
    }

    @Test
    fun `the platform's own items are left to it`() {
        val view = show(reply())
        val menu = menu(view, 4, 15)
        val copy = menu.add(Menu.NONE, android.R.id.copy, 1, "Copy")
        assertFalse(choose(view, copy))
        assertEquals(emptyList<String>(), picked)
    }
}
