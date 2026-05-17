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

fun <T> Response<ApiResponse<T>>.unwrap(): T {
    if (isSuccessful) {
        return body()?.data ?: throw IllegalStateException("Response body is null")
    }
    val errorBody = errorBody()?.string()
    if (errorBody != null) {
        try {
            val apiError = Gson().fromJson(errorBody, ApiError::class.java)
            throw ApiException(
                code = code(),
                apiError = apiError
            )
        } catch (e: com.google.gson.JsonSyntaxException) {
            throw ApiException(code = code(), apiError = ApiError(error = errorBody))
        }
    }
    throw ApiException(code = code(), apiError = ApiError())
}

fun <T> Response<T>.unwrapRaw(): T {
    if (isSuccessful) {
        return body() ?: throw IllegalStateException("Response body is null")
    }
    val errorBody = errorBody()?.string()
    if (errorBody != null) {
        try {
            val apiError = Gson().fromJson(errorBody, ApiError::class.java)
            throw ApiException(code = code(), apiError = apiError)
        } catch (e: com.google.gson.JsonSyntaxException) {
            throw ApiException(code = code(), apiError = ApiError(error = errorBody))
        }
    }
    throw ApiException(code = code(), apiError = ApiError())
}

fun HttpException.extractApiError(): ApiError {
    val errorBody = response()?.errorBody()?.string()
    if (errorBody != null) {
        return try {
            Gson().fromJson(errorBody, ApiError::class.java)
        } catch (e: com.google.gson.JsonSyntaxException) {
            ApiError(error = errorBody)
        }
    }
    return ApiError(error = message())
}

class ApiException(
    val code: Int,
    val apiError: ApiError
) : Exception(apiError.message ?: apiError.error ?: "API error ($code)")
