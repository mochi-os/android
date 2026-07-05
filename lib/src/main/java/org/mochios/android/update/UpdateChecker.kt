// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.update

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.appendingSink
import okio.buffer
import okio.sink
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Daily HTTPS poll of `packages.mochi-os.org/android/versions.json`. When a
 * newer `tracks.production` version is observed (numeric component-wise
 * compare against this APK's PackageInfo.versionName), the new APK is
 * pre-downloaded into `cacheDir/updates/` and a `pending_version` preference
 * is recorded. [UpdateInstaller.promptIfPending] (called from the host
 * Activity's onResume) then triggers the system installer on the user's
 * next foreground entry — no notification, no browser, no manual file
 * lookup. Android still shows its own "Update Mochi?" confirmation; that's
 * unavoidable for sideloaded apps.
 *
 * The check-and-stage logic is also exposed as [UpdateChecker.checkNow] so
 * the About dialog's "Check for updates" button can run it on demand.
 */
class UpdateChecker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        when (checkNow(applicationContext)) {
            CheckOutcome.UpToDate, CheckOutcome.UpdateStaged -> Result.success()
            CheckOutcome.NetworkError, CheckOutcome.DownloadFailed -> Result.retry()
        }

    companion object {
        private const val TAG = "MochiUpdateCheck"
        private const val WORK_NAME = "mochi_update_check"
        // Background download used to finish an update if the dialog is closed
        // mid-download (the foreground inline path is the primary one).
        private const val ONESHOT_WORK = "mochi_update_check_oneshot"
        const val PREFS = "mochi_update"
        const val KEY_PENDING = "pending_version"
        const val KEY_PENDING_PATH = "pending_path"
        private const val TRACK = "production"
        private const val VERSIONS_URL = "https://packages.mochi-os.org/android/versions.json"
        private const val APK_URL = "https://packages.mochi-os.org/android/mochi.apk"

        // The ~40 MB APK pull is the fragile step: on mobile data a single
        // transfer often drops mid-stream (throttling, cell handoff, packet
        // loss) or simply crawls. The periodic worker masks this by returning
        // Result.retry(), but checkNow's on-demand caller (the About dialog) is
        // one-shot — so retry the download here too, each attempt resuming from
        // the partial. Budget is generous because a drop-prone link can need
        // several rounds to finish the tail: 3 attempts once surfaced as a hard
        // "download failed" at ~90% when a couple more would have completed it.
        private const val DOWNLOAD_ATTEMPTS = 6
        private const val DOWNLOAD_RETRY_DELAY_MS = 2_000L

        /**
         * Idempotent daily schedule. Call from the host Application's
         * onCreate. Short-circuits and cancels any previously-scheduled
         * work when the APK was installed from a known app store
         * (Play / F-Droid / …) — the store will deliver updates and our
         * self-installed APK from packages.mochi-os.org would likely fail
         * the signature check anyway.
         */
        fun schedule(context: Context) {
            if (InstallSource.isStoreInstalled(context)) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<UpdateChecker>(
                24, TimeUnit.HOURS,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                request,
            )
        }

        /**
         * Fast on-demand check for the About dialog's "Check for updates" button.
         * Polls ONLY versions.json (never the ~40 MB APK, which would block the
         * button) and reports whether a newer version exists. The actual download
         * is left to the caller so it can run it in the FOREGROUND with live
         * progress: a WorkManager background job's network is throttled as
         * background data on many phones (slow even on fast wifi), so the dialog
         * downloads inline via [checkNow] instead. On [UpdateStatus.Ready] the APK
         * is already staged; on [UpdateStatus.Available] the caller downloads it.
         */
        suspend fun checkForUpdate(context: Context): UpdateStatus = withContext(Dispatchers.IO) {
            val ctx = context.applicationContext
            if (InstallSource.isStoreInstalled(ctx)) return@withContext UpdateStatus.UpToDate
            val current = currentVersionName(ctx) ?: return@withContext UpdateStatus.UpToDate
            val latest = try {
                fetchLatest()
            } catch (e: Exception) {
                Log.i(TAG, "Fetch versions.json failed: ${e.message}")
                return@withContext UpdateStatus.Offline
            } ?: return@withContext UpdateStatus.UpToDate
            if (compareVersions(latest, current) <= 0) {
                clearPending(ctx)
                return@withContext UpdateStatus.UpToDate
            }
            // KEY_PENDING is only written after a complete download, so it == latest
            // means the APK is fully staged and ready to install right now.
            val target = apkFile(ctx, latest)
            val pending = prefs(ctx).getString(KEY_PENDING, "") ?: ""
            if (pending == latest && target.exists() && target.length() > 0) {
                return@withContext UpdateStatus.Ready(latest)
            }
            UpdateStatus.Available(latest)
        }

        /**
         * Continue the APK download in the background (resuming any partial).
         * The dialog calls this only when it's dismissed mid-download, so the
         * foreground inline download isn't simply abandoned — the next time the
         * app comes forward, [UpdateInstaller.promptIfPending] offers to install.
         */
        fun enqueueBackgroundDownload(context: Context) {
            val request = OneTimeWorkRequestBuilder<UpdateChecker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            // KEEP: repeated taps (or an in-flight daily check) don't stack downloads.
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONESHOT_WORK, ExistingWorkPolicy.KEEP, request,
            )
        }

        /**
         * Run one check-and-stage cycle inline (no WorkManager). The full path —
         * version poll AND the resumable APK download (reporting [onProgress]) —
         * used by the periodic worker via [doWork] AND by the About dialog, which
         * runs it in the foreground for full-speed download with a live progress
         * bar.
         *
         * Heavy (blocking IO), so wrapped in Dispatchers.IO — callers can invoke
         * from the main dispatcher (the dialog uses rememberCoroutineScope, which
         * is Main) without tripping NetworkOnMainThreadException; OkHttp's
         * synchronous execute() is blocking. CoroutineWorker.doWork runs off-main
         * on its own, but this entry point may not.
         */
        suspend fun checkNow(
            context: Context,
            onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
        ): CheckOutcome = withContext(Dispatchers.IO) {
            val ctx = context.applicationContext
            if (InstallSource.isStoreInstalled(ctx)) {
                // The store is responsible for updates here. About-dialog
                // "Check for updates" reports UpToDate so the user doesn't
                // get an error when there's nothing for us to do.
                return@withContext CheckOutcome.UpToDate
            }
            val current = currentVersionName(ctx) ?: return@withContext CheckOutcome.UpToDate

            val latest = try {
                fetchLatest()
            } catch (e: Exception) {
                Log.i(TAG, "Fetch versions.json failed: ${e.message}")
                return@withContext CheckOutcome.NetworkError
            } ?: return@withContext CheckOutcome.UpToDate

            if (compareVersions(latest, current) <= 0) {
                Log.d(TAG, "Running $current, latest $latest, nothing to do")
                clearPending(ctx)
                return@withContext CheckOutcome.UpToDate
            }

            val target = apkFile(ctx, latest)
            val prefs = prefs(ctx)
            val pending = prefs.getString(KEY_PENDING, "") ?: ""
            if (pending == latest && target.exists() && target.length() > 0) {
                Log.d(TAG, "$latest already downloaded at ${target.absolutePath}")
                return@withContext CheckOutcome.UpdateStaged
            }

            // Stale entries for prior versions — drop them so cacheDir doesn't
            // accumulate APKs from every release the user ever skipped.
            purgeStale(ctx, keep = latest)

            // Resumable: each attempt continues from the partial file via a
            // Range request rather than restarting, and the partial is KEPT on
            // failure — so a connection that drops every few MB accumulates
            // progress across attempts (and, because the file survives between
            // checkNow calls, across daily polls / repeat button presses) until
            // it completes, instead of forever re-downloading from zero. A
            // newer version landing clears the old partial via purgeStale above.
            var staged = false
            for (attempt in 1..DOWNLOAD_ATTEMPTS) {
                try {
                    if (download(target, onProgress)) {
                        staged = true
                        break
                    }
                    Log.i(TAG, "APK incomplete at ${target.length()} bytes (attempt $attempt/$DOWNLOAD_ATTEMPTS)")
                } catch (e: Exception) {
                    Log.i(TAG, "APK download dropped at ${target.length()} bytes (attempt $attempt/$DOWNLOAD_ATTEMPTS): ${e.message}")
                }
                // Keep the partial — the next attempt resumes from it.
                if (attempt < DOWNLOAD_ATTEMPTS) {
                    // Linear backoff: 2s, then 4s.
                    delay(DOWNLOAD_RETRY_DELAY_MS * attempt)
                }
            }
            if (!staged) {
                // Leave the partial in place on purpose: the next check resumes it.
                return@withContext CheckOutcome.DownloadFailed
            }

            prefs.edit()
                .putString(KEY_PENDING, latest)
                .putString(KEY_PENDING_PATH, target.absolutePath)
                .apply()
            Log.i(TAG, "Update $latest staged at ${target.absolutePath} (running $current)")
            CheckOutcome.UpdateStaged
        }

        private fun fetchLatest(): String? {
            val req = Request.Builder().url(VERSIONS_URL).get().build()
            metaClient().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.i(TAG, "Fetch $VERSIONS_URL: status ${resp.code}")
                    return null
                }
                val body = resp.body?.string().orEmpty()
                val tracks = JSONObject(body).optJSONObject("tracks") ?: return null
                return tracks.optString(TRACK).takeIf { it.isNotBlank() }
            }
        }

        /**
         * Download (or resume) the APK to [target]. Sends a Range request from
         * the current partial length so an interrupted transfer continues
         * rather than restarting. Returns true once the file is complete (its
         * length matches the server's reported total); a mid-stream drop throws,
         * leaving the partial in place for the next attempt to resume from.
         */
        private fun download(
            target: File,
            onProgress: (downloaded: Long, total: Long) -> Unit,
        ): Boolean {
            target.parentFile?.mkdirs()
            val have = if (target.exists()) target.length() else 0L
            val builder = Request.Builder().url(APK_URL).get()
            if (have > 0) builder.header("Range", "bytes=$have-")
            downloadClient().newCall(builder.build()).execute().use { resp ->
                // 206 = resume accepted; 200 = full body (first fetch, or a
                // server that ignored the Range header).
                if (resp.code != 200 && resp.code != 206) {
                    throw IllegalStateException("HTTP ${resp.code}")
                }
                val body = resp.body ?: throw IllegalStateException("empty body")
                val resuming = resp.code == 206 && have > 0
                // Total file size: a 206 reports it as the "/<total>" tail of
                // Content-Range; a 200 reports it as Content-Length.
                val total = if (resuming) {
                    resp.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                } else {
                    body.contentLength().takeIf { it > 0 }
                }
                // Server resent the whole file despite our Range — drop the
                // partial so we don't append a full body onto it.
                if (!resuming && have > 0) target.delete()
                // Stream straight to disk in chunks — append when resuming,
                // truncate when starting fresh — reporting progress as we go (the
                // APK is ~40 MB, so never buffer it all in memory). `total` is the
                // full file size, so downloaded starts at `have` on a resume.
                val out = (if (resuming) target.appendingSink() else target.sink()).buffer()
                out.use { sink ->
                    body.source().use { src ->
                        val chunk = Buffer()
                        var downloaded = if (resuming) have else 0L
                        var lastReported = downloaded
                        while (true) {
                            val read = src.read(chunk, 64L * 1024)
                            if (read == -1L) break
                            sink.write(chunk, read)
                            downloaded += read
                            // Throttle so we don't write the WorkManager progress
                            // row on every 64 KB chunk.
                            if (total != null && downloaded - lastReported >= 512L * 1024) {
                                lastReported = downloaded
                                onProgress(downloaded, total)
                            }
                        }
                        if (total != null) onProgress(downloaded, total)
                    }
                }
                // Complete only when we know the total AND the file matches it —
                // never stage a truncated APK as if it were ready to install.
                return total != null && target.length() == total
            }
        }

        // versions.json poll. A tiny file, so cap the WHOLE call hard with
        // callTimeout. readTimeout alone only bounds the gap between packets —
        // a connection that establishes then stalls (common on mobile data,
        // occasional on wifi) would block for the full readTimeout, leaving the
        // "Check for updates" button spinning. callTimeout bounds connect +
        // write + read + any retries, so the button fails fast and shows an
        // error instead of hanging.
        private fun metaClient(): OkHttpClient = OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        // ~40 MB APK pull, downloaded resumably (see download()). readTimeout is
        // the real guard: it aborts an attempt when the connection goes dead (no
        // bytes for the window) so the retry loop can resume. callTimeout only
        // stops a live-but-pathologically-slow attempt from running away, so it
        // must be generous — a 40 MB pull over slow mobile data is a legitimately
        // long transfer, and a tight cap (4 min) cut off attempts that were still
        // making progress, stranding the download near the end. At 1 hour a
        // single attempt completes even at ~11 KB/s; the cap never strands a real
        // download, and it never makes the user wait on a dead one — readTimeout
        // fails that within 60 s. Range resume carries progress across attempts.
        private fun downloadClient(): OkHttpClient = OkHttpClient.Builder()
            .callTimeout(1, TimeUnit.HOURS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        private fun purgeStale(ctx: Context, keep: String) {
            val dir = updatesDir(ctx)
            val keepFile = apkFile(ctx, keep).name
            dir.listFiles()?.forEach { f ->
                if (f.name != keepFile) {
                    f.delete()
                }
            }
        }

        private fun clearPending(ctx: Context) {
            val prefs = prefs(ctx)
            if (!prefs.contains(KEY_PENDING)) return
            prefs.edit().remove(KEY_PENDING).remove(KEY_PENDING_PATH).apply()
            purgeStale(ctx, keep = "") // empty keep → delete everything
        }

        internal fun prefs(ctx: Context): SharedPreferences =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        internal fun updatesDir(ctx: Context): File =
            File(ctx.cacheDir, "updates")

        internal fun apkFile(ctx: Context, version: String): File =
            File(updatesDir(ctx), "mochi-$version.apk")

        internal fun currentVersionName(ctx: Context): String? = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        } catch (_: Exception) {
            null
        }

        /**
         * Numeric component-wise compare. "0.10" > "0.9", "1.0" > "0.99",
         * etc. Mirrors core's version_compare.
         */
        fun compareVersions(a: String, b: String): Int {
            val aParts = a.split(".", "-").mapNotNull { it.toIntOrNull() }
            val bParts = b.split(".", "-").mapNotNull { it.toIntOrNull() }
            val len = maxOf(aParts.size, bParts.size)
            for (i in 0 until len) {
                val ai = aParts.getOrElse(i) { 0 }
                val bi = bParts.getOrElse(i) { 0 }
                if (ai != bi) return ai - bi
            }
            return 0
        }
    }
}

/**
 * Outcome of one [UpdateChecker.checkNow] cycle (the worker's full check +
 * download). The worker maps these to Result.success / Result.retry.
 */
enum class CheckOutcome {
    UpToDate,
    UpdateStaged,
    NetworkError,
    DownloadFailed,
}

/** Outcome of an on-demand [UpdateChecker.checkForUpdate], for the About dialog. */
sealed interface UpdateStatus {
    /** Already on the latest version (or store-managed). */
    data object UpToDate : UpdateStatus
    /** Couldn't reach the version endpoint. */
    data object Offline : UpdateStatus
    /** Newer [version] is already downloaded and ready to install. */
    data class Ready(val version: String) : UpdateStatus
    /** Newer [version] is available; the caller should download it. */
    data class Available(val version: String) : UpdateStatus
}
