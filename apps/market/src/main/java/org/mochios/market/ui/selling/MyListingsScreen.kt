// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.selling

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.android.ui.components.EmptyState
import org.mochios.market.R
import org.mochios.market.model.Listing
import org.mochios.market.model.ListingStatus
import org.mochios.market.navigation.MarketApp
import org.mochios.market.ui.components.FeeDisclosure
import org.mochios.market.ui.components.ListingCard
import org.mochios.market.ui.components.StatusBadge
import org.mochios.market.ui.components.StripeOnboardingBanner
import org.mochios.market.ui.dialog.AppealRemovalDialog
import org.mochios.market.repository.MarketRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.mochios.android.R as MochiR

/**
 * Seller's own listings screen at [MarketApp.LISTINGS]. Mirrors web's
 * `/_authenticated/listings` page:
 *
 *  - Top: Stripe onboarding banner (hidden once Stripe is fully connected),
 *    fee disclosure card.
 *  - Filter row: status dropdown + free-form title search.
 *  - Grid of [ListingCard]s with a per-card overflow menu for edit / view /
 *    share / delete / relist / appeal flows.
 *  - FAB: New listing.
 *
 * Pagination uses the same idiom as [org.mochios.market.ui.browse.HomeScreen]
 * — when the grid scrolls close to the end, [MyListingsViewModel.loadMore] is
 * called to append the next page. Pull-to-refresh isn't wired here because the
 * grid is short-lived in practice (most sellers have dozens of listings, not
 * thousands).
 */
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
    var showCreateDialog by remember { mutableStateOf(false) }
    var createTitle by remember { mutableStateOf("") }

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

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.market_listings_title)) })
        },
        floatingActionButton = {
            // Only sellers fully connected to Stripe can create listings.
            val stripe = state.stripeStatus
            if (stripe != null && stripe.chargesEnabled && stripe.payoutsEnabled) {
                ExtendedFloatingActionButton(
                    onClick = {
                        createTitle = ""
                        showCreateDialog = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.market_listings_new)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val gridState = rememberLazyGridState()
            val shouldLoadMore by remember {
                derivedStateOf {
                    val info = gridState.layoutInfo
                    val total = info.totalItemsCount
                    val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                    state.hasMore && !state.isLoading && lastVisible >= total - 4
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) viewModel.loadMore()
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(170.dp),
                state = gridState,
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // Header span: Stripe banner + fee disclosure.
                val stripe = state.stripeStatus
                if (stripe != null && !(stripe.chargesEnabled && stripe.payoutsEnabled)) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        StripeOnboardingBanner(
                            repository = marketRepo,
                            returnUrl = "mochi://market/account",
                            initialCharges = stripe.chargesEnabled,
                            initialPayouts = stripe.payoutsEnabled,
                        )
                    }
                }
                val fees = state.fees
                if (fees != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        FeeDisclosure(platformFeePercent = fees.platform)
                    }
                }

                // Filter row.
                item(span = { GridItemSpan(maxLineSpan) }) {
                    FilterRow(
                        statusFilter = state.statusFilter,
                        searchQuery = state.searchQuery,
                        onStatusChange = viewModel::setStatusFilter,
                        onSearchChange = viewModel::setSearchQuery,
                    )
                }

                // Listings.
                if (state.error != null && state.listings.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = state.error!!.userMessage()
                                .ifEmpty { stringResource(R.string.market_listings_load_failed) },
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                } else if (visible.isEmpty() && !state.isLoading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            icon = Icons.Default.Inventory2,
                            title = stringResource(R.string.market_listings_empty_title),
                            subtitle = stringResource(R.string.market_listings_empty_subtitle),
                        )
                    }
                } else {
                    items(visible, key = { it.id }) { listing ->
                        Column {
                            Box {
                                ListingCard(
                                    listing = listing,
                                    category = state.categories
                                        .firstOrNull { it.id == listing.category }?.name,
                                    onClick = {
                                        navController.navigate(
                                            MarketApp.listingDetail(listing.id.toString()),
                                        )
                                    },
                                )
                                ListingOverflow(
                                    listing = listing,
                                    onEdit = {
                                        navController.navigate(
                                            MarketApp.listingEdit(listing.id.toString()),
                                        )
                                    },
                                    onView = {
                                        navController.navigate(
                                            MarketApp.listingDetail(listing.id.toString()),
                                        )
                                    },
                                    onShare = { shareListing(context, listing) },
                                    onDelete = { deleteCandidate = listing },
                                    onRelist = {
                                        viewModel.relistListing(listing, relistFailedFallback)
                                    },
                                    onAppeal = { appealCandidate = listing },
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                listing.status?.let {
                                    StatusBadge(status = it.wireName())
                                }
                            }
                        }
                    }
                    if (state.isLoading) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
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

    // Delete confirmation.
    deleteCandidate?.let { candidate ->
        ConfirmDialog(
            title = stringResource(R.string.market_listings_delete_title),
            message = stringResource(R.string.market_listings_delete_message),
            confirmLabel = stringResource(R.string.market_listings_delete_confirm),
            isDestructive = true,
            onConfirm = {
                viewModel.deleteListing(candidate, deleteFailedFallback)
                deleteCandidate = null
            },
            onDismiss = { deleteCandidate = null },
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

    // New-listing dialog: collect the title up front so the editor always has
    // one to create the listing row with; the row itself is still created
    // lazily on the editor's first save.
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.market_listings_new)) },
            text = {
                OutlinedTextField(
                    value = createTitle,
                    onValueChange = { createTitle = it },
                    label = { Text(stringResource(R.string.market_editor_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = createTitle.isNotBlank(),
                    onClick = {
                        showCreateDialog = false
                        navController.navigate(MarketApp.newListing(createTitle.trim()))
                    },
                ) { Text(stringResource(R.string.market_listings_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(stringResource(org.mochios.android.R.string.common_cancel))
                }
            },
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
        var open by remember { mutableStateOf(false) }
        val labelRes = statusFilter.labelRes()
        ExposedDropdownMenuBox(
            expanded = open,
            onExpandedChange = { open = it },
            modifier = Modifier.weight(0.4f),
        ) {
            OutlinedTextField(
                value = stringResource(labelRes),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(stringResource(R.string.market_listings_filter_status)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
            ) {
                for (filter in ListingsStatusFilter.entries) {
                    DropdownMenuItem(
                        text = { Text(stringResource(filter.labelRes())) },
                        onClick = {
                            onStatusChange(filter)
                            open = false
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = {
                Text(stringResource(R.string.market_listings_search_placeholder))
            },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.weight(0.6f),
        )
    }
}

@Composable
private fun ListingOverflow(
    listing: Listing,
    onEdit: () -> Unit,
    onView: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRelist: () -> Unit,
    onAppeal: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(2.dp)) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.MoreHoriz,
                contentDescription = stringResource(MochiR.string.common_more_options),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.market_listings_action_edit)) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.market_listings_action_view)) },
                onClick = {
                    expanded = false
                    onView()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.market_listings_action_share)) },
                onClick = {
                    expanded = false
                    onShare()
                },
            )
            if (listing.status == ListingStatus.DRAFT) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.market_listings_action_delete)) },
                    onClick = {
                        expanded = false
                        onDelete()
                    },
                )
            }
            if (listing.status == ListingStatus.SOLD || listing.status == ListingStatus.EXPIRED) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.market_listings_action_relist)) },
                    onClick = {
                        expanded = false
                        onRelist()
                    },
                )
            }
            if (listing.status == ListingStatus.REMOVED) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.market_listings_action_appeal)) },
                    onClick = {
                        expanded = false
                        onAppeal()
                    },
                )
            }
        }
    }
}

private fun shareListing(context: android.content.Context, listing: Listing) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, listing.title)
        putExtra(Intent.EXTRA_TEXT, "mochi://market/listings/${listing.id}")
    }
    val chooser = Intent.createChooser(
        intent,
        context.getString(R.string.market_listings_share_chooser),
    )
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
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

private fun ListingStatus.wireName(): String = when (this) {
    ListingStatus.DRAFT -> "draft"
    ListingStatus.ACTIVE -> "active"
    ListingStatus.SOLD -> "sold"
    ListingStatus.EXPIRED -> "expired"
    ListingStatus.REJECTED -> "rejected"
    ListingStatus.REMOVED -> "removed"
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MyListingsEntryPoint {
    fun marketRepository(): MarketRepository
}
