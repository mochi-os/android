package org.mochios.market.ui.buying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mochios.market.R
import org.mochios.market.lib.SavedStore
import org.mochios.market.model.Category
import org.mochios.market.model.Listing
import org.mochios.market.navigation.MarketApp
import org.mochios.market.repository.MarketRepository
import javax.inject.Inject

/**
 * Saved listings screen at [MarketApp.SAVED]. Reads the local saved set from
 * [SavedStore], resolves each ID against [MarketRepository.getListing], and
 * renders a 2-column [LazyVerticalGrid] of the shared
 * [org.mochios.market.ui.components.ListingCard]. Mirrors the web side's
 * "Saved" tab in `apps/market/web/src/routes/_authenticated/saved.tsx`.
 *
 * A "Clear all" action in the TopAppBar wipes the saved set. The Saved
 * screen is non-paginated — the store caps itself implicitly at whatever
 * the user has explicitly saved (typically dozens, not thousands).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedListingsScreen(
    navController: NavController,
    viewModel: SavedListingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.market_saved_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.market_back),
                        )
                    }
                },
                actions = {
                    if (state.listings.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearAll) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = stringResource(R.string.market_saved_clear),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.listings.isEmpty() -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
                state.listings.isEmpty() -> org.mochios.android.ui.components.EmptyState(
                    icon = Icons.Default.BookmarkBorder,
                    title = stringResource(R.string.market_saved_empty_title),
                    subtitle = stringResource(R.string.market_saved_empty_subtitle),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.listings, key = { it.id }) { listing ->
                        org.mochios.market.ui.components.ListingCard(
                            listing = listing,
                            category = state.categories
                                .firstOrNull { it.id == listing.category }?.name,
                            onClick = {
                                navController.navigate(
                                    MarketApp.listingDetail(listing.id.toString()),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

data class SavedListingsUiState(
    val isLoading: Boolean = true,
    val listings: List<Listing> = emptyList(),
    val categories: List<Category> = emptyList(),
)

@HiltViewModel
class SavedListingsViewModel @Inject constructor(
    private val repo: MarketRepository,
    private val savedStore: SavedStore,
) : ViewModel() {

    private val internalState = MutableStateFlow(SavedListingsUiState())

    val state: StateFlow<SavedListingsUiState> = internalState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SavedListingsUiState(),
        )

    init {
        observe()
        loadCategories()
        refresh()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            try {
                internalState.value = internalState.value.copy(categories = repo.listCategories())
            } catch (_: Exception) {
                // Categories are non-critical; cards just omit the chip.
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            try {
                savedStore.clear()
            } catch (_: Exception) {
                // Optimistic rollback already restored the mirror; the
                // observed flow re-renders the restored list.
            }
        }
    }

    /** Hydrate the saved mirror from the server on screen entry. */
    private fun refresh() {
        viewModelScope.launch {
            internalState.value = internalState.value.copy(isLoading = true)
            savedStore.refresh()
            internalState.value = internalState.value.copy(isLoading = false)
        }
    }

    /**
     * The server returns each saved row as a full [Listing] snapshot, so
     * the screen renders straight from the store's mirror — no per-id
     * refetch needed.
     */
    private fun observe() {
        viewModelScope.launch {
            savedStore.saved.collect { listings ->
                internalState.value = internalState.value.copy(listings = listings)
            }
        }
    }
}
