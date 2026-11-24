// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * What a mint asks for: [app], the path of the page the token is for, or
 * [service], the service whose app it is for. A server that reads [service]
 * resolves by it and ignores [app]; one that predates it ignores [service] and
 * resolves [app] as a path, so a request by service names both. Null is left
 * out of the body.
 */
data class TokenRequest(val app: String, val service: String? = null)

/**
 * A mint's answer: [token], the app's year-long JWT, and [asset], a JWT good
 * for half an hour that core honours only to read the app's asset actions;
 * empty from a server that issues none.
 */
data class TokenResponse(
    val token: String = "",
    val asset: String = "",
)

/**
 * Mints a per-app JWT via POST /_/token, authorised by the session cookie on
 * the shared CookieJar.
 */
interface TokenApi {

    @POST("_/token")
    suspend fun fetchToken(@Body body: TokenRequest): Response<TokenResponse>
}
