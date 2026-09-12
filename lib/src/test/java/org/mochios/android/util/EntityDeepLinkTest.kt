// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `mochi:/<entity>` arrives at an exported activity, so its app hint and path
 * segments are attacker-supplied. These pin what survives that.
 */
class EntityDeepLinkTest {

    private val apps = listOf("feeds", "chat", "wikis")

    @Test
    fun `builds the route for a known app`() {
        assertEquals("/feeds/abc123", entityDeepLink("feeds", listOf("abc123"), apps))
        assertEquals(
            "/wikis/abc123/page/Home",
            entityDeepLink("wikis", listOf("abc123", "page", "Home"), apps),
        )
    }

    @Test
    fun `refuses an app this build does not host`() {
        assertNull(entityDeepLink("evil", listOf("abc123"), apps))
        assertNull(entityDeepLink("", listOf("abc123"), apps))
        assertNull(entityDeepLink(null, listOf("abc123"), apps))
    }

    @Test
    fun `refuses an app hint carrying its own path`() {
        assertNull(entityDeepLink("feeds/../settings", listOf("abc123"), apps))
    }

    @Test
    fun `refuses a segment that could smuggle a path or query`() {
        for (bad in listOf("a/b", "a?tab=access", "a#x", "..", "a b", "a%2Fb", "")) {
            assertNull("segment ${'$'}bad must be refused", entityDeepLink("feeds", listOf(bad), apps))
            assertNull(
                "trailing segment ${'$'}bad must be refused",
                entityDeepLink("feeds", listOf("abc123", bad), apps),
            )
        }
    }

    @Test
    fun `refuses an over-long segment`() {
        assertNull(entityDeepLink("feeds", listOf("a".repeat(129)), apps))
        assertEquals("/feeds/${"a".repeat(128)}", entityDeepLink("feeds", listOf("a".repeat(128)), apps))
    }

    @Test
    fun `refuses an empty path`() {
        assertNull(entityDeepLink("feeds", emptyList(), apps))
    }

    /**
     * Negative control: the rule this replaced accepted any app string and any
     * segment, so each of the refusals above produced a live route.
     */
    @Test
    fun `the ungated rule built a route from every one of those`() {
        fun ungated(app: String?, segments: List<String>): String? {
            val entity = segments.firstOrNull() ?: return null
            if (app == null) return null
            return buildString {
                append('/').append(app).append('/').append(entity)
                for (s in segments.drop(1)) append('/').append(s)
            }
        }
        assertEquals("/evil/abc123", ungated("evil", listOf("abc123")))
        assertEquals("/feeds/../settings/abc123", ungated("feeds/../settings", listOf("abc123")))
        assertEquals("/feeds/abc123/a?tab=access", ungated("feeds", listOf("abc123", "a?tab=access")))
    }
}
