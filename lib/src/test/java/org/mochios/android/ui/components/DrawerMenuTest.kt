// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A drawer row's own menu, as a web list row's: a long press opens it, a tap
 * still opens the row, and a row marked unread with nothing counted shows a
 * dot where the count would be.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DrawerMenuTest {

    @get:Rule
    val rule = createComposeRule()

    private val picked = mutableListOf<String>()
    private val opened = mutableListOf<String>()

    private fun show() {
        rule.setContent {
            MochiListDrawer(
                drawerState = rememberDrawerState(DrawerValue.Open),
                items = listOf(
                    DrawerItem(
                        id = "c1",
                        title = "One",
                        menu = listOf(DrawerMenuEntry("Pin", Icons.Outlined.PushPin) { picked += "pin" }),
                    ),
                    DrawerItem(id = "c2", title = "Two", dot = true),
                    DrawerItem(id = "c3", title = "Three", unread = 4, dot = true),
                ),
                selectedId = null,
                onItemClick = { item -> opened += item.id },
            ) {}
        }
    }

    @Test
    fun `a long press opens the row's menu, and an entry does what it says`() {
        show()
        rule.onNodeWithText("One").performTouchInput { longClick() }
        rule.waitForIdle()
        rule.onNodeWithText("Pin").performClick()
        rule.waitForIdle()
        assertEquals(listOf("pin"), picked)
        assertEquals(emptyList<String>(), opened)
        assertEquals(0, rule.onAllNodesWithText("Pin").fetchSemanticsNodes().size)
    }

    @Test
    fun `a tap still opens the row`() {
        show()
        rule.onNodeWithText("One").performClick()
        assertEquals(listOf("c1"), opened)
    }

    @Test
    fun `a dot shows for a row marked unread, and a count in its place when there is one`() {
        show()
        val unread = RuntimeEnvironment.getApplication().getString(R.string.drawer_unread)
        assertEquals(1, rule.onAllNodesWithContentDescription(unread).fetchSemanticsNodes().size)
        rule.onNodeWithText("4").assertExists()
    }
}
