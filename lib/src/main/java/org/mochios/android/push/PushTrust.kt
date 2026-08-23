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
 * Both directions of the distributor protocol gate on this: the distributor
 * refuses REGISTER from an unrelated package, and an app refuses a delivered
 * message from anything that is not our distributor.
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

/** The verdict on a locally delivered push; see [judgeLocalPush]. */
internal enum class LocalPush { ACCEPT, UNIDENTIFIED, UNTRUSTED, EMPTY }

/**
 * Whether a push delivered on Mochi's own action may raise a notification.
 *
 * This is the check that stands in for the connector's decrypt on the local
 * path, so it is kept apart from the Intent it is read out of and decided on
 * plain values: the Android types involved cannot be exercised in a unit test,
 * and this decision is the part worth testing.
 *
 * [sender] is the PendingIntent creator, null when the broadcast carried none;
 * [trusted] is whether that package shares our signature.
 */
internal fun judgeLocalPush(sender: String?, trusted: Boolean, content: ByteArray?): LocalPush =
    when {
        sender == null -> LocalPush.UNIDENTIFIED
        !trusted -> LocalPush.UNTRUSTED
        content == null || content.isEmpty() -> LocalPush.EMPTY
        else -> LocalPush.ACCEPT
    }
