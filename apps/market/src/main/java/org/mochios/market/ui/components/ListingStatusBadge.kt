// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.mochios.android.ui.components.StatusBadgeSize
import org.mochios.market.R
import org.mochios.market.model.Listing
import org.mochios.market.model.ListingStatus

/**
 * The status a listing is shown with: one removed because moderation rejected
 * it reads as rejected, as the web shows it.
 */
internal fun Listing.shownStatus(): ListingStatus? =
    if (status == ListingStatus.REMOVED && moderation == "rejected") {
        ListingStatus.REJECTED
    } else {
        status
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
}

/** The status as the server names it, which picks the badge's colour. */
internal fun ListingStatus.wireName(): String = when (this) {
    ListingStatus.DRAFT -> "draft"
    ListingStatus.ACTIVE -> "active"
    ListingStatus.SOLD -> "sold"
    ListingStatus.EXPIRED -> "expired"
    ListingStatus.REJECTED -> "rejected"
    ListingStatus.REMOVED -> "removed"
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
