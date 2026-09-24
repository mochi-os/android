// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

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
 * Offer a pre-downloaded APK and, when the user says so, hand it to the system
 * installer. The installer is never launched unasked: a Samsung with Auto
 * Blocker on refuses it, and the refusal would otherwise greet the user on
 * every return to the app. Android's own "Update Mochi?" confirmation for a
 * sideloaded install cannot be suppressed.
 */
object UpdateInstaller {

    private const val TAG = "MochiUpdateInstall"
    private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".updates"

    /**
     * The staged version to offer the user, or null. Called from the host
     * Activity's onResume: a version the user has already answered is not
     * offered again until a newer one is staged, unless [force] - the About
     * dialog, where they asked explicitly.
     */
    fun pending(context: Context, force: Boolean = false): String? {
        val ctx = context.applicationContext
        if (InstallSource.isStoreInstalled(ctx)) {
            // Belt and braces: UpdateChecker should already have skipped
            // the download, but if a pending APK is sitting in cacheDir
            // from a previous non-store install + later store reinstall,
            // never offer it.
            return null
        }
        val prefs = UpdateChecker.prefs(ctx)
        val staged = prefs.getString(UpdateChecker.KEY_PENDING, "")
            ?.takeIf { it.isNotBlank() } ?: return null

        val current = UpdateChecker.currentVersionName(ctx)
        if (current != null && UpdateChecker.compareVersions(staged, current) <= 0) {
            // Already installed (either by this prompt or out-of-band) — clean up.
            clear(ctx)
            return null
        }

        val apk = UpdateChecker.apkFile(ctx, staged)
        // Length only, not the digest: this runs on the main thread from every
        // onResume, and hashing 40 MB there would be felt. UpdateChecker
        // re-hashes on the next check.
        val size = prefs.getLong(UpdateChecker.KEY_PENDING_SIZE, 0L)
        if (!apk.exists() || apk.length() == 0L || (size > 0L && apk.length() != size)) {
            Log.w(TAG, "Pending update $staged is ${apk.length()} bytes, expected $size; clearing")
            clear(ctx)
            return null
        }

        return offer(staged, current, prefs.getString(KEY_PROMPTED_VERSION, ""), force)
    }

    /**
     * Whether [pending] is worth offering: newer than [current] (an unknown
     * current never blocks), and not the version already answered as
     * [promptedFor] unless [force]. A newer stage clears the suppression,
     * because it no longer matches.
     */
    internal fun offer(pending: String?, current: String?, promptedFor: String?, force: Boolean): String? {
        val version = pending?.takeIf { it.isNotBlank() } ?: return null
        if (current != null && UpdateChecker.compareVersions(version, current) <= 0) return null
        if (!force && promptedFor == version) return null
        return version
    }

    /** The user answered "later": [version] is not offered again until a newer one is staged. */
    fun decline(context: Context, version: String) {
        UpdateChecker.prefs(context.applicationContext).edit()
            .putString(KEY_PROMPTED_VERSION, version)
            .apply()
    }

    /**
     * Launch the system installer for the staged [version] - the user's
     * answer to [pending]'s offer, or the About dialog's explicit ask.
     */
    fun install(activity: Activity, version: String) {
        val ctx = activity.applicationContext
        val prefs = UpdateChecker.prefs(ctx)
        val apk = UpdateChecker.apkFile(ctx, version)

        // Android 8+ needs per-app "install unknown apps" consent; without it
        // the system installer bounces the user with a generic dialog. Send
        // them to the toggle instead and bail out - the offer is not recorded
        // as answered, so the next onResume asks again.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !ctx.packageManager.canRequestPackageInstalls()) {
            try {
                val grant = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + ctx.packageName),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(grant)
                Log.i(TAG, "Prompting user to grant install-unknown-apps for $version")
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

        // Record the answer BEFORE starting the activity. The system dialog
        // shows independently of our Activity lifecycle, and we'd otherwise
        // offer again on every onResume while it sits there (and forever if
        // the user taps No, or Auto Blocker refuses it).
        prefs.edit().putString(KEY_PROMPTED_VERSION, version).apply()

        try {
            val uri = FileProvider.getUriForFile(
                ctx,
                ctx.packageName + FILE_PROVIDER_AUTHORITY_SUFFIX,
                apk,
            )
            // launch-ok: package installer, on a content: FileProvider URI for the downloaded APK
            val install = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(install)
            Log.i(TAG, "Launched installer for $version")
        } catch (e: Exception) {
            Log.w(TAG, "Could not launch installer: ${e.message}")
        }
    }

    /**
     * Straight to the installer, even for a version the user has already
     * declined - for the About dialog, where they asked explicitly.
     */
    fun forcePrompt(activity: Activity) {
        pending(activity, force = true)?.let { version -> install(activity, version) }
    }

    private fun clear(ctx: Context) {
        val prefs = UpdateChecker.prefs(ctx)
        prefs.edit()
            .remove(UpdateChecker.KEY_PENDING)
            .remove(UpdateChecker.KEY_PENDING_PATH)
            .remove(UpdateChecker.KEY_PENDING_SIZE)
            .remove(UpdateChecker.KEY_PENDING_SHA)
            .remove(KEY_PROMPTED_VERSION)
            .apply()
        UpdateChecker.updatesDir(ctx).listFiles()?.forEach { it.delete() }
    }

    private const val KEY_PROMPTED_VERSION = "prompted_version"
}
