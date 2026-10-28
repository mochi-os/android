// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.handles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Restore
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.util.NaturalCompare
import org.mochios.settings.R
import org.mochios.settings.api.Availability
import org.mochios.settings.api.RegistryAccount
import org.mochios.settings.api.RegistryHandle
import org.mochios.settings.ui.networks.HandleChooser
import org.mochios.settings.ui.networks.networkLabel
import org.mochios.settings.ui.systemusers.PaginationBar
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemHandlesScreen(
    onBack: () -> Unit,
    viewModel: SystemHandlesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var releasing by remember { mutableStateOf<RegistryHandle?>(null) }
    var takingDown by remember { mutableStateOf<RegistryAccount?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(state.error) {
        val failure = state.error
        if (failure != null && !state.isLoading && state.handles.isNotEmpty()) {
            snackbar.showSnackbar(failure.userMessage())
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.handles_title)) },
                navigationIcon = {
                    MochiIconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            MochiTextField(
                value = state.search,
                onValueChange = viewModel::setSearch,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.handles_search_placeholder)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Box(modifier = Modifier.fillMaxSize()) {
                SystemHandlesList(
                    state = state,
                    onRetry = { viewModel.refresh() },
                    check = viewModel::available,
                    onReserve = viewModel::reserve,
                    onTakedown = { takingDown = it },
                    onRestore = { viewModel.takedown(it, down = false) },
                    onRelease = { releasing = it },
                    onLimit = viewModel::setLimit,
                    onPrevious = viewModel::previousPage,
                    onNext = viewModel::nextPage,
                )
            }
        }
    }

    takingDown?.let { account ->
        MochiAlertDialog(
            onDismissRequest = { takingDown = null },
            title = stringResource(R.string.handles_takedown_title, account.identifier),
            text = stringResource(R.string.handles_takedown_message),
            confirmText = stringResource(R.string.handles_takedown_confirm),
            onConfirm = {
                takingDown = null
                viewModel.takedown(account, down = true)
            },
            destructive = true,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
    releasing?.let { handle ->
        MochiAlertDialog(
            onDismissRequest = { releasing = null },
            title = stringResource(R.string.networks_release_title),
            text = stringResource(R.string.handles_release_message, handle.name),
            confirmText = stringResource(R.string.networks_release),
            onConfirm = {
                releasing = null
                viewModel.release(handle)
            },
            destructive = true,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
}

/** The chooser, the page of handles the search matches, and the pages. */
@Composable
internal fun SystemHandlesList(
    state: SystemHandlesUiState,
    onRetry: () -> Unit,
    check: suspend (String, String) -> Availability?,
    onReserve: (name: String, domain: String, onReserved: () -> Unit) -> Unit,
    onTakedown: (RegistryAccount) -> Unit,
    onRestore: (RegistryAccount) -> Unit,
    onRelease: (RegistryHandle) -> Unit,
    onLimit: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    when {
        state.isLoading -> Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        state.error != null && state.handles.isEmpty() -> ErrorState(
            error = state.error,
            onRetry = onRetry,
        )
        else -> LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.domains.isNotEmpty()) {
                item("chooser") {
                    HandleChooser(
                        domains = state.domains,
                        suggestion = "",
                        title = stringResource(R.string.handles_take),
                        reserving = state.busy == "reserve",
                        check = check,
                        onReserve = onReserve,
                    )
                }
            }
            if (state.handles.isEmpty()) {
                item("empty") {
                    Text(
                        stringResource(
                            if (state.search.isNotBlank()) R.string.handles_empty_search else R.string.handles_empty,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.handles, key = { "${it.domain}/${it.name}" }) { handle ->
                RegistryCard(
                    handle = handle,
                    busy = state.busy,
                    onTakedown = onTakedown,
                    onRestore = onRestore,
                    onRelease = onRelease,
                )
            }
            if (state.count > state.limit) {
                item("pages") {
                    PaginationBar(
                        offset = state.offset,
                        limit = state.limit,
                        count = state.count,
                        onLimit = onLimit,
                        onPrev = onPrevious,
                        onNext = onNext,
                        status = R.plurals.handles_pagination_status,
                    )
                }
            }
        }
    }
}

/** One reservation: the name, what kind it is, who holds it, the networks it has accounts on. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RegistryCard(
    handle: RegistryHandle,
    busy: String?,
    onTakedown: (RegistryAccount) -> Unit,
    onRestore: (RegistryAccount) -> Unit,
    onRelease: (RegistryHandle) -> Unit,
) {
    val format = LocalFormat.current
    var menu by remember { mutableStateOf(false) }
    val accounts = handle.accounts.sortedWith(compareBy(NaturalCompare) { it.identifier })
    val networks = handle.accounts.map { it to networkLabel(it.network) }.sortedWith(compareBy(NaturalCompare) { it.second })
    MochiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (handle.parent.isNotEmpty()) 16.dp else 0.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(handle.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    handle.domain + " · " + when {
                        handle.kind == "community" -> stringResource(R.string.handles_community)
                        handle.parent.isNotEmpty() -> stringResource(R.string.handles_name_under, handle.parent)
                        else -> stringResource(R.string.networks_handle)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (handle.holder.isNotEmpty()) "${handle.holder} · ${handle.username}" else handle.username,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (networks.isNotEmpty()) {
                    // The networks by name: each address is the handle in
                    // that network's form. A taken-down one is in the error
                    // colour, and says so to a screen reader.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for ((account, label) in networks) {
                            val down = account.status == "takendown"
                            val gone = stringResource(R.string.handles_taken_down)
                            Text(
                                label,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (down) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = if (down) Modifier.semantics { stateDescription = gone } else Modifier,
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.account_created, format.formatTimestamp(handle.created)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                if (busy == "${handle.domain}/${handle.name}" || accounts.any { busy == it.id }) {
                    CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                } else {
                    MochiIconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreHoriz, contentDescription = stringResource(R.string.networks_handle_actions))
                    }
                }
                MochiDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    for (account in accounts.filter { it.did.isNotEmpty() }) {
                        if (account.status == "takendown") {
                            MochiDropdownMenuItem(
                                text = { Text(stringResource(R.string.handles_restore, account.identifier)) },
                                onClick = { menu = false; onRestore(account) },
                                leadingIcon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
                            )
                        } else {
                            MochiDropdownMenuItem(
                                text = { Text(stringResource(R.string.handles_take_down, account.identifier)) },
                                onClick = { menu = false; onTakedown(account) },
                                leadingIcon = { Icon(Icons.Outlined.Block, contentDescription = null) },
                            )
                        }
                    }
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(R.string.networks_release)) },
                        onClick = { menu = false; onRelease(handle) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                    )
                }
            }
        }
    }
}
