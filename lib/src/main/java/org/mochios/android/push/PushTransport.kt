package org.mochios.android.push

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.mochios.android.auth.SessionManager

/**
 * Server-driven transport selection. After auth, the client asks the user's
 * Mochi server which push transport it prefers via
 * `/notifications/-/push/setup`:
 *
 *   - `{transport: "fcm", firebase_config: {...}}` — server admin pasted
 *     Firebase config into system settings. [FcmRegistrar.connect]
 *     initialises Firebase Messaging against that project, fetches the
 *     device token, and POSTs it back via `/notifications/-/push/register/fcm`.
 *     `PushService` (the UnifiedPush FG-distributor + its "listening for
 *     notifications" status notification) is NOT started.
 *
 *   - `{transport: "unifiedpush"}` — server has no Firebase config. Fall
 *     back to the existing UnifiedPush flow: [PushService] runs as a FG
 *     service and [MochiPushClient.register] picks a distributor.
 *
 * The result is cached in a SharedPreferences pref keyed by server URL so
 * later launches (and the boot receiver / watchdog) can honour the choice
 * without re-fetching every time.
 */
object PushTransport {

    private const val TAG = "MochiPushTransport"
    private const val PREFS = "mochi_push_transport"
    private const val KEY_TRANSPORT = "transport"
    private const val KEY_SERVER = "server"

    const val TRANSPORT_FCM = "fcm"
    const val TRANSPORT_UNIFIEDPUSH = "unifiedpush"

    /**
     * Fetch the server's push transport choice and apply it. Idempotent —
     * safe to call on every identity change / app start.
     */
    suspend fun configure(
        context: Context,
        sessionManager: SessionManager,
        client: OkHttpClient,
    ) = withContext(Dispatchers.IO) {
        val server = sessionManager.getServerUrlBlocking()
        Log.i(TAG, "configure() server=$server")
        if (server.isBlank()) {
            Log.w(TAG, "configure(): blank server URL, bailing")
            return@withContext
        }

        val setup = try {
            fetchSetup(client, server)
        } catch (e: Exception) {
            Log.w(TAG, "fetchSetup($server) failed: ${e.javaClass.simpleName}: ${e.message}", e)
            null
        }
        Log.i(TAG, "configure() setup response: $setup")

        if (setup == null) {
            // Offline / server unreachable / 401. We *don't* know whether
            // FCM is configured, so falling back to UnifiedPush would be
            // wrong — that's how a phone with bad reception ended up with
            // the "listening for notifications" FG service running
            // overnight despite FCM being the configured transport.
            // Honour the last known transport instead: if cached FCM,
            // leave PushService stopped; if cached UnifiedPush, start it.
            // First launch with no cache: stay quiet (no UP service)
            // until the next configure() succeeds.
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
            return@withContext
        }

        val transport = setup.optString("transport")
        if (transport == TRANSPORT_FCM) {
            val configJson = setup.optJSONObject("firebase_config")
            val firebaseConfig = configJson?.let(::parseFirebaseConfig)
            if (firebaseConfig != null && FcmRegistrar.connect(context, client, server, firebaseConfig)) {
                recordTransport(context, server, TRANSPORT_FCM)
                // Stop the UnifiedPush FG distributor if it's running from
                // a prior session — no "listening for notifications"
                // notification while on FCM.
                runCatching { PushService.stop(context) }
                return@withContext
            }
            Log.w(TAG, "FCM advertised but failed to connect; falling back to UnifiedPush")
        }

        // UnifiedPush path: server explicitly said unifiedpush, OR server
        // said FCM but we couldn't connect to Firebase. Either way the
        // server confirmed its preference, so this is a real fallback.
        startUnifiedPush(context, sessionManager)
        recordTransport(context, server, TRANSPORT_UNIFIEDPUSH)
    }

    private suspend fun startUnifiedPush(context: Context, sessionManager: SessionManager) {
        runCatching { FcmRegistrar.disconnect(context) }
        PushService.start(context)
        // MochiPushClient.register requires a stable per-account instance.
        // boundIdentity is set by publishAccount during bootstrap; if it
        // isn't there yet (pre-supersession sessions etc.), skip the
        // distributor register — PushService is already running so the
        // user keeps any existing UP subscription, and the next bootstrap
        // / fresh login will pick this back up via the regular path.
        val identity = sessionManager.getBoundIdentity().orEmpty()
        if (identity.isNotBlank()) {
            MochiPushClient.register(context, identity)
        } else {
            Log.i(TAG, "startUnifiedPush(): no bound identity yet; skipping UP register")
        }
    }

    /** Last-known transport for this server. Used by boot receiver + watchdog. */
    fun current(context: Context): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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

    private fun fetchSetup(client: OkHttpClient, server: String): JSONObject? {
        val appToken = mintAppToken(client, server, "notifications") ?: return null
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

    private fun mintAppToken(client: OkHttpClient, server: String, app: String): String? {
        val url = server.trimEnd('/') + "/_/token"
        val body = JSONObject().put("app", app).toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return try {
                JSONObject(resp.body?.string().orEmpty()).optString("token").ifBlank { null }
            } catch (_: Exception) {
                null
            }
        }
    }
}
