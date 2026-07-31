// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Notification routing had drifted into three disagreeing lists: eleven
 * channels created at startup, ten routed by the UnifiedPush dispatcher (no
 * people, no crm), and four by the FCM service — everything else falling back
 * to Feeds, so on FCM most apps posted on the wrong channel with the wrong
 * name, importance and sound.
 *
 * Both transports now resolve through [notificationChannelFor]. These pin the
 * set against the launcher aliases so a new Mochi-app that gets a channel but
 * no alias, or the reverse, fails here rather than shipping a notification on
 * the Feeds channel or a badge on every icon.
 */
class NotificationRoutingTest {

    /** The slugs MochiApplication creates a channel for, in manifest order. */
    private val createdAtStartup = setOf(
        "feeds", "chat", "forums", "projects", "crm", "people",
        "wikis", "chess", "go", "words", "market", "staff",
    )

    @Test
    fun `routing covers exactly the channels created at startup`() {
        assertEquals(createdAtStartup, NOTIFICATION_CHANNELS)
    }

    @Test
    fun `every routed app resolves to its own channel`() {
        for (slug in NOTIFICATION_CHANNELS) {
            assertEquals(slug, notificationChannelFor(slug, ""))
        }
    }

    /** The apps that used to land on Feeds over FCM. */
    @Test
    fun `apps beyond the original four no longer fall back`() {
        for (slug in listOf("wikis", "chess", "go", "words", "market", "staff", "people", "crm")) {
            assertEquals(slug, notificationChannelFor(slug, ""))
        }
    }

    @Test
    fun `the app slug wins over the link`() {
        assertEquals("chess", notificationChannelFor("chess", "/feeds/abc"))
    }

    @Test
    fun `a blank app falls back to the link's first segment`() {
        assertEquals("wikis", notificationChannelFor("", "/wikis/abc12def"))
        assertEquals("wikis", notificationChannelFor(null, "wikis/abc12def"))
    }

    @Test
    fun `an unknown app uses the fallback channel`() {
        assertEquals(FALLBACK_NOTIFICATION_CHANNEL, notificationChannelFor("nosuchapp", ""))
        assertEquals(FALLBACK_NOTIFICATION_CHANNEL, notificationChannelFor("", ""))
        assertEquals(FALLBACK_NOTIFICATION_CHANNEL, notificationChannelFor(null, null))
    }

    /**
     * A channel without an alias means the notification falls back to the
     * implicit intent and the launcher badges every Mochi icon — which is what
     * happened to eight apps.
     */
    @Test
    fun `every channel has a launcher alias`() {
        for (slug in NOTIFICATION_CHANNELS) {
            assertTrue("no launcher alias for $slug", LAUNCHER_ALIASES.containsKey(slug))
        }
    }

    /** Settings has an alias and no channel — it never posts notifications. */
    @Test
    fun `aliases beyond the channel set are only settings`() {
        assertEquals(setOf("settings"), LAUNCHER_ALIASES.keys - NOTIFICATION_CHANNELS)
    }
}
