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
 * MainActivity is exported and BROWSABLE, so any app or web page can deliver a
 * mochi:oauth-return. Accepting an unsolicited one is destructive rather than
 * merely noisy: the client consumes its verifier before the exchange and the
 * server deletes the ceremony row regardless of whether the verifier matched,
 * so one injected return burns the ceremony — and because it is persisted, it
 * survives process death and poisons the next login attempt too.
 *
 * There is deliberately no link-return predicate here any more. The link flow's
 * marker could never be retired, because core's redirect_local drops the custom
 * scheme so no legitimate return ever arrived — which left the guard armed for
 * good after the first link attempt rather than for the length of a ceremony.
 * The link flow now carries the same machinery; see LoginViewModel.linkOAuth.
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
     * The case the nonce exists for, and the one the outstanding-ceremony check
     * alone could never catch: a forgery delivered DURING a live login. Without
     * the nonce this was accepted, the verifier was consumed before the bogus
     * exchange, and the genuine callback then failed with "missing verifier".
     */
    @Test
    fun `a return carrying the wrong nonce is refused mid-ceremony`() {
        assertFalse(shouldAcceptOAuthReturn(true, ours, "nonce-theirs", code = "abc", error = null))
    }

    /**
     * A forged ERROR is the cheaper attack — it needs no plausible exchange
     * code — so it must be held to the same check as a success, which is why
     * core echoes the nonce on the error branch too. The link flow, which
     * once had no return handling at all, now goes through this same gate.
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
     * Against a server that does not send a nonce — or a ceremony begun before
     * an upgrade, which lives for ten minutes — we hold none. Absence must fall
     * back to the outstanding-ceremony check rather than refusing, or a deploy
     * mid-ceremony locks the user out of finishing their login.
     */
    @Test
    fun `a ceremony with no nonce falls back to the outstanding check`() {
        assertTrue(shouldAcceptOAuthReturn(true, null, null, code = "abc", error = null))
        assertTrue(shouldAcceptOAuthReturn(true, "", null, code = "abc", error = null))
        assertTrue(shouldAcceptOAuthReturn(true, null, "unexpected", code = "abc", error = null))
        assertFalse(shouldAcceptOAuthReturn(false, null, null, code = "abc", error = null))
    }

    /**
     * The sign-in and link returns must stay distinct all the way from the
     * deep-link name. Collapsing them is not cosmetic: a link return handled
     * as a login is exchanged for a session, and a login return handled as a
     * link burns the link ceremony's verifier.
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
