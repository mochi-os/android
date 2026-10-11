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
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowShortcutManager

/** A wiki the user holds goes on the home screen from its pages' menu; one only browsed does not. */
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

    private fun show(shortcut: String?) {
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
    fun `a wiki the user holds goes on the home screen under its name`() {
        show("Notes")
        rule.onNodeWithText(label).performClick()
        assertTrue(dismissed)
        val shortcut = manager.pinnedShortcuts.single()
        assertEquals("mochi:/w1", shortcut.intent?.data.toString())
        assertEquals("wikis", shortcut.intent?.getStringExtra("app"))
        assertEquals("Notes", shortcut.shortLabel.toString())
    }

    @Test
    fun `a wiki only browsed does not`() {
        show(null)
        rule.onNodeWithText(label).assertDoesNotExist()
    }
}
