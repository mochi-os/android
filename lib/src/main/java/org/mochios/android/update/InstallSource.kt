// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * Detects whether the app was installed from a known app store. [UpdateChecker]
 * skips its self-update poll there: our packages.mochi-os.org APK need not
 * match the store's signature, and would bypass the store user's trust chain.
 */
object InstallSource {

    private const val TAG = "InstallSource"

    /**
     * Installers whose presence means the device's store delivers our updates.
     * The system package installers are deliberately absent: that is what our
     * own [UpdateInstaller] records, and self-updates must keep working after
     * it.
     */
    private val STORE_INSTALLERS = setOf(
        "com.android.vending",                  // Google Play
        "org.fdroid.fdroid",                    // F-Droid
        "org.fdroid.fdroid.privileged",         // F-Droid privileged extension
        "com.aurora.store",                     // Aurora Store
        "com.amazon.venezia",                   // Amazon Appstore
        "com.sec.android.app.samsungapps",      // Samsung Galaxy Store
        "com.huawei.appmarket",                 // Huawei AppGallery
        "com.heytap.market",                    // Oppo / OnePlus / Realme
        "com.xiaomi.mipicks",                   // Xiaomi GetApps
        "com.vivo.appstore",                    // Vivo
    )

    /**
     * The installer's package name, or null when it cannot be determined (ADB
     * sideload, package manager error). Uses [InstallSourceInfo] on API 30+.
     */
    fun installerPackage(context: Context): String? = try {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(context.packageName)
        }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to read installer package: ${e.message}")
        null
    }

    /**
     * True when the APK came from a known app store, so updates come from
     * there. False for sideloads and our own [UpdateInstaller].
     */
    fun isStoreInstalled(context: Context): Boolean {
        val installer = installerPackage(context) ?: return false
        val matched = installer in STORE_INSTALLERS
        if (matched) {
            Log.i(TAG, "Installed from store ($installer); self-update disabled")
        }
        return matched
    }
}
