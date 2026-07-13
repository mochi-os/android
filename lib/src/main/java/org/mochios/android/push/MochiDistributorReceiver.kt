// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import org.mochios.android.account.MochiAccount
import java.security.SecureRandom

/**
 * Implements the *distributor* side of the UnifiedPush protocol. Wired by
 * the manifest fragment at `lib/android/src/distributor/AndroidManifest.xml`,
 * which is included only by builds that opt in to host the distributor (the
 * Mochi shell — `apps/menu/android/`).
 *
 * Wire protocol (UnifiedPush v3):
 *
 *   App → distributor:
 *     ACTION_REGISTER     (token, application, optional features/vapid/PI)
 *     ACTION_UNREGISTER   (token)
 *     ACTION_MESSAGE_ACK  (token)
 *
 *   Distributor → App (broadcast targeted at App's package):
 *     ACTION_NEW_ENDPOINT          (token, endpoint)
 *     ACTION_REGISTRATION_FAILED   (token, reason)
 *     ACTION_UNREGISTERED          (token)
 *     ACTION_MESSAGE               (token, bytesMessage)
 *
 * v1 scope of this receiver:
 *   - REGISTER allocates a sub_id, builds an endpoint URL on the user's
 *     active Mochi server (resolved via MochiAccount), persists the
 *     subscription via [DistributorStore], replies with NEW_ENDPOINT.
 *   - UNREGISTER drops the entry, replies with UNREGISTERED.
 *   - MESSAGE_ACK is a no-op.
 *
 * Deferred (TODO, blocked on PushService work):
 *   - Server-side push-account registration (POST to /notifications/-/push/register)
 *     so the user's Mochi server knows about the endpoint and starts
 *     directing notifications to it.
 *   - Web Push key generation (auth + p256dh keypair). Currently emits
 *     placeholder empty strings — the App-side connector library generates
 *     its own keys at register time and the distributor wouldn't normally
 *     need to know them. The placeholder fields here are for the Mochi
 *     server's records, populated when the server-side registration is
 *     wired up.
 *   - WebSocket message routing (server → distributor → MESSAGE intent).
 *     Lives in the planned PushService.
 */
class MochiDistributorReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.getStringExtra(EXTRA_TOKEN) ?: return
        val pi: PendingIntent? = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_PI, PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_PI)
        }
        val appPackage = pi?.creatorPackage ?: intent.getStringExtra(EXTRA_APPLICATION) ?: return

        when (intent.action) {
            ACTION_REGISTER -> handleRegister(context, token, appPackage)
            ACTION_UNREGISTER -> handleUnregister(context, token, appPackage)
            ACTION_MESSAGE_ACK -> { /* no-op for v1 */ }
            else -> Log.w(TAG, "Unexpected action ${intent.action}")
        }
    }

    private fun handleRegister(context: Context, token: String, appPackage: String) {
        val account = MochiAccount.first(context)
        if (account == null) {
            Log.w(TAG, "REGISTER from $appPackage: no Mochi account; sending REGISTRATION_FAILED")
            sendBroadcast(
                context, appPackage, ACTION_REGISTRATION_FAILED,
                Bundle1(EXTRA_TOKEN to token, EXTRA_REASON to "NETWORK"),
            )
            return
        }

        val store = DistributorStore(context)
        val existing = store.get(token)
        val subId = existing?.subId ?: randomSubId()
        val endpoint = buildEndpoint(account.server, subId)

        store.put(
            DistributorStore.Entry(
                token = token,
                appPackage = appPackage,
                subId = subId,
                server = account.server,
                auth = "",  // TODO populate when server-side registration is wired
                p256dh = "",
            )
        )

        Log.i(TAG, "REGISTER from $appPackage token=$token → endpoint=$endpoint")
        sendBroadcast(
            context, appPackage, ACTION_NEW_ENDPOINT,
            Bundle1(EXTRA_TOKEN to token, EXTRA_ENDPOINT to endpoint),
        )
    }

    private fun handleUnregister(context: Context, token: String, appPackage: String) {
        DistributorStore(context).remove(token)
        Log.i(TAG, "UNREGISTER from $appPackage token=$token")
        sendBroadcast(
            context, appPackage, ACTION_UNREGISTERED,
            Bundle1(EXTRA_TOKEN to token),
        )
    }

    private fun sendBroadcast(context: Context, targetPackage: String, action: String, extras: Map<String, String>) {
        val out = Intent(action).apply {
            setPackage(targetPackage)
            for ((k, v) in extras) putExtra(k, v)
        }
        context.sendBroadcast(out)
    }

    @Suppress("FunctionName")
    private fun Bundle1(vararg pairs: Pair<String, String>): Map<String, String> = pairs.toMap()

    private fun randomSubId(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun buildEndpoint(server: String, subId: String): String {
        val base = server.trimEnd('/')
        return "$base/notifications/-/push/inbound/$subId"
    }

    private companion object {
        const val TAG = "MochiDistributor"

        // UnifiedPush v3 wire constants (matched by string against the spec —
        // the connector library exports these as constants but they aren't
        // marked public, so we hard-code the values rather than reflect.)
        const val ACTION_REGISTER = "org.unifiedpush.android.distributor.REGISTER"
        const val ACTION_UNREGISTER = "org.unifiedpush.android.distributor.UNREGISTER"
        const val ACTION_MESSAGE_ACK = "org.unifiedpush.android.distributor.MESSAGE_ACK"
        const val ACTION_NEW_ENDPOINT = "org.unifiedpush.android.connector.NEW_ENDPOINT"
        const val ACTION_REGISTRATION_FAILED = "org.unifiedpush.android.connector.REGISTRATION_FAILED"
        const val ACTION_UNREGISTERED = "org.unifiedpush.android.connector.UNREGISTERED"

        const val EXTRA_TOKEN = "token"
        const val EXTRA_APPLICATION = "application"
        const val EXTRA_PI = "pi"
        const val EXTRA_ENDPOINT = "endpoint"
        const val EXTRA_REASON = "reason"
    }
}
