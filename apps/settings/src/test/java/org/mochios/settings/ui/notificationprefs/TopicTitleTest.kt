// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.notificationprefs

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.settings.api.NotifTopic
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "fr")
class TopicTitleTest {

    @get:Rule
    val rule = createComposeRule()

    /** The title used to be joined with an English colon in every language. */
    @Test
    fun `a topic title joins what happened and which thing as the language writes it`() {
        var titles: List<String> = emptyList()
        rule.setContent {
            titles = listOf(
                topicTitle(NotifTopic(topic = "comment", label = "Commentaire", name = "Ticket 2")),
                topicTitle(NotifTopic(topic = "comment", label = "Commentaire")),
            )
        }
        rule.waitForIdle()
        assertEquals(listOf("Commentaire : Ticket 2", "Commentaire"), titles)
    }
}
