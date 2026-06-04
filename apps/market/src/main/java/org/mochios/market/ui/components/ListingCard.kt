package org.mochios.market.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.market.R
import org.mochios.market.lib.rememberListingThumbnailUrl
import org.mochios.market.model.Listing

/**
 * Grid-cell card for a listing on browse / saved / search surfaces.
 *
 * Layout (top → bottom):
 *  - Square thumbnail with a "save" heart overlay top-left and the
 *    condition / type badge stack top-right.
 *  - Title (single line, ellipsis).
 *  - Price headline via [PriceDisplay] (compact variant).
 *  - Seller row: small [EntityAvatar] + name + [RatingStars].
 *
 * `rounded-lg` (10.dp) corner radius matches the web `rounded-lg` token.
 * The card is themed via [CardDefaults.outlinedCardColors] so the
 * surrounding screen's Mochi theme stays in charge of palette.
 *
 * @param sellerName   Display name pulled from the search response.
 * @param sellerRating 0–5 rating from the search response.
 * @param sellerReviews Review count from the search response.
 * @param onClick      Tapped anywhere on the card.
 * @param onToggleSave Tapped on the heart; the caller decides whether
 *                     to add or remove from the saved list.
 */
@Composable
fun ListingCard(
    listing: Listing,
    modifier: Modifier = Modifier,
    saved: Boolean = false,
    sellerName: String? = null,
    sellerAvatarUrl: String? = null,
    sellerRating: Float = 0f,
    sellerReviews: Int = 0,
    category: String? = null,
    onClick: (Listing) -> Unit = {},
    onToggleSave: (Listing) -> Unit = {},
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(listing) },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.outlinedCardColors(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            val derivedUrl = rememberListingThumbnailUrl(listing)
            if (derivedUrl.isNullOrBlank()) {
                NoPhotoPlaceholder(modifier = Modifier.fillMaxSize())
            } else {
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(derivedUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.market_listing_photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Save heart overlay (top-left).
            IconButton(
                onClick = { onToggleSave(listing) },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(32.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.35f)),
            ) {
                Icon(
                    imageVector = if (saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(
                        if (saved) R.string.market_listing_unsave
                        else R.string.market_listing_save,
                    ),
                    tint = Color.White,
                )
            }

            // Badge stack (top-right).
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            ) {
                listing.condition?.let { ConditionBadge(condition = it) }
                listing.type?.let { TypeBadge(type = it) }
                category?.takeIf { it.isNotBlank() }?.let { CategoryBadge(name = it) }
            }
        }

        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = listing.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            PriceDisplay(listing = listing, compact = true)

            val name = sellerName ?: listing.sellerName.orEmpty()
            val rating = sellerRating.takeIf { it > 0f }
                ?: (listing.sellerRating?.toFloat() ?: 0f)
            val reviews = sellerReviews.takeIf { it > 0 }
                ?: (listing.sellerReviews?.toInt() ?: 0)

            if (name.isNotBlank() || reviews > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    EntityAvatar(
                        name = name.ifBlank { "?" },
                        src = sellerAvatarUrl,
                        seed = listing.seller,
                        size = 20.dp,
                    )
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    RatingStars(
                        rating = rating,
                        count = reviews,
                        showCount = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoPhotoPlaceholder(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ImageNotSupported,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = stringResource(R.string.market_listing_no_photo),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TypeBadge(type: org.mochios.market.model.ListingType) {
    val label = stringResource(
        when (type) {
            org.mochios.market.model.ListingType.DIGITAL -> R.string.market_type_digital
            org.mochios.market.model.ListingType.PHYSICAL -> R.string.market_type_physical
        }
    )
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun CategoryBadge(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
