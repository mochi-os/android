// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

import java.util.Base64

/**
 * Reads the expiry of a per-app JWT so a cached token can be reused instead of
 * minted again. The server signs the token; nothing here verifies it - the
 * payload is read only to decide whether asking for a new one is worthwhile,
 * and a token the server has since rejected is cleared by the 401 handling on
 * the request that used it.
 */
object Token {
    /** How much life a cached token must have left to be handed out without a mint. */
    const val MARGIN: Long = 24L * 60 * 60 * 1000

    private val expiryClaim = Regex("\"exp\"\\s*:\\s*(\\d+)")

    /** The `exp` claim as epoch milliseconds, or null when the token does not carry a readable one. */
    fun expiry(jwt: String): Long? {
        val parts = jwt.split('.')
        if (parts.size != 3) return null
        val payload = try {
            String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val seconds = expiryClaim.find(payload)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        return seconds * 1000
    }

    /** Whether [jwt] can be reused at [now]: it has an expiry, and more than [margin] of it is left. */
    fun fresh(jwt: String?, now: Long, margin: Long = MARGIN): Boolean {
        if (jwt.isNullOrBlank()) return false
        val expiry = expiry(jwt) ?: return false
        return expiry - now > margin
    }
}
