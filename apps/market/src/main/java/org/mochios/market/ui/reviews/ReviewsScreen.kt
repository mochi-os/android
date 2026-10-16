// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.reviews

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.InfiniteList
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiTab
import org.mochios.android.ui.components.MochiTabRow
import org.mochios.android.ui.components.MochiTextField
import org.mochios.market.R
import org.mochios.market.model.Review
import org.mochios.market.navigation.MarketApp
import org.mochios.market.ui.components.MarketLayout
import org.mochios.market.ui.components.ReviewCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewsScreen(
    navController: NavController,
    viewModel: ReviewsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ReviewsEvent.Error -> snackbarHostState.showSnackbar(event.error.userMessage())
                is ReviewsEvent.OpenListing ->
                    navController.navigate(MarketApp.listingDetail(event.listingId))
            }
        }
    }

    val openProfile = { account: String ->
        if (account.isNotEmpty()) {
            navController.navigate(MarketApp.publicProfile(account))
        }
    }
    val openListing = viewModel::openListing

    MarketLayout(
        navController = navController,
        currentRoute = MarketApp.REVIEWS,
        titleRes = R.string.market_reviews_title,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            MochiTabRow(
                tabs = listOf(
                    MochiTab(stringResource(R.string.market_reviews_tab_received)),
                    MochiTab(stringResource(R.string.market_reviews_tab_sent)),
                ),
                selectedIndex = state.selectedTab.ordinal,
                onSelect = { index -> viewModel.selectTab(ReviewsTab.entries[index]) },
            )
            Box(modifier = Modifier.weight(1f)) {
                when (state.selectedTab) {
                    ReviewsTab.RECEIVED -> {
                        ReceivedTab(
                            state = state.received,
                            onLoadMore = { viewModel.loadMore(ReviewsTab.RECEIVED) },
                            onRetry = viewModel::retry,
                            onDraftChange = { id, v -> viewModel.setResponseDraft(id, v) },
                            onSubmit = viewModel::submitResponse,
                            onProfileTap = openProfile,
                            onListingTap = openListing,
                        )
                    }
                    ReviewsTab.SENT -> {
                        SentTab(
                            state = state.sent,
                            onLoadMore = { viewModel.loadMore(ReviewsTab.SENT) },
                            onRetry = viewModel::retry,
                            onProfileTap = openProfile,
                            onListingTap = openListing,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceivedTab(
    state: ReviewsTabState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onDraftChange: (String, String) -> Unit,
    onSubmit: (String) -> Unit,
    onProfileTap: (String) -> Unit,
    onListingTap: (String) -> Unit,
) {
    EmptyOrError(state, R.string.market_reviews_empty_received_title, R.string.market_reviews_empty_received_subtitle, onRetry) {
        InfiniteList(
            items = state.reviews,
            isLoading = state.isLoading,
            hasMore = state.hasMore,
            onLoadMore = onLoadMore,
        ) { review ->
            ReceivedReviewCard(
                review = review,
                draft = state.responseDrafts[review.id].orEmpty(),
                onDraftChange = { onDraftChange(review.id, it) },
                onSubmit = { onSubmit(review.id) },
                onProfileTap = onProfileTap,
                onListingTap = onListingTap,
            )
        }
    }
}

@Composable
private fun SentTab(
    state: ReviewsTabState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onProfileTap: (String) -> Unit,
    onListingTap: (String) -> Unit,
) {
    EmptyOrError(state, R.string.market_reviews_empty_sent_title, R.string.market_reviews_empty_sent_subtitle, onRetry) {
        InfiniteList(
            items = state.reviews,
            isLoading = state.isLoading,
            hasMore = state.hasMore,
            onLoadMore = onLoadMore,
        ) { review ->
            SentReviewCard(
                review = review,
                onProfileTap = onProfileTap,
                onListingTap = onListingTap,
            )
        }
    }
}

@Composable
private fun EmptyOrError(
    state: ReviewsTabState,
    titleRes: Int,
    subtitleRes: Int,
    onRetry: () -> Unit,
    content: @Composable () -> Unit,
) {
    when {
        state.error != null && state.reviews.isEmpty() -> {
            ErrorState(error = state.error, onRetry = onRetry)
        }
        state.reviews.isEmpty() && !state.isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(
                        text = stringResource(titleRes),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(subtitleRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        else -> content()
    }
}

@Composable
private fun ReceivedReviewCard(
    review: Review,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onProfileTap: (String) -> Unit,
    onListingTap: (String) -> Unit,
) {
    ReviewCard(
        review = review,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        responseLabel = stringResource(R.string.market_sale_review_response_label),
        heading = {
            ReviewerName(
                name = review.reviewerName.orEmpty().ifBlank { review.reviewer },
                onClick = { onProfileTap(review.reviewer) },
            )
        },
        onListing = { onListingTap(review.order) },
    ) {
        if (review.response.isEmpty()) {
            MochiTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = {
                    Text(stringResource(R.string.market_reviews_response_placeholder))
                },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                MochiButton(
                    onClick = onSubmit,
                    enabled = draft.isNotBlank(),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.market_reviews_response_submit))
                }
            }
        }
    }
}

@Composable
private fun SentReviewCard(
    review: Review,
    onProfileTap: (String) -> Unit,
    onListingTap: (String) -> Unit,
) {
    ReviewCard(
        review = review,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        responseLabel = stringResource(R.string.market_reviews_response_label),
        heading = {
            ReviewerName(
                name = review.subjectName.orEmpty().ifBlank { review.subject },
                onClick = { onProfileTap(review.subject) },
            )
        },
        onListing = { onListingTap(review.order) },
    ) {
        if (review.response.isEmpty()) {
            Text(
                text = stringResource(R.string.market_reviews_awaiting_response),
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The other party's name over a review card, underlined as a link to their profile. */
@Composable
private fun ReviewerName(name: String, onClick: () -> Unit) {
    Text(
        text = name,
        style = MaterialTheme.typography.titleMedium,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable(onClick = onClick),
    )
}
