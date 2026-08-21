// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.i18n

import android.content.Context

/**
 * Application context for code that needs `getString()` without one. Set in
 * `Application.onCreate`; it carries the locale applied in `attachBaseContext`.
 */
object AppContext {
    @Volatile
    private var instance: Context? = null

    fun set(context: Context) {
        instance = context.applicationContext
    }

    fun get(): Context = instance
        ?: error("AppContext not initialised — call AppContext.set(this) in Application.onCreate")
}
