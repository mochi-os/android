// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.account

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.unwrapRaw
import org.mochios.android.files.Downloads
import org.mochios.settings.api.AccountApi
import org.mochios.settings.ui.login.SettingsStepUpClient
import org.mochios.settings.ui.login.StepUpController
import javax.inject.Inject

data class Identity(
    val entity: String = "",
    val fingerprint: String = "",
    val username: String = "",
    val name: String = "",
    val privacy: String = "private",
    val role: String = "",
)

data class AccountUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: MochiError? = null,
    val identity: Identity = Identity(),
    val nameDraft: String = "",
)

/**
 * Identity only (name, username, fingerprint, directory visibility); login
 * factors live on [org.mochios.settings.ui.login.LoginScreen].
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val api: AccountApi,
    stepUpClient: SettingsStepUpClient,
    private val downloads: Downloads,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    /** Step-up gate for closing the account and for building a data export —
     *  both are key-bearing or destructive, so they re-verify the login
     *  factors. Only one runs at a time. */
    val stepUp = StepUpController(
        client = stepUpClient,
        scope = viewModelScope,
        onError = { e -> _uiState.value = _uiState.value.copy(error = e.toMochiError()) },
    )

    // Emitted once the account has been marked for closure; the screen
    // observes this to sign out (sessions are already revoked server-side).
    private val _closed = MutableSharedFlow<Unit>()
    val closed: SharedFlow<Unit> = _closed.asSharedFlow()

    // The built bundle's filename, once it is built: the screen asks where to
    // save it, and saveExport streams it there.
    private val _exportReady = MutableSharedFlow<String>()
    val exportReady: SharedFlow<String> = _exportReady.asSharedFlow()

    init {
        refresh()
    }

    /** Begin closure: re-verify, then call close. On success the account is
     *  soft-deleted with a grace period and every session is revoked. */
    fun closeAccount() {
        stepUp.request { token ->
            api.closeAccount(token).unwrapRaw()
            _closed.emit(Unit)
        }
    }

    /** Begin a data export. The screen collects the passphrase first; this
     *  re-verifies the login factor(s), builds the encrypted bundle, then emits
     *  its filename for the screen to ask where it goes. */
    fun exportData(passphrase: String) {
        val pass = passphrase.trim()
        if (pass.isEmpty()) return
        stepUp.request { stepUpToken ->
            _exportReady.emit(api.exportData(stepUpToken, pass).unwrapRaw().filename)
        }
    }

    /** Streams built bundle [filename] into [destination], through the
     *  settings client: the transfer outlives this screen and says how it
     *  ended. The server deletes the bundle as it serves it, so one transfer
     *  is all there is. */
    fun saveExport(filename: String, destination: Uri) {
        downloads.save(destination, filename) { api.downloadExport(filename).unwrapRaw() }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val body = api.getIdentity().unwrapRaw()
                val identity = Identity(
                    entity = body.entity,
                    fingerprint = body.fingerprint,
                    username = body.username,
                    name = body.name,
                    privacy = body.privacy,
                    role = body.role,
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    identity = identity,
                    nameDraft = identity.name,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun updateName(draft: String) {
        _uiState.value = _uiState.value.copy(nameDraft = draft)
    }

    fun saveName() {
        val name = _uiState.value.nameDraft.trim()
        if (name.isBlank() || name == _uiState.value.identity.name) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                api.updateIdentity(name = name).unwrapRaw()
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    identity = _uiState.value.identity.copy(name = name),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.toMochiError())
            }
        }
    }

    /**
     * The directory switch: it moves on the tap and goes back if the server
     * refuses; a tap while a save is under way is not taken.
     */
    fun setPrivacy(privacy: String) {
        if (privacy != "public" && privacy != "private") return
        val before = _uiState.value.identity.privacy
        if (privacy == before || _uiState.value.isSaving) return
        _uiState.value = _uiState.value.copy(
            isSaving = true,
            error = null,
            identity = _uiState.value.identity.copy(privacy = privacy),
        )
        viewModelScope.launch {
            try {
                api.updateIdentity(privacy = privacy).unwrapRaw()
                _uiState.value = _uiState.value.copy(isSaving = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.toMochiError(),
                    identity = _uiState.value.identity.copy(privacy = before),
                )
            }
        }
    }
}
