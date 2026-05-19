package org.mochios.market.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.R as MochiR
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.InfiniteList
import org.mochios.android.ui.components.LocationMapView
import org.mochios.market.R
import org.mochios.market.lib.locationName
import org.mochios.market.lib.parseLocation
import org.mochios.market.lib.toPlaceData
import org.mochios.market.model.Account
import org.mochios.market.model.Review
import org.mochios.market.ui.components.RatingStars

/**
 * Public seller profile. Mirrors web's `apps/market/web/src/features/
 * account/PublicProfile`. Layout (top → bottom):
 *
 *  - Gradient banner (primary → secondary) so each profile gets a visual
 *    identity without the server having to ship a banner image.
 *  - Circular [EntityAvatar] overlapping the banner.
 *  - Display name + (when `verified >= 2`) a Verified chip.
 *  - Sales count + member-since row.
 *  - Biography paragraph (only when filled).
 *  - Parsed [parseLocation] → [locationName] row.
 *  - Rating breakdown (one bar per star bucket).
 *  - Reviews list paginated via [InfiniteList].
 *  - Suspended/banned chip (when the projection includes it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProfileScreen(
    navController: NavController,
    viewModel: PublicProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.account?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading && state.account == null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.account == null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        text = state.error?.userMessage()
                            ?: stringResource(R.string.market_profile_load_failed),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            else -> {
                ProfileBody(
                    account = state.account!!,
                    reviews = state.reviews,
                    breakdown = state.ratingBreakdown,
                    isLoadingReviews = state.isLoadingReviews,
                    hasMore = state.hasMore,
                    serverUrl = viewModel.serverUrl,
                    accountId = viewModel.accountId,
                    onLoadMore = viewModel::loadMoreReviews,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun ProfileBody(
    account: Account,
    reviews: List<Review>,
    breakdown: IntArray,
    isLoadingReviews: Boolean,
    hasMore: Boolean,
    serverUrl: String,
    accountId: String,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val format = LocalFormat.current
    val avatarUrl = accountId.takeIf { it.isNotBlank() }?.let {
        "$serverUrl/market/-/user/$it/asset/avatar"
    }
    val parsedLocation = parseLocation(account.location)
    val locationLabel = locationName(parsedLocation)
    val status = account.status.lowercase()

    Column(modifier = modifier.fillMaxSize()) {
        // Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 0.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(44.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    EntityAvatar(
                        name = account.name,
                        src = avatarUrl,
                        seed = accountId,
                        size = 80.dp,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Name + verified
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (account.verified >= 2) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = stringResource(R.string.market_profile_verified),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Sales / member-since
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(
                        R.string.market_profile_sales_count,
                        account.sales.toInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (account.created > 0) {
                    Text(
                        text = "  •  ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.market_profile_member_since,
                            format.formatDate(account.created),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (status == "suspended" || status == "banned") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = if (status == "banned") {
                                    stringResource(R.string.market_profile_status_banned)
                                } else {
                                    stringResource(R.string.market_profile_status_suspended)
                                },
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    )
                }
            }

            // Biography
            if (account.biography.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.market_profile_biography),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = account.biography,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Location row + map (when lat/lng available).
            if (locationLabel.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = locationLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val place = parsedLocation?.toPlaceData()
                if (place != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LocationMapView(
                        checkin = place,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Rating breakdown
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.market_profile_rating_breakdown),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            RatingBreakdown(breakdown)

            // Reviews list header
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.market_profile_reviews_section),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                reviews.isEmpty() && !isLoadingReviews -> {
                    Text(
                        text = stringResource(R.string.market_profile_no_reviews),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                else -> {
                    InfiniteList(
                        items = reviews,
                        isLoading = isLoadingReviews,
                        hasMore = hasMore,
                        onLoadMore = onLoadMore,
                    ) { review ->
                        ReviewRow(review = review, serverUrl = serverUrl)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingBreakdown(counts: IntArray) {
    val total = counts.sum().coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (star in 5 downTo 1) {
            val count = counts.getOrNull(star - 1) ?: 0
            val ratio = count.toFloat() / total.toFloat()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.market_profile_stars_value, star),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(56.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(36.dp),
                )
            }
        }
    }
}

@Composable
private fun ReviewRow(review: Review, serverUrl: String) {
    val format = LocalFormat.current
    val name = review.reviewerName.orEmpty().ifBlank { review.reviewer }
    val avatarUrl = review.reviewer.takeIf { it.isNotBlank() }?.let {
        "$serverUrl/market/-/user/$it/asset/avatar"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntityAvatar(name = name, src = avatarUrl, seed = review.reviewer, size = 28.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RatingStars(rating = review.rating.toFloat(), showCount = false)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = format.formatRelativeTime(review.created),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (review.text.isNotEmpty()) {
            Text(text = review.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
