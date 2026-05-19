package org.mochios.market.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mochios.market.R

/**
 * Five-star rating display with half-star precision.
 *
 * @param rating  Float in `[0, 5]`. Rounded to the nearest half-star.
 * @param count   Number of reviews used in the optional `(N reviews)` suffix.
 * @param showCount Whether to append the count suffix.
 * @param size    Icon size for each star.
 */
@Composable
fun RatingStars(
    rating: Float,
    modifier: Modifier = Modifier,
    count: Int = 0,
    showCount: Boolean = true,
    size: Dp = 14.dp,
) {
    val clamped = rating.coerceIn(0f, 5f)
    val halved = (kotlin.math.round(clamped * 2f) / 2f).coerceIn(0f, 5f)
    val full = halved.toInt()
    val hasHalf = (halved - full) >= 0.5f
    val empty = 5 - full - (if (hasHalf) 1 else 0)

    val starColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(full) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = starColor,
                modifier = Modifier.size(size),
            )
        }
        if (hasHalf) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.StarHalf,
                contentDescription = null,
                tint = starColor,
                modifier = Modifier.size(size),
            )
        }
        repeat(empty) {
            Icon(
                imageVector = Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = starColor,
                modifier = Modifier.size(size),
            )
        }
        if (showCount && count > 0) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.market_rating_reviews, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
