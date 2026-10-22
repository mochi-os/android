// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import android.app.Activity
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A notification from a linked or hosted account links to the post or person
 * on its own network; a path on this server opens in the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FollowLinkTest {

    /** The activity the app follows links from, as MainActivity does. */
    private val context = Robolectric.buildActivity(Activity::class.java).setup().get()

    @Test
    fun `a link off this server opens in the browser and is not routed`() {
        val routed = mutableListOf<String>()
        followLink(context, "https://example.social/@ada/123") { routed.add(it) }
        assertTrue(routed.isEmpty())
        val started = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://example.social/@ada/123", started.dataString)
    }

    @Test
    fun `a path on this server is routed in the app`() {
        val routed = mutableListOf<String>()
        followLink(context, "/feeds/abcdefghi/p1") { routed.add(it) }
        assertEquals(listOf("/feeds/abcdefghi/p1"), routed)
        assertNull(shadowOf(context).nextStartedActivity)
    }
}
