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
        assertTrue(shouldAcceptOAuthLinkReturn(pending = true, provider = "google", error = null))
        assertTrue(shouldAcceptOAuthLinkReturn(pending = true, provider = null, error = "denied"))
    }

    /** The injection: a fabricated success or failure on the security page. */
    @Test
    fun `a link return with no ceremony outstanding is refused`() {
        assertFalse(shouldAcceptOAuthLinkReturn(pending = false, provider = "google", error = null))
        assertFalse(shouldAcceptOAuthLinkReturn(pending = false, provider = null, error = "denied"))
    }

    @Test
    fun `an empty link return is refused either way`() {
        assertFalse(shouldAcceptOAuthLinkReturn(pending = true, provider = null, error = null))
        assertFalse(shouldAcceptOAuthLinkReturn(pending = false, provider = null, error = null))
    }
}
