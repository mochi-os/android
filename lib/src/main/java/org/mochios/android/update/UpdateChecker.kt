// Copyright © 2026 Mochisoft OÜ
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.appendingSink
import okio.buffer
import okio.sink
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
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

    override suspend fun doWork(): Result {
        // A foreground download already holds the gate. Waiting on it would
        // burn this worker's ten-minute window (and a wakelock) doing nothing,
        // so back off and let WorkManager re-run us once it has finished.
        if (state.value is DownloadState.Running) return Result.retry()
        return when (checkNow(applicationContext)) {
            CheckOutcome.UpToDate, CheckOutcome.UpdateStaged -> Result.success()
            CheckOutcome.NetworkError, CheckOutcome.DownloadFailed -> Result.retry()
        }
    }

    companion object {
        private const val TAG = "MochiUpdateCheck"
        private const val WORK_NAME = "mochi_update_check"
        // Background download used to finish an update if the process is killed
        // mid-download (the foreground inline path is the primary one).
        private const val ONESHOT_WORK = "mochi_update_check_oneshot"
        const val PREFS = "mochi_update"
        const val KEY_PENDING = "pending_version"
        const val KEY_PENDING_PATH = "pending_path"
        // Size and digest of the APK as staged. Re-checked before the file is
        // offered to the installer, so a pending update that was damaged after
        // it was verified is discarded rather than handed over as good.
        const val KEY_PENDING_SIZE = "pending_size"
        const val KEY_PENDING_SHA = "pending_sha256"
        private const val TRACK = "production"
        private const val BASE_URL = "https://packages.mochi-os.org/android"
        private const val VERSIONS_URL = "$BASE_URL/versions.json"

        // Absolute ceiling on a download, whatever the manifest claims. The
        // APK is ~40 MB; this only bounds how much cache a compromised or
        // simply wrong manifest can consume.
        private const val APK_MAXIMUM = 256L * 1024 * 1024

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

        // The download belongs to the process, not to whichever dialog asked
        // for it. It used to run in the About dialog's composition scope, so
        // one stray tap outside the dialog cancelled that scope - while the
        // transfer itself carried on regardless, because download() is blocking
        // IO with no suspension point for cancellation to land on. The user got
        // an invisible download, and a second "Check for updates" press started
        // a SECOND writer appending to the same partial file. Interleaved
        // appends produce an APK the length checks accept and the system
        // installer rejects as corrupt.
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // One download at a time, whatever started it - the About dialog, a
        // second dialog, or the daily worker.
        private val gate = Mutex()

        private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)

        /**
         * Live state of the update download, shared across the process so any
         * dialog that opens attaches to a transfer already in flight instead of
         * starting its own.
         */
        val state: StateFlow<DownloadState> = _state.asStateFlow()

        // The download in flight, so a second request joins it rather than
        // starting another.
        @Volatile
        private var active: Job? = null

        /**
         * Start the APK download, owned by the process, and return its job. A
         * second call while one is running returns the SAME job rather than
         * starting another. Callers may join the job to learn when it settles
         * — cancelling that join (closing the dialog) does not touch the
         * download, which belongs to this object's own scope.
         */
        @Synchronized
        fun startDownload(context: Context): Job {
            active?.takeIf { it.isActive }?.let { return it }
            val ctx = context.applicationContext
            val job = scope.launch {
                try {
                    checkNow(ctx)
                } catch (e: Exception) {
                    // Nothing is awaiting this coroutine, so an escaping
                    // exception would reach the default handler and take the
                    // app down. Report it and leave the button usable instead.
                    Log.w(TAG, "Update download failed: ${e.message}")
                    _state.value = DownloadState.Idle
                }
            }
            active = job
            return job
        }

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
            val manifest = try {
                fetchManifest()
            } catch (e: Exception) {
                Log.i(TAG, "Fetch versions.json failed: ${e.message}")
                return@withContext UpdateStatus.Offline
            } ?: return@withContext UpdateStatus.UpToDate
            val latest = manifest.version
            if (compareVersions(latest, current) <= 0) {
                clearPending(ctx)
                return@withContext UpdateStatus.UpToDate
            }
            // Re-verify rather than trusting the preference: staged() confirms
            // the file still hashes to the manifest's digest, and discards it if
            // not, so a damaged APK cannot be offered here for good.
            if (staged(ctx, latest, manifest.release)) return@withContext UpdateStatus.Ready(latest)
            UpdateStatus.Available(latest)
        }

        /**
         * Continue the APK download in the background (resuming any partial).
         * The dialog calls this when it's dismissed mid-download: the in-process
         * download survives the dialog, but not the process being killed, and a
         * WorkManager request does — so the transfer resumes on the next app
         * start rather than waiting for the daily poll.
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
         * version poll AND the resumable APK download — used by the periodic
         * worker via [doWork] and by [startDownload], which the About dialog
         * calls so the download runs in the foreground at full speed (a
         * WorkManager background job's network is throttled as background data
         * on many phones — slow even on fast wifi). Progress is published on
         * [state] rather than returned, so every observer sees the one download.
         *
         * Heavy (blocking IO), so wrapped in Dispatchers.IO — callers can invoke
         * from the main dispatcher without tripping NetworkOnMainThreadException;
         * OkHttp's synchronous execute() is blocking. CoroutineWorker.doWork runs
         * off-main on its own, but this entry point may not.
         */
        suspend fun checkNow(context: Context): CheckOutcome = withContext(Dispatchers.IO) {
            val ctx = context.applicationContext
            if (InstallSource.isStoreInstalled(ctx)) {
                // The store is responsible for updates here. About-dialog
                // "Check for updates" reports UpToDate so the user doesn't
                // get an error when there's nothing for us to do.
                return@withContext CheckOutcome.UpToDate
            }
            val current = currentVersionName(ctx) ?: return@withContext CheckOutcome.UpToDate

            val manifest = try {
                fetchManifest()
            } catch (e: Exception) {
                Log.i(TAG, "Fetch versions.json failed: ${e.message}")
                return@withContext CheckOutcome.NetworkError
            } ?: return@withContext CheckOutcome.UpToDate
            val latest = manifest.version

            if (compareVersions(latest, current) <= 0) {
                Log.d(TAG, "Running $current, latest $latest, nothing to do")
                clearPending(ctx)
                _state.value = DownloadState.Idle
                return@withContext CheckOutcome.UpToDate
            }

            // Everything from here writes the APK file, so it runs under the
            // gate: two callers appending to the same partial produce a file of
            // the right length whose bytes are garbage, which the system
            // installer then rejects as corrupt. Waiting here is cancellable,
            // and the waiter re-checks below — normally finding the file already
            // staged by whoever held the gate.
            gate.withLock {
                val target = apkFile(ctx, latest)
                if (staged(ctx, latest, manifest.release)) {
                    Log.d(TAG, "$latest already downloaded at ${target.absolutePath}")
                    _state.value = DownloadState.Staged(latest)
                    return@withLock CheckOutcome.UpdateStaged
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
                val release = manifest.release
                if (release == null) {
                    // No integrity data means we cannot tell a good APK from a
                    // truncated or spliced one, and this file goes straight to the
                    // system installer. Refuse rather than stage it blind.
                    Log.w(TAG, "Manifest has no verifiable release entry for $latest; not downloading")
                    _state.value = DownloadState.Failed(latest)
                    return@withLock CheckOutcome.DownloadFailed
                }
                if (release.size > APK_MAXIMUM) {
                    Log.w(TAG, "Manifest size ${release.size} for $latest exceeds maximum $APK_MAXIMUM")
                    _state.value = DownloadState.Failed(latest)
                    return@withLock CheckOutcome.DownloadFailed
                }

                _state.value = DownloadState.Running(latest, target.length().coerceAtMost(release.size), release.size)
                var complete = false
                for (attempt in 1..DOWNLOAD_ATTEMPTS) {
                    try {
                        val done = download(target, release) { downloaded, total ->
                            _state.value = DownloadState.Running(latest, downloaded, total)
                        }
                        if (done) {
                            complete = true
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
                if (!complete) {
                    // Leave the partial in place on purpose: the next check resumes it.
                    _state.value = DownloadState.Failed(latest)
                    return@withLock CheckOutcome.DownloadFailed
                }

                prefs(ctx).edit()
                    .putString(KEY_PENDING, latest)
                    .putString(KEY_PENDING_PATH, target.absolutePath)
                    .putLong(KEY_PENDING_SIZE, release.size)
                    .putString(KEY_PENDING_SHA, release.sha256)
                    .apply()
                Log.i(TAG, "Update $latest staged at ${target.absolutePath} (running $current)")
                _state.value = DownloadState.Staged(latest)
                CheckOutcome.UpdateStaged
            }
        }

        /**
         * True when [version]'s APK is on disk and still matches the size and
         * digest it is supposed to have — [release] from the manifest when we
         * have it, otherwise what was recorded at stage time. Re-checking
         * matters because the staged file goes straight to the system installer,
         * which will not tell us why it refused it: an APK damaged after it was
         * verified otherwise stays pending for good, because every later check
         * short-circuits on "pending == latest" and re-offers the same bad
         * bytes. A mismatch discards the file and the preference so the next
         * check downloads it again from scratch.
         */
        private fun staged(ctx: Context, version: String, release: Release?): Boolean {
            val prefs = prefs(ctx)
            if ((prefs.getString(KEY_PENDING, "") ?: "") != version) return false
            val apk = apkFile(ctx, version)
            val size = release?.size ?: prefs.getLong(KEY_PENDING_SIZE, 0L)
            val sum = release?.sha256 ?: prefs.getString(KEY_PENDING_SHA, "") ?: ""
            if (size > 0 && sum.isNotBlank() && apk.length() == size &&
                sha256(apk).equals(sum, ignoreCase = true)
            ) {
                return true
            }
            Log.w(TAG, "Staged $version no longer verifies at ${apk.length()} bytes; discarding")
            apk.delete()
            prefs.edit()
                .remove(KEY_PENDING).remove(KEY_PENDING_PATH)
                .remove(KEY_PENDING_SIZE).remove(KEY_PENDING_SHA)
                .apply()
            return false
        }

        /**
         * Fetch and parse versions.json. Returns the production track's
         * version together with the release entry describing its artifact:
         * the exact file name, its size, and its SHA-256. A manifest without
         * a matching release entry yields a null [Manifest.release], which
         * [download] treats as "cannot verify" and refuses to stage.
         */
        private fun fetchManifest(): Manifest? {
            val req = Request.Builder().url(VERSIONS_URL).get().build()
            metaClient().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.i(TAG, "Fetch $VERSIONS_URL: status ${resp.code}")
                    return null
                }
                val body = resp.body?.string().orEmpty()
                val root = JSONObject(body)
                val tracks = root.optJSONObject("tracks") ?: return null
                val version = tracks.optString(TRACK).takeIf { it.isNotBlank() } ?: return null
                val entry = root.optJSONObject("releases")?.optJSONObject(version)
                val release = entry?.let {
                    val file = it.optString("file")
                    val size = it.optLong("size")
                    val sha256 = it.optString("sha256")
                    // A file name, never a path — the manifest must not be
                    // able to point the download at another host or directory.
                    if (file.isBlank() || file.contains('/') || file.contains('\\') ||
                        size <= 0 || sha256.isBlank()
                    ) {
                        null
                    } else {
                        Release(file, size, sha256)
                    }
                }
                return Manifest(version, release)
            }
        }

        /**
         * Download (or resume) the APK to [target]. Sends a Range request from
         * the current partial length so an interrupted transfer continues
         * rather than restarting. Returns true once the file is complete AND
         * its SHA-256 matches [release]; a mid-stream drop throws, leaving the
         * partial in place for the next attempt to resume from.
         *
         * The URL carries the version, so the bytes a resume appends always
         * belong to the same artifact as the bytes already on disk. Against the
         * old unversioned path, a release landing mid-download appended the
         * tail of the new APK onto the partial of the old one, producing a
         * spliced file of exactly the expected length — accepted as complete
         * here and then rejected by the system installer as a bad signature.
         */
        private fun download(
            target: File,
            release: Release,
            onProgress: (downloaded: Long, total: Long) -> Unit,
        ): Boolean {
            target.parentFile?.mkdirs()
            val have = if (target.exists()) target.length() else 0L
            // Already at (or past) the declared length — there is nothing left
            // to fetch, so check what is on disk instead of asking for a range
            // beyond the end, which answers 416 and would fail every attempt.
            // Reached whenever a previously staged file failed re-verification
            // and has to be judged again from scratch.
            if (have >= release.size) return verify(target, release)
            val builder = Request.Builder().url("$BASE_URL/${release.file}").get()
            if (have > 0) builder.header("Range", "bytes=$have-")
            downloadClient().newCall(builder.build()).execute().use { resp ->
                // 206 = resume accepted; 200 = full body (first fetch, or a
                // server that ignored the Range header).
                if (resp.code != 200 && resp.code != 206) {
                    throw IllegalStateException("HTTP ${resp.code}")
                }
                val body = resp.body ?: throw IllegalStateException("empty body")
                val resuming = resp.code == 206 && have > 0
                // The manifest is authoritative for the total, not the server's
                // headers — it is the size the digest below belongs to.
                val total = release.size
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
                        while (downloaded < total) {
                            val read = src.read(chunk, 64L * 1024)
                            if (read == -1L) break
                            sink.write(chunk, read)
                            downloaded += read
                            // Throttle so we don't write the WorkManager progress
                            // row on every 64 KB chunk.
                            if (downloaded - lastReported >= 512L * 1024) {
                                lastReported = downloaded
                                onProgress(downloaded, total)
                            }
                        }
                        onProgress(downloaded, total)
                    }
                }
                return verify(target, release)
            }
        }

        /**
         * True when [target] is exactly the length the manifest declares and
         * hashes to its digest. Hashes the finished file rather than the
         * stream: a resumed download only streams the tail, so bytes written by
         * earlier attempts would otherwise never be checked. A file that fails
         * either test is deleted — no resume can repair it, and it must never
         * reach the installer.
         */
        private fun verify(target: File, release: Release): Boolean {
            if (target.length() != release.size) {
                // Overshoot means more arrived than the manifest declared; the
                // file is unusable and must not be resumed.
                if (target.length() > release.size) target.delete()
                return false
            }
            val sum = sha256(target)
            if (!sum.equals(release.sha256, ignoreCase = true)) {
                Log.w(TAG, "APK sha256 $sum does not match manifest ${release.sha256}; discarding")
                target.delete()
                return false
            }
            return true
        }

        /** Lowercase hex SHA-256 of a file, streamed so a ~40 MB APK never lands in memory. */
        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
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
            prefs.edit()
                .remove(KEY_PENDING).remove(KEY_PENDING_PATH)
                .remove(KEY_PENDING_SIZE).remove(KEY_PENDING_SHA)
                .apply()
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
 * The production track's version, with the release entry describing its
 * artifact. [release] is null when the manifest carries no verifiable entry
 * for that version, which blocks the download rather than staging blind.
 */
internal data class Manifest(val version: String, val release: Release?)

/**
 * One downloadable artifact, as declared in versions.json. [file] is a bare
 * file name relative to the platform directory, so the manifest names the
 * artifact instead of the client guessing it.
 */
internal data class Release(val file: String, val size: Long, val sha256: String)

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

/**
 * State of the one APK download the process runs at a time, published on
 * [UpdateChecker.state]. It outlives the dialog that started it, so closing
 * the dialog does not lose the transfer and re-opening it shows the same
 * progress rather than offering to start again.
 */
sealed interface DownloadState {
    /** Nothing downloading. */
    data object Idle : DownloadState
    /** [version] is downloading; [downloaded] of [total] bytes are on disk. */
    data class Running(val version: String, val downloaded: Long, val total: Long) : DownloadState {
        val percent: Int
            get() = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
    }
    /** [version] is downloaded, verified, and ready for the installer. */
    data class Staged(val version: String) : DownloadState
    /** [version] could not be downloaded; the partial is kept for a later resume. */
    data class Failed(val version: String) : DownloadState
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
