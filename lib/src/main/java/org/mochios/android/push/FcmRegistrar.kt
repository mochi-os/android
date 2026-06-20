// Copyright © 2026 Mochi OÜ
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
import org.mochios.android.api.unwrapRaw
import org.mochios.android.auth.TokenApi
import org.mochios.android.auth.TokenRequest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Per-server Firebase initialization. The Mochi server's admin pastes their
 * own Firebase project's config into system settings; the notifications app's
 * `push/setup` action returns it to the client; this class then initializes
 * the default [FirebaseApp] against it.
 *
 * We use the *default* FirebaseApp (rather than a named instance) because
 * FirebaseMessaging's per-instance accessor is package-private — the public
 * API only exposes FirebaseMessaging.getInstance() against the default
 * app. Switching to a different server tears down the existing FirebaseApp
 * and re-initializes with the new options, which is fine since a Mochi
 * user is only ever bound to one server at a time.
 */
object FcmRegistrar {

    private const val TAG = "MochiFcmRegistrar"

    data class FirebaseConfig(
        val projectId: String,
        val applicationId: String,
        val apiKey: String,
        val messagingSenderId: String,
    )

    /**
     * Initialize Firebase against the given config (no-op if already
     * initialized with the same projectId), retrieve the FCM token, and
     * register it with the Mochi server via the notifications app's
     * push/register/fcm action.
     *
     * Returns true on success, false on any failure (caller can fall back
     * to UnifiedPush).
     */
    suspend fun connect(
        context: Context,
        client: OkHttpClient,
        server: String,
        config: FirebaseConfig,
    ): Boolean {
        val firebaseApp = try {
            initIfNeeded(context, config)
        } catch (e: Exception) {
            Log.w(TAG, "Firebase init failed: ${e.message}")
            return false
        }

        val token = try {
            FirebaseMessaging.getInstance().awaitToken()
        } catch (e: Exception) {
            Log.w(TAG, "FCM token fetch failed: ${e.message}")
            return false
        }

        val installId = try {
            FirebaseInstallations.getInstance().awaitId()
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Installations ID fetch failed: ${e.message}")
            return false
        }

        val tokenApi = EntryPointAccessors
            .fromApplication(context.applicationContext, PushEntryPoint::class.java)
            .tokenApi()

        return try {
            postRegisterFcm(tokenApi, client, server, token, installId, DeviceName.resolve(context))
            Log.i(TAG, "Registered FCM token with $server (install=$installId)")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Posting FCM token to server failed: ${e.message}")
            false
        }
    }

    /**
     * Tear down on logout / server switch. The FCM token would remain
     * deliverable on Google's side until explicitly deleted, so we both
     * delete the token and tear down the default FirebaseApp so a later
     * connect() to a different project starts clean.
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
        } catch (_: Exception) { /* idempotent */ }
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

    private suspend fun postRegisterFcm(
        tokenApi: TokenApi,
        client: OkHttpClient,
        server: String,
        token: String,
        installId: String,
        device: String,
    ) {
        val appToken = runCatching {
            tokenApi.fetchToken(TokenRequest("notifications")).unwrapRaw().token
        }.getOrNull() ?: error("Could not mint notifications app token")
        val url = server.trimEnd('/') + "/notifications/-/push/register/fcm"
        val body = JSONObject()
            .put("token", token)
            .put("install_id", installId)
            .put("device", device)
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $appToken")
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("/notifications/-/push/register/fcm returned ${resp.code}")
        }
    }
}
