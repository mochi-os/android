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
 * The extra's literal name. [ClipDescription.EXTRA_IS_SENSITIVE] only exists
 * from API 33, and minSdk here is 26; an older platform simply ignores an extra
 * it doesn't know, so the flag is always set and the constant used where it is
 * available.
 */
private const val IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

/**
 * A clip holding [secret] that the system will not show in its clipboard
 * preview.
 *
 * Android 13 and later render copied text in an overlay as it is placed on the
 * clipboard. That is fine for a fingerprint or a feed URL, and not for a TOTP
 * seed or a recovery code, which this marks so the preview shows a redaction
 * instead of the value.
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
