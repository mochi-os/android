// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.page

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
import org.mochios.android.api.userMessage
import org.mochios.wikis.R
import org.mochios.wikis.model.PageFetchResponse
import org.mochios.wikis.navigation.WikisApp
import org.mochios.wikis.repository.WikisRepository
import javax.inject.Inject

/**
 * Confirm-then-delete page mirroring `apps/wikis/web/src/features/wiki/delete-page.tsx`.
 * Loads the page header (title) so the confirm copy can name the page being
 * deleted, then offers Cancel / Delete actions. On success we toast and
 * jump back to the wiki home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageDeleteScreen(
    navController: NavController,
    viewModel: PageDeleteViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val successMsg = stringResource(R.string.wikis_delete_page_success)
    val failedMsg = stringResource(R.string.wikis_delete_page_failed)

    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            Toast.makeText(context, successMsg, Toast.LENGTH_SHORT).show()
            navController.navigate(WikisApp.wikiHome(viewModel.wikiId)) {
                popUpTo(WikisApp.wikiHome(viewModel.wikiId)) { inclusive = true }
            }
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            val msg = it.userMessage().ifEmpty { failedMsg }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wikis_delete_page_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(org.mochios.android.R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.wikis_delete_page_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    val title = state.title.ifEmpty { state.slug }
                    Text(
                        text = stringResource(
                            R.string.wikis_delete_page_message,
                            title,
                            state.slug,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        OutlinedButton(
                            onClick = { navController.popBackStack() },
                            enabled = !state.isDeleting,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.wikis_delete_page_cancel))
                        }
                        Button(
                            onClick = { viewModel.delete() },
                            enabled = !state.isDeleting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            if (state.isDeleting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onError,
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.wikis_delete_page_confirm))
                        }
                    }
                }
            }
        }
    }
}

data class PageDeleteUiState(
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val deleted: Boolean = false,
    val title: String = "",
    val slug: String = "",
    val error: MochiError? = null,
)

@HiltViewModel
class PageDeleteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
) : ViewModel() {

    val wikiId: String = savedStateHandle["wikiId"] ?: ""
    private val slug: String = savedStateHandle["page"] ?: ""

    private val _uiState = MutableStateFlow(PageDeleteUiState(slug = slug))
    val uiState: StateFlow<PageDeleteUiState> = _uiState.asStateFlow()

    init { load() }

    /**
     * Fetch the page header so the confirm copy can show the page title.
     * If the page is already missing we still let the user "confirm" — the
     * server will respond accordingly.
     */
    private fun load() {
        viewModelScope.launch {
            try {
                when (val r = repository.getPage(wikiId, slug)) {
                    is PageFetchResponse.Page -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            title = r.page.title,
                            slug = r.page.slug.ifEmpty { slug },
                        )
                    }
                    is PageFetchResponse.NotFound -> {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true, error = null)
            try {
                repository.deletePage(wikiId, slug)
                _uiState.value = _uiState.value.copy(isDeleting = false, deleted = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDeleting = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
