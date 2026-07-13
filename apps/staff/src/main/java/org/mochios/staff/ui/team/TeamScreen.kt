// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.LoadingState
import org.mochios.staff.R
import org.mochios.staff.model.StaffMember
import org.mochios.staff.ui.components.LocalStaffMe
import org.mochios.staff.ui.components.StaffStatusBadge
import org.mochios.staff.ui.dialog.AddTeamMemberDialog

/**
 * Staff Team management screen. Mirrors web's
 * `apps/staff/web/src/features/team/team-page.tsx`:
 *
 *  - Drawer-driven nav via the parent [StaffLayout]'s [StaffSidebar].
 *  - Top-bar "Add member" action (mounted at the StaffLayout level in
 *    StaffNavGraph, admin-only).
 *  - Table-style list of every team member: avatar + name, role
 *    (inline dropdown for admins, static badge otherwise), added timestamp,
 *    added-by avatar + name (or "System" when seeded by the server), and
 *    per-row Remove (admins only).
 *  - Remove confirmation via lib's [ConfirmDialog].
 *
 * Admin gating is purely cosmetic — the server still enforces it on every
 * action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(
    @Suppress("UNUSED_PARAMETER") navController: NavController,
    viewModel: TeamViewModel = hiltViewModel(),
) {
    val me = LocalStaffMe.current
    val isAdmin = me?.role == "admin"

    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TeamEvent.Toast -> snackbarHostState.showSnackbar(context.getString(event.messageRes))
                is TeamEvent.Error -> {
                    val fallback = context.getString(R.string.staff_team_toast_add_failed)
                    val msg = event.error.userMessage().ifBlank { fallback }
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TeamBody(
            state = state,
            isAdmin = isAdmin,
            onChangeRole = viewModel::changeRole,
            onAskRemove = viewModel::askRemove,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (state.showAddDialog) {
        AddTeamMemberDialog(
            state = state,
            onSearchChange = viewModel::setAddSearch,
            onSelectPerson = viewModel::selectAddPerson,
            onRoleChange = viewModel::setAddRole,
            onSubmit = viewModel::submitAdd,
            onCancel = viewModel::closeAddDialog,
        )
    }

    val removeTarget = state.removeTarget
    if (removeTarget != null) {
        ConfirmDialog(
            title = stringResource(R.string.staff_team_remove_title),
            message = stringResource(R.string.staff_team_remove_desc),
            confirmLabel = stringResource(R.string.staff_team_remove_confirm),
            isDestructive = true,
            onConfirm = viewModel::confirmRemove,
            onDismiss = viewModel::cancelRemove,
        )
    }
}

@Composable
private fun TeamBody(
    state: TeamUiState,
    isAdmin: Boolean,
    onChangeRole: (StaffMember, String) -> Unit,
    onAskRemove: (StaffMember) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        when {
            state.isLoading && state.members.isEmpty() -> LoadingState()
            state.members.isEmpty() -> EmptyState(
                icon = Icons.Default.Group,
                title = stringResource(R.string.staff_team_empty),
            )
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.members, key = { it.id }) { member ->
                        MemberRow(
                            member = member,
                            isAdmin = isAdmin,
                            roleUpdating = state.roleUpdatingId == member.id,
                            onChangeRole = onChangeRole,
                            onAskRemove = onAskRemove,
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: StaffMember,
    isAdmin: Boolean,
    roleUpdating: Boolean,
    onChangeRole: (StaffMember, String) -> Unit,
    onAskRemove: (StaffMember) -> Unit,
) {
    val format = LocalFormat.current
    val displayName = member.name?.takeIf { it.isNotBlank() } ?: fingerprint(member.id)
    val avatarUrl = "/staff/-/user/${member.id}/asset/avatar"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(2f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EntityAvatar(name = displayName, src = avatarUrl, seed = member.id, size = 32.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.padding(top = 4.dp))
            Text(
                text = format.formatRelativeTime(member.added),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (member.addedby.isNotBlank()) {
                AddedByLine(member = member)
            }
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (isAdmin) {
                RoleDropdown(
                    current = member.role,
                    enabled = !roleUpdating,
                    onChange = { onChangeRole(member, it) },
                )
            } else {
                StaffStatusBadge(status = member.role)
            }
        }
        if (isAdmin) {
            OutlinedButton(onClick = { onAskRemove(member) }) {
                Text(stringResource(R.string.staff_team_action_remove))
            }
        }
    }
}

@Composable
private fun AddedByLine(member: StaffMember) {
    val isSystem = member.addedby == "system"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.staff_team_col_added_by) + ": ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isSystem) {
            Text(
                text = stringResource(R.string.staff_team_added_by_system),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val name = member.addedbyName?.takeIf { it.isNotBlank() } ?: fingerprint(member.addedby)
            EntityAvatar(
                name = name,
                src = "/staff/-/user/${member.addedby}/asset/avatar",
                seed = member.addedby,
                size = 20.dp,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RoleDropdown(
    current: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = roleLabel(current)
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
        ) {
            Text(label)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ROLE_OPTIONS.forEach { value ->
                DropdownMenuItem(
                    text = { Text(roleLabel(value)) },
                    onClick = {
                        expanded = false
                        if (value != current) onChange(value)
                    },
                )
            }
        }
    }
}

internal val ROLE_OPTIONS = listOf("admin", "moderator", "support")

@Composable
internal fun roleLabel(role: String): String = when (role.lowercase()) {
    "admin" -> stringResource(R.string.staff_team_role_admin)
    "moderator" -> stringResource(R.string.staff_team_role_moderator)
    "support" -> stringResource(R.string.staff_team_role_support)
    else -> role
}

/** First 9 characters of an entity ID — the standard Mochi fingerprint slice. */
internal fun fingerprint(id: String): String =
    if (id.length <= 9) id else id.substring(0, 9)
