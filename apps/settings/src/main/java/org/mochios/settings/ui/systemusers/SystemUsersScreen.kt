package org.mochios.settings.ui.systemusers

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.settings.R
import org.mochios.android.R as MochiR
import org.mochios.settings.api.SystemUser
import org.mochios.settings.api.SystemUserSession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemUsersScreen(
    onBack: () -> Unit,
    viewModel: SystemUsersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    val toastMessages = systemUsersToastMessages(state.sessionsRevokedCount)
    LaunchedEffect(state.toast, state.sessionsRevokedCount) {
        val key = state.toast ?: return@LaunchedEffect
        val msg = toastMessages[key]
        if (msg != null) snackbar.showSnackbar(msg)
        viewModel.clearToast()
    }

    var createOpen by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<SystemUser?>(null) }
    var deleteTarget by remember { mutableStateOf<SystemUser?>(null) }
    var sessionsTarget by remember { mutableStateOf<SystemUser?>(null) }
    var revokeAllConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = stringResource(R.string.system_users_title)
                    Text(
                        if (state.count > 0) "$title (${state.count})" else title,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { createOpen = true }) {
                        Icon(
                            Icons.Default.PersonAdd,
                            contentDescription = stringResource(R.string.system_users_add_user),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = state.search,
                    onValueChange = viewModel::setSearch,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.system_users_search_placeholder)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                SortBar(
                    sort = state.sort,
                    order = state.order,
                    onSort = viewModel::toggleSort,
                )

                when {
                    state.isLoading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    state.error != null -> Text(
                        text = state.error!!.userMessage(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )

                    state.users.isEmpty() -> EmptyUsers(searchActive = state.search.isNotBlank())

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.users, key = { it.id }) { user ->
                            UserCard(
                                user = user,
                                isSelf = user.username == state.currentUsername,
                                onEdit = { editTarget = user },
                                onDelete = { deleteTarget = user },
                                onToggleStatus = { viewModel.toggleStatus(user) {} },
                                onSessions = {
                                    sessionsTarget = user
                                    viewModel.loadSessions(user.id)
                                },
                            )
                        }
                        if (state.search.isBlank() && state.count > state.limit) {
                            item("pager") {
                                PaginationBar(
                                    offset = state.offset,
                                    limit = state.limit,
                                    count = state.count,
                                    onLimit = viewModel::setLimit,
                                    onPrev = viewModel::previousPage,
                                    onNext = viewModel::nextPage,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (createOpen) {
        UserDialog(
            title = stringResource(R.string.system_users_create_title),
            confirmLabel = stringResource(R.string.system_users_create_title),
            initialUsername = "",
            initialRole = "user",
            requireEmail = true,
            saving = state.mutating,
            onDismiss = { createOpen = false },
            onConfirm = { username, role ->
                viewModel.create(username, role) { ok ->
                    if (ok) createOpen = false
                }
            },
        )
    }

    editTarget?.let { user ->
        UserDialog(
            title = stringResource(R.string.system_users_edit_title),
            confirmLabel = stringResource(R.string.system_users_save_changes),
            initialUsername = user.username,
            initialRole = user.role,
            requireEmail = true,
            saving = state.mutating,
            onDismiss = { editTarget = null },
            onConfirm = { username, role ->
                viewModel.update(user.id, username, role) { ok ->
                    if (ok) editTarget = null
                }
            },
        )
    }

    deleteTarget?.let { user ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.system_users_delete_title)) },
            text = { Text(stringResource(R.string.system_users_delete_message, user.username)) },
            confirmButton = {
                TextButton(
                    enabled = !state.mutating,
                    onClick = {
                        viewModel.delete(user.id) { ok ->
                            if (ok) deleteTarget = null
                        }
                    },
                ) { Text(stringResource(R.string.system_users_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(MochiR.string.common_cancel))
                }
            },
        )
    }

    sessionsTarget?.let { user ->
        SessionsDialog(
            user = user,
            sessions = state.sessions,
            loading = state.sessionsLoadingFor == user.id,
            mutating = state.mutating,
            onRevoke = { sid -> viewModel.revokeSession(user.id, sid) },
            onRevokeAll = { revokeAllConfirm = true },
            onClose = {
                sessionsTarget = null
                viewModel.clearSessions()
            },
        )
    }

    if (revokeAllConfirm) {
        val user = sessionsTarget
        AlertDialog(
            onDismissRequest = { revokeAllConfirm = false },
            title = { Text(stringResource(R.string.system_users_revoke_all_title)) },
            text = { Text(stringResource(R.string.system_users_revoke_all_message)) },
            confirmButton = {
                TextButton(
                    enabled = !state.mutating,
                    onClick = {
                        revokeAllConfirm = false
                        if (user != null) viewModel.revokeSession(user.id, null)
                    },
                ) { Text(stringResource(R.string.system_users_revoke_all)) }
            },
            dismissButton = {
                TextButton(onClick = { revokeAllConfirm = false }) {
                    Text(stringResource(MochiR.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun systemUsersToastMessages(revokedCount: Int): Map<SystemUsersToast, String> {
    val plural = pluralStringResource(
        R.plurals.system_users_sessions_revoked,
        revokedCount.coerceAtLeast(0),
        revokedCount,
    )
    return mapOf(
        SystemUsersToast.USER_CREATED to stringResource(R.string.system_users_toast_created),
        SystemUsersToast.USER_UPDATED to stringResource(R.string.system_users_toast_updated),
        SystemUsersToast.USER_DELETED to stringResource(R.string.system_users_toast_deleted),
        SystemUsersToast.USER_SUSPENDED to stringResource(R.string.system_users_toast_suspended),
        SystemUsersToast.SUSPENSION_REMOVED to stringResource(R.string.system_users_toast_suspension_removed),
        SystemUsersToast.SESSION_REVOKED to stringResource(R.string.system_users_toast_session_revoked),
        SystemUsersToast.SESSIONS_REVOKED to plural,
        SystemUsersToast.CREATE_FAILED to stringResource(R.string.system_users_toast_create_failed),
        SystemUsersToast.UPDATE_FAILED to stringResource(R.string.system_users_toast_update_failed),
        SystemUsersToast.DELETE_FAILED to stringResource(R.string.system_users_toast_delete_failed),
        SystemUsersToast.STATUS_FAILED to stringResource(R.string.system_users_toast_status_failed),
        SystemUsersToast.REVOKE_FAILED to stringResource(R.string.system_users_toast_revoke_failed),
    )
}

@Composable
private fun SortBar(
    sort: SystemUsersSort,
    order: SystemUsersOrder,
    onSort: (SystemUsersSort) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.system_users_sort_by),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SortChip(
            label = stringResource(R.string.system_users_col_user),
            active = sort == SystemUsersSort.USERNAME,
            ascending = order == SystemUsersOrder.ASC,
            onClick = { onSort(SystemUsersSort.USERNAME) },
        )
        SortChip(
            label = stringResource(R.string.system_users_col_status),
            active = sort == SystemUsersSort.STATUS,
            ascending = order == SystemUsersOrder.ASC,
            onClick = { onSort(SystemUsersSort.STATUS) },
        )
        SortChip(
            label = stringResource(R.string.system_users_col_last),
            active = sort == SystemUsersSort.LAST,
            ascending = order == SystemUsersOrder.ASC,
            onClick = { onSort(SystemUsersSort.LAST) },
        )
    }
}

@Composable
private fun SortChip(label: String, active: Boolean, ascending: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = active,
        onClick = onClick,
        label = { Text(label) },
        trailingIcon = if (active) {
            {
                Icon(
                    if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserCard(
    user: SystemUser,
    isSelf: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleStatus: () -> Unit,
    onSessions: () -> Unit,
) {
    val format = LocalFormat.current
    val isAdmin = user.role == "administrator"
    val isSuspended = user.status == "suspended"
    var menu by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    user.username,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            Text(
                                stringResource(
                                    if (isAdmin) R.string.system_users_role_administrator
                                    else R.string.system_users_role_user
                                ),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(),
                    )
                    if (isSuspended) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(
                                    stringResource(R.string.system_users_status_suspended),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                disabledLabelColor = MaterialTheme.colorScheme.error,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                val lastLabel = if (user.last > 0) format.formatTimestamp(user.last)
                else stringResource(R.string.system_users_last_never)
                Text(
                    text = stringResource(R.string.system_users_last_login, lastLabel),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.system_users_open_actions),
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        text = { Text(stringResource(R.string.system_users_edit)) },
                        onClick = {
                            menu = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                        text = { Text(stringResource(R.string.system_users_manage_sessions)) },
                        onClick = {
                            menu = false
                            onSessions()
                        },
                    )
                    if (!isSelf) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                            text = {
                                Text(
                                    stringResource(
                                        if (isSuspended) R.string.system_users_remove_suspension
                                        else R.string.system_users_suspend,
                                    )
                                )
                            },
                            onClick = {
                                menu = false
                                onToggleStatus()
                            },
                        )
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            text = { Text(stringResource(R.string.system_users_delete)) },
                            onClick = {
                                menu = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserDialog(
    title: String,
    confirmLabel: String,
    initialUsername: String,
    initialRole: String,
    requireEmail: Boolean,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var username by remember { mutableStateOf(initialUsername) }
    var role by remember { mutableStateOf(initialRole) }
    val canSubmit = username.isNotBlank() && (!requireEmail || username.contains('@'))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.system_users_email_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                RoleDropdown(role = role, onChange = { role = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit && !saving,
                onClick = { onConfirm(username.trim(), role) },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(MochiR.string.common_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleDropdown(role: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val labelAdmin = stringResource(R.string.system_users_role_administrator)
    val labelUser = stringResource(R.string.system_users_role_user)
    val current = if (role == "administrator") labelAdmin else labelUser
    Column {
        Text(
            stringResource(R.string.system_users_role_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = current,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(labelAdmin) },
                    onClick = {
                        expanded = false
                        onChange("administrator")
                    },
                )
                DropdownMenuItem(
                    text = { Text(labelUser) },
                    onClick = {
                        expanded = false
                        onChange("user")
                    },
                )
            }
        }
    }
}

@Composable
private fun SessionsDialog(
    user: SystemUser,
    sessions: List<SystemUserSession>,
    loading: Boolean,
    mutating: Boolean,
    onRevoke: (String) -> Unit,
    onRevokeAll: () -> Unit,
    onClose: () -> Unit,
) {
    val format = LocalFormat.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.system_users_sessions_title, user.username)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.system_users_sessions_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    loading -> Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    sessions.isEmpty() -> Text(
                        stringResource(R.string.system_users_sessions_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        sessions.forEach { session ->
                            HorizontalDivider()
                            SessionRow(
                                session = session,
                                onRevoke = { onRevoke(session.id) },
                                disabled = mutating,
                                accessedLabel = format.formatTimestamp(session.accessed),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (sessions.isNotEmpty()) {
                TextButton(enabled = !mutating, onClick = onRevokeAll) {
                    Text(stringResource(R.string.system_users_revoke_all))
                }
            } else {
                TextButton(onClick = onClose) { Text(stringResource(MochiR.string.common_close)) }
            }
        },
        dismissButton = {
            if (sessions.isNotEmpty()) {
                TextButton(onClick = onClose) { Text(stringResource(MochiR.string.common_close)) }
            }
        },
    )
}

@Composable
private fun SessionRow(
    session: SystemUserSession,
    accessedLabel: String,
    disabled: Boolean,
    onRevoke: () -> Unit,
) {
    val browser = when {
        session.agent.contains("Chrome") -> "Chrome"
        session.agent.contains("Firefox") -> "Firefox"
        session.agent.contains("Safari") -> "Safari"
        else -> stringResource(R.string.system_users_unknown_browser)
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(browser, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = session.address.ifBlank { stringResource(R.string.system_users_unknown) },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.system_users_session_last_accessed, accessedLabel),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(enabled = !disabled, onClick = onRevoke) {
            Text(stringResource(R.string.system_users_revoke))
        }
    }
}

@Composable
private fun PaginationBar(
    offset: Int,
    limit: Int,
    count: Int,
    onLimit: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val from = offset + 1
    val to = minOf(offset + limit, count)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.system_users_pagination_status, from, to, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageSizeDropdown(limit = limit, onChange = onLimit)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPrev, enabled = offset > 0) {
                    Text(stringResource(R.string.system_users_previous))
                }
                OutlinedButton(onClick = onNext, enabled = offset + limit < count) {
                    Text(stringResource(R.string.system_users_next))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageSizeDropdown(limit: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = limit.toString(),
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .width(96.dp),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(10, 25, 50, 100).forEach { size ->
                DropdownMenuItem(
                    text = { Text(size.toString()) },
                    onClick = {
                        expanded = false
                        onChange(size)
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyUsers(searchActive: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(
                if (searchActive) R.string.system_users_empty_search
                else R.string.system_users_empty,
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (searchActive) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.system_users_empty_search_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
