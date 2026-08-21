// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.auth

import android.content.Context
import android.util.Base64
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
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
    @param:ApplicationContext private val context: Context
) {
    private val credentialManager = CredentialManager.create(context)

    /**
     * WebAuthn registration ceremony. Takes the `passkey/register/begin`
     * options JSON and returns the registrationResponseJson to post as
     * `credential` to `passkey/register/finish`.
     */
    suspend fun register(options: JsonObject): String {
        val request = CreatePublicKeyCredentialRequest(requestJson = options.toString())
        val response: CreateCredentialResponse = credentialManager.createCredential(
            context = context,
            request = request,
        )
        if (response !is CreatePublicKeyCredentialResponse) {
            throw IllegalStateException("Unexpected create-credential response: ${response.type}")
        }
        return response.registrationResponseJson
    }

    suspend fun authenticate(options: JsonObject): PasskeyCredentialResult {
        return extractCredential(getCredential(options))
    }

    /**
     * WebAuthn assertion ceremony; returns the authenticationResponseJson that
     * step-up `passkey/verify/finish` takes as `assertion`.
     */
    suspend fun authenticateRaw(options: JsonObject): String {
        val response = getCredential(options)
        val credential = response.credential
        if (credential !is PublicKeyCredential) {
            throw IllegalStateException("Expected PublicKeyCredential but got ${credential.type}")
        }
        return credential.authenticationResponseJson
    }

    private suspend fun getCredential(options: JsonObject): GetCredentialResponse {
        val publicKeyOption = GetPublicKeyCredentialOption(requestJson = options.toString())
        val request = GetCredentialRequest(credentialOptions = listOf(publicKeyOption))
        return credentialManager.getCredential(context = context, request = request)
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
