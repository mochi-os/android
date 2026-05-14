package org.mochios.android.update

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request
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
        const val PREFS = "mochi_update"
        const val KEY_PENDING = "pending_version"
        const val KEY_PENDING_PATH = "pending_path"
        private const val TRACK = "production"
        private const val VERSIONS_URL = "https://packages.mochi-os.org/android/versions.json"
        private const val APK_URL = "https://packages.mochi-os.org/android/mochi.apk"

        /**
         * Idempotent periodic schedule. Call from the host Application's
         * onCreate. Currently set to 1h for testing the update flow; revert
         * to 24h once the path is validated.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateChecker>(
                1, TimeUnit.HOURS,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                request,
            )
        }

        /**
         * Run one check-and-stage cycle inline (no WorkManager). Same logic
         * the periodic worker uses; callable from a Compose coroutine so the
         * About dialog's button can show fresh status without waiting for the
         * next scheduled fire. Idempotent and safe to call from any context.
         */
        suspend fun checkNow(context: Context): CheckOutcome {
            val ctx = context.applicationContext
            val current = currentVersionName(ctx) ?: return CheckOutcome.UpToDate

            val latest = try {
                fetchLatest()
            } catch (e: Exception) {
                Log.i(TAG, "Fetch versions.json failed: ${e.message}")
                return CheckOutcome.NetworkError
            } ?: return CheckOutcome.UpToDate

            if (compareVersions(latest, current) <= 0) {
                Log.d(TAG, "Running $current, latest $latest, nothing to do")
                clearPending(ctx)
                return CheckOutcome.UpToDate
            }

            val target = apkFile(ctx, latest)
            val prefs = prefs(ctx)
            val pending = prefs.getString(KEY_PENDING, "") ?: ""
            if (pending == latest && target.exists() && target.length() > 0) {
                Log.d(TAG, "$latest already downloaded at ${target.absolutePath}")
                return CheckOutcome.UpdateStaged
            }

            // Stale entries for prior versions — drop them so cacheDir doesn't
            // accumulate APKs from every release the user ever skipped.
            purgeStale(ctx, keep = latest)

            try {
                download(target)
            } catch (e: Exception) {
                Log.i(TAG, "APK download failed: ${e.message}")
                target.delete()
                return CheckOutcome.DownloadFailed
            }
            if (!target.exists() || target.length() == 0L) {
                Log.w(TAG, "APK download produced empty file")
                target.delete()
                return CheckOutcome.DownloadFailed
            }

            prefs.edit()
                .putString(KEY_PENDING, latest)
                .putString(KEY_PENDING_PATH, target.absolutePath)
                .apply()
            Log.i(TAG, "Update $latest staged at ${target.absolutePath} (running $current)")
            return CheckOutcome.UpdateStaged
        }

        private fun fetchLatest(): String? {
            val req = Request.Builder().url(VERSIONS_URL).get().build()
            httpClient().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.i(TAG, "Fetch $VERSIONS_URL: status ${resp.code}")
                    return null
                }
                val body = resp.body?.string().orEmpty()
                val tracks = JSONObject(body).optJSONObject("tracks") ?: return null
                return tracks.optString(TRACK).takeIf { it.isNotBlank() }
            }
        }

        private fun download(target: File) {
            target.parentFile?.mkdirs()
            val req = Request.Builder().url(APK_URL).get().build()
            httpClient().newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("HTTP ${resp.code}")
                }
                val body = resp.body ?: throw IllegalStateException("empty body")
                // Stream straight to disk — APK is ~30 MB, don't buffer in memory.
                target.sink().buffer().use { out ->
                    body.source().use { src -> out.writeAll(src) }
                }
            }
        }

        private fun httpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // Download timeout is permissive — phones on slow networks should be
            // able to finish a 30 MB pull.
            .readTimeout(5, TimeUnit.MINUTES)
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
 * Outcome of one [UpdateChecker.checkNow] cycle. The About dialog renders
 * different status text per outcome; the worker just maps these to
 * Result.success / Result.retry.
 */
enum class CheckOutcome {
    UpToDate,
    UpdateStaged,
    NetworkError,
    DownloadFailed,
}
