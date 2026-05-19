package org.mochios.android.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * Detects whether the running app was installed from a known app store. Used
 * by [UpdateChecker] to short-circuit the daily self-update poll on devices
 * where the store will deliver updates instead — the store's package signature
 * and ours wouldn't necessarily match (F-Droid reproducible builds, Play Store
 * App Bundle splits), so trying to install a packages.mochi-os.org APK on top
 * would either fail with a signature mismatch or bypass the store user's
 * trust chain.
 */
object InstallSource {

    private const val TAG = "InstallSource"

    /**
     * Package names of installers whose presence means "this device's app
     * store will deliver updates for us — don't run our own update path".
     *
     * The system package installers (`com.android.packageinstaller` /
     * `com.google.android.packageinstaller`) are deliberately NOT in this
     * set: that's what's recorded when our own [UpdateInstaller] hands an
     * APK off via `ACTION_VIEW`, and we want subsequent self-updates to
     * keep working in that case.
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
     * Returns the installer's package name, or null if it can't be
     * determined (ADB sideload, very old Android, package manager error).
     * Uses the API 30+ [InstallSourceInfo] when available so we get the
     * authoritative answer; falls back to the deprecated single-string
     * pre-30 API on older devices.
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
     * True when the running APK was installed from a known app store and
     * updates should therefore come from that store, not from us. Returns
     * false for ADB sideload, our own self-install via [UpdateInstaller],
     * and direct APK installs from a browser or files app.
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
