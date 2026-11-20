// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.push

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Whether [packageName] is this app or one signed with the same certificate.
 * The distributor refuses REGISTER and UNREGISTER from an unrelated package,
 * behind the signature permission its receiver is declared with.
 */
internal fun mochiSigned(context: Context, packageName: String): Boolean {
    if (packageName == context.packageName) return true
    return runCatching {
        context.packageManager.checkSignatures(context.packageName, packageName) ==
            PackageManager.SIGNATURE_MATCH
    }.getOrDefault(false)
}

/**
 * The package that created [intent]'s [MochiDistributorReceiver.EXTRA_PI], or null if it
 * carries none. The system records a PendingIntent's creator and a sender
 * cannot forge it, so this is the only identity in a broadcast worth trusting -
 * an extra naming the sender is just a string the sender chose.
 */
internal fun senderPackage(intent: Intent): String? {
    val pending: PendingIntent? = if (android.os.Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(MochiDistributorReceiver.EXTRA_PI, PendingIntent::class.java)
    } else {
        @Suppress("DEPRECATION") intent.getParcelableExtra(MochiDistributorReceiver.EXTRA_PI)
    }
    return pending?.creatorPackage
}
