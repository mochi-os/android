package org.mochios.market.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.market.R
import org.mochios.market.lib.formatFingerprint
import org.mochios.market.lib.formatPrice
import org.mochios.market.model.Bid
import org.mochios.market.model.Currency

/**
 * Auction bid history list. Each row shows the bidder's fingerprint,
 * the formatted bid amount, and the relative time the bid was placed.
 * The highest bid is rendered in primary-coloured text for emphasis.
 *
 * Pass [endsAt] (epoch seconds) to render a countdown banner at the
 * top. Renders nothing if [bids] is empty and there's no countdown to
 * show.
 *
 * @param fallbackCurrency Used when a [Bid.currency] is null (older
 *                        server responses).
 */
@Composable
fun AuctionBidHistory(
    bids: List<Bid>,
    modifier: Modifier = Modifier,
    fallbackCurrency: Currency = Currency.GBP,
    endsAt: Long? = null,
) {
    if (bids.isEmpty() && (endsAt == null || endsAt <= 0L)) return

    val highest = bids.maxByOrNull { it.amount }

    Column(modifier = modifier.fillMaxWidth()) {
        if (endsAt != null && endsAt > 0L) {
            CountdownRow(endsAt = endsAt)
        }
        bids.forEach { bid ->
            val isHigh = highest != null && bid.id == highest.id
            val amount = formatPrice(bid.amount, bid.currency ?: fallbackCurrency)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = formatFingerprint(bid.bidder.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = amount,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isHigh) FontWeight.Bold else FontWeight.Normal,
                    color = if (isHigh) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = relativeTime(bid.created),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun CountdownRow(endsAt: Long) {
    val now = System.currentTimeMillis() / 1000L
    val remaining = endsAt - now
    val label = if (remaining > 0L) {
        stringResource(R.string.market_bid_ends_in, formatRemaining(remaining))
    } else {
        stringResource(R.string.market_bid_ended)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

private fun formatRemaining(seconds: Long): String {
    val s = seconds.coerceAtLeast(0L)
    val d = s / 86_400L
    val h = (s % 86_400L) / 3_600L
    val m = (s % 3_600L) / 60L
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}

private fun relativeTime(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return ""
    val now = System.currentTimeMillis()
    val ms = if (epochSeconds < 1_000_000_000_000L) epochSeconds * 1000L else epochSeconds
    return DateUtils.getRelativeTimeSpanString(
        ms,
        now,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}
