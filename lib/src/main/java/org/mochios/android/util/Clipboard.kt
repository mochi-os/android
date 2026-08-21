// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.util

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle

/**
 * [ClipDescription.EXTRA_IS_SENSITIVE] is API 33+ and minSdk is 26. An older
 * platform ignores an extra it does not know, so the flag is always set.
 */
private const val IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

/**
 * A clip holding [secret] that Android 13+ redacts in its clipboard preview
 * overlay. Use it for a TOTP seed or recovery code, not a fingerprint or URL.
 */
fun sensitiveClip(label: String, secret: String): ClipData {
    val clip = ClipData.newPlainText(label, secret)
    val extras = PersistableBundle()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
    } else {
        extras.putBoolean(IS_SENSITIVE, true)
    }
    clip.description.extras = extras
    return clip
}
