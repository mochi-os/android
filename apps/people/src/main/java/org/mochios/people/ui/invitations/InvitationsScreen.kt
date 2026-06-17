package org.mochios.people.ui.invitations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.people.R
import org.mochios.people.model.FriendInvite
import org.mochios.people.ui.components.PeopleEmptyState
import org.mochios.people.ui.components.PeopleSidebar
import org.mochios.people.ui.components.PeopleSidebarSection
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvitationsScreen(
    @Suppress("unused") onOpenPerson: (id: String) -> Unit,
    onSwitchSection: (PeopleSidebarSection) -> Unit,
    @Suppress("unused") onOpenNotifications: () -> Unit,
    viewModel: InvitationsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    // Avatar URLs are absolute — the lib's Coil loader doesn't share the
    // people-app retrofit baseUrl, so the ViewModel exposes the server URL
    // for the row composables to compose with.
    val serverUrl = viewModel.serverUrl
    var showOverflow by remember { mutableStateOf(false) }
    val received = viewModel.filteredReceived()
    val sent = viewModel.filteredSent()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()

    // Snackbar toasts on accept/decline/cancel/policy-update, matching web.
    val snackbarHostState = remember { SnackbarHostState() }
    val acceptedFmt = stringResource(R.string.people_invitations_toast_accepted)
    val declinedFmt = stringResource(R.string.people_invitations_toast_declined)
    val cancelledFmt = stringResource(R.string.people_invitations_toast_cancelled)
    val policyFmt = stringResource(R.string.people_invitations_toast_policy)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val msg = when (event) {
                is InvitationsEvent.Accepted -> acceptedFmt.format(event.name)
                is InvitationsEvent.Declined -> declinedFmt.format(event.name)
                is InvitationsEvent.Cancelled -> cancelledFmt.format(event.name)
                is InvitationsEvent.PolicyUpdated -> policyFmt
            }
            snackbarHostState.showSnackbar(msg)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            PeopleSidebar(
                current = PeopleSidebarSection.INVITATIONS,
                onSelect = { section ->
                    drawerScope.launch { drawerState.close() }
                    if (section != PeopleSidebarSection.INVITATIONS) onSwitchSection(section)
                },
            )
        },
    ) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.people_invitations_title)) },
                navigationIcon = {
                    IconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = stringResource(R.string.people_open_sidebar),
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(MochiR.string.common_more_options),
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.people_invite_settings)) },
                                onClick = {
                                    showOverflow = false
                                    viewModel.openSettings()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Search bar — always visible to mirror the web, which keeps the
            // input pinned to the page header.
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = { Text(stringResource(R.string.people_friends_search_placeholder)) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Stacked sections (Received above Sent, both visible) matching
            // the web layout. Previously this was a TabRow — the user
            // explicitly asked for parity with web's two-section style.
            when {
                uiState.isLoading && uiState.received.isEmpty() && uiState.sent.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null && uiState.received.isEmpty() && uiState.sent.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = uiState.error!!.userMessage(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(onClick = { viewModel.refresh() }) {
                                Text(stringResource(MochiR.string.common_retry))
                            }
                        }
                    }
                }
                received.isEmpty() && sent.isEmpty() -> InvitationsEmptyState(
                    isSearching = uiState.searchQuery.isNotBlank(),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (received.isNotEmpty()) {
                        item {
                            StackedSectionHeader(
                                text = stringResource(R.string.people_friends_received_tab),
                                count = received.size,
                            )
                        }
                        receivedItems(
                            invites = received,
                            serverUrl = serverUrl,
                            onAccept = viewModel::accept,
                            onDecline = viewModel::decline,
                        )
                    }
                    if (sent.isNotEmpty()) {
                        if (received.isNotEmpty()) {
                            item { Spacer(Modifier.height(16.dp)) }
                        }
                        item {
                            StackedSectionHeader(
                                text = stringResource(R.string.people_friends_sent_tab),
                                count = sent.size,
                            )
                        }
                        sentItems(
                            invites = sent,
                            serverUrl = serverUrl,
                            onCancel = viewModel::cancel,
                        )
                    }
                }
            }
        }
    }
    }

    if (uiState.settingsDialogOpen) {
        InviteSettingsDialog(
            initial = uiState.policy,
            isSaving = uiState.savingPolicy,
            onDismiss = { viewModel.closeSettings() },
            onSave = { viewModel.setPolicy(it) },
        )
    }
}

@Composable
private fun StackedSectionHeader(text: String, count: Int) {
    Text(
        text = "$text ($count)",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

// Both sections live in a single parent LazyColumn so there is exactly one
// scroll container — nesting a LazyColumn inside a verticalScroll'd Column
// crashes with an infinite height constraint. Keys are prefixed per section so
// a received and a sent invite sharing an id can't collide.
private fun LazyListScope.receivedItems(
    invites: List<FriendInvite>,
    serverUrl: String,
    onAccept: (FriendInvite) -> Unit,
    onDecline: (FriendInvite) -> Unit,
) {
    items(invites, key = { invite -> "received-${invite.id}" }) { invite ->
        ReceivedRow(
            invite = invite,
            serverUrl = serverUrl,
            onAccept = { onAccept(invite) },
            onDecline = { onDecline(invite) },
        )
    }
}

private fun LazyListScope.sentItems(
    invites: List<FriendInvite>,
    serverUrl: String,
    onCancel: (FriendInvite) -> Unit,
) {
    items(invites, key = { invite -> "sent-${invite.id}" }) { invite ->
        SentRow(
            invite = invite,
            serverUrl = serverUrl,
            onCancel = { onCancel(invite) },
        )
    }
}

@Composable
private fun ReceivedRow(
    invite: FriendInvite,
    serverUrl: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val avatarSrc = avatarUrl(serverUrl, invite.id)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EntityAvatar(
            name = invite.name,
            src = avatarSrc,
            seed = invite.id.ifEmpty { invite.name },
            size = 40.dp,
        )
        Text(
            text = invite.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Button(onClick = onAccept) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text(stringResource(R.string.people_invitations_accept))
        }
        OutlinedButton(onClick = onDecline) {
            Icon(
                Icons.Default.PersonOff,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text(stringResource(R.string.people_invitations_decline))
        }
    }
}

@Composable
private fun SentRow(
    invite: FriendInvite,
    serverUrl: String,
    onCancel: () -> Unit,
) {
    val avatarSrc = avatarUrl(serverUrl, invite.id)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EntityAvatar(
            name = invite.name,
            src = avatarSrc,
            seed = invite.id.ifEmpty { invite.name },
            size = 40.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = invite.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.people_friends_pending),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onCancel) {
            Icon(
                Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text(stringResource(R.string.people_common_cancel))
        }
    }
}

// Full-screen empty state shown when both received and sent sections are empty.
// When a search is active the hint switches to a "try adjusting your search"
// prompt.
@Composable
private fun InvitationsEmptyState(isSearching: Boolean) {
    PeopleEmptyState(
        icon = Icons.Default.PersonAddAlt,
        title = stringResource(R.string.people_invitations_empty),
        subtitle = stringResource(
            if (isSearching) {
                R.string.people_friends_try_adjusting
            } else {
                R.string.people_invitations_empty_hint
            },
        ),
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 64.dp),
    )
}

@Composable
private fun InviteSettingsDialog(
    initial: String,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var selected by remember(initial) { mutableStateOf(initial) }
    val options = listOf(
        PolicyOption(
            value = "notify",
            label = stringResource(R.string.people_invite_settings_notify_me),
            description = stringResource(R.string.people_invite_settings_notify_description),
        ),
        PolicyOption(
            value = "silent",
            label = stringResource(R.string.people_invite_settings_store_silently),
            description = stringResource(R.string.people_invite_settings_silent_description),
        ),
        PolicyOption(
            value = "reject",
            label = stringResource(R.string.people_invite_settings_reject_all),
            description = stringResource(R.string.people_invite_settings_reject_description),
        ),
        PolicyOption(
            value = "accept",
            label = stringResource(R.string.people_invite_settings_accept_automatically),
            description = stringResource(R.string.people_invite_settings_accept_description),
        ),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.people_invitations_incoming)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (option in options) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = option.value }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(
                            selected = selected == option.value,
                            onClick = { selected = option.value },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(selected) },
                enabled = !isSaving,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(16.dp)
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(stringResource(R.string.people_common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(R.string.people_common_cancel))
            }
        },
    )
}

private data class PolicyOption(
    val value: String,
    val label: String,
    val description: String,
)

private fun avatarUrl(serverUrl: String, id: String): String? {
    if (serverUrl.isBlank() || id.isBlank()) return null
    return "$serverUrl/people/$id/-/avatar"
}
