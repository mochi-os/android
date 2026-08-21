// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the shared origin check used by the session cookie jar, the Retrofit
 * retarget and the push receiver. Every host below is one the client really
 * contacts while signed in - Coil loads RSS images from publisher domains.
 */
class OriginTest {

    private val server = "https://mochi-os.org"

    @Test
    fun `own server matches`() {
        assertTrue(isServerOrigin("https://mochi-os.org/feeds/x".toHttpUrl(), server))
    }

    @Test
    fun `explicit default port matches implicit one`() {
        assertTrue(isServerOrigin("https://mochi-os.org:443/feeds/x".toHttpUrl(), server))
        assertTrue(isServerOrigin("https://host:443/x".toHttpUrl(), "https://host"))
        assertTrue(isServerOrigin("http://host/x".toHttpUrl(), "http://host:80"))
    }

    @Test
    fun `trailing slash on the stored server url still matches`() {
        assertTrue(isServerOrigin("https://mochi-os.org/x".toHttpUrl(), "https://mochi-os.org/"))
    }

    /** The leak: a hostile RSS feed image must not receive the session. */
    @Test
    fun `third party host does not match`() {
        assertFalse(isServerOrigin("https://attacker.example/x.png".toHttpUrl(), server))
        assertFalse(isServerOrigin("https://cdn.japantimes.co.jp/i.jpg".toHttpUrl(), server))
    }

    /**
     * The first path segment names the app, and the asset interceptor reads it
     * to pick a per-app JWT. A foreign URL shaped like ours must stay foreign.
     */
    @Test
    fun `a foreign url shaped like an app path does not match`() {
        assertFalse(isServerOrigin("https://attacker.example/feeds/video.mp4".toHttpUrl(), server))
        assertFalse(isServerOrigin("https://attacker.example/chat/x/-/avatar".toHttpUrl(), server))
    }

    /** A host comparison alone would accept these. */
    @Test
    fun `lookalike hosts do not match`() {
        assertFalse(isServerOrigin("https://mochi-os.org.attacker.example/x".toHttpUrl(), server))
        assertFalse(isServerOrigin("https://notmochi-os.org/x".toHttpUrl(), server))
        assertFalse(isServerOrigin("https://sub.mochi-os.org/x".toHttpUrl(), server))
    }

    /** Downgrade: the session must not ride in cleartext. */
    @Test
    fun `scheme downgrade does not match`() {
        assertFalse(isServerOrigin("http://mochi-os.org/x".toHttpUrl(), server))
    }

    /** A different service on the same host is a different origin. */
    @Test
    fun `different port does not match`() {
        assertFalse(isServerOrigin("https://mochi-os.org:8443/x".toHttpUrl(), server))
    }

    /** Fail closed rather than guessing when the server url is unusable. */
    @Test
    fun `unusable server url matches nothing`() {
        assertFalse(isServerOrigin("https://mochi-os.org/x".toHttpUrl(), ""))
        assertFalse(isServerOrigin("https://mochi-os.org/x".toHttpUrl(), "not a url"))
        assertFalse(isServerOrigin("https://mochi-os.org/x".toHttpUrl(), "mochi-os.org"))
    }

    /**
     * Negative control: reproduces the host-only comparison to show the cases
     * above are not passing vacuously.
     */
    @Test
    fun `host-only comparison would have accepted the leaking cases`() {
        val hostOnly = { url: String ->
            url.toHttpUrl().host == server.toHttpUrl().host
        }
        // Accepted by the old logic, rejected by the new — these are the leak.
        assertTrue(hostOnly("http://mochi-os.org/x"))
        assertTrue(hostOnly("https://mochi-os.org:8443/x"))
        assertFalse(isServerOrigin("http://mochi-os.org/x".toHttpUrl(), server))
        assertFalse(isServerOrigin("https://mochi-os.org:8443/x".toHttpUrl(), server))
        // Rejected by both, so the third-party cases prove the gate exists at
        // all rather than merely being stricter about scheme and port.
        assertFalse(hostOnly("https://attacker.example/x.png"))
        assertFalse(isServerOrigin("https://attacker.example/x.png".toHttpUrl(), server))
    }
}
