// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.theme.LocalSpacing
import org.mochios.android.util.providerName
import java.net.URLEncoder

// The way from an app to setting up an account on another network. Where an
// app offers something on a network the server offers but the user has no
// account on, it shows that network with a Set up button, which opens
// Settings › Networks at the network; Back returns to the app.

/** Settings › Networks at [network], as the link an app opens to set up an account there. */
fun networkSetupLink(network: String): String =
    "settings/user/networks?network=" + URLEncoder.encode(network, Charsets.UTF_8.name())

/**
 * A network the server offers and the user has no account on yet, where an
 * app would otherwise show that account: its name, and a button to Settings ›
 * Networks at it. The app names the button.
 *
 * @param network the network's key, as the server names it.
 * @param label the button's words, in the app's own catalogue.
 * @param onOpenLink opens [networkSetupLink] for the network.
 */
@Composable
fun NetworkSetup(
    network: String,
    label: String,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.gap(4.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.gap(16.dp)),
    ) {
        Text(
            text = providerName(network),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        MochiOutlinedButton(onClick = { onOpenLink(networkSetupLink(network)) }) {
            Text(label)
        }
    }
}
