// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.api

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The asset client authenticates by header alone. It used to copy the app JWT
 * into `?token=` for avatar routes, first for any URL whose last segment was
 * "avatar" - which reached whichever action an author-supplied image URL named -
 * and then for the person route only. Core answers both 200 with no redirect,
 * so the copy bought nothing and wrote a year-long credential into every access
 * log on the way. These assert it never comes back.
 */
class AssetTokenTest {

    private fun sent(url: String, token: String? = "jwt.body.sig") =
        authorised(Request.Builder().url(url).build(), token)

    @Test
    fun `the url is untouched, whatever the route`() {
        for (url in listOf(
            "https://mochi-os.org/people/1abc/-/avatar",
            "https://mochi-os.org/people/1abc/-/avatar?v=3",
            "https://mochi-os.org/market/-/user/1abc/asset/avatar",
            "https://mochi-os.org/feeds/1abc/-/export/avatar",
            "https://mochi-os.org/wikis/1abc/-/attachment/9",
        )) {
            val request = sent(url)
            assertEquals("the url must be sent as given: $url", url, request.url.toString())
            assertNull("no token parameter: ${request.url}", request.url.queryParameter("token"))
            assertFalse(
                "no credential anywhere in the url: ${request.url}",
                request.url.toString().contains("jwt.body.sig"),
            )
        }
    }

    @Test
    fun `the token rides the authorization header`() {
        assertEquals(
            "Bearer jwt.body.sig",
            sent("https://mochi-os.org/people/1abc/-/avatar").header("Authorization"),
        )
    }

    @Test
    fun `no token leaves the request alone`() {
        val request = sent("https://mochi-os.org/people/1abc/-/avatar", token = null)
        assertNull(request.header("Authorization"))
        assertEquals("https://mochi-os.org/people/1abc/-/avatar", request.url.toString())
    }
}
