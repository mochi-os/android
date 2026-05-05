package org.mochi.android.api

import org.mochi.android.R
import org.mochi.android.i18n.AppContext
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed class MochiError : Exception() {
    data class NetworkError(override val cause: Throwable? = null) : MochiError()
    data class AuthError(override val message: String? = null) : MochiError()
    data class ForbiddenError(override val message: String? = null) : MochiError()
    data class NotFoundError(override val message: String? = null) : MochiError()
    data class ServerError(val code: Int, override val message: String? = null) : MochiError()
    data class Unknown(override val message: String? = null) : MochiError()
}

fun Throwable.toMochiError(): MochiError {
    return when (this) {
        is MochiError -> this
        is ApiException -> when (code) {
            401 -> MochiError.AuthError(apiError.message ?: apiError.error)
            403 -> MochiError.ForbiddenError(apiError.message ?: apiError.error)
            404 -> MochiError.NotFoundError(apiError.message ?: apiError.error)
            in 500..599 -> MochiError.ServerError(code, apiError.message ?: apiError.error)
            else -> MochiError.Unknown(apiError.message ?: apiError.error)
        }
        is HttpException -> when (code()) {
            401 -> MochiError.AuthError()
            403 -> MochiError.ForbiddenError()
            404 -> MochiError.NotFoundError()
            in 500..599 -> MochiError.ServerError(code(), message())
            else -> MochiError.Unknown(message())
        }
        is UnknownHostException -> MochiError.NetworkError(this)
        is SocketTimeoutException -> MochiError.NetworkError(this)
        is IOException -> MochiError.NetworkError(this)
        else -> MochiError.Unknown(message)
    }
}

/**
 * Resolve a [MochiError] to a localised user-facing message.
 *
 * Server-supplied messages (in [MochiError.AuthError.message] etc.) are
 * returned as-is — the server is responsible for localising them via the
 * `respond_error` labels system. Only the fallback strings, when the server
 * didn't supply text, come from the Android resource catalog. The catalog
 * lookup uses [AppContext], whose locale was applied in
 * `Application.attachBaseContext`.
 */
fun MochiError.userMessage(): String {
    val ctx = AppContext.get()
    return when (this) {
        is MochiError.NetworkError -> ctx.getString(R.string.error_network)
        is MochiError.AuthError -> message ?: ctx.getString(R.string.error_authentication_required)
        is MochiError.ForbiddenError -> message ?: ctx.getString(R.string.error_access_denied)
        is MochiError.NotFoundError -> message ?: ctx.getString(R.string.error_not_found)
        is MochiError.ServerError -> ctx.getString(R.string.error_server, code)
        is MochiError.Unknown -> message ?: ctx.getString(R.string.error_unexpected)
    }
}
