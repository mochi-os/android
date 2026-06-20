// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.market.R
import org.mochios.market.lib.formatPrice
import org.mochios.market.model.Auction
import org.mochios.market.model.Currency
import org.mochios.market.model.Listing
import org.mochios.market.model.PricingModel

/**
 * Headline price for a listing.
 *
 * Renders four shapes depending on the listing's pricing model:
 *  - Fixed: `formatPrice(price, currency)` as the headline.
 *  - Auction: "High bid: £X" once `auction.bids > 0`, otherwise
 *    "Starting bid: £reserve"; an "instant buy: £Y" suffix appears below
 *    when `auction.instant > 0`.
 *  - PWYW: "from £listing.price" (so the suggested price acts as the
 *    floor when set, falling back to "from £0").
 *  - Subscription: "£X / month" or "£X / year" per [Listing.interval].
 *
 * Falls back to [Currency.GBP] when [Listing.currency] is null so old
 * server responses still render something sensible.
 */
@Composable
fun PriceDisplay(
    listing: Listing,
    modifier: Modifier = Modifier,
    auction: Auction? = null,
    compact: Boolean = false,
) {
    val currency = listing.currency ?: Currency.GBP
    val style = if (compact) MaterialTheme.typography.titleMedium
    else MaterialTheme.typography.headlineSmall

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        when (listing.pricing) {
            PricingModel.AUCTION -> {
                val hasBids = auction != null && auction.bids > 0
                val headlineAmount = when {
                    hasBids -> auction!!.bid
                    else -> auction?.reserve ?: listing.price
                }
                val headlineKey = if (hasBids) {
                    R.string.market_price_high_bid
                } else {
                    R.string.market_price_starting_bid
                }
                Text(
                    text = stringResource(headlineKey, formatPrice(headlineAmount, currency)),
                    style = style,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val instant = auction?.instant ?: 0L
                if (instant > 0L) {
                    Text(
                        text = stringResource(
                            R.string.market_price_instant_buy,
                            formatPrice(instant, currency),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            PricingModel.PWYW -> {
                Text(
                    text = stringResource(
                        R.string.market_price_from,
                        formatPrice(listing.price, currency),
                    ),
                    style = style,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PricingModel.SUBSCRIPTION -> {
                val amount = formatPrice(listing.price, currency)
                val key = when (listing.interval) {
                    org.mochios.market.model.Interval.YEARLY -> R.string.market_price_per_year
                    else -> R.string.market_price_per_month
                }
                Text(
                    text = stringResource(key, amount),
                    style = style,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            else -> {
                Text(
                    text = formatPrice(listing.price, currency),
                    style = style,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Small inline variant for use in lists and cards. Identical content,
 * smaller typography.
 */
@Composable
fun PriceDisplayInline(
    listing: Listing,
    modifier: Modifier = Modifier,
    auction: Auction? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PriceDisplay(listing = listing, auction = auction, compact = true)
    }
}
