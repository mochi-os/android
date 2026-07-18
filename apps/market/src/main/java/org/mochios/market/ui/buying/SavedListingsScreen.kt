// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mochios.market.R
import org.mochios.market.model.Category
import org.mochios.market.model.Listing
import org.mochios.market.navigation.MarketApp
import org.mochios.market.repository.MarketRepository
import org.mochios.market.repository.SavedRepository
import javax.inject.Inject

/**
 * Saved listings screen at [MarketApp.SAVED]. Mirrors the server-backed
 * [SavedRepository.saved] set — which already carries the full [Listing]
 * rows — into a 2-column [LazyVerticalGrid] of the shared
 * [org.mochios.market.ui.components.ListingCard]. Mirrors the web side's
 * "Saved" tab in `apps/market/web/src/routes/_authenticated/saved.tsx`.
 *
 * A "Clear all" action in the TopAppBar wipes the saved set. The Saved
 * screen is non-paginated — the set caps itself implicitly at whatever
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
    private val savedRepository: SavedRepository,
) : ViewModel() {

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
        refresh()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            try {
                val categories = repo.listCategories()
                internalState.value = internalState.value.copy(categories = categories)
            } catch (_: Exception) {
                // Categories are non-critical; cards just omit the chip.
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            runCatching { savedRepository.clear() }
        }
    }

    /**
     * Toggle a listing's saved state. On this screen the listing is always
     * currently saved, so this removes it; the [saveEvents] emission lets the
     * screen confirm with a toast.
     */
    fun toggleSave(listing: Listing) {
        viewModelScope.launch {
            try {
                val nowSaved = savedRepository.toggle(listing)
                _saveEvents.emit(nowSaved)
            } catch (_: Exception) {
                // Optimistic update already reverted; nothing to surface.
            }
        }
    }

    /** Mirror the server-backed saved set (full listings) into screen state. */
    private fun observe() {
        viewModelScope.launch {
            savedRepository.saved.collect { listings ->
                internalState.value = internalState.value.copy(listings = listings)
            }
        }
    }

    /** Pull the latest saved set from the server, toggling the load spinner. */
    private fun refresh() {
        viewModelScope.launch {
            internalState.value = internalState.value.copy(isLoading = true)
            runCatching { savedRepository.refresh() }
            internalState.value = internalState.value.copy(isLoading = false)
        }
    }
}
