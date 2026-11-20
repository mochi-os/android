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

/**
 * Receives FCM messages. The server sends the UnifiedPush payload's fields in
 * `message.data` (`app`, `link`, `title`, `body`, `tag`, `id`, or `sync`), so
 * both transports deliver through [deliverPush]. FCM is registered for the
 * signed-in account alone, so a message names no account.
 */
class MochiFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun deps(): PushEntryPoint =
        EntryPointAccessors.fromApplication(applicationContext, PushEntryPoint::class.java)

    override fun onMessageReceived(message: RemoteMessage) {
        deliverPush(applicationContext, message.data, null)
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

    private companion object {
        const val TAG = "MochiFcm"
    }
}
