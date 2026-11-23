// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mochios.android.api.OwnedAddress

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

    @Test
    fun model_bodyImageWithNoMapperIsAnonymous() {
        assertEquals(AnonymousImage("/someapp/-/some-action"), imageModel("/someapp/-/some-action") { null })
        assertEquals("data:image/png;base64,AAAA", imageModel("data:image/png;base64,AAAA") { null })
    }

    @Test
    fun model_callerMapsItsOwnDestinationsAndNothingElse() {
        val own = OwnedAddress.of("/wikis/w1/-/attachments/a1/thumbnail")
        val model: (String) -> Any? = { destination -> if (destination == "attachments/a1/thumbnail") own else null }
        assertEquals(own, imageModel("attachments/a1/thumbnail", model))
        assertEquals(AnonymousImage("https://cdn.example/a.png"), imageModel("https://cdn.example/a.png", model))
        // An inline image needs no request, whatever the caller maps.
        assertEquals("data:image/png;base64,AAAA", imageModel("data:image/png;base64,AAAA") { own })
    }
}
