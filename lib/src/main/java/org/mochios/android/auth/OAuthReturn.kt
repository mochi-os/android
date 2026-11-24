// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gate for a `mochi:oauth-return`: the exported `mochi:` filter lets anyone
 * deliver one, and accepting an injected return burns the outstanding ceremony.
 * Require an outstanding verifier, and a nonce match when one is held (null
 * means older server).
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

/**
 * Which OAuth ceremony a `mochi:` deep-link name belongs to, or null. Sign-in,
 * link, grant and step-up are separate names gated on separate ceremonies:
 * any fed to another's handler would burn or misuse that ceremony.
 */
enum class OAuthReturnKind { LOGIN, LINK, GRANT, VERIFY }

fun oauthReturnKind(name: String): OAuthReturnKind? = when (name) {
    "oauth-return" -> OAuthReturnKind.LOGIN
    "oauth-link-return" -> OAuthReturnKind.LINK
    "oauth-grant-return" -> OAuthReturnKind.GRANT
    "oauth-verify-return" -> OAuthReturnKind.VERIFY
    else -> null
}

/**
 * The return of a step-up's OAuth consent. The browser the consent ran in
 * holds no session of ours, so the server proves nothing there: it hands
 * `mochi:oauth-verify-return` a one-time code, which the step-up redeems from
 * the app's own session. Held in memory, as the step-up's verifier is: neither
 * outlives the process, and a step-up cut short by its end is begun again.
 */
@Singleton
class OAuthVerifyReturn @Inject constructor() {
    private var nonce: String? = null
    private var returned: Pair<String?, String?>? = null

    /** A step-up began, and its return will carry [nonce]. */
    @Synchronized
    fun expect(nonce: String) {
        this.nonce = nonce.ifBlank { null }
        returned = null
    }

    /**
     * Take a delivered return: false, and nothing kept, unless it carries the
     * nonce of the step-up waiting for one. The `mochi:` filter is exported,
     * so anyone can deliver one, and a step-up takes the first it is handed.
     */
    @Synchronized
    fun deliver(code: String?, error: String?, nonce: String?): Boolean {
        val expected = this.nonce ?: return false
        if (!shouldAcceptOAuthReturn(true, expected, nonce, code, error)) return false
        this.nonce = null
        returned = code to error
        return true
    }

    /** The code and error delivered since [expect], once; null until one comes. */
    @Synchronized
    fun take(): Pair<String?, String?>? = returned.also { returned = null }
}
