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
 * Restarts [PushService] after the device boots so the user doesn't have
 * to open the Mochi shell every reboot for notifications to resume.
 *
 * Wired only by the shell app's manifest (declares this receiver alongside
 * RECEIVE_BOOT_COMPLETED + LOCKED_BOOT_COMPLETED permissions). Per-app
 * apps deliberately do not declare it — only the distributor host needs
 * to wake on boot.
 *
 * Android 12+ restriction: a BroadcastReceiver cannot start a foreground
 * service from background. We start with [Context.startForegroundService]
 * during the BOOT_COMPLETED intent's `goAsync` window, which is one of
 * the explicit allowed contexts for FG service starts. If that proves
 * unreliable on some OEMs, fall back to a one-shot WorkManager job
 * (deferred — the simple direct-start path covers the common case).
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
