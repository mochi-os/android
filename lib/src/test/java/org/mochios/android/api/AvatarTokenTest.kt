// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.api

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The asset client copies the app JWT into `?token=` for the one route whose
 * redirect drops the Authorization header. Anything wider puts the bearer token
 * in the query string of whichever action an author-supplied image URL names.
 */
class AvatarTokenTest {

    private fun personAvatar(url: String) = isPersonAvatar(url.toHttpUrl())

    @Test
    fun `the person avatar route takes the token`() {
        assertTrue(personAvatar("https://mochi-os.org/people/1abc/-/avatar"))
        assertTrue(personAvatar("https://mochi-os.org/people/1abc/-/avatar?v=3"))
    }

    @Test
    fun `another action whose last segment is avatar does not`() {
        assertFalse(personAvatar("https://mochi-os.org/feeds/1abc/-/export/avatar"))
        assertFalse(personAvatar("https://mochi-os.org/wikis/1abc/-/attachment/avatar"))
        assertFalse(personAvatar("https://mochi-os.org/avatar"))
        assertFalse(personAvatar("https://mochi-os.org/people/1abc/-/style"))
        assertFalse(personAvatar("https://mochi-os.org/people/1abc/-/avatar/set"))
    }

    /**
     * Negative control: the rule this replaced was "last segment is avatar", so
     * every foreign action above took the token.
     */
    @Test
    fun `the last-segment rule matched those foreign actions`() {
        for (url in listOf(
            "https://mochi-os.org/feeds/1abc/-/export/avatar",
            "https://mochi-os.org/wikis/1abc/-/attachment/avatar",
            "https://mochi-os.org/avatar",
        )) {
            assertTrue(
                "reproduces the bug for $url",
                url.toHttpUrl().pathSegments.lastOrNull() == "avatar",
            )
        }
    }
}
