// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * PKCE for the mobile OAuth flow: the challenge is base64url(sha256(verifier))
 * with no padding. Use the JDK encoder to match Go's `base64.RawURLEncoding`
 * exactly; `android.util.Base64` flag combinations do not.
 */
object OAuthPkce {

    private const val ALPHANUMERIC =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    fun generateVerifier(): String {
        val rng = SecureRandom()
        val sb = StringBuilder(64)
        repeat(64) { sb.append(ALPHANUMERIC[rng.nextInt(ALPHANUMERIC.length)]) }
        return sb.toString()
    }

    fun challengeFor(verifier: String): String {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }
}
