// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MainActivity is exported and BROWSABLE, so any app or web page can deliver a
 * mochi:oauth-return. Accepting an unsolicited one is destructive rather than
 * merely noisy: the client consumes its verifier before the exchange and the
 * server deletes the ceremony row regardless of whether the verifier matched,
 * so one injected return burns the ceremony — and because it is persisted, it
 * survives process death and poisons the next login attempt too.
 */
class OAuthReturnTest {

    @Test
    fun `a return is accepted while our own ceremony is outstanding`() {
        assertTrue(shouldAcceptOAuthReturn(hasVerifier = true, code = "abc", error = null))
        assertTrue(shouldAcceptOAuthReturn(hasVerifier = true, code = null, error = "access_denied"))
    }

    /** The injection: nothing in progress, so nothing to return to. */
    @Test
    fun `a return with no ceremony outstanding is refused`() {
        assertFalse(shouldAcceptOAuthReturn(hasVerifier = false, code = "abc", error = null))
        assertFalse(shouldAcceptOAuthReturn(hasVerifier = false, code = null, error = "access_denied"))
    }

    @Test
    fun `an empty return is refused either way`() {
        assertFalse(shouldAcceptOAuthReturn(hasVerifier = true, code = null, error = null))
        assertFalse(shouldAcceptOAuthReturn(hasVerifier = false, code = null, error = null))
    }

    @Test
    fun `a link return is accepted while our own link ceremony is outstanding`() {
        assertTrue(shouldAcceptOAuthLinkReturn(pending = "google", provider = "google", error = null))
        assertTrue(shouldAcceptOAuthLinkReturn(pending = "google", provider = null, error = "denied"))
    }

    /** The injection: a fabricated success or failure on the security page. */
    @Test
    fun `a link return with no ceremony outstanding is refused`() {
        assertFalse(shouldAcceptOAuthLinkReturn(pending = null, provider = "google", error = null))
        assertFalse(shouldAcceptOAuthLinkReturn(pending = null, provider = null, error = "denied"))
    }

    /**
     * The empty return is the one that matters for call order. It is refused,
     * and because the caller reads the marker without consuming and retires it
     * only after this returns true, being refused means the live ceremony
     * survives — where a consume-then-decide check would have burned it and
     * silently dropped the genuine callback that followed.
     */
    @Test
    fun `an empty link return is refused, outstanding ceremony or not`() {
        assertFalse(shouldAcceptOAuthLinkReturn(pending = "google", provider = null, error = null))
        assertFalse(shouldAcceptOAuthLinkReturn(pending = null, provider = null, error = null))
    }

    /** A success naming a provider we did not start is somebody else's. */
    @Test
    fun `a link return for a different provider is refused`() {
        assertFalse(shouldAcceptOAuthLinkReturn(pending = "google", provider = "github", error = null))
    }
}
