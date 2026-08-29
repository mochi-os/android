// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import android.app.DownloadManager
import android.content.Context
import android.os.Build
import android.os.Environment

/**
 * Point [request] at a download destination the user can find, without putting
 * the bytes somewhere every other app can read them.
 *
 * From API 29 scoped storage means the public Downloads collection is private
 * to this app unless the user shares it, so that is where a download belongs -
 * it is where people look for one. Below 29 there is no such protection: any
 * app holding READ_EXTERNAL_STORAGE reads the whole volume, and these files are
 * a wiki attachment (someone else's, possibly private) and an account export.
 * There the download goes to this app's own external files directory instead,
 * which is still visible to a file manager under Android/data but is not part
 * of the shared volume.
 *
 * minSdk is 26, so the older branch is reachable on real installs.
 */
fun DownloadManager.Request.destination(
    context: Context,
    name: String,
): DownloadManager.Request = apply {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
    } else {
        setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
    }
}
