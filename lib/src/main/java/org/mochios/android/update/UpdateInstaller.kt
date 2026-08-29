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
 * Hand a pre-downloaded APK to the system installer, from the host Activity's
 * onResume. Android's own "Update Mochi?" confirmation for a sideloaded install
 * cannot be suppressed.
 */
object UpdateInstaller {

    private const val TAG = "MochiUpdateInstall"
    private const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".updates"

    /**
     * Launch the system installer for a staged APK newer than the running one.
     * Idempotent, called every onResume: the prompted version is recorded so a
     * declined update is not re-asked until a newer one is staged, or
     * [forcePrompt].
     */
    fun promptIfPending(activity: Activity) {
        promptInternal(activity, force = false)
    }

    /**
     * Prompt even when the user has already been asked for this version - for
     * the About dialog, where they asked explicitly.
     */
    fun forcePrompt(activity: Activity) {
        promptInternal(activity, force = true)
    }

    private fun promptInternal(activity: Activity, force: Boolean) {
        val ctx = activity.applicationContext
        if (InstallSource.isStoreInstalled(ctx)) {
            // Belt and braces: UpdateChecker should already have skipped
            // the download, but if a pending APK is sitting in cacheDir
            // from a previous non-store install + later store reinstall,
            // never prompt the user to install it.
            return
        }
        val prefs = UpdateChecker.prefs(ctx)
        val pending = prefs.getString(UpdateChecker.KEY_PENDING, "")
            ?.takeIf { it.isNotBlank() } ?: return

        val current = UpdateChecker.currentVersionName(ctx)
        if (current != null && UpdateChecker.compareVersions(pending, current) <= 0) {
            // Already installed (either by this prompt or out-of-band) — clean up.
            clear(ctx)
            return
        }

        // Skip if the user has already been prompted for this exact version
        // and hasn't asked us to retry. A newer staged version clears the
        // suppression because KEY_PROMPTED_VERSION won't match.
        val promptedFor = prefs.getString(KEY_PROMPTED_VERSION, "") ?: ""
        if (!force && promptedFor == pending) {
            Log.d(TAG, "Already prompted for $pending; not re-asking")
            return
        }

        val apk = UpdateChecker.apkFile(ctx, pending)
        // Length only, not the digest: this runs on the main thread from every
        // onResume, and hashing 40 MB there would be felt. UpdateChecker
        // re-hashes on the next check.
        val size = prefs.getLong(UpdateChecker.KEY_PENDING_SIZE, 0L)
        if (!apk.exists() || apk.length() == 0L || (size > 0L && apk.length() != size)) {
            Log.w(TAG, "Pending update $pending is ${apk.length()} bytes, expected $size; clearing")
            clear(ctx)
            return
        }

        // Android 8+ needs per-app "install unknown apps" consent; without it
        // the system installer bounces the user with a generic dialog. Send
        // them to the toggle instead and bail out - the next onResume retries.
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

        // Record that we've prompted for this version BEFORE starting the
        // activity. The system dialog shows independently of our Activity
        // lifecycle, and we'd otherwise re-prompt on every onResume cycle
        // while the dialog sits there (and forever if the user taps No).
        prefs.edit().putString(KEY_PROMPTED_VERSION, pending).apply()

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
            .remove(UpdateChecker.KEY_PENDING_SIZE)
            .remove(UpdateChecker.KEY_PENDING_SHA)
            .remove(KEY_PROMPTED_VERSION)
            .apply()
        UpdateChecker.updatesDir(ctx).listFiles()?.forEach { it.delete() }
    }

    private const val KEY_PROMPTED_VERSION = "prompted_version"
}
