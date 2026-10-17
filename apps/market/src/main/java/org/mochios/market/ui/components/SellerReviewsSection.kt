// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.MochiCard
import org.mochios.market.R
import org.mochios.market.model.Review

@Composable
fun SellerReviewsSection(
    reviews: List<Review>,
    modifier: Modifier = Modifier,
) {
    if (reviews.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        reviews.forEach { review ->
            ReviewCard(review = review)
        }
    }
}

/**
 * A review as the web draws it: the stars, when it was written and the
 * listing it is on, then the review and any reply under [responseLabel].
 * [heading] goes above the stars, [onListing] makes the listing's title a
 * link, and [footer] adds what follows the reply.
 */
@Composable
internal fun ReviewCard(
    review: Review,
    modifier: Modifier = Modifier,
    responseLabel: String = stringResource(R.string.market_listing_detail_seller_response),
    heading: (@Composable () -> Unit)? = null,
    onListing: (() -> Unit)? = null,
    footer: @Composable () -> Unit = {},
) {
    val format = LocalFormat.current
    MochiCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            heading?.invoke()
            Row(verticalAlignment = Alignment.CenterVertically) {
                RatingStars(
                    rating = review.rating.toFloat(),
                    showCount = false,
                    size = 16.dp,
                    tint = RatingStarGold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = format.formatDateTime(review.created),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val listing = review.listingTitle.orEmpty()
                if (listing.isNotBlank()) {
                    Text(
                        text = "  ·  ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = listing,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .then(
                                if (onListing != null) {
                                    Modifier.clickable(onClick = onListing)
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
            if (review.text.isNotBlank()) {
                Text(
                    text = review.text,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (review.response.isNotBlank()) {
                ResponseBlock(label = responseLabel, response = review.response)
            }
            footer()
        }
    }
}

@Composable
private fun ResponseBlock(label: String, response: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = response,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
