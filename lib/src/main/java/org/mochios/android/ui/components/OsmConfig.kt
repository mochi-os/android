// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.content.Context
import java.io.File
import org.osmdroid.config.Configuration

@Volatile
private var osmConfigured = false

/**
 * Points osmdroid at the app's own storage and names the app to the tile
 * server, once per process. Left unset, osmdroid keeps its tile cache under
 * shared storage, which apps can't write to since Android 10, and every map
 * stays on its grey loading grid.
 */
fun configureOsmdroid(context: Context) {
    if (osmConfigured) {
        return
    }
    synchronized(Configuration::class.java) {
        if (osmConfigured) {
            return
        }
        val app = context.applicationContext
        val config = Configuration.getInstance()
        config.load(app, app.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        config.osmdroidBasePath = File(app.filesDir, "osmdroid")
        config.osmdroidTileCache = File(app.cacheDir, "osmdroid")
        config.userAgentValue = app.packageName
        osmConfigured = true
    }
}
