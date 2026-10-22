// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.systemstatus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.util.NaturalCompare
import org.mochios.settings.R
import org.mochios.settings.api.NetworkState
import org.mochios.settings.api.SystemNetworks
import org.mochios.settings.ui.networks.networkLabel
import org.mochios.android.R as MochiR

/** The networks in their standard order. */
private val NETWORK_ORDER = listOf("fediverse", "bluesky", "xmpp", "matrix")

/**
 * The other networks on the Status screen (the federation plan, phase 10):
 * each network's state, each XMPP domain's room service, and each Matrix
 * domain's signing key, which the owner can replace. Shown only while the
 * server offers the networks.
 */
@Composable
internal fun OtherNetworksSections(
    networks: SystemNetworks,
    replacing: String?,
    onReplace: (String) -> Unit,
) {
    if (!networks.development) return
    var confirming by remember { mutableStateOf<String?>(null) }
    val names = networks.networks.keys.sortedBy { NETWORK_ORDER.indexOf(it).let { index -> if (index < 0) NETWORK_ORDER.size else index } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.system_status_other_networks))
        for (name in names) {
            val network = networks.networks.getValue(name)
            NetworkRow(label = networkLabel(name), value = networkValue(network))
        }

        val rooms = networks.networks["xmpp"]?.rooms.orEmpty().sortedWith(compareBy(NaturalCompare) { it.host })
        if (rooms.isNotEmpty()) {
            SectionTitle(stringResource(R.string.system_status_xmpp_rooms))
            for (room in rooms) {
                NetworkRow(
                    label = room.host,
                    value = if (room.state == "working") {
                        stringResource(R.string.system_status_working)
                    } else {
                        stringResource(R.string.system_status_rooms_unreachable)
                    },
                )
            }
        }

        val keys = networks.networks["matrix"]?.keys.orEmpty().sortedWith(compareBy(NaturalCompare) { it.domain })
        if (keys.isNotEmpty()) {
            SectionTitle(stringResource(R.string.system_status_matrix_keys))
            for (key in keys) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            key.domain,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(key.key, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                    MochiOutlinedButton(onClick = { confirming = key.domain }, enabled = replacing == null) {
                        if (replacing == key.domain) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.system_status_replace))
                        }
                    }
                }
            }
        }
    }

    confirming?.let { domain ->
        MochiAlertDialog(
            onDismissRequest = { confirming = null },
            title = stringResource(R.string.system_status_key_title, domain),
            text = stringResource(R.string.system_status_key_message),
            confirmText = stringResource(R.string.system_status_replace),
            onConfirm = {
                confirming = null
                onReplace(domain)
            },
            destructive = true,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
}

/**
 * A network's state in the owner's words, with what it has spent where it
 * reports it: certificates this week, the relay's budget, its standing with
 * the relay.
 */
@Composable
internal fun networkValue(network: NetworkState): String {
    val format = LocalFormat.current
    val parts = mutableListOf(
        when (network.state) {
            "working" -> stringResource(R.string.system_status_working)
            "disabled" -> stringResource(R.string.system_status_disabled)
            else -> stringResource(R.string.system_status_unavailable, unavailableReason(network.reason))
        },
    )
    if (network.state != "disabled") {
        network.certificates?.let {
            parts += stringResource(
                R.string.system_status_certificates,
                format.formatNumber(it.issued),
                format.formatNumber(it.limit),
            )
        }
        network.budget?.let {
            parts += stringResource(
                R.string.system_status_budget,
                format.formatNumber(it.hour.used),
                format.formatNumber(it.hour.limit),
                format.formatNumber(it.day.used),
                format.formatNumber(it.day.limit),
            )
        }
        network.relay?.let { relay ->
            var standing = relayStanding(relay.status)
            if (relay.limit > 0) {
                standing += ", " + stringResource(
                    R.string.system_status_relay_accounts,
                    format.formatNumber(relay.accounts),
                    format.formatNumber(relay.limit),
                )
            }
            parts += standing
        }
    }
    return parts.joinToString(" · ")
}

/** Why a network is unavailable; the engines add their own reasons as they are built. */
@Composable
private fun unavailableReason(reason: String): String = when (reason) {
    "domain" -> stringResource(R.string.system_status_reason_domain)
    "port" -> stringResource(R.string.system_status_reason_port)
    else -> reason
}

/** The relay's standing towards this server, as the relay reports it. */
@Composable
private fun relayStanding(status: String): String = stringResource(
    when (status) {
        "active" -> R.string.system_status_relay_active
        "idle" -> R.string.system_status_relay_idle
        "offline" -> R.string.system_status_relay_offline
        "throttled" -> R.string.system_status_relay_throttled
        "banned" -> R.string.system_status_relay_banned
        "HostNotFound" -> R.string.system_status_relay_unknown_host
        else -> R.string.system_status_relay_unknown
    },
)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun NetworkRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(112.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
