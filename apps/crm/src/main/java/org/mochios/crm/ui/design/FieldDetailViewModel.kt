// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.design

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.crm.model.CrmDetails
import org.mochios.crm.repository.CrmsRepository
import javax.inject.Inject

/**
 * Screen state for the field-detail destination.
 *
 * @property crmDetails Design the field is read out of, null until loaded.
 * @property isLoading Whether the design is being fetched.
 * @property error Last failure, cleared by [FieldDetailViewModel.clearError].
 * @property deleted Set once the field is gone, so the screen can navigate away.
 */
data class FieldDetailUiState(
    val crmDetails: CrmDetails? = null,
    val isLoading: Boolean = false,
    val error: MochiError? = null,
    val deleted: Boolean = false
)

/**
 * Backs the field-detail destination: one field of one class, with its type,
 * flags, display position, validation rules and enumerated options.
 */
@HiltViewModel
class FieldDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CrmsRepository
) : ViewModel() {

    /** CRM the field belongs to, taken from the route. */
    val crmId: String = savedStateHandle.get<String>("crmId") ?: ""

    /** Class the field belongs to, taken from the route. */
    val classId: String = savedStateHandle.get<String>("classId") ?: ""

    /** Field this screen edits, taken from the route. */
    val fieldId: String = savedStateHandle.get<String>("fieldId") ?: ""

    private val _uiState = MutableStateFlow(FieldDetailUiState(isLoading = true))
    val uiState: StateFlow<FieldDetailUiState> = _uiState.asStateFlow()

    /** Fetches the design. Called on every resume, like the class screen. */
    fun loadCrm() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val details = repository.getCrmInfo(crmId)
                _uiState.value = _uiState.value.copy(
                    crmDetails = details,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.toMochiError()
                )
            }
        }
    }

    /** Saves the edited field. Null arguments leave that property unchanged. */
    fun updateField(
        name: String?,
        fieldtype: String?,
        flags: String?,
        multi: Boolean?,
        card: Boolean?,
        position: String?,
        rows: Int?,
        pattern: String? = null,
        minlength: Int? = null,
        maxlength: Int? = null
    ) {
        viewModelScope.launch {
            try {
                repository.updateField(
                    crmId,
                    classId,
                    fieldId,
                    name,
                    fieldtype,
                    flags,
                    multi,
                    card,
                    position,
                    rows,
                    pattern,
                    minlength,
                    maxlength
                )
                loadCrm()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    /**
     * Deletes the field and raises [FieldDetailUiState.deleted]. The screen
     * waits for that flag rather than navigating straight away, which would
     * cancel this scope mid-request.
     */
    fun deleteField() {
        viewModelScope.launch {
            try {
                repository.deleteField(crmId, classId, fieldId)
                _uiState.value = _uiState.value.copy(deleted = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    /** Removes an option from an enumerated field. */
    fun deleteOption(optionId: String) {
        viewModelScope.launch {
            try {
                repository.deleteOption(crmId, classId, fieldId, optionId)
                loadCrm()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    /** Drops the current error once it has been shown. */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
