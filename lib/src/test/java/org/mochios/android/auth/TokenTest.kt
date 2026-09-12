// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * The cache decision that keeps AuthRepository from minting a token on every
 * call: a cached JWT is reused while its `exp` is comfortably ahead of now.
 */
class TokenTest {

    private val now = 1_700_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun encode(json: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(Charsets.UTF_8))

    private fun jwt(payload: String): String =
        encode("""{"alg":"HS256","typ":"JWT"}""") + "." + encode(payload) + ".signature"

    @Test
    fun `expiry reads the exp claim in seconds and answers milliseconds`() {
        val token = jwt("""{"app":"feeds","exp":1700086400,"iat":1700000000}""")
        assertEquals(1_700_086_400_000L, Token.expiry(token))
    }

    @Test
    fun `expiry survives an unpadded payload whose length is not a multiple of four`() {
        for (filler in listOf("", "x", "xy", "xyz")) {
            val token = jwt("""{"n":"$filler","exp":1700086400}""")
            assertEquals("payload with filler '$filler'", 1_700_086_400_000L, Token.expiry(token))
        }
    }

    @Test
    fun `a token without three segments, without exp or with garbage has no expiry`() {
        assertNull(Token.expiry("not-a-jwt"))
        assertNull(Token.expiry(jwt("""{"app":"feeds"}""")))
        assertNull(Token.expiry("a.!!!.c"))
    }

    @Test
    fun `fresh needs more than the margin left`() {
        val token = jwt("""{"exp":${(now + 2 * day) / 1000}}""")
        assertTrue(Token.fresh(token, now, day))
        assertFalse(Token.fresh(token, now + day, day))
        assertFalse(Token.fresh(token, now + 3 * day, day))
    }

    @Test
    fun `nothing cached, a blank token or an unreadable one is never fresh`() {
        assertFalse(Token.fresh(null, now))
        assertFalse(Token.fresh("", now))
        assertFalse(Token.fresh("not-a-jwt", now))
    }
}
