// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.i18n

import java.util.Locale

/**
 * The user's own language with a region whose week starts where the user's
 * does, for Material's date picker, which takes its first day from the
 * locale's region and has no setting of its own: the United States for
 * Sunday (0), Britain for Monday (1), Egypt for Saturday (6). Only the region
 * changes, so the months and weekdays stay in the user's language; any other
 * day keeps [current] as it is.
 */
fun weekLocale(weekStartsOn: Int, current: Locale): Locale {
    val region = when (weekStartsOn) {
        0 -> "US"
        1 -> "GB"
        6 -> "EG"
        else -> return current
    }
    return Locale.Builder().setLocale(current).setRegion(region).build()
}
