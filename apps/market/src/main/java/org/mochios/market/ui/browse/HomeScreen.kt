// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.browse

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.InlineErrorState
import org.mochios.android.ui.components.LoadState
import org.mochios.android.ui.components.FilterButton
import org.mochios.android.ui.components.FilterChipRow
import org.mochios.android.ui.components.FilterSheet
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.components.NotificationBell
import org.mochios.market.R
import org.mochios.market.lib.formatPrice
import org.mochios.market.lib.toMinorUnits
import org.mochios.market.model.Currency
import org.mochios.market.model.Listing
import org.mochios.market.navigation.MarketApp
import org.mochios.market.ui.components.MarketLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    onOpenNotifications: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // Confirm each save/unsave with a short toast.
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.saveEvents.collect { saved ->
            val message = resources.getString(
                if (saved) R.string.market_listing_save
                else R.string.market_listing_unsave,
            )
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    // An action the server refused, in its own words.
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.failures.collect { failure -> snackbar.showSnackbar(failure.userMessage()) }
    }

    // Debounce search input so the ViewModel doesn't fire a request per
    // keystroke. The web side debounces at 300 ms; match it here.
    var searchInput by remember { mutableStateOf(state.query) }
    // Pull external query resets (e.g. Clear filters) back into the field;
    // the debounce effect below only pushes local -> VM, never the reverse.
    LaunchedEffect(state.query) {
        if (state.query != searchInput) searchInput = state.query
    }
    LaunchedEffect(searchInput) {
        delay(300L)
        if (searchInput != state.query) viewModel.setQuery(searchInput)
    }

    MarketLayout(
        navController = navController,
        currentRoute = MarketApp.HOME,
        titleRes = R.string.market_title,
        actions = {
            FilterButton(
                active = activeFilterCount(state.filters) > 0,
                onClick = { viewModel.openFilterSheet() },
                contentDescription = stringResource(R.string.market_filter_open),
            )
            NotificationBell(onClick = onOpenNotifications)
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        HomeContent(
            padding = padding,
            state = state,
            searchInput = searchInput,
            onSearchInput = { searchInput = it },
            onClearSearch = {
                searchInput = ""
                viewModel.setQuery("")
            },
            onOpenFilter = { viewModel.openFilterSheet(it) },
            onRemoveFilter = { viewModel.setFilter(it, null) },
            onClearAll = { viewModel.clearFilters() },
            onLoadMore = viewModel::loadMore,
            onRetry = viewModel::retry,
            onListingClick = { listing ->
                viewModel.viewListing(listing)
                navController.navigate(
                    MarketApp.listingDetail(listing.id.toString(), listing.title),
                )
            },
            onToggleSave = viewModel::toggleSave,
            onActivateAccount = viewModel::activateAccount,
            onDismissOnboarding = viewModel::dismissOnboarding,
            onClearRecent = viewModel::clearRecentlyViewed,
        )
    }

    if (state.filterSheetOpen) {
        MarketFilterSheet(
            state = state,
            onUpdate = viewModel::setFilter,
            onDismiss = viewModel::closeFilterSheet,
            onClearAll = {
                viewModel.clearFilters()
                viewModel.closeFilterSheet()
            },
        )
    }
}

/** The browse screen below its top bar, apart for testing: the screen's bell cannot render in a test. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeContent(
    padding: PaddingValues,
    state: HomeUiState,
    searchInput: String,
    onSearchInput: (String) -> Unit,
    onClearSearch: () -> Unit,
    onOpenFilter: (Filter) -> Unit,
    onRemoveFilter: (Filter) -> Unit,
    onClearAll: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onListingClick: (Listing) -> Unit,
    onToggleSave: (Listing) -> Unit,
    onActivateAccount: () -> Unit,
    onDismissOnboarding: () -> Unit,
    onClearRecent: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    // Only the scroll position is derived here — it depends solely on the
    // stably-remembered gridState. Whether there is more and how the last
    // page went are read fresh in the LaunchedEffect below; capturing them
    // inside this remembered block would freeze them at their
    // first-composition values. The next page is asked for only while no page
    // is being read or has failed, so a failed page waits for its retry
    // rather than being asked for again at once.
    val reachedEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 6
        }
    }
    LaunchedEffect(reachedEnd, state.paging.more, state.load) {
        if (reachedEnd && state.paging.more && state.load == LoadState.Idle) {
            onLoadMore()
        }
    }

    val isColdStart = state.query.isBlank() && state.filters.isEmpty()
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        if (state.accountActive == false) {
            OnboardingCard(
                activating = state.activatingAccount,
                onActivate = onActivateAccount,
                onDismiss = onDismissOnboarding,
            )
        }
        MochiTextField(
            value = searchInput,
            onValueChange = onSearchInput,
            placeholder = { Text(stringResource(R.string.market_search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (searchInput.isNotEmpty()) {
                {
                    MochiIconButton(onClick = onClearSearch) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.market_search_clear),
                        )
                    }
                }
            } else null,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        FilterPillsRow(state = state, onOpenFilter = onOpenFilter)
        ActiveFilterChips(state = state, onRemove = onRemoveFilter, onClearAll = onClearAll)

        val load = state.load
        if (load is LoadState.Failed && state.listings.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                ErrorState(error = load.error, onRetry = onRetry)
            }
            return@Column
        }
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 170.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // Recently viewed: an on-device strip above the grid on the
                // default browse view, excluding items already in the grid.
                // Mirrors web's home-page recently-viewed section.
                val visibleRecent = state.recentListings.filter { recent ->
                    state.listings.none { it.id == recent.id }
                }
                if (isColdStart && visibleRecent.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.market_section_recently_viewed),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            MochiTextButton(onClick = onClearRecent) {
                                Text(stringResource(R.string.market_recently_viewed_clear))
                            }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(visibleRecent, key = { it.id }) { listing ->
                                org.mochios.market.ui.components.ListingCard(
                                    listing = listing,
                                    modifier = Modifier.width(160.dp),
                                    saved = listing.id.toString() in state.savedIds,
                                    onClick = { onListingClick(listing) },
                                    onToggleSave = onToggleSave,
                                )
                            }
                        }
                    }
                }

                if (state.listings.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(R.string.market_section_listings),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }

                items(state.listings, key = { it.id }) { listing ->
                    org.mochios.market.ui.components.ListingCard(
                        listing = listing,
                        saved = listing.id.toString() in state.savedIds,
                        onClick = { onListingClick(listing) },
                        onToggleSave = onToggleSave,
                    )
                }

                when (load) {
                    LoadState.Loading -> item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is LoadState.Failed -> item(span = { GridItemSpan(maxLineSpan) }) {
                        InlineErrorState(error = load.error, onRetry = onRetry)
                    }
                    LoadState.Idle -> Unit
                }
            }

            // Only a search that answered with nothing; a failure is above.
            if (state.listings.isEmpty() && load == LoadState.Idle && !isColdStart) {
                EmptyState(
                    icon = Icons.Default.SearchOff,
                    title = stringResource(R.string.market_empty_title),
                    subtitle = stringResource(R.string.market_empty_subtitle),
                    action = {
                        MochiOutlinedButton(onClick = onClearAll) {
                            Text(stringResource(R.string.market_filter_clear))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterPillsRow(state: HomeUiState, onOpenFilter: (Filter) -> Unit) {
    val pills = listOf(
        Filter.CATEGORY to stringResource(R.string.market_filter_category),
        Filter.TYPE to stringResource(R.string.market_filter_type),
        Filter.CONDITION to stringResource(R.string.market_filter_condition),
        Filter.PRICING to stringResource(R.string.market_filter_pricing),
        Filter.DELIVERY to stringResource(R.string.market_filter_delivery),
        Filter.PRICE_MIN to stringResource(R.string.market_filter_price_range),
        Filter.SORT to stringResource(R.string.market_filter_sort),
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(pills) { (filter, label) ->
            val active = state.filters.containsKey(filter) ||
                (filter == Filter.PRICE_MIN && state.filters.containsKey(Filter.PRICE_MAX))
            FilterChip(
                selected = active,
                onClick = { onOpenFilter(filter) },
                label = { Text(label) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveFilterChips(
    state: HomeUiState,
    onRemove: (Filter) -> Unit,
    onClearAll: () -> Unit,
) {
    if (state.filters.isEmpty()) return
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((filter, value) in state.filters) {
            // The currency is how the price chips read, not a filter of its own.
            val label = labelForFilter(state, filter, value) ?: continue
            AssistChip(
                onClick = { onRemove(filter) },
                label = { Text(label) },
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.market_filter_remove),
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
        AssistChip(
            onClick = onClearAll,
            label = { Text(stringResource(R.string.market_filter_clear)) },
        )
    }
}

/**
 * An active filter's chip text, from the same labels the filter sheet offers;
 * null for a value with no label, and for the currency, which the price
 * chips carry. A category whose name has not loaded reads as the filter's
 * name, never its id.
 */
@Composable
internal fun labelForFilter(state: HomeUiState, filter: Filter, value: String): String? {
    return when (filter) {
        Filter.CATEGORY -> state.categories.firstOrNull { it.id == value }?.name
            ?: stringResource(R.string.market_filter_category)
        Filter.PRICE_MIN -> stringResource(R.string.market_filter_chip_min, priceLabel(state, value))
        Filter.PRICE_MAX -> stringResource(R.string.market_filter_chip_max, priceLabel(state, value))
        Filter.CURRENCY -> null
        else -> filterOptions(filter).firstOrNull { (option, _) -> option == value }?.second
    }
}

/** A typed price bound as a price in the range's currency, or as typed when that cannot be read. */
@Composable
private fun priceLabel(state: HomeUiState, value: String): String {
    val code = state.filters[Filter.CURRENCY] ?: HomeViewModel.DEFAULT_CURRENCY
    val currency = Currency.entries.firstOrNull { entry -> entry.name.equals(code, ignoreCase = true) }
        ?: return value
    return formatPrice(toMinorUnits(value, currency), currency)
}

/**
 * The values [filter] can take and their labels, shared by the filter sheet
 * and the active-filter chips; empty for a filter that is not chosen from a
 * list.
 */
@Composable
private fun filterOptions(filter: Filter): List<Pair<String, String>> = when (filter) {
    Filter.TYPE -> listOf(
        "physical" to stringResource(R.string.market_filter_type_physical),
        "digital" to stringResource(R.string.market_filter_type_digital),
    )
    Filter.CONDITION -> listOf(
        "new" to stringResource(R.string.market_filter_condition_new),
        "used" to stringResource(R.string.market_filter_condition_used),
        "refurbished" to stringResource(R.string.market_filter_condition_refurbished),
    )
    Filter.PRICING -> listOf(
        "fixed" to stringResource(R.string.market_filter_pricing_fixed),
        "pwyw" to stringResource(R.string.market_filter_pricing_pwyw),
        "subscription" to stringResource(R.string.market_filter_pricing_subscription),
        "auction" to stringResource(R.string.market_filter_pricing_auction),
    )
    Filter.DELIVERY -> listOf(
        "shipping" to stringResource(R.string.market_filter_delivery_shipping),
        "pickup" to stringResource(R.string.market_filter_delivery_pickup),
        "download" to stringResource(R.string.market_filter_delivery_download),
    )
    Filter.SORT -> listOf(
        "recent" to stringResource(R.string.market_filter_sort_recent),
        "price_low" to stringResource(R.string.market_filter_sort_price_low),
        "price_high" to stringResource(R.string.market_filter_sort_price_high),
        "rating" to stringResource(R.string.market_filter_sort_rating),
    )
    else -> emptyList()
}

/** [filter]'s options in the sheet, after "All". */
@Composable
private fun sheetOptions(filter: Filter): List<Pair<String?, String>> =
    listOf(null to stringResource(R.string.market_filter_all)) + filterOptions(filter)

@Composable
private fun OnboardingCard(
    activating: Boolean,
    onActivate: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.market_onboarding_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.market_onboarding_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MochiButton(onClick = onActivate, enabled = !activating) {
                    Icon(
                        Icons.Default.Storefront,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.market_onboarding_activate))
                }
                MochiTextButton(onClick = onDismiss, enabled = !activating) {
                    Text(stringResource(R.string.market_onboarding_dismiss))
                }
            }
        }
    }
}

/**
 * Filters apply live as the user toggles them; Apply only dismisses, Clear
 * resets every axis.
 */
@Composable
private fun MarketFilterSheet(
    state: HomeUiState,
    onUpdate: (Filter, String?) -> Unit,
    onDismiss: () -> Unit,
    onClearAll: () -> Unit,
) {
    FilterSheet(
        title = stringResource(R.string.market_filter_title),
        onDismiss = onDismiss,
        bottomSpacing = 8.dp,
    ) {
        CategoryDropdown(state = state, onUpdate = onUpdate)

        SectionLabel(stringResource(R.string.market_filter_type))
        FilterChipRow(
            options = sheetOptions(Filter.TYPE),
            isSelected = { option -> option == state.filters[Filter.TYPE] },
            onSelect = { onUpdate(Filter.TYPE, it) },
        )

        SectionLabel(stringResource(R.string.market_filter_condition))
        FilterChipRow(
            options = sheetOptions(Filter.CONDITION),
            isSelected = { option -> option == state.filters[Filter.CONDITION] },
            onSelect = { onUpdate(Filter.CONDITION, it) },
        )

        SectionLabel(stringResource(R.string.market_filter_pricing))
        FilterChipRow(
            options = sheetOptions(Filter.PRICING),
            isSelected = { option -> option == state.filters[Filter.PRICING] },
            onSelect = { onUpdate(Filter.PRICING, it) },
        )

        SectionLabel(stringResource(R.string.market_filter_delivery))
        FilterChipRow(
            options = sheetOptions(Filter.DELIVERY),
            isSelected = { option -> option == state.filters[Filter.DELIVERY] },
            onSelect = { onUpdate(Filter.DELIVERY, it) },
        )

        SectionLabel(stringResource(R.string.market_filter_price_range))
        FilterChipRow(
            options = Currency.entries.map { it.name.lowercase() to it.name },
            isSelected = { option ->
                option == (state.filters[Filter.CURRENCY] ?: HomeViewModel.DEFAULT_CURRENCY)
            },
            onSelect = { onUpdate(Filter.CURRENCY, it) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MochiTextField(
                value = state.filters[Filter.PRICE_MIN].orEmpty(),
                onValueChange = { onUpdate(Filter.PRICE_MIN, it.ifBlank { null }) },
                label = { Text(stringResource(R.string.market_filter_price_min)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            MochiTextField(
                value = state.filters[Filter.PRICE_MAX].orEmpty(),
                onValueChange = { onUpdate(Filter.PRICE_MAX, it.ifBlank { null }) },
                label = { Text(stringResource(R.string.market_filter_price_max)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        SectionLabel(stringResource(R.string.market_filter_sort))
        SortDropdown(state = state, onUpdate = onUpdate)

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            MochiOutlinedButton(
                onClick = onClearAll,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.market_filter_clear))
            }
            MochiButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.market_filter_apply))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    state: HomeUiState,
    onUpdate: (Filter, String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = state.filters[Filter.CATEGORY]
    val selectedLabel = if (current == null) {
        stringResource(R.string.market_filter_all)
    } else {
        state.categories.firstOrNull { it.id.toString() == current }?.name
            ?: stringResource(R.string.market_filter_all)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel(stringResource(R.string.market_filter_category))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            MochiTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                MochiDropdownMenuItem(
                    text = { Text(stringResource(R.string.market_filter_all)) },
                    onClick = {
                        onUpdate(Filter.CATEGORY, null)
                        expanded = false
                    },
                )
                for (category in state.categories) {
                    MochiDropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            onUpdate(Filter.CATEGORY, category.id.toString())
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortDropdown(
    state: HomeUiState,
    onUpdate: (Filter, String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val sortOptions = listOf(null to stringResource(R.string.market_filter_sort_default)) +
        filterOptions(Filter.SORT)
    val current = state.filters[Filter.SORT]
    val selectedLabel = sortOptions.firstOrNull { it.first == current }?.second
        ?: stringResource(R.string.market_filter_sort_default)
    Box {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            MochiTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                for ((value, label) in sortOptions) {
                    MochiDropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onUpdate(Filter.SORT, value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
