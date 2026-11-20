// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.StatusBadgeSize
import org.mochios.market.R
import org.mochios.market.model.Listing
import org.mochios.market.model.ListingStatus

/**
 * The status a listing is shown with: one removed because moderation rejected
 * it reads as rejected, and a live one moderation holds reads as held, since
 * no buyer can see it, as the web shows them.
 */
internal fun Listing.shownStatus(): ListingStatus? = when {
    status == ListingStatus.REMOVED && moderation == "rejected" -> ListingStatus.REJECTED
    status == ListingStatus.ACTIVE && moderation == "hold" -> ListingStatus.HELD
    else -> status
}

/** The status's translated name, as the listings' status filter shows it. */
@StringRes
internal fun ListingStatus.labelRes(): Int = when (this) {
    ListingStatus.DRAFT -> R.string.market_listings_filter_draft
    ListingStatus.ACTIVE -> R.string.market_listings_filter_active
    ListingStatus.SOLD -> R.string.market_listings_filter_sold
    ListingStatus.EXPIRED -> R.string.market_listings_filter_expired
    ListingStatus.REJECTED -> R.string.market_listings_filter_rejected
    ListingStatus.REMOVED -> R.string.market_listings_filter_removed
    ListingStatus.HELD -> R.string.market_listings_status_held
}

/** The status as the server names it, which picks the badge's colour. */
internal fun ListingStatus.wireName(): String = when (this) {
    ListingStatus.DRAFT -> "draft"
    ListingStatus.ACTIVE -> "active"
    ListingStatus.SOLD -> "sold"
    ListingStatus.EXPIRED -> "expired"
    ListingStatus.REJECTED -> "rejected"
    ListingStatus.REMOVED -> "removed"
    ListingStatus.HELD -> "held"
}

/**
 * A listing's status badge, translated and coloured by [shownStatus]; nothing
 * when the listing has no status.
 */
@Composable
internal fun ListingStatusBadge(
    listing: Listing,
    modifier: Modifier = Modifier,
    size: StatusBadgeSize = StatusBadgeSize.Compact,
) {
    val status = listing.shownStatus() ?: return
    MarketStatusBadge(
        status = status.wireName(),
        modifier = modifier,
        localised = stringResource(status.labelRes()),
        size = size,
    )
}

/**
 * The seller's notice on a listing moderation holds or rejected: what
 * happened, and the reason staff gave when they gave one. Nothing for any
 * other listing.
 */
@Composable
internal fun ModerationNotice(listing: Listing, modifier: Modifier = Modifier) {
    val headline = when (listing.shownStatus()) {
        ListingStatus.HELD -> R.string.market_listing_held_title
        ListingStatus.REJECTED -> R.string.market_listing_rejected_title
        else -> return
    }
    MochiCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(headline),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            if (listing.notes.isNotBlank()) {
                Text(
                    text = listing.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
