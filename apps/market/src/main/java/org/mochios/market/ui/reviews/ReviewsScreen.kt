package org.mochios.market.ui.reviews

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.InfiniteList
import org.mochios.market.R
import org.mochios.market.model.Review
import org.mochios.market.navigation.MarketApp
import org.mochios.market.ui.components.RatingStars

/**
 * Two-tab reviews surface. Mirrors web's `apps/market/web/src/features/
 * reviews/ReviewsScreen`. The "Received" tab lists reviews where the
 * current identity is the subject (with an inline response composer if
 * none was filed yet); the "Sent" tab lists reviews the current identity
 * authored (with the seller's response when it's there). Both tabs use
 * [InfiniteList] for pagination.
 */
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
            if (event is ReviewsEvent.Error) {
                snackbarHostState.showSnackbar(event.error.userMessage())
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.market_reviews_title)) })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = state.selectedTab.ordinal) {
                Tab(
                    selected = state.selectedTab == ReviewsTab.RECEIVED,
                    onClick = { viewModel.selectTab(ReviewsTab.RECEIVED) },
                    text = { Text(stringResource(R.string.market_reviews_tab_received)) },
                )
                Tab(
                    selected = state.selectedTab == ReviewsTab.SENT,
                    onClick = { viewModel.selectTab(ReviewsTab.SENT) },
                    text = { Text(stringResource(R.string.market_reviews_tab_sent)) },
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                when (state.selectedTab) {
                    ReviewsTab.RECEIVED -> {
                        ReceivedTab(
                            state = state.received,
                            serverUrl = viewModel.serverUrl,
                            onLoadMore = { viewModel.loadMore(ReviewsTab.RECEIVED) },
                            onDraftChange = { id, v -> viewModel.setResponseDraft(id, v) },
                            onSubmit = viewModel::submitResponse,
                        )
                    }
                    ReviewsTab.SENT -> {
                        SentTab(
                            state = state.sent,
                            serverUrl = viewModel.serverUrl,
                            onLoadMore = { viewModel.loadMore(ReviewsTab.SENT) },
                            onListingTap = { listingId ->
                                if (listingId > 0L) {
                                    navController.navigate(
                                        MarketApp.listingDetail(listingId.toString()),
                                    )
                                }
                            },
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
    serverUrl: String,
    onLoadMore: () -> Unit,
    onDraftChange: (Long, String) -> Unit,
    onSubmit: (Long) -> Unit,
) {
    EmptyOrError(state, R.string.market_reviews_empty_received_title, R.string.market_reviews_empty_received_subtitle) {
        InfiniteList(
            items = state.reviews,
            isLoading = state.isLoading,
            hasMore = state.hasMore,
            onLoadMore = onLoadMore,
        ) { review ->
            ReceivedReviewCard(
                review = review,
                serverUrl = serverUrl,
                draft = state.responseDrafts[review.id].orEmpty(),
                onDraftChange = { onDraftChange(review.id, it) },
                onSubmit = { onSubmit(review.id) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun SentTab(
    state: ReviewsTabState,
    serverUrl: String,
    onLoadMore: () -> Unit,
    onListingTap: (Long) -> Unit,
) {
    EmptyOrError(state, R.string.market_reviews_empty_sent_title, R.string.market_reviews_empty_sent_subtitle) {
        InfiniteList(
            items = state.reviews,
            isLoading = state.isLoading,
            hasMore = state.hasMore,
            onLoadMore = onLoadMore,
        ) { review ->
            SentReviewCard(review = review, serverUrl = serverUrl, onListingTap = onListingTap)
            HorizontalDivider()
        }
    }
}

@Composable
private fun EmptyOrError(
    state: ReviewsTabState,
    titleRes: Int,
    subtitleRes: Int,
    content: @Composable () -> Unit,
) {
    when {
        state.error != null && state.reviews.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = state.error.userMessage(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(24.dp),
                )
            }
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
    serverUrl: String,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val format = LocalFormat.current
    val name = review.reviewerName.orEmpty().ifBlank { review.reviewer }
    val avatarUrl = review.reviewer.takeIf { it.isNotBlank() }?.let {
        "$serverUrl/market/-/user/$it/asset/avatar"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntityAvatar(name = name, src = avatarUrl, seed = review.reviewer, size = 36.dp)
            Spacer(modifier = Modifier.width(10.dp))
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

        if (review.response.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.market_reviews_response_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = review.response, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            // Compose: response sub-form
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = {
                    Text(stringResource(R.string.market_reviews_response_placeholder))
                },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onSubmit,
                    enabled = draft.isNotBlank(),
                ) {
                    Icon(
                        Icons.Default.Send,
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
    serverUrl: String,
    onListingTap: (Long) -> Unit,
) {
    val format = LocalFormat.current
    val name = review.subjectName.orEmpty().ifBlank { review.subject }
    val avatarUrl = review.subject.takeIf { it.isNotBlank() }?.let {
        "$serverUrl/market/-/user/$it/asset/avatar"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntityAvatar(name = name, src = avatarUrl, seed = review.subject, size = 36.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                val listingTitle = review.listingTitle.orEmpty()
                if (listingTitle.isNotEmpty()) {
                    Text(
                        text = listingTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onListingTap(review.order) },
                    )
                }
            }
            Text(
                text = format.formatRelativeTime(review.created),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RatingStars(rating = review.rating.toFloat(), showCount = false)
        if (review.text.isNotEmpty()) {
            Text(text = review.text, style = MaterialTheme.typography.bodyMedium)
        }
        if (review.response.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.market_reviews_response_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = review.response, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
