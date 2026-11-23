// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.account

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.unwrapRaw
import org.mochios.android.files.Downloads
import org.mochios.settings.R
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

/** Where the user's data export is: nothing to show, a bundle being built, or
 *  one built and not yet downloaded. A failure is said once, as an error. */
sealed interface Export {
    data object None : Export
    data object Building : Export
    data class Ready(val file: String) : Export
}

/** How often the screen asks again while a bundle is being built. */
internal const val EXPORT_POLL_MILLISECONDS = 3_000L

data class AccountUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: MochiError? = null,
    val identity: Identity = Identity(),
    val nameDraft: String = "",
    val export: Export = Export.None,
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

    // The built bundle's filename, once it is built while the user waits: the
    // screen asks where to save it, and saveExport streams it there.
    private val _exportReady = MutableSharedFlow<String>()
    val exportReady: SharedFlow<String> = _exportReady.asSharedFlow()

    // A message for the screen to show once: a bundle it watched being built
    // is ready.
    private val _notices = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val notices: SharedFlow<Int> = _notices.asSharedFlow()

    /** How long [watchExport] waits between reads; a test shortens it. */
    internal var pollMillis = EXPORT_POLL_MILLISECONDS

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
     *  re-verifies the login factor(s) and starts the encrypted bundle. One
     *  the server built at once is emitted for the screen to ask where it
     *  goes; a larger one goes on being built, and [watchExport] follows it. */
    fun exportData(passphrase: String) {
        val pass = passphrase.trim()
        if (pass.isEmpty()) return
        stepUp.request { stepUpToken ->
            val answer = api.exportData(stepUpToken, pass).unwrapRaw()
            val file = answer.file.ifBlank { answer.filename }
            if (answer.state == "building") {
                _uiState.update { it.copy(export = Export.Building) }
            } else if (file.isNotBlank()) {
                // Offered as well, for when the user backs out of choosing
                // where it goes.
                _uiState.update { it.copy(export = Export.Ready(file)) }
                _exportReady.emit(file)
            }
        }
    }

    /** Read how the export stands, and while a bundle is being built read it
     *  again every [pollMillis]. The screen runs this while it is shown, so a
     *  bundle built while it was away is offered when it opens. */
    suspend fun watchExport() {
        readExport()
        while (_uiState.value.export == Export.Building) {
            delay(pollMillis)
            readExport()
        }
    }

    private suspend fun readExport() {
        val status = try {
            api.exportStatus().unwrapRaw()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A failed read leaves the section as it was; the next one may do.
            return
        }
        // A change is told only for a build this screen saw under way: a
        // failure from an earlier visit was told by its notification.
        val watched = _uiState.value.export == Export.Building
        when (status.state) {
            "building" -> _uiState.update { it.copy(export = Export.Building) }
            "ready" -> {
                _uiState.update { it.copy(export = if (status.file.isBlank()) Export.None else Export.Ready(status.file)) }
                if (watched && status.file.isNotBlank()) _notices.tryEmit(R.string.account_data_ready)
            }
            "failed" -> _uiState.update {
                it.copy(
                    export = Export.None,
                    error = if (watched) MochiError.Unknown(status.error.ifBlank { null }) else it.error,
                )
            }
            else -> _uiState.update { it.copy(export = Export.None) }
        }
    }

    /** Streams built bundle [filename] into [destination], through the
     *  settings client: the transfer outlives this screen and says how it
     *  ended. The server deletes the bundle as it serves it, so one transfer
     *  is all there is, and the section no longer offers it. */
    fun saveExport(filename: String, destination: Uri) {
        _uiState.update { if (it.export == Export.Ready(filename)) it.copy(export = Export.None) else it }
        downloads.save(destination, filename) { api.downloadExport(filename).unwrapRaw() }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
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
