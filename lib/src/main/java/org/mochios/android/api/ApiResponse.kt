// Copyright © 2026 Mochisoft OÜ
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
 * A JSON body is the server's `{error, message}`, wrapped in [ApiException]
 * with its status. Anything else came from a proxy or gateway rather than
 * Mochi, so it becomes [MochiError.NetworkError] and the user gets the retry
 * screen.
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

/**
 * [unwrapRaw] for a no-content endpoint: an empty body deserialises to null,
 * which [unwrapRaw] rejects.
 */
fun Response<*>.unwrapEmpty() {
    if (!isSuccessful) throw errorForResponse(code(), errorBody()?.string())
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
