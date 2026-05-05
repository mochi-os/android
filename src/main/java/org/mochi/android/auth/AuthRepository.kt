package org.mochi.android.auth

import org.mochi.android.api.unwrapRaw
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthResult {
    data object Success : AuthResult()
    data object NeedsIdentity : AuthResult()
    data class NeedsMfa(
        val partial: String,
        val remaining: List<String>
    ) : AuthResult()
}

data class BeginResult(
    val methods: List<String>,
    val hasPasskey: Boolean,
    val isNew: Boolean
)

data class PasskeyChallenge(
    val options: com.google.gson.JsonObject,
    val ceremony: String
)

data class Identity(
    val userId: Int,
    val name: String,
    val fingerprint: String
)

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val sessionManager: SessionManager
) {
    suspend fun beginLogin(email: String): BeginResult {
        val response = authApi.begin(EmailRequest(email)).unwrapRaw()
        return BeginResult(
            methods = response.methods,
            hasPasskey = response.hasPasskey,
            isNew = response.new
        )
    }

    suspend fun requestCode(email: String) {
        authApi.requestCode(EmailRequest(email)).unwrapRaw()
    }

    suspend fun verifyCode(code: String): AuthResult {
        val response = authApi.verify(CodeRequest(code))
        extractSessionCookie(response)
        val data = response.unwrapRaw()
        return mapVerifyResponse(data)
    }

    suspend fun verifyTotp(email: String, code: String): AuthResult {
        val response = authApi.verifyTotp(TotpRequest(email, code))
        extractSessionCookie(response)
        val data = response.unwrapRaw()
        return mapVerifyResponse(data)
    }

    suspend fun completeMfa(partial: String, emailCode: String?, totpCode: String?): AuthResult {
        val response = authApi.completeMfa(MfaRequest(partial, emailCode, totpCode))
        extractSessionCookie(response)
        val data = response.unwrapRaw()
        return mapVerifyResponse(data)
    }

    suspend fun beginPasskey(): PasskeyChallenge {
        val response = authApi.passkeyBegin().unwrapRaw()
        return PasskeyChallenge(
            options = response.options,
            ceremony = response.ceremony
        )
    }

    suspend fun finishPasskey(
        ceremony: String,
        id: String,
        rawId: String,
        type: String,
        clientDataJSON: String,
        authenticatorData: String,
        signature: String
    ): AuthResult {
        val response = authApi.passkeyFinish(
            PasskeyFinishRequest(
                ceremony = ceremony,
                id = id,
                rawId = rawId,
                type = type,
                response = PasskeyResponseData(
                    clientDataJSON = clientDataJSON,
                    authenticatorData = authenticatorData,
                    signature = signature
                )
            )
        )
        extractSessionCookie(response)
        val data = response.unwrapRaw()
        return mapVerifyResponse(data)
    }

    suspend fun verifyRecoveryCode(email: String, code: String): AuthResult {
        val response = authApi.verifyRecoveryCode(RecoveryRequest(email, code))
        extractSessionCookie(response)
        val data = response.unwrapRaw()
        return mapVerifyResponse(data)
    }

    suspend fun fetchToken(app: String): String {
        val response = authApi.fetchToken(TokenRequest(app)).unwrapRaw()
        sessionManager.saveToken(app, response.token)
        return response.token
    }

    suspend fun createIdentity(name: String) {
        authApi.createIdentity(IdentityRequest(name)).unwrapRaw()
    }

    suspend fun getIdentity(): Identity {
        val response = authApi.getIdentity().unwrapRaw()
        return Identity(
            userId = response.user,
            name = response.name,
            fingerprint = response.fingerprint
        )
    }

    suspend fun getAvailableMethods(): List<String> {
        val response = authApi.getAvailableMethods().unwrapRaw()
        return response.methods
    }

    private fun mapVerifyResponse(data: VerifyResponse): AuthResult {
        if (data.mfa == true && data.partial != null && data.remaining != null) {
            return AuthResult.NeedsMfa(
                partial = data.partial,
                remaining = data.remaining
            )
        }
        if (!data.hasIdentity) {
            return AuthResult.NeedsIdentity
        }
        return AuthResult.Success
    }

    private fun <T> extractSessionCookie(response: retrofit2.Response<T>) {
        val setCookie = response.headers().values("Set-Cookie")
        for (cookie in setCookie) {
            if (cookie.startsWith("session=")) {
                val value = cookie.substringAfter("session=").substringBefore(";")
                kotlinx.coroutines.runBlocking {
                    sessionManager.saveSession(value)
                }
                break
            }
        }
    }
}
