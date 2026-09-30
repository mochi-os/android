// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.find

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.feeds.model.Feed
import org.mochios.feeds.repository.probeFeed

class ProbeLinkTest {

    @Test
    fun `a share link is resolved through the probe, as a web URL is`() {
        assertTrue(isProbeLink("mochi://12D3KooWPeer/abc"))
        assertTrue(isProbeLink(" https://example.com/feeds/abc "))
        assertFalse(isProbeLink("cooking"))
    }

    @Test
    fun `the probe's answer is the feed's own entry`() {
        // The shape action_probe answers for a share link.
        val entry = Gson().fromJson(
            """{"id": "abc", "name": "Recipes", "fingerprint": "fp", "class": "feed", "peer": "12D3KooWPeer", "remote": true}""",
            Feed::class.java,
        )
        val feed = probeFeed(entry)
        assertEquals("abc", feed?.id)
        assertEquals("Recipes", feed?.name)
        assertEquals("12D3KooWPeer", feed?.peer)
    }

    @Test
    fun `an empty answer is no feed`() {
        assertNull(probeFeed(Feed()))
    }
}
