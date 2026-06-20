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
import androidx.compose.material.icons.filled.History
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
import androidx.compose.material3.OutlinedTextField
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
import org.mochios.wikis.navigation.WikisApp
import org.mochios.wikis.repository.WikisRepository
import javax.inject.Inject

/**
 * Confirm-then-revert screen mirroring
 * `apps/wikis/web/src/features/wiki/revert-page.tsx`. The user can edit the
 * revert comment before pressing the destructive confirm button. On success
 * we toast and navigate to the (new revision of the) page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageRevertScreen(
    navController: NavController,
    viewModel: PageRevertViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val successMsg = stringResource(R.string.wikis_revert_page_success, state.version)
    val failedMsg = stringResource(R.string.wikis_revert_page_failed)
    // Default revert comment string — formatted with the version number.
    val defaultComment = stringResource(R.string.wikis_revert_page_default_comment, state.version)

    LaunchedEffect(state.version) {
        // Seed the comment once, when we know the version. The ViewModel
        // exposes the comment as state so the field is editable.
        if (!state.commentSeeded && state.version != 0) {
            viewModel.setComment(defaultComment, seeded = true)
        }
    }

    LaunchedEffect(state.reverted) {
        if (state.reverted) {
            Toast.makeText(context, successMsg, Toast.LENGTH_SHORT).show()
            navController.navigate(WikisApp.pageView(viewModel.wikiId, state.slug)) {
                popUpTo(WikisApp.pageView(viewModel.wikiId, state.slug)) { inclusive = true }
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
                title = { Text(stringResource(R.string.wikis_revert_page_title)) },
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
                            Icons.Filled.History,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.wikis_revert_page_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(
                            R.string.wikis_revert_page_message,
                            state.slug,
                            state.version,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(
                        value = state.comment,
                        onValueChange = { viewModel.setComment(it) },
                        label = { Text(stringResource(R.string.wikis_revert_page_comment_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        OutlinedButton(
                            onClick = { navController.popBackStack() },
                            enabled = !state.isReverting,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.wikis_revert_page_cancel))
                        }
                        Button(
                            onClick = { viewModel.revert() },
                            enabled = !state.isReverting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            if (state.isReverting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onError,
                                )
                            } else {
                                Icon(
                                    Icons.Filled.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.wikis_revert_page_confirm))
                        }
                    }
                }
            }
        }
    }
}

data class PageRevertUiState(
    val slug: String = "",
    val version: Int = 0,
    val comment: String = "",
    val commentSeeded: Boolean = false,
    val isReverting: Boolean = false,
    val reverted: Boolean = false,
    val error: MochiError? = null,
)

@HiltViewModel
class PageRevertViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WikisRepository,
) : ViewModel() {

    val wikiId: String = savedStateHandle["wikiId"] ?: ""
    private val slug: String = savedStateHandle["page"] ?: ""
    private val version: Int = savedStateHandle["version"] ?: 0

    private val _uiState = MutableStateFlow(
        PageRevertUiState(slug = slug, version = version),
    )
    val uiState: StateFlow<PageRevertUiState> = _uiState.asStateFlow()

    /**
     * Set the revert comment. The first call after the screen renders also
     * marks the field as seeded so the [LaunchedEffect] doesn't re-seed
     * once the user has started editing.
     */
    fun setComment(value: String, seeded: Boolean = false) {
        _uiState.value = _uiState.value.copy(
            comment = value,
            commentSeeded = _uiState.value.commentSeeded || seeded,
        )
    }

    fun revert() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isReverting = true, error = null)
            try {
                repository.revertPage(wikiId, slug, version, _uiState.value.comment.trim())
                _uiState.value = _uiState.value.copy(isReverting = false, reverted = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isReverting = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
