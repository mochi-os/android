// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.content.Context
import android.util.Log
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.mochios.android.BuildConfig
import org.mochios.android.auth.AuthRepository
import org.mochios.android.auth.SessionManager

/**
 * Server-driven transport selection: `/notifications/-/push/setup` answers
 * `{transport: "fcm", firebase_config: {...}}` or `{transport: "unifiedpush"}`.
 * The choice is cached with the server it was made for, since it says nothing
 * about another.
 */
object PushTransport {

    private const val TAG = "MochiPushTransport"
    private const val PREFS = "mochi_push_transport"
    private const val KEY_TRANSPORT = "transport"
    private const val KEY_SERVER = "server"

    const val TRANSPORT_FCM = "fcm"
    const val TRANSPORT_UNIFIEDPUSH = "unifiedpush"

    // Serialises configure(): MainActivity calls it from both LaunchedEffect
    // and onResume on cold start, and concurrent callers race on
    // FirebaseApp.initializeApp().
    private val configureMutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Fetch the server's push transport choice and apply it. Idempotent —
     * safe to call on every identity change / app start.
     */
    suspend fun configure(
        context: Context,
        sessionManager: SessionManager,
        client: OkHttpClient,
    ) = withContext(Dispatchers.IO) {
        configureMutex.withLock { configureLocked(context, sessionManager, client) }
    }

    private suspend fun configureLocked(
        context: Context,
        sessionManager: SessionManager,
        client: OkHttpClient,
    ) {
        val server = sessionManager.getServerUrlBlocking()
        // Which server the user is on is theirs, and this runs on every resume,
        // so it stays out of a release log. Same reason ApiClient keeps its HTTP
        // logging to debug builds.
        Log.i(TAG, "configure() starting")
        if (BuildConfig.DEBUG) Log.d(TAG, "configure() server=$server")
        if (server.isBlank()) {
            Log.w(TAG, "configure(): blank server URL, bailing")
            return
        }

        // This device first, so the push account either transport registers
        // below binds to it; on every configure, so a renamed phone updates
        // itself and the server's last-seen time moves.
        DeviceRegistrar.register(context, client, server)

        val authRepository = EntryPointAccessors
            .fromApplication(context.applicationContext, PushEntryPoint::class.java)
            .authRepository()
        val setup = try {
            fetchSetup(authRepository, client, server)
        } catch (e: Exception) {
            Log.w(TAG, "fetchSetup failed: ${e.javaClass.simpleName}: ${e.message}", e)
            null
        }
        // The transport is the decision everything below branches on, and is all
        // a release log needs. The response body carries firebase_config - the
        // server admin's project id, app id, sender id and API key - which was
        // written to logcat verbatim on every resume.
        Log.i(TAG, "configure() transport=${setup?.optString("transport") ?: "none"}")
        if (BuildConfig.DEBUG) Log.d(TAG, "configure() setup response: $setup")

        if (setup == null) {
            // Transport unknown (offline / 401): falling back to UnifiedPush
            // would start the FG distributor on an FCM server, so honour the
            // cached transport, and stay quiet when there is none.
            when (current(context)) {
                TRANSPORT_FCM -> {
                    runCatching { PushService.stop(context) }
                    Log.i(TAG, "fetchSetup failed; honouring cached FCM transport (no fallback)")
                }

                TRANSPORT_UNIFIEDPUSH -> {
                    startUnifiedPush(context, sessionManager)
                    Log.i(TAG, "fetchSetup failed; honouring cached UnifiedPush transport")
                }

                else -> {
                    runCatching { PushService.stop(context) }
                    Log.i(TAG, "fetchSetup failed and no cached transport; standing by")
                }
            }
            return
        }

        val transport = setup.optString("transport")
        if (transport == TRANSPORT_FCM) {
            val configJson = setup.optJSONObject("firebase_config")
            val firebaseConfig = configJson?.let(::parseFirebaseConfig)
            if (firebaseConfig != null && FcmRegistrar.connect(
                    context,
                    client,
                    server,
                    firebaseConfig
                )
            ) {
                recordTransport(context, server, TRANSPORT_FCM)
                // No UnifiedPush distributor, and no status notification, while
                // on FCM.
                runCatching { PushService.stop(context) }
                return
            }
            Log.w(TAG, "FCM advertised but failed to connect; falling back to UnifiedPush")
        }

        // UnifiedPush path: server explicitly said unifiedpush, OR server
        // said FCM but we couldn't connect to Firebase. Either way the
        // server confirmed its preference, so this is a real fallback.
        startUnifiedPush(context, sessionManager)
        recordTransport(context, server, TRANSPORT_UNIFIEDPUSH)
    }

    /**
     * Tear down all push on sign-out: stops [PushService] and deletes the FCM
     * token. Clear the session first - deleteToken() fires onNewToken, which
     * only skips re-registering while no session is active.
     */
    suspend fun tearDown(context: Context) = withContext(Dispatchers.IO) {
        configureMutex.withLock {
            Log.i(TAG, "tearDown(): stopping push service and clearing FCM token")
            runCatching { PushService.stop(context) }
                .onFailure { Log.w(TAG, "PushService.stop failed: ${it.message}") }
            runCatching { FcmRegistrar.disconnect(context) }
                .onFailure { Log.w(TAG, "FcmRegistrar.disconnect failed: ${it.message}") }
        }
    }

    private suspend fun startUnifiedPush(context: Context, sessionManager: SessionManager) {
        runCatching { FcmRegistrar.disconnect(context) }
        PushService.start(context)
        // MochiPushClient.register needs a stable instance; with no bound
        // identity yet, skip it - PushService is running and the next bootstrap
        // registers.
        val identity = sessionManager.getBoundIdentity().orEmpty()
        if (identity.isNotBlank()) {
            MochiPushClient.register(context, identity)
        } else {
            Log.i(TAG, "startUnifiedPush(): no bound identity yet; skipping UP register")
        }
    }

    /**
     * Last-known transport, or null when none was recorded for [server]. A null
     * [server] means "whatever was recorded"; pass the current one after a
     * server switch or the previous server's choice is honoured.
     */
    fun current(context: Context, server: String? = null): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (server != null && prefs.getString(KEY_SERVER, null) != server) return null
        return prefs.getString(KEY_TRANSPORT, null)
    }

    private fun recordTransport(context: Context, server: String, transport: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER, server)
            .putString(KEY_TRANSPORT, transport)
            .apply()
    }

    private suspend fun fetchSetup(
        authRepository: AuthRepository,
        client: OkHttpClient,
        server: String,
    ): JSONObject? {
        val appToken = authRepository.fetchToken("notifications").getOrNull() ?: return null
        val url = server.trimEnd('/') + "/notifications/-/push/setup"
        // GET — the action takes no parameters; server reads from settings.
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $appToken")
            .post(JSONObject().toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "/notifications/-/push/setup returned ${resp.code}")
                return null
            }
            val body = resp.body?.string().orEmpty()
            return JSONObject(body).optJSONObject("data")
        }
    }

    private fun parseFirebaseConfig(json: JSONObject): FcmRegistrar.FirebaseConfig? {
        val project = json.optString("project_id")
        val app = json.optString("app_id")
        val key = json.optString("api_key")
        val sender = json.optString("messaging_sender_id")
        if (project.isBlank() || app.isBlank() || key.isBlank() || sender.isBlank()) {
            Log.w(TAG, "firebase_config missing required fields")
            return null
        }
        return FcmRegistrar.FirebaseConfig(project, app, key, sender)
    }
}
