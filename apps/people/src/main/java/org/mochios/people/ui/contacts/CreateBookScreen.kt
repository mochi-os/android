// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.contacts

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.ui.components.CreateEntityForm
import org.mochios.android.ui.components.CreateEntityScaffold
import org.mochios.android.ui.components.MochiTextField
import org.mochios.people.R
import org.mochios.people.repository.PeopleRepository
import javax.inject.Inject

data class CreateBookUiState(
    val isCreating: Boolean = false,
    val error: MochiError? = null,
    val createdBookId: String? = null,
)

@HiltViewModel
class CreateBookViewModel @Inject constructor(
    private val repository: PeopleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateBookUiState())
    val uiState: StateFlow<CreateBookUiState> = _uiState.asStateFlow()

    fun createBook(name: String) {
        if (name.isBlank() || _uiState.value.isCreating) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)
            try {
                val book = repository.createBook(name.trim())
                _uiState.value = _uiState.value.copy(isCreating = false, createdBookId = book.id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun consumeCreatedBook() {
        _uiState.value = _uiState.value.copy(createdBookId = null)
    }
}

@Composable
fun CreateBookScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: CreateBookViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var name by remember { mutableStateOf("") }

    LaunchedEffect(uiState.createdBookId) {
        uiState.createdBookId?.let { id ->
            onCreated(id)
            viewModel.consumeCreatedBook()
        }
    }

    CreateEntityScaffold(
        title = stringResource(R.string.people_books_create),
        submitLabel = stringResource(R.string.people_books_create),
        submitEnabled = name.isNotBlank() && !uiState.isCreating,
        isBusy = uiState.isCreating,
        error = uiState.error,
        onBack = onBack,
        onSubmit = { viewModel.createBook(name) },
    ) { padding ->
        CreateEntityForm(padding) {
            MochiTextField(
                value = name,
                onValueChange = { value -> name = value },
                label = { Text(stringResource(R.string.people_books_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
