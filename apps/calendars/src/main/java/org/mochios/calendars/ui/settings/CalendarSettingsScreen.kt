// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.calendars.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
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
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.ColorPicker
import org.mochios.android.ui.components.CreateEntityForm
import org.mochios.android.ui.components.CreateEntityScaffold
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.util.characters
import org.mochios.calendars.R
import org.mochios.calendars.di.Viewer
import org.mochios.calendars.model.Calendar
import org.mochios.calendars.repository.CalendarsRepository
import org.mochios.calendars.ui.calendar.AddressDialogs
import org.mochios.calendars.ui.calendar.LinkState
import org.mochios.calendars.ui.dialogs.NAME_MAXIMUM
import org.mochios.calendars.ui.dialogs.RevokeLinkDialog
import org.mochios.android.R as MochiR
import javax.inject.Inject

/**
 * The calendar settings screen's state. [opened] is the calendar as loaded;
 * [name] and [colour] are what the user has made of its name and colour.
 * [saved] is null until they are saved, then whether the name changed.
 * [link] is the calendar's address while its dialog is open, and [revoking]
 * says a revoke is under way.
 */
data class CalendarSettingsUiState(
    val opened: Calendar? = null,
    val name: String = "",
    val colour: String = "",
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val loadError: MochiError? = null,
    val error: MochiError? = null,
    val saved: Boolean? = null,
    val link: LinkState = LinkState(),
    val revoking: Boolean = false,
) {
    /** Whether the name or the colour differs from the calendar as loaded. */
    val changed: Boolean
        get() = opened != null && (name.trim() != opened.name || colour != opened.colour)
}

/** Something the settings screen says once, in its snackbar. */
sealed class CalendarSettingsEvent {

    /** A request failed, which [error] says. */
    data class Failed(val error: MochiError) : CalendarSettingsEvent()

    /** A request succeeded, which [message] says. */
    data class Done(@param:StringRes val message: Int) : CalendarSettingsEvent()
}

/**
 * Loads one calendar and saves changes to its name and colour, and hands
 * out, replaces and revokes its address, as the drawer's menu does.
 */
@HiltViewModel
class CalendarSettingsViewModel @Inject constructor(
    private val repository: CalendarsRepository,
    private val viewer: Viewer,
    handle: SavedStateHandle,
) : ViewModel() {

    private val id: String = handle.get<String>("calendar").orEmpty()

    private val _uiState = MutableStateFlow(CalendarSettingsUiState())

    /** The screen's state. */
    val uiState: StateFlow<CalendarSettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CalendarSettingsEvent>(extraBufferCapacity = 4)

    /** What the screen has to say once. */
    val events: SharedFlow<CalendarSettingsEvent> = _events.asSharedFlow()

    init {
        load()
    }

    /** Reads the calendar; one no longer there is a not-found error. */
    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, loadError = null)
            try {
                val calendar = repository.listCalendars()
                    .firstOrNull { calendar -> calendar.id == id }
                if (calendar == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        loadError = MochiError.NotFoundError(),
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        opened = calendar,
                        name = calendar.name,
                        colour = calendar.colour,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    loadError = e.toMochiError(),
                )
            }
        }
    }

    /** Changes the name in the draft. */
    fun name(value: String) {
        _uiState.value = _uiState.value.copy(name = characters(value, NAME_MAXIMUM), error = null)
    }

    /** Changes the colour in the draft. */
    fun colour(value: String) {
        _uiState.value = _uiState.value.copy(colour = value, error = null)
    }

    /**
     * Saves what changed: the name, then the colour.
     * [CalendarSettingsUiState.saved] says whether the name changed once
     * both are in.
     */
    fun save() {
        val state = _uiState.value
        val opened = state.opened ?: return
        if (!state.changed || state.isBusy || state.name.isBlank()) {
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = null)
            try {
                val name = state.name.trim()
                val renamed = name != opened.name
                if (renamed) {
                    repository.renameCalendar(opened.id, name)
                }
                val colour = state.colour.trim().lowercase()
                if (colour != opened.colour) {
                    repository.recolourCalendar(opened.id, colour)
                }
                _uiState.value = _uiState.value.copy(isBusy = false, saved = renamed)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isBusy = false, error = e.toMochiError())
            }
        }
    }

    /**
     * Asks for the calendar's address. The first call mints it and it is
     * shown once; later calls only say one exists, until [regenerate]
     * revokes the old address and mints another.
     */
    fun openLink(regenerate: Boolean = false) {
        val current = _uiState.value.link
        if (current.busy) {
            return
        }
        if (!regenerate && current.calendar == id) {
            return
        }
        val issued = current.calendar == id && current.exists
        viewModelScope.launch {
            link(LinkState(calendar = id, busy = true, exists = issued))
            try {
                val answer = repository.link(id, regenerate)
                val base = viewer.server().trimEnd('/')
                link(
                    LinkState(
                        calendar = id,
                        url = answer.token.takeIf { token -> token.isNotBlank() }
                            ?.let { token -> "$base${answer.path}?token=$token" },
                        exists = answer.exists || answer.token.isNotBlank(),
                    ),
                )
            } catch (e: Exception) {
                link(LinkState(calendar = id, exists = issued))
                _events.tryEmit(CalendarSettingsEvent.Failed(e.toMochiError()))
            }
        }
    }

    /** Closes the address dialog. */
    fun closeLink() {
        link(LinkState())
    }

    /**
     * Revokes the calendar's address, saying whether there was one; [done]
     * runs once it is revoked.
     */
    fun revokeLink(done: () -> Unit = {}) {
        if (_uiState.value.revoking) {
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(revoking = true)
            try {
                val revoked = repository.revokeLink(id)
                val message = if (revoked) {
                    R.string.calendars_link_revoked
                } else {
                    R.string.calendars_link_none
                }
                _events.tryEmit(CalendarSettingsEvent.Done(message))
                done()
            } catch (e: Exception) {
                _events.tryEmit(CalendarSettingsEvent.Failed(e.toMochiError()))
            } finally {
                _uiState.value = _uiState.value.copy(revoking = false)
            }
        }
    }

    private fun link(state: LinkState) {
        _uiState.value = _uiState.value.copy(link = state)
    }
}

/**
 * One calendar's settings: its name and colour, saved together, and, in the
 * top bar's overflow menu, its address to copy or revoke. [onSaved] is told
 * whether the name changed once the name or colour is saved.
 */
@Composable
fun CalendarSettingsScreen(
    onBack: () -> Unit,
    onSaved: (Boolean) -> Unit,
    viewModel: CalendarSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var linking by remember { mutableStateOf(false) }
    var revoking by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.saved) {
        uiState.saved?.let { renamed -> onSaved(renamed) }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CalendarSettingsEvent.Failed -> snackbar.showSnackbar(event.error.userMessage())
                is CalendarSettingsEvent.Done ->
                    snackbar.showSnackbar(resources.getString(event.message))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CalendarSettingsContent(
            uiState = uiState,
            onBack = onBack,
            onSave = viewModel::save,
            onRetry = viewModel::load,
            onName = viewModel::name,
            onColour = viewModel::colour,
            onCopy = { linking = true },
            onRevoke = { revoking = true },
        )
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp),
        )
    }

    val calendar = uiState.opened
    if (linking && calendar != null) {
        LaunchedEffect(calendar.id) { viewModel.openLink() }
        AddressDialogs(
            calendar = calendar,
            link = uiState.link,
            onReplace = { viewModel.openLink(regenerate = true) },
            onClose = {
                linking = false
                viewModel.closeLink()
            },
            onRevoke = {
                linking = false
                viewModel.closeLink()
                revoking = true
            },
        )
    }
    if (revoking) {
        RevokeLinkDialog(
            busy = uiState.revoking,
            onDismiss = { revoking = false },
            onConfirm = { viewModel.revokeLink { revoking = false } },
        )
    }
}

/**
 * The settings screen drawn from [uiState] alone: the form once the calendar
 * is in, the load error, or a spinner. Save turns on once the name or the
 * colour differs from what was loaded.
 */
@Composable
internal fun CalendarSettingsContent(
    uiState: CalendarSettingsUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onName: (String) -> Unit,
    onColour: (String) -> Unit,
    onCopy: () -> Unit,
    onRevoke: () -> Unit,
) {
    val calendar = uiState.opened
    var menu by remember { mutableStateOf(false) }
    CreateEntityScaffold(
        title = stringResource(MochiR.string.settings_title),
        submitLabel = stringResource(MochiR.string.common_save),
        submitEnabled = calendar != null && uiState.changed && uiState.name.isNotBlank() &&
            !uiState.isBusy,
        isBusy = uiState.isBusy,
        error = uiState.error,
        onBack = onBack,
        onSubmit = onSave,
        actions = {
            if (calendar != null) {
                Box {
                    MochiIconButton(onClick = { menu = true }, enabled = !uiState.isBusy) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(MochiR.string.common_more_options),
                        )
                    }
                    MochiDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        MochiDropdownMenuItem(
                            text = { Text(stringResource(R.string.calendars_link_copy)) },
                            leadingIcon = {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                            },
                            onClick = {
                                menu = false
                                onCopy()
                            },
                        )
                        MochiDropdownMenuItem(
                            text = { Text(stringResource(R.string.calendars_link_revoke)) },
                            leadingIcon = {
                                Icon(Icons.Outlined.LinkOff, contentDescription = null)
                            },
                            destructive = true,
                            onClick = {
                                menu = false
                                onRevoke()
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val loadError = uiState.loadError
        when {
            calendar != null -> CreateEntityForm(padding) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    MochiTextField(
                        value = uiState.name,
                        onValueChange = onName,
                        label = { Text(stringResource(R.string.calendars_name)) },
                        singleLine = true,
                        enabled = !uiState.isBusy && !calendar.birthdays,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.calendars_colour),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    ColorPicker(
                        hex = uiState.colour,
                        onHexChange = onColour,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            loadError != null -> Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                ErrorState(error = loadError, onRetry = onRetry)
            }

            else -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
