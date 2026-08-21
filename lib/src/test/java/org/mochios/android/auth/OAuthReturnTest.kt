// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MainActivity is exported and BROWSABLE, so anything can deliver a
 * mochi:oauth-return. An unsolicited one is destructive, not merely noisy: it
 * burns the verifier and the ceremony row, and that state persists into the
 * next login attempt.
 */
class OAuthReturnTest {

    private val ours = "nonce-ours"

    @Test
    fun `a return carrying our nonce is accepted`() {
        assertTrue(shouldAcceptOAuthReturn(true, ours, ours, code = "abc", error = null))
        assertTrue(shouldAcceptOAuthReturn(true, ours, ours, code = null, error = "access_denied"))
    }

    /** The persistent injection: nothing in progress, so nothing to return to. */
    @Test
    fun `a return with no ceremony outstanding is refused`() {
        assertFalse(shouldAcceptOAuthReturn(false, ours, ours, code = "abc", error = null))
        assertFalse(shouldAcceptOAuthReturn(false, ours, ours, code = null, error = "access_denied"))
    }

    @Test
    fun `an empty return is refused either way`() {
        assertFalse(shouldAcceptOAuthReturn(true, ours, ours, code = null, error = null))
        assertFalse(shouldAcceptOAuthReturn(false, ours, ours, code = null, error = null))
    }

    /**
     * The forgery the outstanding-ceremony check cannot catch: delivered during
     * a live login.
     */
    @Test
    fun `a return carrying the wrong nonce is refused mid-ceremony`() {
        assertFalse(shouldAcceptOAuthReturn(true, ours, "nonce-theirs", code = "abc", error = null))
    }

    /**
     * A forged error needs no exchange code, so core echoes the nonce on that
     * branch too.
     */
    @Test
    fun `a forged error is refused mid-ceremony`() {
        assertFalse(shouldAcceptOAuthReturn(true, ours, "nonce-theirs", code = null, error = "access_denied"))
        assertFalse(shouldAcceptOAuthReturn(true, ours, null, code = null, error = "access_denied"))
    }

    /** Carrying no nonce at all is not a way to skip the check. */
    @Test
    fun `a return omitting the nonce is refused when we hold one`() {
        assertFalse(shouldAcceptOAuthReturn(true, ours, null, code = "abc", error = null))
        assertFalse(shouldAcceptOAuthReturn(true, ours, "", code = "abc", error = null))
    }

    /**
     * A server that sends no nonce, or a ceremony begun before an upgrade, must
     * fall back to the outstanding check - refusing locks out a live login.
     */
    @Test
    fun `a ceremony with no nonce falls back to the outstanding check`() {
        assertTrue(shouldAcceptOAuthReturn(true, null, null, code = "abc", error = null))
        assertTrue(shouldAcceptOAuthReturn(true, "", null, code = "abc", error = null))
        assertTrue(shouldAcceptOAuthReturn(true, null, "unexpected", code = "abc", error = null))
        assertFalse(shouldAcceptOAuthReturn(false, null, null, code = "abc", error = null))
    }

    /**
     * Login and link returns must stay distinct: a link return handled as a
     * login is exchanged for a session, and the reverse burns the link's
     * verifier.
     */
    @Test
    fun `the two OAuth returns are routed apart`() {
        assertEquals(OAuthReturnKind.LOGIN, oauthReturnKind("oauth-return"))
        assertEquals(OAuthReturnKind.LINK, oauthReturnKind("oauth-link-return"))
        assertNotEquals(oauthReturnKind("oauth-return"), oauthReturnKind("oauth-link-return"))
    }

    /** Anything else is not an OAuth return and must not reach either handler. */
    @Test
    fun `other deep-link names are not OAuth returns`() {
        assertNull(oauthReturnKind("notification"))
        assertNull(oauthReturnKind(""))
        assertNull(oauthReturnKind("oauth"))
        assertNull(oauthReturnKind("oauth-return-x"))
    }
}
