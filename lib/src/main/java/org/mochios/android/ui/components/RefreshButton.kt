// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.mochios.android.R
import org.mochios.android.i18n.LocalFormat

/**
 * The refresh button of a timeline's top bar. Posts that arrive after the list
 * was loaded wait behind it rather than being injected while the user reads,
 * and their [count] is written beside the icon in the bar's own type: a number
 * on the glyph's corner covered part of the arrow, and a pill over the content
 * covered the post being read. [label] names the count for a screen reader
 * ("3 new posts"); the button speaks it, and the written number stays out of
 * the reading order.
 */
@Composable
fun RefreshButton(
    count: Int,
    label: String,
    onClick: () -> Unit,
) {
    if (count <= 0) {
        MochiIconButton(onClick = onClick) { RefreshIcon() }
        return
    }
    MochiTextButton(
        onClick = onClick,
        modifier = Modifier.semantics { stateDescription = label },
    ) {
        RefreshIcon()
        Spacer(Modifier.width(4.dp))
        Text(
            text = LocalFormat.current.formatNumber(count),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { hideFromAccessibility() },
        )
    }
}

/** The same glyph at the same size whether or not a count sits beside it. */
@Composable
private fun RefreshIcon() {
    Icon(
        Icons.Default.Refresh,
        contentDescription = stringResource(R.string.common_refresh),
        modifier = Modifier.size(24.dp),
    )
}
