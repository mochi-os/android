// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.UserPreferences
import org.mochios.feeds.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Sentences that carry a count are plurals, so one reads as one ("1 post",
 * not "1 posts") and a language with more forms than two has each. A sentence
 * with two counts is two counted phrases, joined as the language lists them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportCountsTest {

    private val resources = RuntimeEnvironment.getApplication().resources
    private val format = Format(UserPreferences())

    @Test
    fun `follows still importing count each number in its own phrase`() {
        assertEquals("Importing 1 follow and 1 added so far", importingMessage(resources, format, 1, 1))
        assertEquals("Importing 5 follows and 2 added so far", importingMessage(resources, format, 5, 2))
    }

    @Test
    fun `the copies a feed published on an account`() {
        assertEquals(
            "The feed published 1 post there. It stays unless you delete it.",
            resources.getQuantityString(R.plurals.feeds_copies_delete_message, 1, format.formatNumber(1)),
        )
        assertEquals(
            "The feed published 3 posts there. They stay unless you delete them.",
            resources.getQuantityString(R.plurals.feeds_copies_delete_message, 3, format.formatNumber(3)),
        )
    }

    @Test
    fun `sources added so far`() {
        assertEquals(
            "Added 1 source so far; the rest follow",
            resources.getQuantityString(R.plurals.feeds_import_sources_more, 1, format.formatNumber(1)),
        )
        assertEquals(
            "Added 4 sources so far; the rest follow",
            resources.getQuantityString(R.plurals.feeds_import_sources_more, 4, format.formatNumber(4)),
        )
    }
}
