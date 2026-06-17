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
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.LoadingState
import org.mochios.staff.R
import org.mochios.staff.model.Review
import org.mochios.staff.ui.components.FilterChipSpec
import org.mochios.staff.ui.components.FilterChipsRow
import org.mochios.staff.ui.components.StaffStatusBadge

/**
 * Staff review moderation screen. Mirrors web's
 * `apps/staff/web/src/features/reviews/reviews-page.tsx`:
 *
 *  - Drawer-driven nav via the parent [StaffLayout]'s [StaffSidebar].
 *  - Status filter row (All / Published / Hidden / Removed).
 *  - Table-style list of reviews with reviewer / subject / listing / rating /
 *    body / status / created columns.
 *  - Per-row overflow menu offering `Hide` (when published), `Restore` (when
 *    hidden or removed), and `Remove` (when not already removed).
 *  - Remove flows through a [ConfirmDialog] with the
 *    `reviewer → subject on listing` summary; the action is destructive and
 *    cannot be undone.
 *
 * The screen owns the drawer state and a snackbar host; everything else lives
 * on [ReviewsViewModel].
 */
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
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ReviewsEvent.Toast -> snackbarHostState.showSnackbar(context.getString(event.messageRes))
                is ReviewsEvent.Error -> {
                    val fallback = context.getString(R.string.staff_reviews_toast_update_failed)
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
            serverUrl = viewModel.serverUrl,
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

    // Remove confirmation. Uses lib's ConfirmDialog with the
    // "reviewer → subject on listing" body string the web version composes.
    val pending = state.pendingRemove
    if (pending != null) {
        val reviewerName = pending.reviewerName.orEmpty().ifBlank { fingerprint(pending.reviewer) }
        val subjectName = pending.subjectName.orEmpty().ifBlank { fingerprint(pending.subject) }
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
        ConfirmDialog(
            title = stringResource(R.string.staff_reviews_remove_title),
            message = message,
            confirmLabel = stringResource(R.string.staff_reviews_remove_confirm),
            isDestructive = true,
            onConfirm = viewModel::confirmRemove,
            onDismiss = viewModel::cancelRemove,
        )
    }
}

@Composable
private fun ReviewsBody(
    padding: PaddingValues,
    state: ReviewsUiState,
    serverUrl: String,
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
        FilterBar(state.filter, onFilterChange)
        ActiveFilterChips(filter = state.filter, onFilterChange = onFilterChange)

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
                            serverUrl = serverUrl,
                            onAction = onAction,
                            onAskRemove = onAskRemove,
                        )
                        HorizontalDivider()
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
private fun FilterBar(
    current: ReviewStatusFilter,
    onChange: (ReviewStatusFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (current) {
        ReviewStatusFilter.ALL -> stringResource(R.string.staff_reviews_filter_all)
        ReviewStatusFilter.PUBLISHED -> stringResource(R.string.staff_reviews_filter_published)
        ReviewStatusFilter.HIDDEN -> stringResource(R.string.staff_reviews_filter_hidden)
        ReviewStatusFilter.REMOVED -> stringResource(R.string.staff_reviews_filter_removed)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Button(onClick = { expanded = true }) {
                Text(label)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ReviewStatusFilter.values().forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                when (option) {
                                    ReviewStatusFilter.ALL -> stringResource(R.string.staff_reviews_filter_all)
                                    ReviewStatusFilter.PUBLISHED -> stringResource(R.string.staff_reviews_filter_published)
                                    ReviewStatusFilter.HIDDEN -> stringResource(R.string.staff_reviews_filter_hidden)
                                    ReviewStatusFilter.REMOVED -> stringResource(R.string.staff_reviews_filter_removed)
                                },
                            )
                        },
                        onClick = {
                            expanded = false
                            onChange(option)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Removable chip for the active status filter. Renders nothing when the
 * filter is [ReviewStatusFilter.ALL]. Mirrors `ActiveFilterChips` in
 * [org.mochios.staff.ui.accounts.AccountsScreen] — clicking the chip
 * resets the filter to ALL.
 */
@Composable
private fun ActiveFilterChips(
    filter: ReviewStatusFilter,
    onFilterChange: (ReviewStatusFilter) -> Unit,
) {
    if (filter == ReviewStatusFilter.ALL) {
        FilterChipsRow(chips = emptyList())
        return
    }
    val label = stringResource(R.string.staff_filter_label_status)
    val value = when (filter) {
        ReviewStatusFilter.ALL -> stringResource(R.string.staff_reviews_filter_all)
        ReviewStatusFilter.PUBLISHED -> stringResource(R.string.staff_reviews_filter_published)
        ReviewStatusFilter.HIDDEN -> stringResource(R.string.staff_reviews_filter_hidden)
        ReviewStatusFilter.REMOVED -> stringResource(R.string.staff_reviews_filter_removed)
    }
    FilterChipsRow(
        chips = listOf(
            FilterChipSpec(label, value) { onFilterChange(ReviewStatusFilter.ALL) },
        ),
    )
}

@Composable
private fun ReviewRow(
    review: Review,
    serverUrl: String,
    onAction: (Review, String) -> Unit,
    onAskRemove: (Review) -> Unit,
) {
    val format = LocalFormat.current
    val reviewerName = review.reviewerName.orEmpty().ifBlank { fingerprint(review.reviewer) }
    val subjectName = review.subjectName.orEmpty().ifBlank { fingerprint(review.subject) }
    val avatarUrl = review.reviewer.takeIf { it.isNotBlank() }?.let {
        "$serverUrl/staff/-/user/$it/asset/avatar"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EntityAvatar(name = reviewerName, src = avatarUrl, seed = review.reviewer, size = 36.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = reviewerName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        ReviewerRoleChip(role = review.role)
                    }
                }
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
        OverflowMenu(review = review, onAction = onAction, onAskRemove = onAskRemove)
    }
}

@Composable
private fun OverflowMenu(
    review: Review,
    onAction: (Review, String) -> Unit,
    onAskRemove: (Review) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.MoreHoriz,
                contentDescription = stringResource(R.string.staff_reviews_overflow_actions),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (review.status == "published") {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.staff_reviews_action_hide)) },
                    onClick = {
                        expanded = false
                        onAction(review, "hide")
                    },
                )
            }
            if (review.status == "hidden" || review.status == "removed") {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.staff_reviews_action_restore)) },
                    onClick = {
                        expanded = false
                        onAction(review, "restore")
                    },
                )
            }
            if (review.status != "removed") {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.staff_reviews_action_remove)) },
                    onClick = {
                        expanded = false
                        onAskRemove(review)
                    },
                )
            }
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
 * Small inline chip that surfaces whether the review was authored by the
 * Buyer or the Seller. Mirrors web's `reviews-page.tsx` where the role sits
 * next to the reviewer name as muted text — Android tightens this to a
 * pill so the buyer-vs-seller distinction is glanceable in a scrolling list
 * where the surrounding metadata is dense.
 *
 * `buyer` / `seller` aren't statuses [StaffStatusBadge] knows about (its
 * vocabulary is moderation states + account/role labels for the team
 * page), so this chip rolls its own subdued tone instead of stretching the
 * shared component.
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

/** First 9 characters of an entity ID — the standard Mochi fingerprint slice. */
private fun fingerprint(id: String): String =
    if (id.length <= 9) id else id.substring(0, 9)
