// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRemaining
import org.mochios.market.R

/**
 * Seconds left until [ends] (epoch seconds), ticking each second. [offset] is
 * the server's clock less the phone's, so the count is the server's and not a
 * phone set wrong. [onEnded] runs once when the count reaches zero while it is
 * shown; a time already past when it is first shown does not call it.
 * [clock] is the phone's time in epoch seconds.
 */
@Composable
fun rememberRemaining(
    ends: Long,
    offset: Long = 0L,
    onEnded: () -> Unit = {},
    clock: () -> Long = { System.currentTimeMillis() / 1000L },
): Long {
    val skew by rememberUpdatedState(offset)
    val ended by rememberUpdatedState(onEnded)
    var now by remember { mutableLongStateOf(clock() + offset) }
    LaunchedEffect(ends) {
        now = clock() + skew
        if (now >= ends) return@LaunchedEffect
        while (now < ends) {
            delay(1_000L)
            now = clock() + skew
        }
        ended()
    }
    return (ends - now).coerceAtLeast(0L)
}

/**
 * "Ends in 2d 4h" for an auction closing at [ends], ticking; "Auction ended"
 * once it has passed. See [rememberRemaining].
 */
@Composable
fun auctionRemainingLabel(ends: Long, offset: Long = 0L, onEnded: () -> Unit = {}): String {
    val remaining = rememberRemaining(ends, offset, onEnded)
    return if (remaining > 0L) {
        stringResource(R.string.market_bid_ends_in, LocalFormat.current.formatRemaining(remaining))
    } else {
        stringResource(R.string.market_bid_ended)
    }
}
