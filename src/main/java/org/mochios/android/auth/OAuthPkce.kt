package org.mochios.android.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * PKCE for the mobile OAuth flow. The app generates a 64-char alphanumeric
 * verifier, sends `sha256(verifier)` (base64url, no padding) as the challenge
 * in `/_/auth/oauth/<provider>/begin`, and presents the verifier back to
 * `/_/auth/oauth/exchange` after the deep-link return. The server's check is
 * `RawURLEncoding(sha256(verifier)) == storedChallenge` — encoding mismatches
 * here surface as `errors.exchange_verifier_mismatch` ("PKCE verifier does
 * not match challenge"), so we use the JDK Base64 URL encoder directly to
 * match Go's `base64.RawURLEncoding` byte-for-byte instead of relying on
 * `android.util.Base64` flag combinations.
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
