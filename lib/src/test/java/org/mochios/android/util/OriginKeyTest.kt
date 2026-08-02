// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The cookie cache buckets by origin. Keyed by host, two origins sharing a
 * hostname shared a bucket: a `session` cookie set by `http://host` or
 * `host:8443` was replayed to `https://host`, and it also satisfied the
 * "already has a session" test, suppressing the real stored session in favour
 * of whatever the other origin had set.
 *
 * Calls the production [originOf] that SessionManager's jar keys on. An earlier
 * version of this suite reimplemented the expression instead, which meant it
 * would have passed unchanged if production regressed to host-only buckets.
 */
class OriginKeyTest {

    private fun key(url: String) = originOf(url.toHttpUrl())

    /**
     * Pins the shape, not just the relations below: a regression to a host-only
     * key would still satisfy every equality here, so assert what the key
     * actually renders to.
     */
    @Test
    fun `the key carries scheme, host and effective port`() {
        assertEquals("https://mochi-os.org:443", key("https://mochi-os.org/x"))
        assertEquals("http://mochi-os.org:80", key("http://mochi-os.org/x"))
        assertEquals("https://mochi-os.org:8443", key("https://mochi-os.org:8443/x"))
    }

    @Test
    fun `the same origin shares a bucket regardless of path`() {
        assertEquals(key("https://mochi-os.org/a/b"), key("https://mochi-os.org/c"))
    }

    @Test
    fun `an implicit and explicit default port are the same bucket`() {
        assertEquals(key("https://mochi-os.org/x"), key("https://mochi-os.org:443/x"))
    }

    /** The bug: these three used to collide on hostname alone. */
    @Test
    fun `scheme and port separate buckets on one hostname`() {
        val canonical = key("https://mochi-os.org/x")
        assertNotEquals(canonical, key("http://mochi-os.org/x"))
        assertNotEquals(canonical, key("https://mochi-os.org:8443/x"))
        assertNotEquals(key("http://mochi-os.org/x"), key("https://mochi-os.org:8443/x"))
    }

    @Test
    fun `different hosts never share a bucket`() {
        assertNotEquals(key("https://mochi-os.org/x"), key("https://attacker.example/x"))
    }
}
