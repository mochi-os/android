// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.reviews

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.R as MochiR
import org.mochios.android.api.userMessage
import org.mochios.android.format.formatFingerprint
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.LoadingState
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiCard
import org.mochios.staff.R
import org.mochios.staff.model.Review
import org.mochios.staff.ui.components.StaffCardAction
import org.mochios.staff.ui.components.StaffCardMenu
import org.mochios.staff.ui.components.StaffFilter
import org.mochios.staff.ui.components.StaffFilters
import org.mochios.staff.ui.components.StaffStatusBadge
import org.mochios.staff.ui.components.StaffUserAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewsScreen(
    @Suppress("UNUSED_PARAMETER") navController: NavController,
    viewModel: ReviewsViewModel = hiltViewModel(),
) {
    // Role is read from LocalStaffMe.current in case future review-moderation
    // affordances need admin gating; the current body doesn't yet branch on
    // role, but the lookup is in place for #557 (reviewer role badge) and
    // #564 (route-level admin gates).
    @Suppress("unused")
    val me = org.mochios.staff.ui.components.LocalStaffMe.current

    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ReviewsEvent.Toast -> snackbarHostState.showSnackbar(resources.getString(event.messageRes))
                is ReviewsEvent.Error -> {
                    val fallback = resources.getString(R.string.staff_reviews_toast_update_failed)
                    val msg = event.error.userMessage().ifBlank { fallback }
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        ReviewsBody(
            padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            state = state,
            onFilterChange = viewModel::setFilter,
            onAction = viewModel::runAction,
            onAskRemove = viewModel::askRemove,
            onLoadMore = viewModel::loadMore,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // Remove confirmation. Uses lib's MochiAlertDialog with the
    // "reviewer → subject on listing" body string the web version composes.
    val pending = state.pendingRemove
    if (pending != null) {
        val reviewerName = pending.reviewerName.orEmpty().ifBlank { formatFingerprint(pending.reviewerFingerprint.orEmpty()) }
        val subjectName = pending.subjectName.orEmpty().ifBlank { formatFingerprint(pending.subjectFingerprint.orEmpty()) }
        val message = if (!pending.listingTitle.isNullOrBlank()) {
            stringResource(
                R.string.staff_reviews_remove_desc_full,
                reviewerName,
                subjectName,
                pending.listingTitle.orEmpty(),
            )
        } else {
            stringResource(R.string.staff_reviews_remove_desc_short, reviewerName, subjectName)
        }
        MochiAlertDialog(
            onDismissRequest = viewModel::cancelRemove,
            title = stringResource(R.string.staff_reviews_remove_title),
            text = message,
            confirmText = stringResource(R.string.staff_reviews_remove_confirm),
            onConfirm = viewModel::confirmRemove,
            destructive = true,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
}

@Composable
private fun ReviewsBody(
    padding: PaddingValues,
    state: ReviewsUiState,
    onFilterChange: (ReviewStatusFilter) -> Unit,
    onAction: (Review, String) -> Unit,
    onAskRemove: (Review) -> Unit,
    onLoadMore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        StaffFilters(
            filters = listOf(
                StaffFilter(
                    label = stringResource(R.string.staff_filter_label_status),
                    chipLabel = stringResource(R.string.staff_filter_label_status),
                    anyLabel = stringResource(R.string.staff_reviews_filter_all),
                    options = listOf(
                        "published" to stringResource(R.string.staff_reviews_filter_published),
                        "removed" to stringResource(R.string.staff_reviews_filter_removed),
                    ),
                    current = state.filter.wireValue(),
                    onSelect = { value ->
                        onFilterChange(
                            when (value) {
                                "published" -> ReviewStatusFilter.PUBLISHED
                                "removed" -> ReviewStatusFilter.REMOVED
                                else -> ReviewStatusFilter.ALL
                            },
                        )
                    },
                ),
            ),
        )

        when {
            state.isLoading && state.reviews.isEmpty() -> LoadingState()
            state.reviews.isEmpty() -> EmptyState(
                icon = Icons.Default.Star,
                title = stringResource(R.string.staff_reviews_empty),
            )
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.reviews, key = { it.id }) { review ->
                        ReviewRow(
                            review = review,
                            onAction = onAction,
                            onAskRemove = onAskRemove,
                        )
                    }
                    if (state.hasMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                LaunchedEffect(state.reviews.size) { onLoadMore() }
                                if (state.isLoadingMore) {
                                    androidx.compose.material3.CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewRow(
    review: Review,
    onAction: (Review, String) -> Unit,
    onAskRemove: (Review) -> Unit,
) {
    val format = LocalFormat.current
    val reviewerName = review.reviewerName.orEmpty().ifBlank { formatFingerprint(review.reviewerFingerprint.orEmpty()) }
    val subjectName = review.subjectName.orEmpty().ifBlank { formatFingerprint(review.subjectFingerprint.orEmpty()) }
    MochiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StaffUserAvatar(name = reviewerName, id = review.reviewer, size = 36.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = reviewerName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ReviewerRoleChip(role = review.role)
                    StaffStatusBadge(status = review.status)
                }
                Spacer(modifier = Modifier.height(6.dp))
                // Subject + listing block.
                Text(
                    text = subjectName,
                    style = MaterialTheme.typography.bodyMedium,
                )
                val listingTitle = review.listingTitle.orEmpty()
                if (listingTitle.isNotEmpty()) {
                    Text(
                        text = listingTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (review.order.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.staff_reviews_order_label, review.order),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                InlineRatingStars(rating = review.rating)
                if (review.text.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = review.text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = format.formatRelativeTime(review.created),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StaffCardMenu(
                actions = listOf(
                    if (review.status == "removed") {
                        StaffCardAction(
                            label = stringResource(R.string.staff_reviews_action_restore),
                            icon = Icons.Outlined.Restore,
                            onClick = { onAction(review, "restore") },
                        )
                    } else {
                        StaffCardAction(
                            label = stringResource(R.string.staff_reviews_action_remove),
                            icon = Icons.Outlined.Block,
                            destructive = true,
                            onClick = { onAskRemove(review) },
                        )
                    },
                ),
            )
        }
    }
}

/** Compact inline 5-star row. Mirrors web's flex of 5 Star icons with the
 *  filled / muted treatment driven by index < rating. */
@Composable
private fun InlineRatingStars(rating: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val clamped = rating.coerceIn(0, 5)
        val starColor = MaterialTheme.colorScheme.primary
        repeat(clamped) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = starColor,
                modifier = Modifier.size(14.dp),
            )
        }
        repeat(5 - clamped) {
            Icon(
                imageVector = Icons.Default.StarOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun roleLabel(role: String): String = when (role.lowercase()) {
    "buyer" -> stringResource(R.string.staff_reviews_role_buyer)
    "seller" -> stringResource(R.string.staff_reviews_role_seller)
    else -> role
}

/**
 * Buyer / seller chip. [StaffStatusBadge] has no vocabulary for these, so this
 * rolls its own tone.
 */
@Composable
private fun ReviewerRoleChip(role: String) {
    val key = role.trim().lowercase()
    if (key != "buyer" && key != "seller") return
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = if (key == "buyer") {
        scheme.secondaryContainer to scheme.onSecondaryContainer
    } else {
        scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    Text(
        text = roleLabel(role),
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
