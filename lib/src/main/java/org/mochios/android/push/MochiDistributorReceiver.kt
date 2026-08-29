// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import org.mochios.android.account.MochiAccount
import java.security.SecureRandom

/**
 * Distributor side of the UnifiedPush v3 protocol, wired only by builds that
 * host it (the Mochi shell). Apps send REGISTER / UNREGISTER / MESSAGE_ACK; the
 * distributor broadcasts NEW_ENDPOINT, REGISTRATION_FAILED, UNREGISTERED and
 * MESSAGE to the app.
 */
class MochiDistributorReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.getStringExtra(EXTRA_TOKEN) ?: return
        if (token.length > MAXIMUM_TOKEN) {
            Log.w(TAG, "Rejecting over-long token (${token.length} characters)")
            return
        }
        // Identify the caller only from the PendingIntent, whose creator the
        // system records and a sender cannot forge. The protocol's
        // "application" extra is a plain string the caller chooses, so it
        // attests nothing and is deliberately not consulted.
        val appPackage = senderPackage(intent)
        if (appPackage == null) {
            Log.w(TAG, "Rejecting ${intent.action}: no PendingIntent to identify the caller")
            return
        }
        // This distributor exists only for Mochi's own push (see the class
        // comment); no third-party UnifiedPush app is meant to use it. Refuse
        // anything not signed with our certificate, and stay silent rather than
        // answering, so a probing app learns nothing about what is registered.
        if (!mochiSigned(context, appPackage)) {
            Log.w(TAG, "Rejecting ${intent.action} from unrelated package $appPackage")
            return
        }

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
        // A token belongs to the package that first registered it. Re-pointing
        // one at a different package would hand that package another app's
        // subscription, so a mismatch is refused rather than overwritten.
        if (existing != null && existing.appPackage != appPackage) {
            Log.w(TAG, "REGISTER from $appPackage: token belongs to ${existing.appPackage}")
            sendBroadcast(
                context, appPackage, ACTION_REGISTRATION_FAILED,
                Bundle1(EXTRA_TOKEN to token, EXTRA_REASON to "INTERNAL_ERROR"),
            )
            return
        }
        if (existing == null && store.count() >= MAXIMUM_REGISTRATIONS) {
            Log.w(TAG, "REGISTER from $appPackage: at the $MAXIMUM_REGISTRATIONS registration cap")
            sendBroadcast(
                context, appPackage, ACTION_REGISTRATION_FAILED,
                Bundle1(EXTRA_TOKEN to token, EXTRA_REASON to "INTERNAL_ERROR"),
            )
            return
        }
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

        Log.i(TAG, "REGISTER from $appPackage on ${account.server}")
        sendBroadcast(
            context, appPackage, ACTION_NEW_ENDPOINT,
            Bundle1(EXTRA_TOKEN to token, EXTRA_ENDPOINT to endpoint),
        )
    }

    private fun handleUnregister(context: Context, token: String, appPackage: String) {
        // Only the package that owns the token may retire it. An unknown token
        // is answered the same way an owned one is, so the reply cannot be used
        // to probe which tokens exist.
        val existing = DistributorStore(context).get(token)
        if (existing != null && existing.appPackage != appPackage) {
            Log.w(TAG, "UNREGISTER from $appPackage: token belongs to ${existing.appPackage}")
            return
        }
        DistributorStore(context).remove(token)
        Log.i(TAG, "UNREGISTER from $appPackage")
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

    internal companion object {
        const val TAG = "MochiDistributor"

        // The connector's tokens are UUIDs; the bound is only so a caller
        // cannot grow the store with arbitrarily long keys.
        const val MAXIMUM_TOKEN = 128

        // One registration per signed-in identity, so a handful in practice.
        const val MAXIMUM_REGISTRATIONS = 64

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
        const val EXTRA_PI = "pi"
        const val EXTRA_ENDPOINT = "endpoint"
        const val EXTRA_REASON = "reason"
    }
}
