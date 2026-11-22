// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.selling

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import org.mochios.android.ui.components.InlineErrorState
import org.mochios.android.ui.components.LoadState
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.share.ShareSheet
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
import org.mochios.market.ui.components.MarketListPadding
import org.mochios.market.ui.components.MarketListPrice
import org.mochios.market.ui.components.MarketListRow
import org.mochios.market.ui.components.MarketListSpacing
import org.mochios.market.ui.components.StripeOnboardingBanner
import org.mochios.market.ui.components.shareable
import org.mochios.market.ui.components.shownStatus
import org.mochios.market.ui.dialog.AppealRemovalDialog
import org.mochios.market.ui.dialog.RemoveListingDialog

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
    val format = LocalFormat.current
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
                MyListingsEvent.NotSeller ->
                    navController.navigate(MarketApp.SELLER_SETTINGS) {
                        popUpTo(MarketApp.LISTINGS) { inclusive = true }
                    }
            }
        }
    }

    MarketLayout(
        navController = navController,
        currentRoute = MarketApp.LISTINGS,
        titleRes = R.string.market_listings_title,
        actions = {
            // Only a seller whose payments are set up can create listings.
            if (state.listable) {
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
            // Only the scroll position is derived; the next page is asked for
            // only while no page is being read or has failed, so a failed page
            // waits for its retry rather than being asked for again at once.
            val reachedEnd by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                    lastVisible >= info.totalItemsCount - 4
                }
            }
            LaunchedEffect(reachedEnd, state.paging.more, state.load) {
                if (reachedEnd && state.paging.more && state.load == LoadState.Idle) viewModel.loadMore()
            }

            LazyColumn(
                state = listState,
                contentPadding = MarketListPadding,
                verticalArrangement = MarketListSpacing,
                modifier = Modifier.fillMaxSize(),
            ) {
                // A seller whose payments are not set up yet is shown how.
                if (state.onboarding) {
                    item {
                        StripeOnboardingBanner(
                            repository = marketRepo,
                            returnUrl = "mochi://market/account",
                            onReady = { viewModel.loadAccount(fresh = true) },
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
                val load = state.load
                if (load is LoadState.Failed && state.listings.isEmpty()) {
                    item {
                        InlineErrorState(
                            message = load.error.userMessage()
                                .ifEmpty { stringResource(R.string.market_listings_load_failed) },
                            onRetry = viewModel::refresh,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                } else if (visible.isEmpty() && load == LoadState.Idle) {
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
                            onShare = { viewModel.share(listing, format) },
                        )
                    }
                    when (load) {
                        LoadState.Loading -> item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        is LoadState.Failed -> item {
                            InlineErrorState(error = load.error, onRetry = viewModel::loadMore)
                        }
                        LoadState.Idle -> Unit
                    }
                }
            }
        }
    }

    // Delete confirmation. A draft is simply deleted; for anything else the
    // server is asked first what removal cuts off (live bidders, subscribers)
    // and the dialog opens once it answers, as web does.
    deleteCandidate?.let { candidate ->
        val draft = candidate.status == ListingStatus.DRAFT
        var removal by remember(candidate.id) { mutableStateOf<RemovalCheck?>(null) }
        var checked by remember(candidate.id) { mutableStateOf(draft) }
        LaunchedEffect(candidate.id) {
            if (!draft) {
                removal = runCatching { marketRepo.listingRemovalCheck(candidate.id) }.getOrNull()
                checked = true
            }
        }
        if (checked) {
            RemoveListingDialog(
                listing = candidate,
                check = removal,
                onConfirm = {
                    viewModel.deleteListing(candidate, deleteFailedFallback)
                    deleteCandidate = null
                },
                onDismiss = { deleteCandidate = null },
            )
        }
    }

    ShareSheet(viewModel.shares)

    // Appeal removal dialog.
    appealCandidate?.let { candidate ->
        AppealRemovalDialog(
            open = true,
            listingId = candidate.id,
            notes = candidate.notes,
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
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
 * listed, then its price, its status, Appeal while moderation has rejected it,
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
    onShare: () -> Unit,
) {
    val format = LocalFormat.current
    MarketListRow(
        title = listing.title,
        onClick = onClick,
        subtitle = format.formatDateTime(listing.created),
        badges = {
            ListingStatusBadge(listing = listing)
            if (listing.shownStatus() == ListingStatus.REJECTED) {
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
        },
        trailing = if (listing.price > 0) {
            {
                MarketListPrice(
                    text = formatPrice(listing.price, listing.currency ?: Currency.GBP),
                )
            }
        } else {
            null
        },
    ) {
        ListingMenu(
            listing = listing,
            onEdit = onEdit,
            onDelete = onDelete,
            onRelist = onRelist,
            onClear = onClear,
            onShare = onShare,
        )
    }
}

/**
 * The web's menu for a listing: a draft is edited or deleted; one moderation
 * rejected or otherwise removed is cleared from the seller's list; anything
 * else is relisted as a new draft or removed, and one live for sale shared.
 */
@Composable
private fun ListingMenu(
    listing: Listing,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRelist: () -> Unit,
    onClear: () -> Unit,
    onShare: () -> Unit,
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
            } else if (
                listing.shownStatus() == ListingStatus.REJECTED ||
                listing.status == ListingStatus.REMOVED
            ) {
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.market_listings_clear)) },
                    onClick = {
                        expanded = false
                        onClear()
                    },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                )
            } else {
                if (listing.shareable()) {
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(MochiR.string.share_title)) },
                        onClick = {
                            expanded = false
                            onShare()
                        },
                        leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                    )
                }
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.market_listings_action_relist)) },
                    onClick = {
                        expanded = false
                        onRelist()
                    },
                    leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
                )
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
