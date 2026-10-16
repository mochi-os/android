// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.selling

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.mochios.android.R as MochiR
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.market.R
import org.mochios.market.lib.formatPrice
import org.mochios.market.model.Currency
import org.mochios.market.model.Listing
import org.mochios.market.model.ListingStatus
import org.mochios.market.model.RemovalCheck
import org.mochios.market.navigation.MarketApp
import org.mochios.market.repository.MarketRepository
import org.mochios.market.ui.components.ListingStatusBadge
import org.mochios.market.ui.components.MarketLayout
import org.mochios.market.ui.components.MarketStatusBadge
import org.mochios.market.ui.components.StripeOnboardingBanner
import org.mochios.market.ui.components.shownStatus
import org.mochios.market.ui.dialog.AppealRemovalDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyListingsScreen(
    navController: NavController,
    viewModel: MyListingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val visible = remember(state.listings, state.searchQuery) { viewModel.visibleListings() }
    val marketRepo = remember(context) {
        EntryPointAccessors
            .fromApplication(context.applicationContext, MyListingsEntryPoint::class.java)
            .marketRepository()
    }

    // Per-listing dialog state.
    var deleteCandidate by remember { mutableStateOf<Listing?>(null) }
    var appealCandidate by remember { mutableStateOf<Listing?>(null) }

    val deleteFailedFallback = stringResource(R.string.market_listings_delete_failed)
    val relistFailedFallback = stringResource(R.string.market_listings_relist_failed)

    // Wire side-effect events (toast + relist→edit navigation).
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MyListingsEvent.NavigateToEdit ->
                    navController.navigate(MarketApp.listingEdit(event.listingId.toString()))
                is MyListingsEvent.Toast ->
                    snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    MarketLayout(
        navController = navController,
        currentRoute = MarketApp.LISTINGS,
        titleRes = R.string.market_listings_title,
        actions = {
            // Only sellers fully connected to Stripe can create listings.
            val stripe = state.stripeStatus
            if (stripe != null && stripe.chargesEnabled && stripe.payoutsEnabled) {
                MochiIconButton(onClick = { navController.navigate(MarketApp.CREATE_LISTING) }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.market_listings_new),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val listState = rememberLazyListState()
            val shouldLoadMore by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val total = info.totalItemsCount
                    val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                    state.hasMore && !state.isLoading && lastVisible >= total - 4
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) viewModel.loadMore()
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // Header span: Stripe banner + fee disclosure.
                val stripe = state.stripeStatus
                if (stripe != null && !(stripe.chargesEnabled && stripe.payoutsEnabled)) {
                    item {
                        StripeOnboardingBanner(
                            repository = marketRepo,
                            returnUrl = "mochi://market/account",
                            initialCharges = stripe.chargesEnabled,
                            initialPayouts = stripe.payoutsEnabled,
                        )
                    }
                }
                // Filter row.
                item {
                    FilterRow(
                        statusFilter = state.statusFilter,
                        searchQuery = state.searchQuery,
                        onStatusChange = viewModel::setStatusFilter,
                        onSearchChange = viewModel::setSearchQuery,
                    )
                }

                // Listings.
                if (state.error != null && state.listings.isEmpty()) {
                    item {
                        Text(
                            text = state.error!!.userMessage()
                                .ifEmpty { stringResource(R.string.market_listings_load_failed) },
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                } else if (visible.isEmpty() && !state.isLoading) {
                    item {
                        EmptyState(
                            icon = Icons.Default.Inventory2,
                            title = stringResource(R.string.market_listings_empty_title),
                            subtitle = stringResource(R.string.market_listings_empty_subtitle),
                        )
                    }
                } else {
                    items(visible, key = { listing -> listing.id }) { listing ->
                        ListingRow(
                            listing = listing,
                            onClick = {
                                navController.navigate(
                                    MarketApp.listingDetail(listing.id, listing.title),
                                )
                            },
                            onEdit = {
                                navController.navigate(MarketApp.listingEdit(listing.id))
                            },
                            onDelete = { deleteCandidate = listing },
                            onRelist = { viewModel.relistListing(listing, relistFailedFallback) },
                            onClear = { viewModel.deleteListing(listing, deleteFailedFallback) },
                            onAppeal = { appealCandidate = listing },
                        )
                    }
                    if (state.isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation. A draft is simply deleted; for anything else the
    // server says what removal cuts off (live bidders, subscribers) and the
    // warning is tailored to it, as web does.
    deleteCandidate?.let { candidate ->
        val draft = candidate.status == ListingStatus.DRAFT
        var removal by remember(candidate.id) { mutableStateOf<RemovalCheck?>(null) }
        LaunchedEffect(candidate.id) {
            if (!draft) {
                removal = runCatching { marketRepo.listingRemovalCheck(candidate.id) }.getOrNull()
            }
        }
        val check = removal
        val message = when {
            draft -> stringResource(R.string.market_listings_delete_message)
            check?.hasActiveAuction == true && check.activeBidders > 0 -> pluralStringResource(
                R.plurals.market_listings_remove_message_bidders,
                check.activeBidders.toInt(),
                LocalFormat.current.formatNumber(check.activeBidders.toInt()),
            )
            check?.hasActiveAuction == true ->
                stringResource(R.string.market_listings_remove_message_auction)
            check != null && check.activeSubscribers > 0 -> pluralStringResource(
                R.plurals.market_listings_remove_message_subscribers,
                check.activeSubscribers.toInt(),
                LocalFormat.current.formatNumber(check.activeSubscribers.toInt()),
            )
            else -> stringResource(R.string.market_listings_remove_message)
        }
        MochiAlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = stringResource(
                if (draft) R.string.market_listings_delete_title else R.string.market_listings_remove_title,
            ),
            text = message,
            confirmText = stringResource(
                if (draft) R.string.market_listings_delete_confirm else R.string.market_listings_remove_confirm,
            ),
            onConfirm = {
                viewModel.deleteListing(candidate, deleteFailedFallback)
                deleteCandidate = null
            },
            destructive = true,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }

    // Appeal removal dialog.
    appealCandidate?.let { candidate ->
        AppealRemovalDialog(
            open = true,
            listingId = candidate.id,
            onDismiss = { appealCandidate = null },
            onSuccess = { viewModel.refresh() },
        )
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    statusFilter: ListingsStatusFilter,
    searchQuery: String,
    onStatusChange: (ListingsStatusFilter) -> Unit,
    onSearchChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MochiTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = {
                Text(stringResource(R.string.market_listings_search_placeholder))
            },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.weight(0.6f),
        )
        var open by remember { mutableStateOf(false) }
        val labelRes = statusFilter.labelRes()
        ExposedDropdownMenuBox(
            expanded = open,
            onExpandedChange = { open = it },
            modifier = Modifier.weight(0.4f),
        ) {
            MochiTextField(
                value = stringResource(labelRes),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
            ) {
                for (filter in ListingsStatusFilter.entries) {
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(filter.labelRes())) },
                        onClick = {
                            onStatusChange(filter)
                            open = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * A listing as a row of the web's listings table: its title over when it was
 * listed, then its price, its status, Appeal while it is rejected or removed,
 * and a menu of what can be done with it in that state.
 */
@Composable
private fun ListingRow(
    listing: Listing,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRelist: () -> Unit,
    onClear: () -> Unit,
    onAppeal: () -> Unit,
) {
    val format = LocalFormat.current
    MochiCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = listing.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = format.formatDateTime(listing.created),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ListingStatusBadge(listing = listing)
                    if (listing.status == ListingStatus.REJECTED || listing.status == ListingStatus.REMOVED) {
                        MochiOutlinedButton(
                            onClick = onAppeal,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Flag,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.market_listings_appeal))
                        }
                    }
                }
            }
            if (listing.price > 0) {
                Text(
                    text = formatPrice(listing.price, listing.currency ?: Currency.GBP),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            ListingMenu(
                listing = listing,
                onEdit = onEdit,
                onDelete = onDelete,
                onRelist = onRelist,
                onClear = onClear,
            )
        }
    }
}

/**
 * The web's menu for a listing: a draft is edited or deleted; one moderation
 * rejected is cleared from the seller's list; anything else is relisted as a
 * new draft or, unless already removed, removed.
 */
@Composable
private fun ListingMenu(
    listing: Listing,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRelist: () -> Unit,
    onClear: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        MochiIconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.MoreHoriz,
                contentDescription = stringResource(MochiR.string.common_more_options),
            )
        }
        MochiDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (listing.status == ListingStatus.DRAFT) {
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.market_listings_action_edit)) },
                    onClick = {
                        expanded = false
                        onEdit()
                    },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                )
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.market_listings_delete_draft)) },
                    onClick = {
                        expanded = false
                        onDelete()
                    },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                )
            } else if (listing.shownStatus() == ListingStatus.REJECTED) {
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.market_listings_clear)) },
                    onClick = {
                        expanded = false
                        onClear()
                    },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                )
            } else {
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.market_listings_action_relist)) },
                    onClick = {
                        expanded = false
                        onRelist()
                    },
                    leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                )
                if (listing.status != ListingStatus.REMOVED) {
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(R.string.market_listings_remove_listing)) },
                        onClick = {
                            expanded = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                    )
                }
            }
        }
    }
}

private fun ListingsStatusFilter.labelRes(): Int = when (this) {
    ListingsStatusFilter.ALL -> R.string.market_listings_filter_all
    ListingsStatusFilter.DRAFT -> R.string.market_listings_filter_draft
    ListingsStatusFilter.ACTIVE -> R.string.market_listings_filter_active
    ListingsStatusFilter.SOLD -> R.string.market_listings_filter_sold
    ListingsStatusFilter.EXPIRED -> R.string.market_listings_filter_expired
    ListingsStatusFilter.REJECTED -> R.string.market_listings_filter_rejected
    ListingsStatusFilter.REMOVED -> R.string.market_listings_filter_removed
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MyListingsEntryPoint {
    fun marketRepository(): MarketRepository
}
