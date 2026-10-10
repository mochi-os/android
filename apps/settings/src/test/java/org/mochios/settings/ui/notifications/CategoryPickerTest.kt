// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.notifications

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R
import org.mochios.android.notifications.NotificationCategory
import org.mochios.settings.api.NotifTopic
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The category menu on a notification, as the web's category button lists it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CategoryPickerTest {

    @get:Rule
    val rule = createComposeRule()

    /** The menu used to list the stored English labels, sorted by them. */
    @Test
    fun `the menu lists the names shown, in natural order, with No notifications last`() {
        val categories = listOf(
            NotificationCategory(id = "0", label = "No notifications", display = "Keine Benachrichtigungen"),
            NotificationCategory(id = "a", label = "Normal", display = "Wichtig", default = 1),
            NotificationCategory(id = "b", label = "Group 10"),
            NotificationCategory(id = "c", label = "Group 2"),
        )
        rule.setContent {
            CategoryPicker(topic = NotifTopic(topic = "post"), categories = categories, onSetCategory = { _, _ -> })
        }
        val change = RuntimeEnvironment.getApplication().getString(R.string.notifications_change_category)
        rule.onNodeWithContentDescription(change).performClick()
        rule.waitForIdle()

        val names = listOf("Group 2", "Group 10", "Wichtig", "Keine Benachrichtigungen")
        val tops = names.map { name ->
            rule.onAllNodesWithText(name).fetchSemanticsNodes().single().boundsInRoot.top
        }
        assertEquals(tops.sorted(), tops)
        assertEquals(0, rule.onAllNodesWithText("Normal").fetchSemanticsNodes().size)
    }
}
