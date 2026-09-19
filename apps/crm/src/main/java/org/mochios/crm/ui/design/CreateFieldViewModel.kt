// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.design

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.crm.repository.CrmsRepository

/**
 * Screen state for the create-field destination.
 *
 * @property isCreating Whether the create request is in flight.
 * @property error Last failure, shown above the submit button.
 * @property created Set once the field exists, so the screen can go back.
 */
data class CreateFieldUiState(
    val isCreating: Boolean = false,
    val error: MochiError? = null,
    val created: Boolean = false
)

/** Backs the create-field destination: adds a field to one class of a CRM. */
@HiltViewModel
class CreateFieldViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CrmsRepository
) : ViewModel() {

    /** CRM the class belongs to, taken from the route. */
    val crmId: String = savedStateHandle.get<String>("crmId") ?: ""

    /** Class the field is added to, taken from the route. */
    val classId: String = savedStateHandle.get<String>("classId") ?: ""

    private val _uiState = MutableStateFlow(CreateFieldUiState())

    /** State of the create form. */
    val uiState: StateFlow<CreateFieldUiState> = _uiState.asStateFlow()

    /**
     * Creates the field and raises [CreateFieldUiState.created].
     *
     * @param name Field name.
     * @param fieldtype Server key of the field type.
     * @param flags Comma-separated flags, or null for none.
     * @param multi Whether an enumerated field takes several values; null otherwise.
     */
    fun createField(name: String, fieldtype: String, flags: String?, multi: Boolean?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)
            try {
                repository.createField(crmId, classId, name.trim(), fieldtype, flags, multi)
                _uiState.value = _uiState.value.copy(isCreating = false, created = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    error = e.toMochiError()
                )
            }
        }
    }
}
