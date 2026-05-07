package org.mochios.android.auth

import android.content.Context
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class PasskeyCredentialResult(
    val id: String,
    val rawId: String,
    val type: String,
    val clientDataJSON: String,
    val authenticatorData: String,
    val signature: String
)

@Singleton
class PasskeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val credentialManager = CredentialManager.create(context)

    suspend fun authenticate(options: JsonObject): PasskeyCredentialResult {
        val requestJson = options.toString()
        val publicKeyOption = GetPublicKeyCredentialOption(
            requestJson = requestJson
        )
        val request = GetCredentialRequest(
            credentialOptions = listOf(publicKeyOption)
        )
        val response = credentialManager.getCredential(
            context = context,
            request = request
        )
        return extractCredential(response)
    }

    private fun extractCredential(response: GetCredentialResponse): PasskeyCredentialResult {
        val credential = response.credential
        if (credential !is PublicKeyCredential) {
            throw IllegalStateException("Expected PublicKeyCredential but got ${credential.type}")
        }

        val responseJson = com.google.gson.JsonParser.parseString(
            credential.authenticationResponseJson
        ).asJsonObject

        val id = responseJson.get("id").asString
        val rawId = responseJson.get("rawId").asString
        val type = responseJson.get("type").asString

        val responseObj = responseJson.getAsJsonObject("response")
        val clientDataJSON = responseObj.get("clientDataJSON").asString
        val authenticatorData = responseObj.get("authenticatorData").asString
        val signature = responseObj.get("signature").asString

        return PasskeyCredentialResult(
            id = id,
            rawId = rawId,
            type = type,
            clientDataJSON = clientDataJSON,
            authenticatorData = authenticatorData,
            signature = signature
        )
    }
}
