package org.mochios.market.ui.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.LocationMapView
import org.mochios.market.R
import org.mochios.market.lib.locationName
import org.mochios.market.lib.parseLocation
import org.mochios.market.lib.ratingStars
import org.mochios.market.lib.toPlaceData
import org.mochios.market.model.Account
import org.mochios.market.model.Review
import org.mochios.market.ui.components.RatingStarGold
import org.mochios.market.ui.components.RatingStars
import org.mochios.market.ui.components.VerifiedGreen

/**
 * Public seller profile. Mirrors web's `apps/market/web/src/features/
 * account/PublicProfile`. The page scrolls as one list (header card + review
 * cards), paginating reviews as the user nears the bottom:
 *
 *  - A bordered header card: soft gradient banner, a squircle [EntityAvatar]
 *    overlapping it, display name + (when `verified >= 2`) a green check,
 *    sales count, and an inline rating-stars + review-count + joined-date row.
 *  - Optional biography / location cards (only when filled).
 *  - "Reviews" section: one bordered card per review (stars + timestamp +
 *    text, plus a "Seller response" block when the seller replied).
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

    val listState = rememberLazyListState()
    LoadMoreEffect(listState, hasMore, isLoadingReviews, onLoadMore)

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") {
            ProfileHeaderCard(
                account = account,
                avatarUrl = avatarUrl,
                accountId = accountId,
                joinedText = account.created.takeIf { it > 0 }?.let {
                    stringResource(R.string.market_profile_joined, format.formatDateTime(it))
                },
            )
        }

        if (account.biography.isNotBlank()) {
            item(key = "bio") {
                SectionCard {
                    Text(
                        text = stringResource(R.string.market_profile_biography),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(text = account.biography, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (locationLabel.isNotEmpty()) {
            item(key = "location") {
                SectionCard {
                    Text(
                        text = locationLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    parsedLocation?.toPlaceData()?.let { place ->
                        Spacer(Modifier.height(8.dp))
                        LocationMapView(
                            checkin = place,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    }
                }
            }
        }

        item(key = "reviews_header") {
            Text(
                text = stringResource(R.string.market_profile_reviews_section),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        if (reviews.isEmpty() && !isLoadingReviews) {
            item(key = "reviews_empty") {
                Text(
                    text = stringResource(R.string.market_profile_no_reviews),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(reviews, key = { it.id }) { review ->
                ReviewCard(review = review, dateTimeText = format.formatDateTime(review.created))
            }
        }

        if (isLoadingReviews) {
            item(key = "reviews_loading") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

/** Auto-load the next page of reviews as the list nears its end. */
@Composable
private fun LoadMoreEffect(
    listState: LazyListState,
    hasMore: Boolean,
    isLoading: Boolean,
    onLoadMore: () -> Unit,
) {
    val shouldLoadMore by remember(hasMore, isLoading) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMore && !isLoading && lastVisible >= layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }
}

@Composable
private fun ProfileHeaderCard(
    account: Account,
    avatarUrl: String?,
    accountId: String,
    joinedText: String?,
) {
    val status = account.status.lowercase()

    ProfileCard(contentPadding = 0.dp) {
        // Banner + overlapping avatar / name row.
        Box(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        ),
                    ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 56.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // Squircle avatar sitting on a surface "plate" so it reads as a
                // card lifted off the banner.
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(4.dp),
                ) {
                    EntityAvatar(
                        name = account.name,
                        src = avatarUrl,
                        seed = accountId,
                        size = 70.dp,
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f).padding(bottom = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = account.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (account.verified >= 2) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = stringResource(R.string.market_profile_verified),
                                tint = VerifiedGreen,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Text(
                        text = stringResource(
                            R.string.market_profile_sales_count,
                            account.sales.toInt(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Rating stars + review count + joined date. Stars match the listing
        // detail screen (gold, default size) and are hidden when there is no
        // rating yet.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (account.rating > 0.0) {
                RatingStars(
                    rating = ratingStars(account.rating),
                    showCount = false,
                    tint = RatingStarGold,
                )
            }
            if (account.reviews > 0) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "(${account.reviews})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (joinedText != null) {
                if (account.rating > 0.0 || account.reviews > 0) {
                    Spacer(Modifier.width(16.dp))
                }
                Text(
                    text = joinedText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (status == "suspended" || status == "banned") {
            Row(modifier = Modifier.padding(start = 16.dp, bottom = 16.dp)) {
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
    }
}

@Composable
private fun ReviewCard(review: Review, dateTimeText: String) {
    ProfileCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RatingStars(
                rating = review.rating.toFloat(),
                showCount = false,
                tint = RatingStarGold,
            )
            Text(
                text = dateTimeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (review.text.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(text = review.text, style = MaterialTheme.typography.bodyMedium)
        }
        if (review.response.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.market_listing_detail_seller_response),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(text = review.response, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Bordered surface card used for the header and every review. */
@Composable
private fun ProfileCard(
    contentPadding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(contentPadding)) { content() }
    }
}

/** [ProfileCard] preset for simple titled sections (biography, location). */
@Composable
private fun SectionCard(content: @Composable () -> Unit) = ProfileCard(content = content)
