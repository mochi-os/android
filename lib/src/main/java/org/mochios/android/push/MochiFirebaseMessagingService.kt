package org.mochios.android.push

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.mochios.android.auth.SessionManager

/**
 * Receives FCM messages and posts the system notification on the matching
 * per-feature channel.
 *
 * Wire payload (set by the Mochi server in FCM v1 `message.data`) mirrors
 * the UnifiedPush envelope so the on-device side is transport-agnostic:
 *
 *     { "app": "forums", "link": "/forums/abc12def", "title": "...", "body": "...", "tag": "..." }
 *
 * `onNewToken` POSTs the refreshed token back to the notifications app's
 * `push/register/fcm` action so the server can replace any stale entry.
 *
 * The service is plumbed in the lib so all per-feature channels see the same
 * routing; channel IDs are the feature slugs ("feeds" / "chat" / "forums" /
 * "projects") shipped by the per-feature modules' channel-setup helpers.
 */
class MochiFirebaseMessagingService : FirebaseMessagingService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun sessionManager(): SessionManager
        fun okHttpClient(): OkHttpClient
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun deps(): Deps =
        EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"].orEmpty()
        val body = data["body"].orEmpty()
        val link = data["link"].orEmpty()
        val tag = data["tag"].orEmpty()
        val app = data["app"].orEmpty()

        if (title.isBlank() && body.isBlank()) {
            Log.w(TAG, "FCM payload missing title/body; ignoring")
            return
        }
        postSystemNotification(applicationContext, title, body, link, tag, app)
    }

    override fun onNewToken(token: String) {
        Log.i(TAG, "FCM token refreshed")
        scope.launch {
            try {
                val deps = deps()
                val server = deps.sessionManager().getServerUrlBlocking()
                postPushRegisterFcm(deps.okHttpClient(), applicationContext, server, token)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register FCM token with Mochi server: ${e.message}")
            }
        }
    }

    /**
     * Body shape matches [FcmRegistrar.postRegisterFcm] — server requires
     * `install_id` (Firebase Installations ID, used as the per-device dedup
     * key) and `device` (display name) alongside `token`. Sending only the
     * token returned 400 and the cold-start race then fell back to
     * UnifiedPush, surfacing the "listening for notifications" status row.
     */
    private fun postPushRegisterFcm(client: OkHttpClient, context: Context, server: String, token: String) {
        val appToken = mintAppToken(client, server, "notifications") ?: return
        val installId = try {
            com.google.firebase.installations.FirebaseInstallations.getInstance().id
                .let { task ->
                    com.google.android.gms.tasks.Tasks.await(task)
                }
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch Firebase Installations ID for FCM re-register: ${e.message}")
            return
        }
        val url = server.trimEnd('/') + "/notifications/-/push/register/fcm"
        val body = JSONObject()
            .put("token", token)
            .put("install_id", installId)
            .put("device", DeviceName.resolve(context))
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $appToken")
            .post(body)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "/notifications/-/push/register/fcm returned ${resp.code}")
            }
        }
    }

    private fun mintAppToken(client: OkHttpClient, server: String, app: String): String? {
        val url = server.trimEnd('/') + "/_/token"
        val body = JSONObject().put("app", app).toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "/_/token($app) returned ${resp.code}")
                return null
            }
            return try {
                JSONObject(resp.body?.string().orEmpty()).optString("token").ifBlank { null }
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Channel routing is identical to the UnifiedPush path's
     * MochiPushDispatcher.channelId: prefer the server-supplied `app`
     * slug, fall back to the first path segment of `link`. Both refer to
     * channels created by the per-feature `setup*NotificationChannel`
     * helpers using the slug as the channel ID.
     */
    private fun channelIdFor(app: String, link: String): String {
        val key = app.ifBlank {
            link.trimStart('/').substringBefore('/')
        }.lowercase()
        return when (key) {
            "feeds", "chat", "forums", "projects" -> key
            else -> "feeds"
        }
    }

    private fun postSystemNotification(
        context: Context,
        title: String,
        body: String,
        link: String,
        tag: String,
        app: String,
    ) {
        val channelId = channelIdFor(app, link)

        // mochi:notification?link=<encoded> — same opaque URI the
        // UnifiedPush dispatcher uses; routed by MainActivity through
        // claude/plans/mochi-uri-scheme.md.
        val deepLink = Uri.parse("mochi:notification?link=${Uri.encode(link)}")
        val intent = Intent(Intent.ACTION_VIEW, deepLink).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getActivity(context, 0, intent, pendingFlags)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)

        val nm = NotificationManagerCompat.from(context)
        if (nm.areNotificationsEnabled()) {
            try {
                nm.notify(tag.ifBlank { "mochi" }, tag.hashCode(), builder.build())
            } catch (e: SecurityException) {
                Log.w(TAG, "Notification post denied: ${e.message}")
            }
        }
    }

    private companion object {
        const val TAG = "MochiFcm"
    }
}
