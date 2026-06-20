// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * Best-effort friendly name for the device, used as the [account.label] on
 * the user's Mochi server so the notification destinations list shows
 * something a human can identify. Falls through, in order of preference:
 *
 *   1. User-set system device name (Settings → About phone → Device name).
 *      Often "Alistair's Phone" on Pixels; sometimes set on Samsung; usually
 *      blank elsewhere. When present this is the best signal because the
 *      user themselves chose it.
 *   2. MANUFACTURER + MODEL, deduplicated and properly cased. Produces
 *      "Samsung SM-S928B" — uglier than the marketing name but at least
 *      tells the user it's the Samsung-flavour device, not a Pixel.
 *   3. MODEL alone if MANUFACTURER is blank.
 *   4. Generic "Mobile device" as a last resort.
 *
 * Future: a model-code → marketing-name lookup (e.g. SM-S928B → "Galaxy
 * S24 Ultra") would render this fully friendly, but needs a maintained
 * database. Skipped for v1.
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
