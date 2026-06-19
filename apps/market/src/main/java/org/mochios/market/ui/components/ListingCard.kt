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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.theme.LocalEntityRadius
import org.mochios.market.R
import org.mochios.market.lib.ratingStars
import org.mochios.market.model.Condition
import org.mochios.market.model.Listing
import org.mochios.market.model.ListingType
import org.mochios.market.model.PricingModel

/**
 * Grid-cell card for a listing on browse / saved / search surfaces.
 *
 * Layout (top → bottom):
 *  - Square photo with overlays:
 *      - top row : [PricingPill] (non-fixed pricing model) on the left and
 *                  the colour-coded [ConditionOverlayBadge] on the right,
 *                  sharing one row — the condition stays full and the pill
 *                  ellipsizes into the remaining width so they never overlap.
 *      - bottom-left  : [OverlayIndicator] flagging a digital listing
 *                       (non-interactive — taps open the detail page).
 *      - bottom-right : save/bookmark toggle ([OverlayIconButton]).
 *  - Title (single line, ellipsis).
 *  - Price headline via [PriceDisplay].
 *  - A divider, then the seller row: [EntityAvatar] + name + an optional
 *    green verified tick.
 *  - Star rating with a `(N)` review count.
 *
 * Corner radius follows the user-configurable [LocalEntityRadius]. The card
 * is filled (no outline) so it lifts off the screen background the way the
 * web client's listing card does.
 *
 * @param saved         Whether this listing is in the saved list (drives the
 *                      bookmark icon's filled state).
 * @param sellerName    Display name; falls back to [Listing.sellerName].
 * @param sellerRating  0–5 rating; falls back to [Listing.sellerRating]
 *                      (stored as hundredths, normalised via [ratingStars]).
 * @param sellerReviews Review count; falls back to [Listing.sellerReviews].
 * @param sellerVerified Shows the green tick beside the seller; defaults on
 *                      when the listing reports the seller as onboarded.
 * @param category      Retained for source compatibility with existing call
 *                      sites; no longer rendered on the card face.
 * @param onClick       Tapped anywhere on the card — including the download
 *                      indicator, which is non-interactive and falls through
 *                      to here so a tap opens the listing detail.
 * @param onToggleSave  Tapped on the bookmark.
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
    sellerVerified: Boolean = false,
    category: String? = null,
    onClick: (Listing) -> Unit = {},
    onToggleSave: (Listing) -> Unit = {},
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(listing) },
        shape = RoundedCornerShape(LocalEntityRadius.current),
        colors = CardDefaults.cardColors(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            // Resolve the photo from the listing itself. The relative path is
            // expanded by RelativeAssetUrlMapper inside the Coil pipeline.
            val photoUrl = listing.photo?.id
                ?.takeIf { id -> id.isNotBlank() }
                ?.let { id -> "/market/-/photo/$id/thumbnail" }
            if (photoUrl.isNullOrBlank()) {
                NoPhotoPlaceholder(modifier = Modifier.fillMaxSize())
            } else {
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(photoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.market_listing_photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Pricing + condition share one top row so they never overlap:
            // the condition badge always renders in full (pinned right) and
            // the pricing pill ellipsizes into whatever width is left.
            val pricing = pricingLabel(listing.pricing)
            val condition = listing.condition
            if (pricing != null || condition != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (pricing != null) {
                            PricingPill(
                                label = pricing,
                                modifier = Modifier.align(Alignment.TopStart),
                            )
                        }
                    }
                    if (condition != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        ConditionOverlayBadge(condition = condition)
                    }
                }
            }

            // Digital-listing indicator (bottom-left). Non-interactive — a tap
            // falls through to the card's onClick and opens the detail page.
            if (listing.type == ListingType.DIGITAL) {
                OverlayIndicator(
                    icon = Icons.Filled.Download,
                    contentDescription = stringResource(R.string.market_asset_download),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                )
            }

            // Save toggle (bottom-right).
            OverlayIconButton(
                icon = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = stringResource(
                    if (saved) R.string.market_listing_unsave else R.string.market_listing_save,
                ),
                onClick = { onToggleSave(listing) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
            )
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = listing.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(6.dp))
            PriceDisplay(listing = listing, compact = true)

            val name = sellerName ?: listing.sellerName.orEmpty()
            // Resolve the seller's avatar from the listing; an explicit
            // sellerAvatarUrl from the caller still wins when provided. The
            // relative path is expanded + token-gated inside EntityAvatar.
            val derivedAvatarUrl = listing.seller
                .takeIf { id -> id.isNotBlank() }
                ?.let { id -> "/market/-/user/$id/asset/avatar" }
            val avatarUrl = sellerAvatarUrl ?: derivedAvatarUrl
            val rating = sellerRating.takeIf { it > 0f }
                ?: listing.sellerRating?.let { ratingStars(it) } ?: 0f
            val reviews = sellerReviews.takeIf { it > 0 }
                ?: (listing.sellerReviews?.toInt() ?: 0)
            val verified = sellerVerified || (listing.sellerOnboarded ?: 0L) > 0L

            // The seller + rating block is always laid out (even with no
            // rating) so every card resolves to the same height in the grid.
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EntityAvatar(
                    name = name.ifBlank { "?" },
                    src = avatarUrl,
                    seed = listing.seller,
                    size = 24.dp,
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (verified) {
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = stringResource(R.string.market_profile_verified),
                        tint = VerifiedGreen,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            // Reserve the rating row's height unconditionally; unrated
            // listings leave the space empty rather than collapsing.
            Box(
                modifier = Modifier.height(RatingRowHeight),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (reviews > 0 || rating > 0f) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RatingStars(rating = rating, showCount = false, tint = RatingStarGold)
                        if (reviews > 0) {
                            Text(
                                text = "($reviews)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Green tick beside a verified / onboarded seller, matching the web accent. */
internal val VerifiedGreen = Color(0xFF22C55E)

/** Fixed height reserved for the rating row so cards align in the grid. */
private val RatingRowHeight = 18.dp

/** Resolves the overlay label for noteworthy pricing models, or null for fixed. */
@Composable
internal fun pricingLabel(pricing: PricingModel?): String? = when (pricing) {
    PricingModel.PWYW -> stringResource(R.string.market_filter_pricing_pwyw)
    PricingModel.SUBSCRIPTION -> stringResource(R.string.market_filter_pricing_subscription)
    PricingModel.AUCTION -> stringResource(R.string.market_filter_pricing_auction)
    else -> null
}

/** Dark translucent fill shared by the pricing pill / detail pricing chip. */
internal val PricingFill = Color.Black.copy(alpha = 0.55f)

/** Colour-coded condition fill: green (new), amber (used), blue (refurbished). */
internal fun conditionBadgeColor(condition: Condition): Color = when (condition) {
    Condition.NEW -> Color(0xFF16A34A)
    Condition.USED -> Color(0xFFF59E0B)
    Condition.REFURBISHED -> Color(0xFF2563EB)
}

/** Localised condition label shared by the badge / detail chip. */
@Composable
internal fun conditionLabel(condition: Condition): String = when (condition) {
    Condition.NEW -> stringResource(R.string.market_condition_new)
    Condition.USED -> stringResource(R.string.market_condition_used)
    Condition.REFURBISHED -> stringResource(R.string.market_condition_refurbished)
}

/** Dark translucent pill naming the pricing model, overlaid on the photo. */
@Composable
private fun PricingPill(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(PricingFill)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * Colour-coded condition chip overlaid on the photo: green (new), amber
 * (used), blue (refurbished). White label for contrast against the fill.
 */
@Composable
private fun ConditionOverlayBadge(condition: Condition, modifier: Modifier = Modifier) {
    Text(
        text = conditionLabel(condition),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(conditionBadgeColor(condition))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * Material "extra small" icon-button footprint for the on-photo overlays
 * (32dp container / 20dp icon — material3 1.3.1 has no expressive size API,
 * so the dimensions are set explicitly).
 */
private val OverlayButtonSize = 32.dp
private val OverlayIconSize = 20.dp

/** Circular dark-translucent icon button used for the save toggle. */
@Composable
private fun OverlayIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Drop IconButton's 48dp minimum touch target so the overlay actually
    // renders at [OverlayButtonSize] instead of reserving the larger area.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        IconButton(
            onClick = onClick,
            modifier = modifier
                .size(OverlayButtonSize)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(OverlayIconSize),
            )
        }
    }
}

/**
 * Non-interactive sibling of [OverlayIconButton] — same circular badge, but
 * no click handling, so taps fall through to the card's onClick.
 */
@Composable
private fun OverlayIndicator(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(OverlayButtonSize)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f)),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(OverlayIconSize),
        )
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
