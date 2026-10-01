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
import org.mochios.settings.api.DestinationFeed
import org.mochios.settings.api.DestinationRow
import org.mochios.settings.api.DestinationsAvailable
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DestinationOptionsTest {

    @get:Rule
    val rule = createComposeRule()

    /** "Feed 10" used to be listed before "Feed 2". */
    @Test
    fun `destinations read in natural order of their labels`() {
        val available = DestinationsAvailable(
            feeds = listOf(
                DestinationFeed(id = "ten", name = "Feed 10"),
                DestinationFeed(id = "two", name = "feed 2"),
                DestinationFeed(id = "one", name = "Feed 1"),
            ),
        )
        var options: List<Pair<DestinationRow, String>> = emptyList()
        rule.setContent { options = destinationOptions(available) }
        rule.waitForIdle()
        assertEquals(
            listOf("one", "two", "ten"),
            options.map { (row, _) -> row }.filter { it.type == "rss" }.map { it.target },
        )
    }
}
