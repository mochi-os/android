// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.ui.components.ErrorState
import org.mochios.wikis.model.WikiInfo
import org.mochios.wikis.navigation.WikisApp
import org.mochios.wikis.repository.WikisRepository
import javax.inject.Inject

/**
 * UI state for [WikiHomeScreen]. Tiny by design — this screen just loads
 * `/-/info` and immediately redirects to the wiki's home page route.
 */
data class WikiHomeUiState(
    val isLoading: Boolean = true,
    val wiki: WikiInfo? = null,
    val error: MochiError? = null,
)

@HiltViewModel
class WikiHomeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
) : ViewModel() {

    val wikiId: String = savedStateHandle.get<String>("wikiId").orEmpty()

    private val _uiState = MutableStateFlow(WikiHomeUiState())
    val uiState: StateFlow<WikiHomeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = WikiHomeUiState(isLoading = true)
            try {
                val response = repository.getInfo(wikiId)
                _uiState.value = WikiHomeUiState(
                    isLoading = false,
                    wiki = response.wiki,
                )
            } catch (e: Exception) {
                _uiState.value = WikiHomeUiState(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }
}

@Composable
fun WikiHomeScreen(
    navController: NavController,
    viewModel: WikiHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val wikiHomeRoute = WikisApp.wikiHome(viewModel.wikiId)

    // When info arrives, hop straight to the page view. The popUpTo on the
    // wiki-home route (this screen) with inclusive=true wipes the transient
    // resolver out of the back stack — pressing Back from the page view
    // lands on the wiki list as the user expects.
    LaunchedEffect(state.wiki) {
        val wiki = state.wiki
        if (wiki != null) {
            val home = wiki.home.ifBlank { "home" }
            navController.navigate(WikisApp.pageView(viewModel.wikiId, home)) {
                popUpTo(wikiHomeRoute) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> WikiHomeSkeleton()
            state.error != null -> ErrorState(
                error = state.error!!,
                onRetry = { viewModel.load() },
            )
            // state.wiki != null is handled by the LaunchedEffect above —
            // this composable is then transient and renders nothing.
        }
    }
}

@Composable
private fun WikiHomeSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        SkeletonStripe(width = 0.7f, height = 24.dp)
        Spacer(Modifier.height(16.dp))
        SkeletonStripe(width = 1f, height = 14.dp)
        Spacer(Modifier.height(8.dp))
        SkeletonStripe(width = 1f, height = 14.dp)
        Spacer(Modifier.height(8.dp))
        SkeletonStripe(width = 0.85f, height = 14.dp)
    }
}

@Composable
private fun SkeletonStripe(width: Float, height: androidx.compose.ui.unit.Dp) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier
            .fillMaxWidth(width)
            .height(height),
    ) { /* tinted surface = visual placeholder */ }
}
