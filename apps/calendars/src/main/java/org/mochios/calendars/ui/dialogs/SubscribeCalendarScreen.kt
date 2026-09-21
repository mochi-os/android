// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiTextField
import org.mochios.calendars.R
import org.mochios.calendars.repository.CalendarsRepository
import org.mochios.calendars.repository.PermissionRequiredException
import javax.inject.Inject

/** The colour a new subscription starts on. */
private const val DEFAULT_COLOUR = "#a78bfa"

/**
 * A consent the server asked for before it will fetch from the URL's host;
 * [url], [name] and [colour] replay the subscribe once it is granted.
 */
data class PendingPermission(
    val app: String,
    val permission: String,
    val label: String,
    val url: String,
    val name: String,
    val colour: String,
)

data class SubscribeUiState(
    val url: String = "",
    val name: String = "",
    val colour: String = DEFAULT_COLOUR,
    val isBusy: Boolean = false,
    val error: MochiError? = null,
    val permission: PendingPermission? = null,
    val subscribed: String? = null,
)

@HiltViewModel
class SubscribeCalendarViewModel @Inject constructor(
    private val repository: CalendarsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscribeUiState())
    val uiState: StateFlow<SubscribeUiState> = _uiState.asStateFlow()

    fun setUrl(value: String) {
        _uiState.value = _uiState.value.copy(url = value, error = null)
    }

    fun setName(value: String) {
        _uiState.value = _uiState.value.copy(name = value, error = null)
    }

    fun setColour(value: String) {
        _uiState.value = _uiState.value.copy(colour = value, error = null)
    }

    fun subscribe() {
        val state = _uiState.value
        if (state.url.isBlank() || state.isBusy) return
        subscribe(state.url.trim(), state.name.trim(), state.colour.trim().lowercase())
    }

    private fun subscribe(url: String, name: String, colour: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = null)
            try {
                val calendar = repository.subscribeCalendar(url, name, colour)
                _uiState.value = _uiState.value.copy(isBusy = false, subscribed = calendar.id)
            } catch (e: PermissionRequiredException) {
                // Not a failure: the subscribe succeeds once the app may fetch
                // from that host, so ask and replay it.
                val label = runCatching { repository.permissionName(e.permission) }.getOrDefault(e.permission)
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    permission = PendingPermission(e.app, e.permission, label, url, name, colour),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isBusy = false, error = e.toMochiError())
            }
        }
    }

    fun allow() {
        val pending = _uiState.value.permission ?: return
        _uiState.value = _uiState.value.copy(permission = null, isBusy = true)
        viewModelScope.launch {
            try {
                repository.grantPermission(pending.app, pending.permission)
                subscribe(pending.url, pending.name, pending.colour)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isBusy = false, error = e.toMochiError())
            }
        }
    }

    fun deny() {
        _uiState.value = _uiState.value.copy(permission = null)
    }
}

/**
 * Subscribes to an external calendar by its ICS address. The server fetches
 * it on a schedule and the calendar is read-only here. The first address on a
 * new host needs the user's consent, which the dialog below asks for.
 */
@Composable
fun SubscribeCalendarScreen(
    onBack: () -> Unit,
    onSubscribed: () -> Unit,
    viewModel: SubscribeCalendarViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.subscribed) {
        if (uiState.subscribed != null) onSubscribed()
    }

    CreateEntityScaffold(
        title = stringResource(R.string.calendars_subscribe),
        submitLabel = stringResource(R.string.calendars_subscribe_submit),
        submitEnabled = uiState.url.isNotBlank() && !uiState.isBusy,
        isBusy = uiState.isBusy,
        error = uiState.error,
        onBack = onBack,
        onSubmit = viewModel::subscribe,
    ) { padding ->
        CreateEntityForm(padding) {
            MochiTextField(
                value = uiState.url,
                onValueChange = viewModel::setUrl,
                label = { Text(stringResource(R.string.calendars_subscribe_url)) },
                singleLine = true,
                enabled = !uiState.isBusy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
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

    uiState.permission?.let { pending ->
        MochiAlertDialog(
            onDismissRequest = viewModel::deny,
            title = stringResource(R.string.calendars_permission_title),
            confirmText = stringResource(R.string.calendars_permission_allow),
            onConfirm = viewModel::allow,
            dismissText = stringResource(R.string.calendars_permission_deny),
            onDismiss = viewModel::deny,
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.calendars_permission_message, pending.app),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = pending.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            },
        )
    }
}
