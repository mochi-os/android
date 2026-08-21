// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * Best-effort friendly device name, used as the push account's label on the
 * server. Prefers the user-set system device name, then manufacturer plus
 * model.
 */
object DeviceName {

    fun resolve(context: Context): String {
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val manufacturer = Build.MANUFACTURER.orEmpty().titleCase()
        val model = Build.MODEL.orEmpty()

        return when {
            manufacturer.isBlank() && model.isBlank() -> "Mobile device"
            manufacturer.isBlank() -> model
            model.isBlank() -> manufacturer
            model.startsWith(manufacturer, ignoreCase = true) -> model.titleCase()
            else -> "$manufacturer $model"
        }
    }

    private fun String.titleCase(): String =
        if (isEmpty()) this else this[0].uppercaseChar() + substring(1)
}
