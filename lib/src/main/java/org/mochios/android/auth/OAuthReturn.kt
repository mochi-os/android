// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

/**
 * Whether a `mochi:oauth-return` should be acted on.
 *
 * MainActivity is exported with a broad `mochi:` filter and BROWSABLE, so any
 * app or web page can deliver one of these. PKCE stops account confusion — the
 * server compares sha256(verifier) against the stored challenge — but nothing
 * stops an unsolicited return being *accepted*, and accepting one is
 * destructive: the client consumes its verifier before the exchange and the
 * server deletes the ceremony row whether or not the verifier matches, so a
 * single injected `mochi:oauth-return?code=x` burns the ceremony and the user's
 * genuine return then fails with "missing verifier". Because the value is
 * persisted, an injected one survives process death and poisons the *next*
 * login attempt too.
 *
 * Two independent checks, because they fail in different situations:
 *
 * [hasVerifier] rejects a return arriving when no ceremony is outstanding. It
 * is written at `/begin` and consumed at exchange.
 *
 * [expected] vs [returned] rejects a return arriving DURING an active ceremony
 * that did not come from the server we started it with. The nonce is minted per
 * ceremony by core, handed to this client in the `/begin` response, and echoed
 * on the deep-link redirect — on the error branch as well as the success one,
 * since a forged error needs no plausible code and is the cheaper forgery.
 *
 * [expected] is null against a server that predates the nonce, and against a
 * ceremony begun before an upgrade. Absence is therefore NOT treated as a
 * failure — doing so would lock out every in-flight ceremony across a deploy —
 * but when we hold one, a return must carry it and match. Note that a supplied
 * [returned] is ignored when we hold nothing to compare it against: an
 * attacker cannot downgrade us by inventing a nonce, only by our not having
 * one.
 *
 * RELEASE ORDER, and it is load-bearing: deploy CORE FIRST, then this client.
 * The fallback is what keeps a deploy from stranding in-flight logins, and
 * against an older core it is the only branch that ever runs — so a client
 * released ahead of its server has this protection on paper and none of it in
 * practice, for every login rather than for one ceremony lifetime. Core's
 * ceremonies last ten minutes, so that is the settling time between the two
 * deploys.
 */
fun shouldAcceptOAuthReturn(
    hasVerifier: Boolean,
    expected: String?,
    returned: String?,
    code: String?,
    error: String?,
): Boolean {
    if (code == null && error == null) return false
    if (!hasVerifier) return false
    if (expected.isNullOrEmpty()) return true
    return expected == returned
}
