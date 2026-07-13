// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.i18n

import android.content.Context

/**
 * Application-context holder for code paths that need `getString()` without
 * an explicit Context parameter — chiefly [org.mochios.android.api.userMessage].
 *
 * Set once in `Application.onCreate`; reads after that are safe.
 *
 * The held context inherits whatever locale was applied via [LocaleHelper.wrap]
 * in `attachBaseContext`, so `getString()` calls return the user's language.
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
