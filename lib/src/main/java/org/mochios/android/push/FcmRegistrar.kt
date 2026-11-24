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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.mochios.android.auth.AuthRepository
import org.unifiedpush.android.connector.data.PublicKeySet
import org.unifiedpush.android.connector.keys.DefaultKeyManager
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Per-server Firebase initialization from the config `push/setup` returns. Uses
 * the default [FirebaseApp] because FirebaseMessaging only exposes
 * getInstance() against it; switching servers tears it down and re-initializes.
 *
 * Registers by Firebase Installation ID (FID), the replacement for FCM
 * registration tokens. The manifest's `firebase_messaging_installation_id_enabled`
 * flag turns this on, and with it set the token APIs throw.
 */
object FcmRegistrar {

    private const val TAG = "MochiFcmRegistrar"

    private val registerMutex = Mutex()

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

        // register() reuses the existing FID, so the ID read afterwards is the
        // one FCM now delivers to.
        val installationId = try {
            FirebaseMessaging.getInstance().awaitRegister()
            FirebaseInstallations.getInstance().awaitId()
        } catch (e: Exception) {
            Log.w(TAG, "FCM registration failed: ${e.message}")
            return Outcome.FAILED
        }

        return register(context, client, server, installationId)
    }

    /**
     * Register or refresh this installation with the server, resolving the
     * device name itself so [MochiFirebaseMessagingService.onRegistered] can
     * reuse it.
     *
     * Serialized: [connect] and [MochiFirebaseMessagingService.onRegistered]
     * both land here for the same installation, and the one that waits reads
     * the memo the other just wrote instead of posting a second time.
     */
    suspend fun register(
        context: Context,
        client: OkHttpClient,
        server: String,
        installationId: String,
    ): Outcome = registerMutex.withLock {
        registerLocked(context, client, server, installationId)
    }

    private suspend fun registerLocked(
        context: Context,
        client: OkHttpClient,
        server: String,
        installationId: String,
    ): Outcome {
        val deps = EntryPointAccessors
            .fromApplication(context.applicationContext, PushEntryPoint::class.java)

        val keys = publicKeys(context)

        // The memo: an unchanged FID and keys were registered on an earlier
        // resume, or were refused and re-posting them would only be refused
        // again. The keys are part of it, so a build that first has them
        // registers them once over a memo of the FID alone.
        val credential = if (keys == null) RegistrationMemo.fingerprint(installationId)
        else RegistrationMemo.fingerprint(installationId, keys.auth, keys.pubKey)
        val store = RegistrationStore(context)
        val now = System.currentTimeMillis()
        when (RegistrationMemo.judge(now, server, PushTransport.TRANSPORT_FCM, credential, store.last(), store.refusal())) {
            RegistrationMemo.Verdict.FRESH -> {
                Log.i(TAG, "FCM installation already registered; not re-posting")
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
                installationId,
                label = DeviceName.resolve(context),
                device = deps.deviceStore().id(),
                keys = keys,
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
                    Log.i(TAG, "Registered FCM installation")
                    Outcome.REGISTERED
                }
                answer.refused -> {
                    // Ours to fix, not to retry: an ID the server will not take,
                    // or a wire format this build no longer shares with it.
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
            Log.w(TAG, "Posting FCM installation to server failed: ${e.message}")
            Outcome.FAILED
        }
    }

    /**
     * This device's Web Push keys for FCM, made on first use and kept in the
     * UnifiedPush connector's key store under [FCM_KEYS]: the server seals
     * every message to them, and [fcmFields] opens it. Null when the key
     * store fails, and the device then registers without keys: plain messages
     * rather than none.
     */
    private fun publicKeys(context: Context): PublicKeySet? = try {
        val manager = DefaultKeyManager(context)
        if (!manager.exists(FCM_KEYS)) manager.generate(FCM_KEYS)
        manager.getPublicKeySet(FCM_KEYS)
    } catch (e: Exception) {
        Log.w(TAG, "FCM keys unavailable: ${e.message}")
        null
    }

    /**
     * Tear down on logout / server switch. The installation stays deliverable
     * on Google's side until unregistered, so drop it and the FirebaseApp too,
     * and the keys, so the next server or account is given new ones.
     */
    suspend fun disconnect(context: Context) {
        try {
            DefaultKeyManager(context).delete(FCM_KEYS)
        } catch (e: Exception) {
            Log.w(TAG, "FCM keys not deleted: ${e.message}")
        }
        val app = try {
            FirebaseApp.getInstance()
        } catch (_: IllegalStateException) {
            return
        }
        try {
            FirebaseMessaging.getInstance().awaitUnregister()
        } catch (e: Exception) {
            Log.w(TAG, "FCM unregister failed: ${e.message}")
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

    private suspend fun FirebaseMessaging.awaitRegister(): Unit =
        suspendCancellableCoroutine { cont ->
            register().addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { error -> cont.resumeWithException(error) }
        }

    private suspend fun FirebaseMessaging.awaitUnregister(): Unit =
        suspendCancellableCoroutine { cont ->
            unregister().addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { error -> cont.resumeWithException(error) }
        }

    private suspend fun FirebaseInstallations.awaitId(): String =
        suspendCancellableCoroutine { cont ->
            id.addOnSuccessListener { fid -> cont.resume(fid) }
                .addOnFailureListener { error -> cont.resumeWithException(error) }
        }

    /** The status the server answered, with the push account id when it accepted. */
    private suspend fun postRegisterFcm(
        authRepository: AuthRepository,
        client: OkHttpClient,
        server: String,
        installationId: String,
        label: String,
        device: String,
        keys: PublicKeySet?,
    ): Answer {
        val appToken = authRepository.fetchToken("notifications").getOrNull()
            ?: error("Could not mint notifications app token")
        val url = server.trimEnd('/') + "/notifications/-/push/register/fcm"
        // The FID goes in `token` as well as `installation`: FCM's send API
        // accepts an FID in its `token` field while it moves to `fid`, so a
        // server that still sends by token keeps delivering.
        val json = JSONObject()
            .put("token", installationId)
            .put("installation", installationId)
            .put("label", label)
        // The keys the server seals messages to; a server that predates them
        // ignores them and sends plain fields, which fcmFields still reads.
        if (keys != null) json.put("auth", keys.auth).put("p256dh", keys.pubKey)
        val body = json.toString().toRequestBody("application/json".toMediaType())
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
            val raw = resp.body.string()
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
