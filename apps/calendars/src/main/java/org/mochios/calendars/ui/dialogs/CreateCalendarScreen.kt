// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.dialogs

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import org.mochios.android.ui.components.ColorPicker
import org.mochios.android.ui.components.CreateEntityForm
import org.mochios.android.ui.components.CreateEntityScaffold
import org.mochios.android.ui.components.MochiTextField
import org.mochios.calendars.R
import org.mochios.calendars.repository.CalendarsRepository
import javax.inject.Inject

/** The colour a new calendar starts on, the same one the server would pick. */
private const val DEFAULT_COLOUR = "#60a5fa"

data class CreateCalendarUiState(
    val name: String = "",
    val colour: String = DEFAULT_COLOUR,
    val isBusy: Boolean = false,
    val error: MochiError? = null,
    val created: String? = null,
)

@HiltViewModel
class CreateCalendarViewModel @Inject constructor(
    private val repository: CalendarsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateCalendarUiState())
    val uiState: StateFlow<CreateCalendarUiState> = _uiState.asStateFlow()

    fun setName(value: String) {
        _uiState.value = _uiState.value.copy(name = value, error = null)
    }

    fun setColour(value: String) {
        _uiState.value = _uiState.value.copy(colour = value, error = null)
    }

    fun create() {
        val state = _uiState.value
        if (state.name.isBlank() || state.isBusy) return
        viewModelScope.launch {
            _uiState.value = state.copy(isBusy = true, error = null)
            try {
                val calendar = repository.createCalendar(state.name.trim(), state.colour.trim().lowercase())
                _uiState.value = _uiState.value.copy(isBusy = false, created = calendar.id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isBusy = false, error = e.toMochiError())
            }
        }
    }
}

/** Creates a calendar: its name and its colour, nothing else. */
@Composable
fun CreateCalendarScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    viewModel: CreateCalendarViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.created) {
        if (uiState.created != null) onCreated()
    }

    CreateEntityScaffold(
        title = stringResource(R.string.calendars_create),
        submitLabel = stringResource(R.string.calendars_create_submit),
        submitEnabled = uiState.name.isNotBlank() && !uiState.isBusy,
        isBusy = uiState.isBusy,
        error = uiState.error,
        onBack = onBack,
        onSubmit = viewModel::create,
    ) { padding ->
        CreateEntityForm(padding) {
            MochiTextField(
                value = uiState.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.calendars_name)) },
                singleLine = true,
                enabled = !uiState.isBusy,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            ColorPicker(
                hex = uiState.colour,
                onHexChange = viewModel::setColour,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
