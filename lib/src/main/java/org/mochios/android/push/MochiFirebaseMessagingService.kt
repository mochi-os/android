// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Base64

private const val TAG = "MochiFcm"

/**
 * The fields an FCM message carries. A server holding this device's keys
 * ([FcmKeys]) seals them (RFC 8291) into the one field `encrypted`,
 * base64url, so Google relays ciphertext; one that predates the keys sends
 * them as the message's data. [open] answers the plaintext, or null when the
 * device holds no keys. Null for a sealed message that will not open.
 */
internal fun fcmFields(data: Map<String, String>, open: (ByteArray) -> ByteArray?): Map<String, String>? {
    val sealed = data["encrypted"] ?: return data
    val plain = try {
        open(Base64.getUrlDecoder().decode(sealed))
    } catch (e: Exception) {
        Log.w(TAG, "FCM message did not open: ${e.message}")
        return null
    }
    if (plain == null) {
        Log.w(TAG, "FCM message is sealed, but this device holds no keys")
        return null
    }
    return pushFields(plain)
}

/**
 * Receives FCM messages. The server sends the UnifiedPush payload's fields
 * (`app`, `link`, `title`, `body`, `tag`, `id`, or `sync`), sealed to this
 * device's keys ([fcmFields]), so both transports deliver through
 * [deliverPush]. FCM is registered for the signed-in account alone, so a
 * message names no account.
 */
class MochiFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun deps(): PushEntryPoint =
        EntryPointAccessors.fromApplication(applicationContext, PushEntryPoint::class.java)

    override fun onMessageReceived(message: RemoteMessage) {
        val fields = fcmFields(message.data, FcmKeys(applicationContext)::open) ?: return
        deliverPush(applicationContext, fields, null)
    }

    override fun onRegistered(installationId: String) {
        Log.i(TAG, "FCM installation registered")
        scope.launch {
            val deps = deps()
            // Auto-init re-registers on the next start after a sign-out's
            // unregister() and fires this callback. Registering it would
            // re-subscribe the device we signed out of, so skip when there's
            // no session.
            if (!deps.sessionManager().isAuthenticated.first()) {
                Log.i(TAG, "No active session; skipping FCM registration")
                return@launch
            }
            val server = deps.sessionManager().getServerUrlBlocking()
            if (server.isBlank()) {
                Log.w(TAG, "No server bound; skipping FCM registration")
                return@launch
            }
            FcmRegistrar.register(applicationContext, deps.okHttpClient(), server, installationId)
        }
    }
}
