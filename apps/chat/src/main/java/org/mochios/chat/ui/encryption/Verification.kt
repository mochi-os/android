// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.encryption

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.chat.R
import org.mochios.chat.model.Verification
import org.mochios.chat.model.verificationShown
import org.mochios.chat.repository.ChatRepository
import javax.inject.Inject
import org.mochios.android.R as MochiR

/** What the server said of a failure, else [fallback]. */
internal fun said(error: Throwable, fallback: String): String {
    val mochi = error.toMochiError()
    if (mochi is MochiError.NetworkError || mochi is MochiError.Local) return fallback
    return mochi.message?.takeIf { it.isNotBlank() } ?: fallback
}

/** How often the verifications are read: often while one is under way. */
private const val VERIFICATIONS_OPEN = 1_500L
private const val VERIFICATIONS_IDLE = 6_000L

data class VerificationUiState(
    val verifications: List<Verification> = emptyList(),
    /** The verifications the user closed, which are not shown again. */
    val dismissed: Set<String> = emptySet(),
    val acting: Boolean = false,
) {
    val shown: Verification? get() = verificationShown(verifications, dismissed)
}

/**
 * Matrix's emoji verification, which the server runs for the device it holds
 * on each of the user's Matrix accounts: a request from someone or from the
 * user's other clients, the emoji to compare, and how it ended.
 */
@HiltViewModel
class VerificationViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val application: Application,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VerificationUiState())
    val uiState: StateFlow<VerificationUiState> = _uiState.asStateFlow()

    // One-shot messages, already in the user's words: how each verification ended.
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    /** The verifications seen under way, and those whose end was said. */
    private val seen = mutableSetOf<String>()
    private val told = mutableSetOf<String>()

    /** Read the verifications for as long as the caller keeps this running. */
    suspend fun watch() {
        while (true) {
            read()
            delay(if (_uiState.value.verifications.any { it.open }) VERIFICATIONS_OPEN else VERIFICATIONS_IDLE)
        }
    }

    suspend fun read() {
        try {
            val verifications = repository.verifications()
            _uiState.value = _uiState.value.copy(verifications = verifications)
            announce(verifications)
        } catch (_: Exception) {
            // Read again on the next tick.
        }
    }

    /** How each verification seen under way ended, said once. */
    private fun announce(verifications: List<Verification>) {
        for (v in verifications) {
            if (v.open) {
                seen.add(v.flow)
                continue
            }
            if (v.flow !in seen || v.flow in told) continue
            told.add(v.flow)
            _events.tryEmit(
                when {
                    v.phase == "done" -> application.getString(R.string.chat_encryption_verified)
                    v.reason == "m.mismatched_sas" -> application.getString(R.string.chat_verification_mismatched)
                    else -> application.getString(R.string.chat_verification_cancelled)
                },
            )
        }
    }

    /** A step of the verification shown: accept, start, confirm, mismatch or cancel. */
    fun act(action: String) {
        val shown = _uiState.value.shown ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(acting = true)
            try {
                repository.act(shown.account, shown.flow, action)
            } catch (e: Exception) {
                _events.tryEmit(said(e, application.getString(R.string.chat_verification_failed)))
            }
            _uiState.value = _uiState.value.copy(acting = false)
            read()
        }
    }

    /** Close the verification shown, cancelling it. */
    fun close() {
        val shown = _uiState.value.shown ?: return
        act("cancel")
        _uiState.value = _uiState.value.copy(dismissed = _uiState.value.dismissed + shown.flow)
    }
}

/** The verification under way, when there is one, while [enabled]. */
@Composable
fun VerificationWatcher(enabled: Boolean, viewModel: VerificationViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(enabled) {
        if (enabled) viewModel.watch()
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
    val shown = uiState.shown ?: return
    VerificationDialog(
        verification = shown,
        acting = uiState.acting,
        onAct = viewModel::act,
        onClose = viewModel::close,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VerificationDialog(
    verification: Verification,
    acting: Boolean,
    onAct: (String) -> Unit,
    onClose: () -> Unit,
) {
    val who = verification.name.ifEmpty { verification.person }
    val own = verification.room.isEmpty()
    val requested = verification.phase == "requested" && !verification.started
    val description = when {
        verification.phase == "compare" -> stringResource(R.string.chat_verification_compare)
        requested && own -> stringResource(R.string.chat_verification_requested_own)
        requested -> stringResource(R.string.chat_verification_requested, who)
        else -> stringResource(R.string.chat_verification_waiting)
    }
    val cancel = stringResource(MochiR.string.common_cancel)
    val (confirm, action) = when {
        verification.phase == "compare" -> stringResource(R.string.chat_verification_match) to "confirm"
        requested -> stringResource(R.string.chat_invitation_accept) to "accept"
        verification.phase == "ready" -> stringResource(R.string.chat_encryption_compare) to "start"
        else -> null to ""
    }
    MochiAlertDialog(
        onDismissRequest = onClose,
        title = if (own) stringResource(R.string.chat_verification_own) else stringResource(R.string.chat_verification_title, who),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(description, style = MaterialTheme.typography.bodyMedium)
                if (verification.phase == "compare") {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        verification.emoji.forEach { emoji -> Text(emoji, fontSize = 32.sp) }
                    }
                }
            }
        },
        confirmText = confirm,
        onConfirm = { onAct(action) },
        confirmEnabled = !acting,
        confirmLoading = acting,
        dismissText = when {
            verification.phase == "compare" -> stringResource(R.string.chat_verification_mismatch)
            requested -> stringResource(R.string.chat_invitation_decline_short)
            else -> cancel
        },
        onDismiss = if (verification.phase == "compare") ({ onAct("mismatch") }) else onClose,
        dismissEnabled = !acting,
    )
}
