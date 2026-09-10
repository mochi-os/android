// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.systemdocuments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.unwrapEmpty
import org.mochios.android.api.unwrapRaw
import org.mochios.settings.api.SystemDocument
import org.mochios.settings.api.SystemDocumentsApi
import javax.inject.Inject
import org.mochios.settings.ui.login.SettingsStepUpClient
import org.mochios.settings.ui.login.StepUpController

enum class DocumentKind(val value: String) {
    RULES("rules"),
    TERMS("terms"),
    PRIVACY("privacy");

    companion object {
        fun from(value: String?): DocumentKind = values().firstOrNull { it.value == value } ?: RULES
    }
}

data class SystemDocumentsUiState(
    val isLoading: Boolean = true,
    val documents: List<SystemDocument> = emptyList(),
    val tab: DocumentKind = DocumentKind.RULES,
    val language: String? = null,
    val savingKey: String? = null,
    val current: SystemDocument? = null,
    val loadingDocument: Boolean = false,
    val error: MochiError? = null,
    val savedToast: Boolean = false,
    val saveError: MochiError? = null,
)

@HiltViewModel
class SystemDocumentsViewModel @Inject constructor(
    private val api: SystemDocumentsApi,
    stepUpClient: SettingsStepUpClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SystemDocumentsUiState())
    val uiState: StateFlow<SystemDocumentsUiState> = _uiState.asStateFlow()

    /** Step-up gate for the administrator mutations: a stolen session must
     *  re-verify a login factor before it can change the server. */
    val stepUp = StepUpController(
        client = stepUpClient,
        scope = viewModelScope,
        onError = { e -> _uiState.value = _uiState.value.copy(saveError = e.toMochiError()) },
    )

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val data = api.list().unwrapRaw()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    documents = data.documents,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun setTab(tab: DocumentKind) {
        // Clear the explicit language so the screen re-falls-back per the new tab's
        // available languages (mirrors the web behaviour where the URL search param
        // resets when switching tabs unless the user explicitly picks a language).
        _uiState.value = _uiState.value.copy(tab = tab, language = null)
    }

    fun setLanguage(language: String) {
        _uiState.value = _uiState.value.copy(language = language)
    }

    /** Load one document's body and bundled default: the list carries
     *  only the (name x language) pairs. */
    fun load(name: String, language: String) {
        val have = _uiState.value.current
        if (have != null && have.name == name && have.language == language) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loadingDocument = true, current = null)
            try {
                val document = api.get(name, language).unwrapRaw()
                _uiState.value = _uiState.value.copy(loadingDocument = false, current = document)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(loadingDocument = false, error = e.toMochiError())
            }
        }
    }

    fun save(name: String, language: String, body: String) {
        val key = "$name/$language"
        stepUp.request { token ->
            _uiState.value = _uiState.value.copy(savingKey = key, saveError = null)
            try {
                api.set(name, language, body, token).unwrapEmpty()
                val data = api.list().unwrapRaw()
                val document = api.get(name, language).unwrapRaw()
                _uiState.value = _uiState.value.copy(
                    savingKey = null,
                    documents = data.documents,
                    current = document,
                    savedToast = true,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    savingKey = null,
                    saveError = e.toMochiError(),
                )
            }
        }
    }

    fun consumeSavedToast() {
        _uiState.value = _uiState.value.copy(savedToast = false)
    }

    fun consumeSaveError() {
        _uiState.value = _uiState.value.copy(saveError = null)
    }
}
