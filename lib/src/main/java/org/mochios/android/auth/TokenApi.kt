// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class TokenRequest(val app: String)

data class TokenResponse(
    val token: String = ""
)

/**
 * Mints a per-app JWT via POST /_/token. Authorised by the session cookie on
 * the shared OkHttpClient's CookieJar — the same pattern the web shell uses to
 * issue tokens to its iframe SPAs. Kept separate from [AuthApi] because token
 * minting is a cross-cutting concern, not part of the login/passkey/OAuth flow.
 */
interface TokenApi {

    @POST("_/token")
    suspend fun fetchToken(@Body body: TokenRequest): Response<TokenResponse>
}
