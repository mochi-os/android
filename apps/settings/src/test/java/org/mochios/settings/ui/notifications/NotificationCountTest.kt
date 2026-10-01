// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.notifications

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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
                    topic = null,
                    categories = emptyList(),
                    onSetCategory = { _, _ -> },
                    onClick = {},
                )
            }
        }
        rule.onNodeWithText("×12.345").assertExists()
    }
}
