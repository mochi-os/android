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
 * Periodic safety net that restarts [PushService] if a hostile OEM
 * killed it.
 *
 * Background: Samsung One UI (and similar from Xiaomi / Huawei /
 * OnePlus / Oppo / Vivo) aggressively kills foreground services
 * ~10 min after the screen turns off, despite
 * FOREGROUND_SERVICE_TYPE_SPECIAL_USE. Empirically confirmed on
 * Samsung S24 Ultra: FOREGROUND_SERVICE_STOP fired 10 min after
 * the last user interaction. WorkManager is JobScheduler-backed
 * and OEMs honour it better than raw FG services, so this watchdog
 * fires on its 15-minute cadence (the platform minimum for
 * PeriodicWorkRequest) and re-launches PushService if needed.
 *
 * This raises the floor for non-whitelisted users but cannot
 * substitute for the OEM whitelist — under deep Doze the job
 * itself is deferred, so worst-case latency between an OEM kill
 * and our re-spawn is "next maintenance window," which can be
 * several hours.
 *
 * Idempotent: starting an already-running service is a no-op via
 * [PushService.start] (Context.startForegroundService just calls
 * onStartCommand again, which connect() guards with putIfAbsent).
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
         * Schedule the watchdog. Call from the host Application's onCreate.
         * Uses KEEP policy so reschedules from later Application starts
         * don't reset the timer.
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
