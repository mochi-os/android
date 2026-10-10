// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.AppContext
import org.mochios.android.notifications.MochiNotification
import org.mochios.android.notifications.NotificationCategory
import org.mochios.android.notifications.NotificationTopic
import org.mochios.home.ui.CategoryPicker
import org.mochios.home.ui.HomeMenuState
import org.mochios.home.ui.UserButton
import org.mochios.home.ui.UserMenu
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The user menu as the web's: who is signed in, log out, and the unread notifications. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UserMenuTest {

    @get:Rule
    val rule = createComposeRule()

    private val chat = MochiNotification(id = "1", app = "chat", topic = "message", `object` = "c", title = "Hello", link = "/chat/c")
    private val feeds = MochiNotification(id = "2", app = "feeds", topic = "post", `object` = "f", title = "New post")

    private val events = mutableListOf<String>()

    private fun show(state: HomeMenuState) {
        AppContext.set(RuntimeEnvironment.getApplication())
        rule.setContent {
            UserMenu(
                state = state,
                onLogout = { events += "logout" },
                onReadAll = { events += "read all" },
                onViewAll = { events += "view all" },
                onOpen = { events += "open ${it.id}" },
                onPick = { events += "pick ${it.id}" },
                onClosePicker = { events += "close" },
                onCategorise = { notification, category -> events += "move ${notification.id} to $category" },
                onManageCategories = { events += "manage" },
            )
        }
    }

    @Test
    fun `it names who is signed in and logs them out`() {
        show(HomeMenuState(name = "Ada", identity = "p1", loading = false))
        rule.onNodeWithText("Ada").assertExists()
        rule.onNodeWithContentDescription("Log out").performClick()
        assertEquals(listOf("logout"), events)
    }

    @Test
    fun `a nameless user reads as User`() {
        show(HomeMenuState(loading = false))
        rule.onNodeWithText("User").assertExists()
    }

    @Test
    fun `with nothing unread it says so, and still offers every notification`() {
        show(HomeMenuState(name = "Ada", loading = false))
        rule.onNodeWithText("No unread notifications").assertExists()
        rule.onAllNodesWithContentDescription("Mark all read").assertCountEquals(0)
        rule.onNodeWithContentDescription("View all").performClick()
        assertEquals(listOf("view all"), events)
    }

    @Test
    fun `the unread are counted, listed and can all be marked read`() {
        show(HomeMenuState(name = "Ada", unread = listOf(chat, feeds), loading = false))
        rule.onNodeWithText("Notifications (2)").assertExists()
        rule.onNodeWithText("Hello").assertExists()
        rule.onNodeWithText("New post").assertExists()
        rule.onNodeWithContentDescription("Mark all read").performClick()
        assertEquals(listOf("read all"), events)
    }

    @Test
    fun `tapping a notification opens it`() {
        show(HomeMenuState(unread = listOf(chat), loading = false))
        rule.onNodeWithText("Hello").performClick()
        assertEquals(listOf("open 1"), events)
    }

    @Test
    fun `a failure is shown`() {
        val error = MochiError.ServerError(500, "Something broke")
        show(HomeMenuState(unread = listOf(chat), loading = false, error = error))
        rule.onNodeWithText(error.userMessage()).assertExists()
    }

    @Test
    fun `a notification's picker offers its categories and moves it`() {
        val picker = CategoryPicker(
            notification = "1",
            categories = listOf(NotificationCategory(id = "a", label = "Normal")),
            topic = NotificationTopic(app = "chat", category = null),
        )
        show(HomeMenuState(unread = listOf(chat), loading = false, picker = picker))
        rule.onNodeWithText("Normal").performClick()
        rule.onNodeWithText("Unassigned").performClick()
        assertEquals(listOf("move 1 to a", "move 1 to null"), events)
    }

    @Test
    fun `the picker opens from its button and links to the categories`() {
        show(HomeMenuState(unread = listOf(chat), loading = false, picker = CategoryPicker("1", emptyList(), null)))
        rule.onNodeWithText("No topic record yet — try again after the next notification.").assertExists()
        rule.onNodeWithText("Manage categories").performClick()
        assertEquals(listOf("manage"), events)
    }

    @Test
    fun `the category button asks for the picker`() {
        show(HomeMenuState(unread = listOf(chat), loading = false))
        rule.onNodeWithContentDescription("Change notification category").performClick()
        assertEquals(listOf("pick 1"), events)
    }

    @Test
    fun `the avatar carries the unread count and opens the menu`() {
        AppContext.set(RuntimeEnvironment.getApplication())
        var opened = 0
        rule.setContent { UserButton(name = "Ada", identity = "", count = 3) { opened++ } }
        rule.onNodeWithText("3").assertExists()
        rule.onNodeWithContentDescription("Open menu").performClick()
        assertEquals(1, opened)
    }
}
