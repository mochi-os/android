package org.mochios.market.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mochios.android.ui.components.EmptyState
import org.mochios.market.R
import org.mochios.market.model.Category
import org.mochios.market.model.Listing
import org.mochios.market.navigation.MarketApp
import org.mochios.market.ui.components.MarketSidebar

/**
 * Market landing / browse screen. Mirrors
 * `apps/market/web/src/routes/_authenticated/index.tsx` `BrowsePage`:
 *
 *  - TopAppBar with hamburger (opens [MarketSidebar] drawer) and a filter
 *    icon (opens [FilterSheet]).
 *  - Debounced search field below the bar.
 *  - Horizontal pill row of filter axes — tapping any pill opens the filter
 *    sheet focused on that section.
 *  - Active-filter chips (FlowRow) below the pills row, each removable.
 *  - When the user is in cold-start mode (no query, no filters) a 6-column
 *    category grid is rendered above the listings grid.
 *  - Recently-viewed strip rendered above the main grid when populated.
 *  - Adaptive 170dp listing grid that paginates by ViewModel cursor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()

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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MarketSidebar(
                currentRoute = MarketApp.HOME,
                navController = navController,
                onNavigate = { route ->
                    drawerScope.launch { drawerState.close() }
                    if (route != MarketApp.HOME) navController.navigate(route)
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.market_title)) },
                    navigationIcon = {
                        IconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = stringResource(R.string.market_open_sidebar),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.openFilterSheet() }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = stringResource(R.string.market_filter_open),
                            )
                        }
                    },
                )
            },
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
                onListingClick = { listing ->
                    viewModel.viewListing(listing)
                    navController.navigate(MarketApp.listingDetail(listing.id.toString()))
                },
                onCategoryClick = { category ->
                    viewModel.setFilter(Filter.CATEGORY, category.id.toString())
                },
                onActivateAccount = viewModel::activateAccount,
                onDismissOnboarding = viewModel::dismissOnboarding,
            )
        }
    }

    if (state.filterSheetOpen) {
        FilterSheet(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    padding: PaddingValues,
    state: HomeUiState,
    searchInput: String,
    onSearchInput: (String) -> Unit,
    onClearSearch: () -> Unit,
    onOpenFilter: (Filter) -> Unit,
    onRemoveFilter: (Filter) -> Unit,
    onClearAll: () -> Unit,
    onLoadMore: () -> Unit,
    onListingClick: (Listing) -> Unit,
    onCategoryClick: (Category) -> Unit,
    onActivateAccount: () -> Unit,
    onDismissOnboarding: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.hasMore && !state.isLoading && last >= total - 6
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    val isColdStart = state.query.isBlank() && state.filters.isEmpty()
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        if (state.testMode) {
            TestModeBanner()
        }
        if (!state.accountActive) {
            OnboardingCard(
                activating = state.activatingAccount,
                onActivate = onActivateAccount,
                onDismiss = onDismissOnboarding,
            )
        }
        OutlinedTextField(
            value = searchInput,
            onValueChange = onSearchInput,
            placeholder = { Text(stringResource(R.string.market_search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (searchInput.isNotEmpty()) {
                {
                    IconButton(onClick = onClearSearch) {
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

        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 170.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (isColdStart && state.categories.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(R.string.market_section_categories),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        CategoryGrid(
                            categories = state.categories,
                            onClick = onCategoryClick,
                        )
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
                        category = state.categories
                            .firstOrNull { it.id == listing.category }?.name,
                        onClick = { onListingClick(listing) },
                    )
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

            if (state.listings.isEmpty() && !state.isLoading && !isColdStart) {
                EmptyState(
                    icon = Icons.Default.SearchOff,
                    title = stringResource(R.string.market_empty_title),
                    subtitle = stringResource(R.string.market_empty_subtitle),
                    action = {
                        OutlinedButton(onClick = onClearAll) {
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
            AssistChip(
                onClick = { onRemove(filter) },
                label = { Text(labelForFilter(state, filter, value)) },
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

@Composable
private fun labelForFilter(state: HomeUiState, filter: Filter, value: String): String {
    return when (filter) {
        Filter.CATEGORY -> state.categories.firstOrNull { it.id.toString() == value }?.name ?: value
        Filter.PRICE_MIN -> stringResource(R.string.market_filter_chip_min, value)
        Filter.PRICE_MAX -> stringResource(R.string.market_filter_chip_max, value)
        else -> value.replaceFirstChar { it.titlecase() }
    }
}

@Composable
private fun CategoryGrid(
    categories: List<Category>,
    onClick: (Category) -> Unit,
) {
    // Hand-rolled grid: nesting LazyVerticalGrid inside LazyVerticalGrid is
    // illegal in Compose. Capping at the first 12 categories matches what the
    // web cold-start surface shows above the fold.
    val capped = categories.take(12)
    val rows = capped.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in rows) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (category in row) {
                    Card(
                        onClick = { onClick(category) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 64.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Sticky amber banner shown above the home screen when the Comptroller is
 * wired to Stripe's test environment (`stripe_testmode = true` on the
 * own-account response). Mirrors the web banner in
 * `apps/market/web/src/components/layout/market-layout.tsx`. No dismiss
 * button — it disappears automatically once the operator flips the platform
 * to a `sk_live_*` secret key.
 *
 * Uses `tertiaryContainer` so the banner picks up the user's chosen Mochi
 * theme. The label and the Stripe test-card number (a public Stripe-supplied
 * constant) live in `res/values/strings.xml` so locales can translate the
 * surrounding sentence; the digit groups themselves stay literal.
 */
@Composable
private fun TestModeBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.market_test_mode_banner),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.market_test_mode_card),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Onboarding card shown when the caller hasn't activated their market
 * account yet. Tapping "Activate" fires the server-side activation flow;
 * "Maybe later" hides the card for this session without activating.
 */
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
                Button(onClick = onActivate, enabled = !activating) {
                    Text(stringResource(R.string.market_onboarding_activate))
                }
                TextButton(onClick = onDismiss, enabled = !activating) {
                    Text(stringResource(R.string.market_onboarding_dismiss))
                }
            }
        }
    }
}

