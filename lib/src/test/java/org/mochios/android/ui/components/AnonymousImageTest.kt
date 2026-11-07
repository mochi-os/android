// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnonymousImageTest {

    private val server = "https://mochi.example"

    @Test
    fun resolve_serverRelativePath() {
        assertEquals(
            "https://mochi.example/someapp/-/some-action",
            resolveAnonymousImage("/someapp/-/some-action", server),
        )
    }

    @Test
    fun resolve_absoluteUrlUnchanged() {
        assertEquals(
            "https://cdn.example/a.png",
            resolveAnonymousImage("https://cdn.example/a.png", server),
        )
    }

    @Test
    fun resolve_schemeRelativeTakesServerScheme() {
        assertEquals(
            "https://cdn.example/a.png",
            resolveAnonymousImage("//cdn.example/a.png", server),
        )
    }

    @Test
    fun resolve_nonWebSchemeRefused() {
        assertNull(resolveAnonymousImage("file:///data/data/x/secret.png", server))
        assertNull(resolveAnonymousImage("content://contacts/photo", server))
    }

    @Test
    fun resolve_noServerRefused() {
        assertNull(resolveAnonymousImage("/a.png", ""))
    }
}
