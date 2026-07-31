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
 * stopped an unsolicited return being *accepted*, and accepting one is
 * destructive: the client consumes its verifier before the exchange and the
 * server deletes the ceremony row whether or not the verifier matches, so a
 * single injected `mochi:oauth-return?code=x` burns the ceremony and the user's
 * genuine return then fails with "missing verifier". Because the value is
 * persisted, an injected one survives process death and poisons the *next*
 * login attempt too.
 *
 * [hasVerifier] is the only local evidence we started this: it is written at
 * `/begin` and consumed at exchange.
 *
 * Partial by construction, and deliberately so. This rejects a return that
 * arrives when no ceremony is outstanding, which is the persistent case. It
 * cannot distinguish an injected return from the real one *during* an active
 * ceremony — that needs a nonce round-tripped through the server's redirect,
 * which the redirect does not currently carry (it sends only `code`/`error`
 * plus extras, core/server/oauth.go:1140-1152) and so needs a core change.
 */
fun shouldAcceptOAuthReturn(hasVerifier: Boolean, code: String?, error: String?): Boolean {
    if (code == null && error == null) return false
    return hasVerifier
}
