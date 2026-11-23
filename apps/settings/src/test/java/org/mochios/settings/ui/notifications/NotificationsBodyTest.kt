// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.notifications

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R
import org.mochios.android.api.MochiError
import org.mochios.android.i18n.AppContext
import org.mochios.android.notifications.MochiNotification
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The list under the Notifications screen's tabs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationsBodyTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun begin() {
        AppContext.set(context)
    }

    private fun show(state: NotificationsUiState, onRefresh: () -> Unit = {}) {
        rule.setContent { NotificationsBody(uiState = state, onRefresh = onRefresh, onOpen = {}, onSetCategory = { _, _ -> }) }
    }

    /** A list that was never loaded used to read "No unread notifications", with no way to try again. */
    @Test
    fun `a failed first read says why and offers Retry, not an empty list`() {
        var refreshed = 0
        show(NotificationsUiState(error = MochiError.ServerError(500, "Notifications are down")), onRefresh = { refreshed++ })
        rule.onNodeWithText("Notifications are down").assertExists()
        rule.onNodeWithText(context.getString(R.string.notifications_empty_unread)).assertDoesNotExist()
        rule.onNodeWithText(context.getString(R.string.common_retry)).performClick()
        rule.waitForIdle()
        assertEquals(1, refreshed)
    }

    @Test
    fun `an empty list that was read says so`() {
        show(NotificationsUiState())
        rule.onNodeWithText(context.getString(R.string.notifications_empty_unread)).assertExists()
    }

    /** With rows held, a failure is the snackbar's to say; the rows stay. */
    @Test
    fun `rows held stay on screen when a later read fails`() {
        val row = MochiNotification(id = "1", app = "feeds", topic = "post", `object` = "f", title = "A new post")
        show(NotificationsUiState(items = listOf(row), error = MochiError.ServerError(500, "Notifications are down")))
        rule.onNodeWithText("A new post").assertExists()
        rule.onNodeWithText("Notifications are down").assertDoesNotExist()
    }
}
