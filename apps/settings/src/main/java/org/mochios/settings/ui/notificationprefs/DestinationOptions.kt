// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.notificationprefs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.mochios.settings.R
import org.mochios.settings.api.DestinationRow
import org.mochios.settings.api.DestinationsAvailable

/**
 * The destination rows offered to every category, paired with their display labels.
 *
 * @param available the destinations the server reports for this user.
 * @return each row with the label to show for it, sorted by that label.
 */
@Composable
internal fun destinationOptions(
    available: DestinationsAvailable,
): List<Pair<DestinationRow, String>> {
    // Every destination is one row named for what it is: the browser's bell, a
    // device's in-app list ("S24U app"), the push account registered from a
    // device ("S24U push"), a push account bound to no device, or a feed. One
    // flat list sorted by name, which also keeps a device's rows together.
    val deviceFallback = stringResource(R.string.notifprefs_dest_device)
    val bound = available.devices.map { device -> device.id }.toSet()
    return buildList {
        add(DestinationRow(type = "web", target = "") to stringResource(R.string.notifprefs_dest_web))
        for (device in available.devices) {
            val name = device.label.ifBlank { deviceFallback }
            add(
                DestinationRow(type = "device", target = device.id) to
                    stringResource(R.string.notifprefs_device_app, name)
            )
            for (acc in available.accounts) {
                if (acc.device == device.id) {
                    add(
                        DestinationRow(type = "account", target = acc.id) to
                            stringResource(R.string.notifprefs_device_push, name)
                    )
                }
            }
        }
        val fcm = stringResource(R.string.notifprefs_transport_fcm)
        for (acc in available.accounts) {
            if (acc.device.isNotBlank() && acc.device in bound) continue
            val transport = if (acc.type == "fcm") fcm else acc.type
            val name = if (acc.label.isNotBlank()) acc.label else if (acc.identifier.isNotBlank()) acc.identifier else transport
            // A push account bound to no device names its transport, so two
            // that share a phone's name can still be told apart.
            val push = acc.type == "browser" || acc.type == "unifiedpush" || acc.type == "fcm"
            add(DestinationRow(type = "account", target = acc.id) to (if (push && name != transport) "$name · $transport" else name))
        }
        for (feed in available.feeds) {
            add(DestinationRow(type = "rss", target = feed.id) to feed.name)
        }
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { option -> option.second })
}
