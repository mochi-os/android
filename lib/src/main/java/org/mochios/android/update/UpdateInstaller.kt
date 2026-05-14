package org.mochios.android.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider

/**
 * Hand a pre-downloaded APK off to the system installer. Called from the
 * host Activity's onResume. Android always shows a confirmation dialog
 * for sideloaded installs ("Update Mochi?"); we can't suppress it, but we
 * can pre-download in the background so the user sees only that single
 * dialog instead of the browser → file picker → installer chain.
 */
object UpdateInstaller {

    private const val TAG = "MochiUpdateInstall"
    private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".updates"

    /**
     * If a newer-than-current APK is staged in cacheDir/updates/, launches
     * the system installer with it. Idempotent — called every onResume; the
     * stage flag is cleared once the install dialog has been launched so a
     * cancelled install doesn't re-trigger on every screen unlock.
     *
     * If the staged version turns out to match (or be older than) the
     * currently-installed APK — common right after a successful upgrade —
     * the staged file and pending flag are cleared.
     */
    fun promptIfPending(activity: Activity) {
        val ctx = activity.applicationContext
        val prefs = UpdateChecker.prefs(ctx)
        val pending = prefs.getString(UpdateChecker.KEY_PENDING, "")
            ?.takeIf { it.isNotBlank() } ?: return

        val current = UpdateChecker.currentVersionName(ctx)
        if (current != null && UpdateChecker.compareVersions(pending, current) <= 0) {
            // Already installed (either by this prompt or out-of-band) — clean up.
            clear(ctx)
            return
        }

        val apk = UpdateChecker.apkFile(ctx, pending)
        if (!apk.exists() || apk.length() == 0L) {
            Log.w(TAG, "Pending update $pending has no APK on disk; clearing")
            clear(ctx)
            return
        }

        // Android 8+ requires per-app "install unknown apps" consent on top
        // of the manifest REQUEST_INSTALL_PACKAGES permission. If we don't
        // have it yet, the system installer just bounces the user with a
        // generic "For your security…" dialog and no clear path forward.
        // Send them straight to the toggle for this app instead and bail
        // out — the next onResume after they grant it (and return) retries.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !ctx.packageManager.canRequestPackageInstalls()) {
            try {
                val grant = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + ctx.packageName),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(grant)
                Log.i(TAG, "Prompting user to grant install-unknown-apps for $pending")
            } catch (e: Exception) {
                // Some OEM builds don't expose the per-app screen; fall back
                // to the global one so the user can find the toggle manually.
                Log.w(TAG, "Per-app install-sources screen unavailable: ${e.message}")
                try {
                    activity.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) { /* nothing more to try */ }
            }
            return
        }

        // Mark as launched *before* starting the activity. The system dialog
        // shows independently of our Activity lifecycle, and we'd otherwise
        // re-prompt on every onResume cycle while the dialog sits there.
        prefs.edit().putBoolean(KEY_PROMPTED, true).apply()

        try {
            val uri = FileProvider.getUriForFile(
                ctx,
                ctx.packageName + FILE_PROVIDER_AUTHORITY_SUFFIX,
                apk,
            )
            val install = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(install)
            Log.i(TAG, "Launched installer for $pending")
        } catch (e: Exception) {
            Log.w(TAG, "Could not launch installer: ${e.message}")
        }
    }

    private fun clear(ctx: Context) {
        val prefs = UpdateChecker.prefs(ctx)
        prefs.edit()
            .remove(UpdateChecker.KEY_PENDING)
            .remove(UpdateChecker.KEY_PENDING_PATH)
            .remove(KEY_PROMPTED)
            .apply()
        UpdateChecker.updatesDir(ctx).listFiles()?.forEach { it.delete() }
    }

    private const val KEY_PROMPTED = "prompted"
}
