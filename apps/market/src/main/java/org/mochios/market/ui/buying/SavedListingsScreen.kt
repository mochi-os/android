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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.market.R
import org.mochios.market.ui.components.MarketLayout
import org.mochios.market.model.Listing
import org.mochios.market.navigation.MarketApp
import org.mochios.market.navigation.open
import org.mochios.market.repository.SavedRepository
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedListingsScreen(
    navController: NavController,
    viewModel: SavedListingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
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
    // A change the server refused, in its own words.
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.failures.collect { failure -> snackbar.showSnackbar(failure.userMessage()) }
    }
    MarketLayout(
        navController = navController,
        currentRoute = MarketApp.SAVED,
        titleRes = R.string.market_saved_title,
        actions = {
            if (state.listings.isNotEmpty()) {
                MochiIconButton(onClick = viewModel::clearAll) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = stringResource(R.string.market_saved_clear),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.listings.isEmpty() -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
                state.error != null && state.listings.isEmpty() -> ErrorState(
                    error = state.error!!,
                    onRetry = viewModel::refresh,
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
                            saved = true,
                            onClick = {
                                navController.open(
                                    MarketApp.listingDetail(listing.id.toString(), listing.title),
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
    /** Why the saved listings could not be read, while none is held. */
    val error: MochiError? = null,
)

@HiltViewModel
class SavedListingsViewModel @Inject constructor(
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
     * Save-toggle results for the screen's toast: `true` saved, `false`
     * removed.
     */
    private val _saveEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 4)
    val saveEvents: SharedFlow<Boolean> = _saveEvents.asSharedFlow()

    /** Changes the server refused, for the screen's snackbar. */
    private val _failures = MutableSharedFlow<MochiError>(extraBufferCapacity = 4)
    val failures: SharedFlow<MochiError> = _failures.asSharedFlow()

    init {
        observe()
        refresh()
    }

    /** Remove every saved listing; a refusal puts them back and says why. */
    fun clearAll() {
        viewModelScope.launch {
            try {
                savedRepository.clear()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _failures.emit(e.toMochiError())
            }
        }
    }

    fun toggleSave(listing: Listing) {
        viewModelScope.launch {
            try {
                val nowSaved = savedRepository.toggle(listing)
                _saveEvents.emit(nowSaved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The optimistic change has been undone; say why.
                _failures.emit(e.toMochiError())
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
    fun refresh() {
        viewModelScope.launch {
            internalState.value = internalState.value.copy(isLoading = true, error = null)
            val failure = savedRepository.refresh()
            internalState.value = internalState.value.copy(isLoading = false, error = failure)
        }
    }
}
