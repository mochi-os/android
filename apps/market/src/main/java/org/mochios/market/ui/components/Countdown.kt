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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRemaining
import org.mochios.market.R

/**
 * Seconds until a count with [left] seconds to go shows a different label in
 * [formatRemaining]'s two largest units: the next hour while it shows days, the
 * next minute while it shows hours, else the next second.
 */
internal fun untilLabelChanges(left: Long): Long {
    val unit = when {
        left >= 86_400L -> 3_600L
        left >= 3_600L -> 60L
        else -> 1L
    }
    return left % unit + 1
}

/**
 * Seconds left until [ends] (epoch seconds). It moves only when its label next
 * changes (see [untilLabelChanges]), so a caller reading it is not redrawn each
 * second while the label shows days or hours. [offset] is the server's clock
 * less the phone's, so the count is the server's and not a phone set wrong.
 * [onEnded] runs once when the count reaches zero while it is shown, or when
 * it is shown again after reaching zero in the background; a time already past
 * when it is first shown does not call it. [clock] is the phone's time in
 * epoch seconds.
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
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(ends, lifecycle) {
        now = clock() + skew
        if (now >= ends) return@LaunchedEffect
        var over = false
        // delay() stops while the phone sleeps, and a wait can be an hour, so
        // the count runs only while the screen is shown and reads the clock
        // again each time it is shown, rather than waking late.
        lifecycle.currentStateFlow
            .map { state -> state.isAtLeast(Lifecycle.State.STARTED) }
            .distinctUntilChanged()
            .collectLatest { shown ->
                if (!shown || over) return@collectLatest
                while (true) {
                    now = clock() + skew
                    val left = ends - now
                    if (left <= 0L) break
                    delay(untilLabelChanges(left) * 1_000L)
                }
                over = true
                ended()
            }
    }
    return (ends - now).coerceAtLeast(0L)
}

/**
 * "Ends in 2d 4h" for an auction closing at [ends], kept current; "Auction ended"
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
