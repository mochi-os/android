// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.api

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The passkey and linked-account lists read the server's responses as it
 * sends them: a passkey's transports come as one string, which a list type
 * failed on and took the whole Login & security screen down with it.
 */
class PasskeysResponseTest {

    private val gson = Gson()

    @Test
    fun `the server's own passkey list parses, transports as one string`() {
        val response = gson.fromJson(
            """{"passkeys": [{"created": 1781168284, "id": "DRAzRCNX3y6PinbMHNl_NWqIxb0=",
                "last_used": 0, "name": "Iphone", "transports": "hybrid,internal"}]}""",
            PasskeysResponse::class.java,
        )
        val iphone = Passkey(
            id = "DRAzRCNX3y6PinbMHNl_NWqIxb0=",
            name = "Iphone",
            transports = "hybrid,internal",
            created = 1781168284,
        )
        assertEquals(listOf(iphone), response.passkeys)
    }

    @Test
    fun `a passkey without transports has none`() {
        val response = gson.fromJson(
            """{"passkeys": [{"id": "p1", "name": "Pixel", "created": 1791306000,
                "last_used": 1791392400}]}""",
            PasskeysResponse::class.java,
        )
        val pixel = Passkey(id = "p1", name = "Pixel", created = 1791306000, lastUsed = 1791392400)
        assertEquals(listOf(pixel), response.passkeys)
    }

    @Test
    fun `the server's own linked accounts parse with when each was last used`() {
        val response = gson.fromJson(
            """{"identities": [{"created": 1781172738, "email": "person@example.org",
                "name": "Person", "provider": "google", "used": 1791368862}]}""",
            OAuthIdentitiesResponse::class.java,
        )
        val google = OAuthIdentity(
            provider = "google",
            email = "person@example.org",
            name = "Person",
            created = 1781172738,
            used = 1791368862,
        )
        assertEquals(listOf(google), response.identities)
    }
}
