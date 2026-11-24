// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.R
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.NumberFormat
import org.mochios.android.i18n.UserPreferences
import org.mochios.android.notifications.MochiNotification
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationCountTest {

    @get:Rule
    val rule = createComposeRule()

    /** The repeat count used to be written raw, ignoring the user's number format. */
    @Test
    fun `a repeated notification writes its count as the user writes numbers`() {
        rule.setContent {
            CompositionLocalProvider(
                LocalFormat provides Format(UserPreferences(numberFormat = NumberFormat.EUROPEAN_DOT_COMMA)),
            ) {
                NotificationCard(
                    notification = MochiNotification(id = "n", title = "Comment", count = 12345),
                    onClick = {},
                )
            }
        }
        rule.onNodeWithText("×12.345").assertExists()
    }

    /**
     * A row with no sender drew a monogram of its topic, which says nothing
     * about where it came from; the web shows the app's icon.
     */
    @Test
    fun `a notification with no sender shows the icon of its app`() {
        assertEquals(R.drawable.ic_notification_chess, sourceIcon(MochiNotification(app = "chess")))
        // A published app's id is an entity id; its link names it.
        assertEquals(
            R.drawable.ic_notification_wikis,
            sourceIcon(MochiNotification(app = "12254aHfG39LqrizXg8Wv7", link = "/wikis/abc12def")),
        )
        assertEquals(R.drawable.ic_mochi_notification, sourceIcon(MochiNotification(app = "air")))
    }

    @Test
    fun `a notification with a sender shows the person instead`() {
        assertNull(sourceIcon(MochiNotification(app = "chess", sender = "12254aHfG39LqrizXg8Wv7")))
    }

    @Test
    fun `a card with no sender draws no monogram of its topic`() {
        rule.setContent {
            NotificationCard(
                notification = MochiNotification(id = "n", app = "chess", topic = "move", title = "Your turn"),
                onClick = {},
            )
        }
        rule.onNodeWithText("Your turn").assertExists()
        rule.onNodeWithText("M").assertDoesNotExist()
    }
}
