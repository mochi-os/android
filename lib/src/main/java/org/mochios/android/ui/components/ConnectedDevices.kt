// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mochios.android.R
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.userMessage
import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.Format
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.util.NaturalCompare
import org.mochios.android.util.characters

// The connected devices, the same screen for every app that serves a device
// account (Calendars over CalDAV, People over CardDAV): the address and the
// username every device's account takes, then each device with when it was
// created and last used, each deletable alone. Add device names a new one and
// shows its password, once. A device's password reaches every app that serves
// one, so each app lists the same devices.

/**
 * A device's credential as `-/token/list` lists it. [hash] identifies it for
 * deletion; the password itself is shown only when made. [created] and
 * [used] are epoch seconds, [used] 0 until the device first signs in with it.
 */
data class Device(
    val hash: String = "",
    val name: String = "",
    val created: Long = 0,
    val used: Long = 0,
)

/** The devices, and the username every device enters. */
data class DeviceListing(val devices: List<Device> = emptyList(), val username: String = "")

/** A new device's password, shown this once, and the username to enter beside it. */
data class DeviceCreated(val token: String = "", val username: String = "")

/** An app's three device actions, `-/token/list`, `-/token/create` and `-/token/delete`. */
interface DeviceTokens {
    suspend fun list(): DeviceListing
    suspend fun create(name: String): DeviceCreated
    suspend fun delete(hash: String)
}

/** The server refuses a device name longer than this. */
const val DEVICE_NAME_MAXIMUM = 100

/**
 * What the connected-devices screen shows. [token] is the new device's
 * password, held only here and dropped when the screen goes: the server never
 * shows it again. [address] is what a device's account is given, where it
 * asks for a server as well.
 */
data class DevicesState(
    val address: String = "",
    val devices: List<Device> = emptyList(),
    val isLoading: Boolean = true,
    val error: MochiError? = null,
    /** The add dialog is open, asking for [name]. */
    val adding: Boolean = false,
    val name: String = "",
    val isCreating: Boolean = false,
    val token: String? = null,
    /** The username every device enters, the account's address: from the device list, or a new device's own answer. */
    val username: String = "",
    val deleting: Device? = null,
    val isDeleting: Boolean = false,
)

sealed interface DevicesEvent {
    data class Failed(val error: MochiError) : DevicesEvent
    data object Deleted : DevicesEvent
}

/**
 * The connected-devices screen's state, held by an app's view model over its
 * own [tokens]. [path] is where the app serves its device protocol on the
 * user's server, such as `people/carddav/`.
 */
class ConnectedDevices(
    private val scope: CoroutineScope,
    session: SessionManager,
    path: String,
    private val tokens: DeviceTokens,
) {
    private val _state = MutableStateFlow(DevicesState())
    val state: StateFlow<DevicesState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<DevicesEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<DevicesEvent> = _events.asSharedFlow()

    init {
        scope.launch {
            val server = session.serverUrl.first().trimEnd('/')
            _state.update { it.copy(address = "$server/$path") }
        }
        load()
    }

    fun load() {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val listing = tokens.list()
                val devices = listing.devices.sortedWith(compareBy(NaturalCompare) { it.name })
                _state.update { it.copy(isLoading = false, devices = devices, username = listing.username.ifBlank { it.username }) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.toMochiError()) }
            }
        }
    }

    fun startAdd() {
        _state.update { it.copy(adding = true, name = "") }
    }

    fun cancelAdd() {
        _state.update { if (it.isCreating) it else it.copy(adding = false, name = "") }
    }

    fun setName(name: String) {
        _state.update { it.copy(name = characters(name, DEVICE_NAME_MAXIMUM)) }
    }

    fun create() {
        val name = _state.value.name.trim()
        if (name.isBlank() || _state.value.isCreating) return
        _state.update { it.copy(isCreating = true) }
        scope.launch {
            try {
                val created = tokens.create(name)
                _state.update {
                    it.copy(isCreating = false, adding = false, name = "", token = created.token, username = created.username.ifBlank { it.username })
                }
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isCreating = false) }
                _events.emit(DevicesEvent.Failed(e.toMochiError()))
            }
        }
    }

    /** The password has been saved elsewhere; forget it. */
    fun done() {
        _state.update { it.copy(token = null) }
    }

    fun requestDelete(device: Device) {
        _state.update { it.copy(deleting = device) }
    }

    fun cancelDelete() {
        _state.update { if (it.isDeleting) it else it.copy(deleting = null) }
    }

    fun confirmDelete() {
        val target = _state.value.deleting ?: return
        if (_state.value.isDeleting) return
        _state.update { it.copy(isDeleting = true) }
        scope.launch {
            try {
                tokens.delete(target.hash)
                _state.update { state ->
                    state.copy(isDeleting = false, deleting = null, devices = state.devices.filterNot { it.hash == target.hash })
                }
                _events.emit(DevicesEvent.Deleted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The confirmation stays up, so the delete can be retried or cancelled.
                _state.update { it.copy(isDeleting = false) }
                _events.emit(DevicesEvent.Failed(e.toMochiError()))
            }
        }
    }
}

/**
 * The connected-devices screen for [devices]. [addressLabel] names the
 * address as the app's own protocol calls it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectedDevicesScreen(
    devices: ConnectedDevices,
    addressLabel: String,
    onBack: () -> Unit,
) {
    val state by devices.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(devices) {
        devices.events.collect { event ->
            when (event) {
                is DevicesEvent.Failed -> snackbar.showSnackbar(event.error.userMessage())
                DevicesEvent.Deleted -> snackbar.showSnackbar(resources.getString(R.string.devices_deleted))
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.devices_title)) },
                navigationIcon = {
                    MochiIconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.token == null) {
                MochiFab(onClick = devices::startAdd) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.devices_add))
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // The same for every device, so on show until a new password
            // brings its own copy of them.
            val token = state.token
            if (token == null) {
                item(key = "details") {
                    DetailsSection(addressLabel = addressLabel, address = state.address, username = state.username)
                }
            } else {
                item(key = "credentials") {
                    CredentialsSection(
                        addressLabel = addressLabel,
                        address = state.address,
                        username = state.username,
                        token = token,
                        onDone = devices::done,
                    )
                }
            }
            val error = state.error
            when {
                state.isLoading && state.devices.isEmpty() -> item(key = "loading") {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                error != null && state.devices.isEmpty() -> item(key = "error") {
                    InlineErrorState(error = error, onRetry = devices::load)
                }
                state.devices.isEmpty() -> item(key = "empty") {
                    Text(
                        text = stringResource(R.string.devices_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> items(state.devices, key = { it.hash }) { device ->
                    DeviceRow(device = device, onDelete = { devices.requestDelete(device) })
                }
            }
        }
    }

    if (state.deleting != null) {
        MochiAlertDialog(
            onDismissRequest = devices::cancelDelete,
            title = stringResource(R.string.devices_delete_title),
            text = stringResource(R.string.devices_delete_message),
            confirmText = stringResource(R.string.common_delete),
            onConfirm = devices::confirmDelete,
            confirmLoading = state.isDeleting,
            destructive = true,
            dismissText = stringResource(R.string.common_cancel),
            dismissEnabled = !state.isDeleting,
        )
    }

    if (state.adding) {
        AddDeviceDialog(
            name = state.name,
            creating = state.isCreating,
            onNameChange = devices::setName,
            onCreate = devices::create,
            onDismiss = devices::cancelAdd,
        )
    }
}

/** A new device's name, asked for before its password is made. */
@Composable
private fun AddDeviceDialog(
    name: String,
    creating: Boolean,
    onNameChange: (String) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.devices_add),
        confirmText = stringResource(R.string.devices_create),
        onConfirm = onCreate,
        confirmEnabled = name.isNotBlank(),
        confirmLoading = creating,
        dismissText = stringResource(R.string.common_cancel),
        dismissEnabled = !creating,
    ) {
        MochiTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.devices_name)) },
            singleLine = true,
            enabled = !creating,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCreate() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * What every device's account asks for besides its password: the address,
 * which serves where a client asks for a server as well, and the username.
 */
@Composable
private fun DetailsSection(addressLabel: String, address: String, username: String) {
    MochiCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CredentialRow(addressLabel, address)
            if (username.isNotEmpty()) {
                CredentialRow(stringResource(R.string.devices_username), username)
            }
        }
    }
}

/**
 * What the new device's account asks for. The window is kept out of
 * screenshots and the recent-apps thumbnail while the password is on screen.
 */
@Composable
private fun CredentialsSection(
    addressLabel: String,
    address: String,
    username: String,
    token: String,
    onDone: () -> Unit,
) {
    SecureWindow()
    MochiCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CredentialRow(addressLabel, address)
            CredentialRow(stringResource(R.string.devices_username), username)
            CredentialRow(stringResource(R.string.devices_password), token, sensitive = true)
            Text(
                text = stringResource(R.string.devices_warning),
                style = MaterialTheme.typography.bodyMedium,
            )
            MochiOutlinedButton(
                onClick = onDone,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.devices_done))
            }
        }
    }
}

/**
 * A labelled value with its copy button. The value wraps rather than
 * truncating: the user may have to read it off this screen and type it on the
 * other device.
 */
@Composable
private fun CredentialRow(label: String, value: String, sensitive: Boolean = false) {
    LabelledRow(label = label) {
        DataChip(
            value = value,
            copyable = false,
            wrap = true,
            modifier = Modifier.weight(1f),
        )
        CopyButton(
            value = value,
            contentDescription = stringResource(R.string.devices_copy, label),
            sensitive = sensitive,
        )
    }
}

@Composable
private fun LabelledRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(112.dp),
        )
        content()
    }
}

/**
 * The line under a device's name: when it was made, then when it was last
 * used or that it never has been. A device the server gives no creation time
 * shows its use alone.
 */
@Composable
internal fun deviceDates(device: Device, format: Format): String {
    val used = if (device.used > 0) {
        stringResource(R.string.devices_used, format.formatTimestamp(device.used))
    } else {
        stringResource(R.string.devices_unused)
    }
    if (device.created <= 0) return used
    return stringResource(R.string.devices_created, format.formatTimestamp(device.created)) + " · " + used
}

@Composable
private fun DeviceRow(device: Device, onDelete: () -> Unit) {
    MochiCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Smartphone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = deviceDates(device, LocalFormat.current),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MochiIconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.devices_delete),
                )
            }
        }
    }
}
