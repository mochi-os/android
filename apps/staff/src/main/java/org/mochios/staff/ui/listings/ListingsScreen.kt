// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.listings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShieldMoon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.InfiniteList
import org.mochios.staff.R
import org.mochios.staff.model.PendingListing
import org.mochios.staff.ui.components.FilterChipSpec
import org.mochios.staff.ui.components.FilterChipsRow
import org.mochios.staff.ui.components.ScoreColorChip
import org.mochios.staff.ui.components.StaffStatusBadge
import org.mochios.staff.ui.dialog.ListingActionDialog
import java.text.NumberFormat
import java.util.Locale

/**
 * Staff "Listings" moderation screen.
 *
 * Android port of `apps/staff/web/src/features/listings/listings-page.tsx`.
 * Two filter dropdowns (status + moderation) and a debounced search input
 * narrow the list; each row exposes Approve / Reject / Remove actions that
 * route through [ListingsViewModel] -> [ListingActionDialog].
 *
 * The Approve / Reject controls only show when the listing is `active` and
 * the moderation column is `hold` or `review`; Remove shows for any
 * `active` row. Both rules mirror the web `pendingModeration` helper.
 *
 * Navigation: tapping a row's title opens the market listing detail at
 * `market/listing/{id}`. We don't depend on `:apps:market` directly so the
 * route is constructed inline — keeping the staff module's compile
 * boundary unchanged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingsScreen(
    navController: NavController,
    viewModel: ListingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    val approvedMsg = stringResource(R.string.staff_listings_approved_toast)
    val rejectedMsg = stringResource(R.string.staff_listings_rejected_toast)
    val removedMsg = stringResource(R.string.staff_listings_removed_toast)

    // Snackbar plumbing — translate ViewModel sentinel markers to their
    // localised string before showing.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ListingsEvent.Toast -> {
                    val text = when (event.message) {
                        ListingsViewModel.SUCCESS_APPROVED -> approvedMsg
                        ListingsViewModel.SUCCESS_REJECTED -> rejectedMsg
                        ListingsViewModel.SUCCESS_REMOVED -> removedMsg
                        else -> event.message
                    }
                    snackbar.showSnackbar(text)
                }
            }
        }
    }

    // 300 ms debounce on the search field — match the web side exactly.
    var searchInput by remember { mutableStateOf(state.query) }
    LaunchedEffect(searchInput) {
        delay(300L)
        if (searchInput != state.query) viewModel.setQuery(searchInput)
    }

    Box(Modifier.fillMaxSize()) {
        ListingsBody(
            state = state,
            searchInput = searchInput,
            onSearchInput = { searchInput = it },
            onClearSearch = {
                searchInput = ""
                viewModel.setQuery("")
            },
            onStatusChange = viewModel::setStatus,
            onModerationChange = viewModel::setModeration,
            onLoadMore = viewModel::loadMore,
            onOpenListing = { listing ->
                navController.navigate("market/listing/${listing.id}")
            },
            onAction = { type, listing -> viewModel.openAction(type, listing) },
            onRetry = viewModel::reload,
        )
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    state.pendingAction?.let { pending ->
        ListingActionDialog(
            action = pending,
            submitting = state.submitting,
            onDismiss = viewModel::dismissAction,
            onSubmit = viewModel::submitAction,
        )
    }
}

@Composable
private fun ListingsBody(
    state: ListingsUiState,
    searchInput: String,
    onSearchInput: (String) -> Unit,
    onClearSearch: () -> Unit,
    onStatusChange: (String?) -> Unit,
    onModerationChange: (String?) -> Unit,
    onLoadMore: () -> Unit,
    onOpenListing: (PendingListing) -> Unit,
    onAction: (ListingActionType, PendingListing) -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        FiltersRow(
            status = state.status,
            moderation = state.moderation,
            onStatusChange = onStatusChange,
            onModerationChange = onModerationChange,
        )
        OutlinedTextField(
            value = searchInput,
            onValueChange = onSearchInput,
            placeholder = { Text(stringResource(R.string.staff_listings_search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (searchInput.isNotEmpty()) {
                {
                    IconButton(onClick = onClearSearch) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.staff_listings_search_clear),
                        )
                    }
                }
            } else null,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        ActiveFilterChips(
            status = state.status,
            moderation = state.moderation,
            query = state.query,
            onStatusChange = onStatusChange,
            onModerationChange = onModerationChange,
            onClearSearch = onClearSearch,
        )

        val currentError = state.error
        when {
            state.isLoading && state.listings.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            currentError != null && state.listings.isEmpty() ->
                ErrorState(error = currentError, onRetry = onRetry)

            state.listings.isEmpty() -> EmptyState(
                icon = Icons.Default.ShieldMoon,
                title = stringResource(R.string.staff_listings_empty),
            )

            else -> InfiniteList(
                items = state.listings,
                isLoading = state.isLoadingMore,
                hasMore = state.listings.size < state.total,
                onLoadMore = onLoadMore,
            ) { listing ->
                ListingRow(
                    listing = listing,
                    onOpen = { onOpenListing(listing) },
                    onAction = { type -> onAction(type, listing) },
                )
            }
        }
    }
}

@Composable
private fun ListingRow(
    listing: PendingListing,
    onOpen: () -> Unit,
    onAction: (ListingActionType) -> Unit,
) {
    val format = LocalFormat.current
    val pending = listing.status == "active" &&
        (listing.moderation == "hold" || listing.moderation == "review")
    val canRemove = listing.status == "active"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
    ) {
        // Title -> opens market listing detail
        Text(
            text = listing.title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
        )
        Spacer(Modifier.height(6.dp))

        // Seller — avatar + name + onboarded tick.
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntityAvatar(
                name = listing.sellerName.ifBlank { listing.seller },
                seed = listing.seller,
                size = 20.dp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = listing.sellerName.ifBlank { formatFingerprint(listing.seller) },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (listing.sellerOnboarded != 0) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.staff_listings_seller_verified),
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // Status / moderation / score chips.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StaffStatusBadge(status = listing.status)
            StaffStatusBadge(status = listing.moderation)
            ScoreColorChip(score = listing.score.toInt())
        }
        Spacer(Modifier.height(6.dp))

        // Price + created.
        Row(verticalAlignment = Alignment.CenterVertically) {
            val priceText = if (listing.price > 0L) {
                formatPriceMinor(listing.price, listing.currency)
            } else {
                stringResource(R.string.staff_listings_price_placeholder)
            }
            Text(
                text = priceText,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = format.formatTimestamp(listing.created),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Action row.
        if (pending || canRemove) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (pending) {
                    OutlinedButton(onClick = { onAction(ListingActionType.APPROVE) }) {
                        Text(stringResource(R.string.staff_listings_approve))
                    }
                    OutlinedButton(onClick = { onAction(ListingActionType.REJECT) }) {
                        Text(stringResource(R.string.staff_listings_reject))
                    }
                }
                if (canRemove) {
                    OutlinedButton(onClick = { onAction(ListingActionType.REMOVE) }) {
                        Text(stringResource(R.string.staff_listings_remove))
                    }
                }
            }
        }
    }
}

@Composable
private fun FiltersRow(
    status: String?,
    moderation: String?,
    onStatusChange: (String?) -> Unit,
    onModerationChange: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterDropdown(
            label = stringResource(R.string.staff_listings_filter_status_label),
            current = status,
            options = listingStatusOptions(),
            anyLabel = stringResource(R.string.staff_listings_any_status),
            onSelect = onStatusChange,
            modifier = Modifier.weight(1f),
        )
        FilterDropdown(
            label = stringResource(R.string.staff_listings_filter_moderation_label),
            current = moderation,
            options = moderationStateOptions(),
            anyLabel = stringResource(R.string.staff_listings_any_moderation),
            onSelect = onModerationChange,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FilterDropdown(
    label: String,
    current: String?,
    options: List<Pair<String, String>>,
    anyLabel: String,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.first == current }?.second ?: anyLabel
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "$label: $currentLabel", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(anyLabel) },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            options.forEach { (value, l) ->
                DropdownMenuItem(
                    text = { Text(l) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun listingStatusOptions(): List<Pair<String, String>> = listOf(
    "active" to stringResource(R.string.staff_listings_status_active),
    "draft" to stringResource(R.string.staff_listings_status_draft),
    "sold" to stringResource(R.string.staff_listings_status_sold),
    "expired" to stringResource(R.string.staff_listings_status_expired),
    "removed" to stringResource(R.string.staff_listings_status_removed),
    "rejected" to stringResource(R.string.staff_listings_status_rejected),
)

@Composable
private fun moderationStateOptions(): List<Pair<String, String>> = listOf(
    "hold" to stringResource(R.string.staff_listings_moderation_hold),
    "review" to stringResource(R.string.staff_listings_moderation_review),
    "auto_approved" to stringResource(R.string.staff_listings_moderation_auto_approved),
    "soft_approved" to stringResource(R.string.staff_listings_moderation_soft_approved),
    "manual" to stringResource(R.string.staff_listings_moderation_manual),
    "rejected" to stringResource(R.string.staff_listings_moderation_rejected),
)

@Composable
private fun ActiveFilterChips(
    status: String?,
    moderation: String?,
    query: String,
    onStatusChange: (String?) -> Unit,
    onModerationChange: (String?) -> Unit,
    onClearSearch: () -> Unit,
) {
    val statusLabel = stringResource(R.string.staff_filter_label_status)
    val moderationLabel = stringResource(R.string.staff_filter_label_moderation)
    val queryLabel = stringResource(R.string.staff_filter_label_query)
    val statusOpts = listingStatusOptions()
    val modOpts = moderationStateOptions()
    val chips = buildList {
        if (!status.isNullOrBlank()) {
            val value = statusOpts.firstOrNull { it.first == status }?.second ?: status
            add(FilterChipSpec(statusLabel, value) { onStatusChange(null) })
        }
        if (!moderation.isNullOrBlank()) {
            val value = modOpts.firstOrNull { it.first == moderation }?.second ?: moderation
            add(FilterChipSpec(moderationLabel, value) { onModerationChange(null) })
        }
        if (query.isNotBlank()) {
            add(FilterChipSpec(queryLabel, query, onClearSearch))
        }
    }
    FilterChipsRow(chips = chips)
}

/**
 * Slice a person fingerprint into the 9-char "xxx-xxx-xxx" display form,
 * mirroring `formatFingerprint` in `apps/staff/web/src/lib/format.ts`.
 */
internal fun formatFingerprint(id: String): String {
    val fp = id.take(9).padEnd(9, ' ').trimEnd()
    if (fp.length < 9) return fp
    return "${fp.substring(0, 3)}-${fp.substring(3, 6)}-${fp.substring(6, 9)}"
}

/**
 * Format a minor-unit price + free-form currency code as a localised
 * currency string. The wire `currency` is the lowercase ISO 4217 code
 * (`gbp`, `usd`, `eur`, `jpy`); when [NumberFormat] doesn't recognise it
 * we fall back to `"<symbol> <amount>"`.
 *
 * JPY uses 0 decimal places by convention; everything else assumes 2 —
 * matching the web `CURRENCIES_DATA` table in `staff/web`.
 */
internal fun formatPriceMinor(amountMinor: Long, currency: String): String {
    val iso = currency.uppercase(Locale.ROOT).ifBlank { "GBP" }
    val decimals = if (iso == "JPY") 0 else 2
    val major = amountMinor.toDouble() / Math.pow(10.0, decimals.toDouble())
    return try {
        val nf = NumberFormat.getCurrencyInstance(Locale.getDefault())
        nf.currency = java.util.Currency.getInstance(iso)
        nf.minimumFractionDigits = decimals
        nf.maximumFractionDigits = decimals
        nf.format(major)
    } catch (_: Exception) {
        "$iso ${"%.${decimals}f".format(Locale.ROOT, major)}"
    }
}

