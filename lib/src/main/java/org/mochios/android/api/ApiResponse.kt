// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.api

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import retrofit2.HttpException
import retrofit2.Response

data class ApiResponse<T>(
    val data: T
)

data class ApiError(
    val error: String? = null,
    val message: String? = null
)

/**
 * Build the throwable for a failed HTTP response.
 *
 * A JSON object body is the Mochi server's structured `{error, message}` error
 * (from `respond_error`) — wrapped in [ApiException] with the status code so
 * code-aware handling (401 re-auth, the wikis 502 "remote unreachable" branch,
 * etc.) keeps working.
 *
 * A non-JSON body, however, is NOT a Mochi API response: it's an HTML error page
 * from a reverse proxy / gateway / CDN (e.g. a "502 Bad Gateway" when the backend
 * is unreachable), the server's framework-level unhandled-error page, or a wrong
 * endpoint (app/server version skew). Previously the entire HTML document was
 * stuffed into the error message and rendered verbatim — an ugly web-page-like
 * error screen with red text. Since these mean "we're not talking to a healthy
 * Mochi server", they're surfaced as a [MochiError.NetworkError] so the user
 * gets the proper "can't reach the server" screen with a Retry button.
 */
private fun errorForResponse(code: Int, errorBody: String?): Throwable {
    val body = errorBody?.trimStart()
    if (body.isNullOrEmpty() || !body.startsWith("{")) return MochiError.NetworkError()
    val apiError = try {
        Gson().fromJson(body, ApiError::class.java) ?: ApiError()
    } catch (e: com.google.gson.JsonSyntaxException) {
        return MochiError.NetworkError()
    }
    return ApiException(code = code, apiError = apiError)
}

fun <T> Response<ApiResponse<T>>.unwrap(): T {
    if (isSuccessful) {
        return body()?.data ?: throw IllegalStateException("Response body is null")
    }
    throw errorForResponse(code(), errorBody()?.string())
}

fun <T> Response<T>.unwrapRaw(): T {
    if (isSuccessful) {
        return body() ?: throw IllegalStateException("Response body is null")
    }
    throw errorForResponse(code(), errorBody()?.string())
}

fun HttpException.extractApiError(): ApiError {
    val body = response()?.errorBody()?.string()?.trimStart()
    if (body.isNullOrEmpty() || !body.startsWith("{")) return ApiError()
    return try {
        Gson().fromJson(body, ApiError::class.java) ?: ApiError()
    } catch (e: com.google.gson.JsonSyntaxException) {
        ApiError()
    }
}

class ApiException(
    val code: Int,
    val apiError: ApiError
) : Exception(apiError.message ?: apiError.error ?: "API error ($code)")
