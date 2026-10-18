// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis

import android.content.pm.ShortcutManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R as MochiR
import org.mochios.wikis.model.WikiPermissions
import org.mochios.wikis.ui.page.PageOverflowMenu
import org.mochios.wikis.ui.page.PageShortcut
import org.mochios.wikis.ui.page.pageShortcut
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowShortcutManager

/**
 * A wiki the user holds goes on the home screen from its pages' menu: the wiki
 * itself from its home page, and the page from any other, so the icon opens
 * where it was added. One only browsed does not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h1600dp")
class PageMenuShortcutTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(ShortcutManager::class.java)
    private val label = context.getString(MochiR.string.launcher_add_to_home)
    private var dismissed = false

    @Before
    fun setUp() {
        ShadowShortcutManager.reset()
        shadowOf(manager).setIsRequestPinShortcutSupported(true)
    }

    private fun show(shortcut: PageShortcut?) {
        rule.setContent {
            PageOverflowMenu(
                expanded = true,
                onDismiss = { dismissed = true },
                wikiId = "w1",
                slug = "home",
                permissions = WikiPermissions(view = true),
                commentCount = 0,
                canUnsubscribe = false,
                shortcut = shortcut,
                onEdit = {},
                onRename = {},
                onHistory = {},
                onComments = {},
                onDelete = {},
                onTags = {},
                onChanges = {},
                onNewPage = {},
                onSettings = {},
                onShare = {},
                onUnsubscribe = {},
                onRssCopy = {},
                onRssRevoke = {},
            )
        }
    }

    @Test
    fun `from its home page, a wiki the user holds goes on the home screen under its name`() {
        show(pageShortcut("w1", "Notes", home = "home", slug = "home", title = "Home"))
        rule.onNodeWithText(label).performClick()
        assertTrue(dismissed)
        val shortcut = manager.pinnedShortcuts.single()
        assertEquals("mochi:/w1", shortcut.intent?.data.toString())
        assertEquals("wikis", shortcut.intent?.getStringExtra("app"))
        assertEquals("Notes", shortcut.shortLabel.toString())
    }

    @Test
    fun `from any other page, the page goes on the home screen under its title`() {
        show(pageShortcut("w1", "Notes", home = "home", slug = "setup", title = "Setting up"))
        rule.onNodeWithText(label).performClick()
        val shortcut = manager.pinnedShortcuts.single()
        assertEquals("mochi:/w1/setup", shortcut.intent?.data.toString())
        assertEquals("wikis", shortcut.intent?.getStringExtra("app"))
        assertEquals("Setting up", shortcut.shortLabel.toString())
    }

    @Test
    fun `the home page is the one the wiki names, else home`() {
        assertEquals(PageShortcut("w1", "Notes"), pageShortcut("w1", "Notes", "start", "start", "Start"))
        assertEquals(PageShortcut("w1/home", "Home"), pageShortcut("w1", "Notes", "start", "home", "Home"))
        assertEquals(PageShortcut("w1", "Notes"), pageShortcut("w1", "Notes", "", "home", "Home"))
        assertEquals(PageShortcut("w1", "Notes"), pageShortcut("w1", "Notes", null, "home", "Home"))
        assertEquals(PageShortcut("w1", "Notes"), pageShortcut("w1", "Notes", "home", "", ""))
    }

    @Test
    fun `a page with no title yet is named by its address`() {
        assertEquals(PageShortcut("w1/setup", "setup"), pageShortcut("w1", "Notes", "home", "setup", ""))
    }

    @Test
    fun `a wiki only browsed does not`() {
        show(null)
        rule.onNodeWithText(label).assertDoesNotExist()
    }
}
