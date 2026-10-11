// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.launcher

import android.content.pm.ShortcutManager
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowShortcutManager

/** "Add to home screen" in an item's menu. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AddToHomeItemTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(ShortcutManager::class.java)
    private val label = context.getString(R.string.launcher_add_to_home)

    @Before
    fun setUp() {
        ShadowShortcutManager.reset()
    }

    @Test
    fun `it pins the item and closes the menu`() {
        shadowOf(manager).setIsRequestPinShortcutSupported(true)
        var closed = false
        rule.setContent { Column { AddToHomeItem("feeds", "f1", "Garden") { closed = true } } }
        rule.onNodeWithText(label).performClick()
        assertTrue(closed)
        val shortcut = manager.pinnedShortcuts.single()
        assertEquals("feeds_f1", shortcut.id)
        assertEquals("Garden", shortcut.shortLabel.toString())
    }

    @Test
    fun `it is not offered where the launcher cannot pin`() {
        shadowOf(manager).setIsRequestPinShortcutSupported(false)
        rule.setContent { Column { AddToHomeItem("feeds", "f1", "Garden") {} } }
        rule.onNodeWithText(label).assertDoesNotExist()
    }
}
