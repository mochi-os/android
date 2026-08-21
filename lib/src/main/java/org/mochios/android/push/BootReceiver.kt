// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Restarts [PushService] after boot. Declared only by the shell app's manifest
 * - only the distributor host wakes on boot. Android 12+ forbids a background
 * receiver from starting a foreground service; the BOOT_COMPLETED window is an
 * allowed context.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            -> {
                if (PushTransport.current(context.applicationContext) == PushTransport.TRANSPORT_FCM) {
                    Log.i(TAG, "Boot completed — transport=fcm, leaving PushService stopped")
                } else {
                    Log.i(TAG, "Boot completed — starting PushService")
                    runCatching { PushService.start(context.applicationContext) }
                        .onFailure { Log.w(TAG, "Failed to start PushService on boot: ${it.message}") }
                }
                // Re-arm the watchdog: WorkManager's persisted state typically
                // survives reboot, but enqueueUniquePeriodicWork with KEEP is
                // a cheap idempotent guard against edge cases where it didn't.
                // The watchdog itself respects the current transport.
                PushServiceWatchdog.schedule(context.applicationContext)
            }
        }
    }

    private companion object {
        const val TAG = "MochiPushBoot"
    }
}
