// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects

import android.content.pm.ShortcutManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R as MochiR
import org.mochios.projects.model.Project
import org.mochios.projects.model.Owner
import org.mochios.projects.ui.project.ProjectRow
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowShortcutManager

/** A project's row: its menu puts the project on the home screen, by its fingerprint. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProjectRowTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(ShortcutManager::class.java)
    private val menu = context.getString(MochiR.string.common_more_options)

    @Before
    fun setUp() {
        ShadowShortcutManager.reset()
    }

    @Test
    fun `the menu puts it on the home screen`() {
        shadowOf(manager).setIsRequestPinShortcutSupported(true)
        rule.setContent { ProjectRow(Project(id = "i1", fingerprint = "fp1", name = "Plans"), onClick = {}, onUnsubscribe = {}) }
        rule.onNodeWithContentDescription(menu).performClick()
        rule.onNodeWithText(context.getString(MochiR.string.launcher_add_to_home)).performClick()
        val shortcut = manager.pinnedShortcuts.single()
        assertEquals("mochi:/fp1", shortcut.intent?.data.toString())
        assertEquals("projects", shortcut.intent?.getStringExtra("app"))
        assertEquals("Plans", shortcut.shortLabel.toString())
    }

    @Test
    fun `one of the user's own, where the launcher cannot pin, has no menu`() {
        shadowOf(manager).setIsRequestPinShortcutSupported(false)
        rule.setContent { ProjectRow(Project(id = "i1", name = "Plans", owner = Owner(local = true)), onClick = {}, onUnsubscribe = {}) }
        rule.onNodeWithContentDescription(menu).assertDoesNotExist()
    }

    @Test
    fun `one subscribed to keeps its menu for Unsubscribe`() {
        shadowOf(manager).setIsRequestPinShortcutSupported(false)
        rule.setContent { ProjectRow(Project(id = "i1", name = "Plans"), onClick = {}, onUnsubscribe = {}) }
        rule.onNodeWithContentDescription(menu).performClick()
        rule.onNodeWithText(context.getString(R.string.projects_settings_unsubscribe)).assertExists()
        rule.onNodeWithText(context.getString(MochiR.string.launcher_add_to_home)).assertDoesNotExist()
    }
}
