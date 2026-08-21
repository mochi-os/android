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
 * Daily poll of `packages.mochi-os.org/android/versions.json`. A newer
 * production version is staged in `cacheDir/updates/` and recorded as
 * `pending_version`, which [UpdateInstaller.promptIfPending] offers on the next
 * foreground entry.
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

        // The APK pull drops mid-stream on mobile data, so retry, each attempt
        // resuming from the partial. The budget is deliberately generous: 3
        // attempts surfaced a hard failure at ~90% on a drop-prone link.
        private const val DOWNLOAD_ATTEMPTS = 6
        private const val DOWNLOAD_RETRY_DELAY_MS = 2_000L

        // The download belongs to the process, not to the dialog that asked for
        // it: download() is blocking IO that cancellation cannot land on, and a
        // second writer appending to the same partial produces a corrupt APK.
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        // One download at a time, whatever started it - the About dialog, a
        // second dialog, or the daily worker.
        private val gate = Mutex()

        private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)

        /**
         * Live state of the update download, shared across the process so a
         * dialog attaches to a transfer already in flight.
         */
        val state: StateFlow<DownloadState> = _state.asStateFlow()

        // The download in flight, so a second request joins it rather than
        // starting another.
        @Volatile
        private var active: Job? = null

        /**
         * Start the APK download and return its job; a second call while one
         * runs returns the same job. Cancelling a join does not touch the
         * download.
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
         * Idempotent daily schedule; call from the host Application's onCreate.
         * Cancels any existing work when the APK came from an app store.
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
         * On-demand check that polls only versions.json, never the ~40 MB APK.
         * The caller downloads, so it can run it in the foreground -
         * WorkManager's network is throttled as background data on many phones.
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
         * Continue the download in the background, resuming any partial. The
         * in-process download survives the dialog but not the process; this
         * does, so the transfer resumes on the next start rather than the daily
         * poll.
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
         * Run one check-and-stage cycle inline: version poll plus the resumable
         * download, publishing progress on [state]. Wrapped in Dispatchers.IO,
         * as OkHttp's execute() blocks and callers may be on the main
         * dispatcher.
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

            // Everything below writes the APK, so it runs under the gate: two
            // callers appending to one partial produce a file of the right
            // length and garbage bytes. The waiter re-checks, usually finding
            // it already staged.
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

                // Resumable: each attempt continues from the partial via a
                // Range request, and the partial is kept on failure so progress
                // accumulates across attempts and across checkNow calls.
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
         * True when [version]'s APK is on disk and still matches its declared
         * size and digest. Damage after staging would otherwise be re-offered
         * for good, since later checks short-circuit on pending == latest; a
         * mismatch discards it.
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
         * Fetch and parse versions.json: the production track's version and the
         * release entry (file, size, SHA-256). A missing entry yields a null
         * [Manifest.release], which refuses to stage rather than downloading
         * blind.
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
         * Download or resume the APK to [target] with a Range request; true
         * once it is complete and matches [release]'s SHA-256, a drop throws
         * and keeps the partial. The versioned URL stops a resume splicing two
         * different artifacts.
         */
        private fun download(
            target: File,
            release: Release,
            onProgress: (downloaded: Long, total: Long) -> Unit,
        ): Boolean {
            target.parentFile?.mkdirs()
            val have = if (target.exists()) target.length() else 0L
            // Already at the declared length: verify what is on disk rather
            // than requesting a range beyond the end, which answers 416.
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
         * True when [target] matches the manifest's length and digest. Hashes
         * the finished file, not the stream: a resume only streams the tail. A
         * failure deletes the file - no resume repairs it, and it must never be
         * installed.
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

        // versions.json poll. callTimeout caps the whole call: readTimeout
        // alone only bounds the gap between packets, so a connection that
        // stalls after connecting would leave the "Check for updates" button
        // spinning.
        private fun metaClient(): OkHttpClient = OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        // ~40 MB resumable APK pull. readTimeout is the real guard - it aborts
        // a dead connection so the retry loop can resume - while callTimeout
        // stays generous: a tight cap strands attempts that are still making
        // progress.
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
 * The production track's version and the release describing its artifact. A
 * null [release] blocks the download rather than staging blind.
 */
internal data class Manifest(val version: String, val release: Release?)

/**
 * One downloadable artifact from versions.json. [file] is a bare name relative
 * to the platform directory.
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
 * [UpdateChecker.state]. It outlives the dialog that started it.
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
