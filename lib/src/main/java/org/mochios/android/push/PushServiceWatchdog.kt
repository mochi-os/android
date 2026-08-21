// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Periodic safety net that restarts [PushService] after an OEM kills it -
 * Samsung and similar drop foreground services ~10 minutes after screen-off.
 * WorkManager is honoured more reliably; 15 minutes is the platform minimum and
 * deep Doze defers even that.
 */
class PushServiceWatchdog(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val transport = PushTransport.current(applicationContext)
            if (transport == PushTransport.TRANSPORT_FCM) {
                // FCM is doing delivery; nothing for the UP distributor to do.
                Log.d(TAG, "Transport=fcm; PushService deliberately not running")
                return Result.success()
            }
            if (isPushServiceRunning(applicationContext)) {
                Log.d(TAG, "PushService alive; nothing to do")
            } else {
                Log.i(TAG, "PushService is dead; restarting")
                PushService.start(applicationContext)
            }
            Result.success()
        } catch (e: Throwable) {
            // startForegroundService can throw ForegroundServiceStartNotAllowedException
            // on Android 12+ if the app has no recent background-start exemption.
            // We retry next cycle — the next foreground entry will refresh it.
            Log.w(TAG, "Could not restart PushService: ${e.message}")
            Result.retry()
        }
    }

    private fun isPushServiceRunning(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        @Suppress("DEPRECATION") // Returns this app's own services only — that's all we need.
        return am.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == PushService::class.java.name }
    }

    companion object {
        const val TAG = "MochiPushWatchdog"
        const val WORK_NAME = "mochi_push_watchdog"

        /**
         * Schedule the watchdog; call from the host Application's onCreate.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PushServiceWatchdog>(
                15, TimeUnit.MINUTES,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
