// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mochios.android.launcher.LAUNCHER_ACTIVITIES

/**
 * Both transports resolve a push's app through [pushApp], for its channel, icon
 * and activity. These pin the channel set against the launcher activities: a
 * channel with none badges every Mochi icon, and an app with no channel posts
 * on Feeds.
 */
class NotificationRoutingTest {

    /** The slugs MochiApplication creates a channel for, in manifest order. */
    private val createdAtStartup = setOf(
        "feeds", "chat", "forums", "projects", "crm", "people",
        "wikis", "chess", "go", "words", "market", "staff", "calendars",
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

    /**
     * On a server running published apps the payload's `app` is the sending
     * app's entity id; the link, which starts with the app's path, names it.
     */
    @Test
    fun `a published app's id routes by the link`() {
        val id = "12254aHfG39LqrizXg8Wv7"
        assertEquals("chat", pushApp(id, "/chat/c1"))
        assertEquals("chat", notificationChannelFor(id, "/chat/c1"))
        assertEquals("market", pushApp(id, "/market?tab=orders"))
        assertEquals("forums", notificationChannelFor(id, "forums/f1/p1"))
    }

    @Test
    fun `an app that names no client app with no app in its link has none`() {
        assertEquals(null, pushApp("12254aHfG39LqrizXg8Wv7", "/air/game"))
        assertEquals(null, pushApp(null, null))
        assertEquals(FALLBACK_NOTIFICATION_CHANNEL, notificationChannelFor("12254aHfG39LqrizXg8Wv7", "/air/game"))
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
     * A channel with no launcher activity falls back to the implicit intent, badging every
     * Mochi icon.
     */
    @Test
    fun `every channel has a launcher activity`() {
        for (slug in NOTIFICATION_CHANNELS) {
            assertTrue("no launcher activity for $slug", LAUNCHER_ACTIVITIES.containsKey(slug))
        }
    }

    /**
     * Home and Settings have launcher activities and no channel: neither ever
     * posts notifications.
     */
    @Test
    fun `launcher activities beyond the channel set are only home and settings`() {
        assertEquals(setOf("home", "settings"), LAUNCHER_ACTIVITIES.keys - NOTIFICATION_CHANNELS)
    }
}
