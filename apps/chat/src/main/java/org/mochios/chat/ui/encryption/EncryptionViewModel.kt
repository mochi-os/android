// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.encryption

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
import org.mochios.chat.R
import org.mochios.chat.model.EncryptionDevice
import org.mochios.chat.model.EncryptionOverview
import org.mochios.chat.repository.ChatRepository
import javax.inject.Inject
import org.mochios.android.R as MochiR

/** What the section tells the user once: a message in their words, or how many keys a recovery restored. */
sealed class EncryptionEvent {
    data class Said(val text: String) : EncryptionEvent()
    data class Restored(val count: Int) : EncryptionEvent()
}

/** A person's devices, read when their row is opened. */
data class PersonDevices(
    val isLoading: Boolean = true,
    val devices: List<EncryptionDevice> = emptyList(),
    val error: MochiError? = null,
)

/** Setting up the account's recovery: its password where its server wants it, then the key, shown once. */
data class SetupState(
    val asked: Boolean = false,
    val password: String = "",
    val recovery: String = "",
    val busy: Boolean = false,
)

data class EncryptionUiState(
    val overview: EncryptionOverview? = null,
    val isLoading: Boolean = true,
    val error: MochiError? = null,
    /** The people whose devices are open, by the id their devices are asked under. */
    val open: Set<String> = emptySet(),
    val devices: Map<String, PersonDevices> = emptyMap(),
    /** The person a verification is being asked of. */
    val verifying: String? = null,
    val setup: SetupState? = null,
    /** The recovery key being entered, while that dialog is open. */
    val recovery: String? = null,
    val recovering: Boolean = false,
    /** Why the server refused the key entered, shown in its dialog until the key changes. */
    val refusal: MochiError? = null,
)

/**
 * A Matrix or XMPP conversation's encryption, which the server holds the
 * device for: the device's fingerprints, the people's devices and the user's
 * word on each, and on Matrix comparing emoji and the account's recovery key.
 */
@HiltViewModel
class EncryptionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ChatRepository,
    private val application: Application,
) : ViewModel() {

    private val chatId: String = savedStateHandle["chatId"] ?: ""

    private val _uiState = MutableStateFlow(EncryptionUiState())
    val uiState: StateFlow<EncryptionUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<EncryptionEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<EncryptionEvent> = _events.asSharedFlow()

    private fun say(text: String) {
        _events.tryEmit(EncryptionEvent.Said(text))
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { state -> state.copy(isLoading = state.overview == null, error = null) }
            try {
                // The answer first, then the state as it stands by then: a
                // key dialog closed or a person's devices opened meanwhile stay so.
                val overview = repository.encryption(chatId)
                _uiState.update { state -> state.copy(overview = overview, isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { state -> state.copy(isLoading = false, error = e.toMochiError()) }
            }
        }
    }

    /** Open or close a person's devices, reading them as they open. */
    fun toggle(person: String) {
        val open = _uiState.value.open
        if (person in open) {
            _uiState.value = _uiState.value.copy(open = open - person)
            return
        }
        _uiState.value = _uiState.value.copy(open = open + person)
        devices(person)
    }

    private fun devices(person: String) {
        if (person.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(devices = _uiState.value.devices + (person to PersonDevices()))
            val read = try {
                PersonDevices(isLoading = false, devices = repository.devices(chatId, person))
            } catch (e: Exception) {
                PersonDevices(isLoading = false, error = e.toMochiError())
            }
            _uiState.value = _uiState.value.copy(devices = _uiState.value.devices + (person to read))
        }
    }

    /** The user's word on a device of [person]'s, having compared its fingerprint. */
    fun trust(person: String, device: String, trust: String) {
        viewModelScope.launch {
            try {
                repository.trust(chatId, person, device, trust)
                say(application.getString(MochiR.string.common_saved))
                devices(person)
            } catch (e: Exception) {
                say(said(e, application.getString(R.string.chat_encryption_save_failed)))
            }
        }
    }

    /** Ask to verify [person] by comparing emoji; the verification itself shows once they accept. */
    fun verify(person: String) {
        if (_uiState.value.verifying != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(verifying = person)
            try {
                repository.verify(chatId, person)
                say(application.getString(R.string.chat_encryption_verify_waiting))
            } catch (e: Exception) {
                say(said(e, application.getString(R.string.chat_encryption_verify_failed)))
            }
            _uiState.value = _uiState.value.copy(verifying = null)
        }
    }

    fun openSetup() {
        _uiState.value = _uiState.value.copy(setup = SetupState())
    }

    fun setPassword(password: String) {
        val setup = _uiState.value.setup ?: return
        _uiState.value = _uiState.value.copy(setup = setup.copy(password = password))
    }

    /** Set up recovery: where the server wants the account's password, ask for it, then show the key once. */
    fun setup() {
        val setup = _uiState.value.setup ?: return
        if (setup.busy) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(setup = setup.copy(busy = true))
            try {
                val result = repository.setup(chatId, setup.password)
                val current = _uiState.value.setup ?: return@launch
                if (result.password) {
                    if (current.asked) say(application.getString(R.string.chat_encryption_password_refused))
                    _uiState.value = _uiState.value.copy(setup = current.copy(asked = true, busy = false))
                    return@launch
                }
                _uiState.value = _uiState.value.copy(setup = current.copy(recovery = result.recovery, busy = false))
                load()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(setup = _uiState.value.setup?.copy(busy = false))
                say(said(e, application.getString(R.string.chat_encryption_setup_failed)))
            }
        }
    }

    fun closeSetup() {
        _uiState.value = _uiState.value.copy(setup = null)
    }

    fun openRecovery() {
        _uiState.update { state -> state.copy(recovery = "", refusal = null) }
    }

    fun setRecovery(key: String) {
        _uiState.update { state -> if (state.recovery == null) state else state.copy(recovery = key, refusal = null) }
    }

    /**
     * Take a recovery key set up on another client, which restores the
     * history's keys. A refusal says why in the dialog, in the server's words:
     * a key that is not the account's, no recovery set up, or the key's
     * backup failing to open.
     */
    fun recover() {
        val key = _uiState.value.recovery?.trim().orEmpty()
        if (key.isEmpty() || _uiState.value.recovering) return
        viewModelScope.launch {
            _uiState.update { state -> state.copy(recovering = true, refusal = null) }
            try {
                val restored = repository.recover(chatId, key)
                _events.tryEmit(EncryptionEvent.Restored(restored))
                _uiState.update { state -> state.copy(recovering = false, recovery = null) }
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { state -> state.copy(recovering = false, refusal = e.toMochiError()) }
            }
        }
    }

    fun closeRecovery() {
        _uiState.update { state -> state.copy(recovery = null, refusal = null) }
    }
}
