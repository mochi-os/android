package org.mochios.market.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mochios.android.i18n.LocalFormat
import org.mochios.market.R
import org.mochios.market.model.Review

/**
 * "Seller reviews" list for the listing detail page. Renders each review as an
 * outlined card carrying the star rating, the absolute review timestamp, the
 * review body, and — when present — the seller's response in an indented block.
 *
 * Early-exits when there are no reviews so the caller can place it
 * unconditionally; the parent owns the section heading.
 *
 * @param reviews The seller's reviews, already ordered newest-first by the API.
 */
@Composable
fun SellerReviewsSection(
    reviews: List<Review>,
    modifier: Modifier = Modifier,
) {
    if (reviews.isEmpty()) return
    val format = LocalFormat.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        reviews.forEach { review ->
            ReviewCard(
                review = review,
                createdLabel = format.formatDateTime(review.created),
            )
        }
    }
}

@Composable
private fun ReviewCard(review: Review, createdLabel: String) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RatingStars(
                    rating = review.rating.toFloat(),
                    showCount = false,
                    size = 16.dp,
                    tint = RatingStarGold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = createdLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (review.text.isNotBlank()) {
                Text(
                    text = review.text,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (review.response.isNotBlank()) {
                SellerResponseBlock(response = review.response)
            }
        }
    }
}

@Composable
private fun SellerResponseBlock(response: String) {
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
                text = stringResource(R.string.market_listing_detail_seller_response),
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
