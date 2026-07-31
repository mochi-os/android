// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.mochi.push

import android.content.Context
import android.net.Uri
import org.mochios.android.push.MochiPushReceiver
import org.mochios.android.push.notificationChannelFor

class MochiPushDispatcher : MochiPushReceiver() {

    // Shared with the FCM path. This used to be a ten-way `when` over the
    // per-module channel constants, which are all just their own slug, so it
    // was an elaborate identity that could — and did — drift from the FCM
    // copy. It also had no arm for people or crm.
    override fun channelId(context: Context, instance: String, app: String, link: String): String =
        notificationChannelFor(app, link)

    override fun deepLinkFor(
        context: Context,
        instance: String,
        link: String,
        id: String,
        nonce: String,
    ): Uri {
        // Per claude/plans/mochi-uri-scheme.md, system intents use the opaque
        // shape (0 slashes): mochi:notification?link=…
        // Hand-construct via the opaque-URI builder to avoid the //authority/path
        // shape Uri.parse + buildUpon would otherwise emit.
        val encodedLink = Uri.encode(link)
        val ssp = buildString {
            append("notification?link=")
            append(encodedLink)
            if (id.isNotEmpty()) {
                append("&id=")
                append(Uri.encode(id))
            }
            append("&nonce=")
            append(Uri.encode(nonce))
        }
        return Uri.parse("mochi:$ssp")
    }
}
