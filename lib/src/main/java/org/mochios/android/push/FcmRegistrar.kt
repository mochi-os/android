// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.mochios.android.auth.AuthRepository
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Per-server Firebase initialization from the config `push/setup` returns. Uses
 * the default [FirebaseApp] because FirebaseMessaging only exposes
 * getInstance() against it; switching servers tears it down and re-initializes.
 */
object FcmRegistrar {

    private const val TAG = "MochiFcmRegistrar"

    /**
     * How a registration ended. FRESH and REFUSED made no request: the memo
     * answered. FAILED is transient (Firebase, network, a 5xx) and carries no
     * memo, so the next configure tries again.
     */
    enum class Outcome { REGISTERED, FRESH, REFUSED, FAILED }

    data class FirebaseConfig(
        val projectId: String,
        val applicationId: String,
        val apiKey: String,
        val messagingSenderId: String,
    )

    suspend fun connect(
        context: Context,
        client: OkHttpClient,
        server: String,
        config: FirebaseConfig,
    ): Outcome {
        val firebaseApp = try {
            initIfNeeded(context, config)
        } catch (e: Exception) {
            Log.w(TAG, "Firebase init failed: ${e.message}")
            return Outcome.FAILED
        }

        val token = try {
            FirebaseMessaging.getInstance().awaitToken()
        } catch (e: Exception) {
            Log.w(TAG, "FCM token fetch failed: ${e.message}")
            return Outcome.FAILED
        }

        return register(context, client, server, token)
    }

    /**
     * Register or refresh an FCM token with the server, resolving the
     * Installations ID and device name itself so
     * [MochiFirebaseMessagingService.onNewToken] can reuse it.
     */
    suspend fun register(
        context: Context,
        client: OkHttpClient,
        server: String,
        token: String,
    ): Outcome {
        val installId = try {
            FirebaseInstallations.getInstance().awaitId()
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Installations ID fetch failed: ${e.message}")
            return Outcome.FAILED
        }

        val deps = EntryPointAccessors
            .fromApplication(context.applicationContext, PushEntryPoint::class.java)

        // The memo: an unchanged token was registered on an earlier resume, or
        // was refused and re-posting it would only be refused again.
        val credential = RegistrationMemo.fingerprint(token, installId)
        val store = RegistrationStore(context)
        val now = System.currentTimeMillis()
        when (RegistrationMemo.judge(now, server, PushTransport.TRANSPORT_FCM, credential, store.last(), store.refusal())) {
            RegistrationMemo.Verdict.FRESH -> {
                Log.i(TAG, "FCM token already registered; not re-posting")
                return Outcome.FRESH
            }
            RegistrationMemo.Verdict.REFUSED -> {
                Log.i(TAG, "FCM registration was refused recently; not retrying")
                return Outcome.REFUSED
            }
            RegistrationMemo.Verdict.REGISTER -> {}
        }

        return try {
            val answer = postRegisterFcm(
                deps.authRepository(),
                client,
                server,
                token,
                installId,
                label = DeviceName.resolve(context),
                device = deps.deviceStore().id(),
            )
            val registration = Registration(server, PushTransport.TRANSPORT_FCM, credential, now)
            when {
                answer.accepted -> {
                    // Keep the account id: sign-out hands it to
                    // `/notifications/-/accounts/remove` so the server stops pushing to
                    // this device. Keyed by identity, matching the UnifiedPush path.
                    val identity = deps.sessionManager().getBoundIdentity().orEmpty()
                    if (answer.account != null && identity.isNotBlank()) {
                        deps.pushAccountStore().store(identity, answer.account)
                    }
                    store.success(registration)
                    Log.i(TAG, "Registered FCM token")
                    Outcome.REGISTERED
                }
                answer.refused -> {
                    // Ours to fix, not to retry: an invalid token, or a wire
                    // format this build no longer shares with the server.
                    store.refuse(registration)
                    Log.w(TAG, "/notifications/-/push/register/fcm refused ${answer.code}; not retrying for a day")
                    Outcome.REFUSED
                }
                else -> {
                    Log.w(TAG, "/notifications/-/push/register/fcm returned ${answer.code}")
                    Outcome.FAILED
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Posting FCM token to server failed: ${e.message}")
            Outcome.FAILED
        }
    }

    /**
     * Tear down on logout / server switch. The token stays deliverable on
     * Google's side until deleted, so drop it and the FirebaseApp too.
     */
    suspend fun disconnect(context: Context) {
        val app = try {
            FirebaseApp.getInstance()
        } catch (_: IllegalStateException) {
            return
        }
        try {
            FirebaseMessaging.getInstance().awaitDeleteToken()
        } catch (e: Exception) {
            Log.w(TAG, "FCM token delete failed: ${e.message}")
        }
        try {
            app.delete()
        } catch (_: Exception) { /* idempotent */
        }
    }

    private fun initIfNeeded(context: Context, config: FirebaseConfig): FirebaseApp {
        val existing = try {
            FirebaseApp.getInstance()
        } catch (_: IllegalStateException) {
            null
        }
        if (existing != null) {
            if (existing.options.projectId == config.projectId) return existing
            // Different project — tear down so initializeApp below replaces it.
            existing.delete()
        }
        val options = FirebaseOptions.Builder()
            .setProjectId(config.projectId)
            .setApplicationId(config.applicationId)
            .setApiKey(config.apiKey)
            .setGcmSenderId(config.messagingSenderId)
            .build()
        return FirebaseApp.initializeApp(context, options)
    }

    private suspend fun FirebaseMessaging.awaitToken(): String =
        suspendCancellableCoroutine { cont ->
            token.addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    private suspend fun FirebaseMessaging.awaitDeleteToken(): Unit =
        suspendCancellableCoroutine { cont ->
            deleteToken().addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    private suspend fun FirebaseInstallations.awaitId(): String =
        suspendCancellableCoroutine { cont ->
            id.addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    /** The status the server answered, with the push account id when it accepted. */
    private suspend fun postRegisterFcm(
        authRepository: AuthRepository,
        client: OkHttpClient,
        server: String,
        token: String,
        installId: String,
        label: String,
        device: String,
    ): Answer {
        val appToken = authRepository.fetchToken("notifications").getOrNull()
            ?: error("Could not mint notifications app token")
        val url = server.trimEnd('/') + "/notifications/-/push/register/fcm"
        val body = JSONObject()
            .put("token", token)
            .put("installation", installId)
            .put("label", label)
            .toString()
            .toRequestBody("application/json".toMediaType())
        // The Device header binds the push account to this device, so a later
        // registration from the same phone replaces it rather than adding to it.
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $appToken")
            .header("Device", device)
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return Answer(resp.code, null)
            val raw = resp.body?.string().orEmpty()
            val account = try {
                JSONObject(raw).optJSONObject("data")
                    ?.optString("id")
                    ?.takeIf { id -> id.isNotBlank() }
            } catch (_: Exception) {
                Log.w(TAG, "Could not parse /notifications/-/push/register/fcm response")
                null
            }
            return Answer(resp.code, account)
        }
    }
}
