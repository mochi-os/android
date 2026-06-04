package org.mochios.market.ui.buying

import android.widget.Toast
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.saveEvents.collect { saved ->
            val message = context.getString(
                if (saved) R.string.market_listing_save
                else R.string.market_listing_unsave,
            )
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
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
                            saved = true,
                            onClick = {
                                navController.navigate(
                                    MarketApp.listingDetail(listing.id.toString()),
                                )
                            },
                            onToggleSave = viewModel::toggleSave,
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

    /**
     * Cache of resolved listings by string ID. Avoids re-fetching the same
     * listing every time the saved-IDs set updates (e.g. after the user
     * un-saves one).
     */
    private val listingCache = mutableMapOf<String, Listing>()

    private val internalState = MutableStateFlow(SavedListingsUiState())

    val state: StateFlow<SavedListingsUiState> = internalState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SavedListingsUiState(),
        )

    /**
     * One-shot save-toggle results for the screen to surface as a toast.
     * `true` means the listing was just saved, `false` means it was removed.
     */
    private val _saveEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 4)
    val saveEvents: SharedFlow<Boolean> = _saveEvents.asSharedFlow()

    init {
        observe()
        loadCategories()
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
            savedStore.clear()
        }
    }

    /**
     * Toggle a listing's saved state. On this screen the listing is always
     * currently saved, so this removes it; the [saveEvents] emission lets the
     * screen confirm with a toast.
     */
    fun toggleSave(listing: Listing) {
        viewModelScope.launch {
            val id = listing.id.toString()
            savedStore.toggle(id)
            _saveEvents.emit(savedStore.isSaved(id))
        }
    }

    private fun observe() {
        viewModelScope.launch {
            savedStore.observe().collect { idSet ->
                val ids = idSet.toList()
                internalState.value = internalState.value.copy(isLoading = true)
                val resolved = arrayOfNulls<Listing>(ids.size)
                val missingIndexes = mutableListOf<Int>()
                val missingIds = mutableListOf<Long>()
                ids.forEachIndexed { index, id ->
                    val cached = listingCache[id]
                    if (cached != null) {
                        resolved[index] = cached
                    } else {
                        val longId = id.toLongOrNull() ?: return@forEachIndexed
                        missingIndexes += index
                        missingIds += longId
                    }
                }
                if (missingIds.isNotEmpty()) {
                    // Concurrent fan-out via the repo's batch helper, replacing
                    // the previous N sequential round-trips on screen open.
                    // Listings removed server-side fail individually inside the
                    // helper and are dropped — the saved set is purged
                    // opportunistically (permission changes are sometimes
                    // temporary, so we don't aggressively prune).
                    val fetched = repo.getListingsByIds(missingIds)
                    val byId = fetched.associateBy { it.id.toString() }
                    missingIndexes.forEach { slot ->
                        val id = ids[slot]
                        val listing = byId[id]
                        if (listing != null) {
                            listingCache[id] = listing
                            resolved[slot] = listing
                        }
                    }
                }
                internalState.value = internalState.value.copy(
                    isLoading = false,
                    listings = resolved.filterNotNull(),
                )
            }
        }
    }
}
