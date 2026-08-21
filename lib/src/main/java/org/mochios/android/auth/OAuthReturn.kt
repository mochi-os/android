// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

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
 * Which OAuth ceremony a `mochi:` deep-link name belongs to, or null. Sign-in
 * and link are separate names gated on separate stored ceremonies: either fed
 * to the other's handler would burn or misuse that ceremony.
 */
enum class OAuthReturnKind { LOGIN, LINK }

fun oauthReturnKind(name: String): OAuthReturnKind? = when (name) {
    "oauth-return" -> OAuthReturnKind.LOGIN
    "oauth-link-return" -> OAuthReturnKind.LINK
    else -> null
}
