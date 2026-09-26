// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.team

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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.R as MochiR
import org.mochios.android.api.userMessage
import org.mochios.android.format.formatFingerprint
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.LoadingState
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.staff.R
import org.mochios.staff.model.StaffMember
import org.mochios.staff.ui.components.LocalStaffMe
import org.mochios.staff.ui.components.StaffStatusBadge
import org.mochios.staff.ui.components.StaffUserAvatar

/**
 * Staff team management. Admin gating here is cosmetic; the server enforces it
 * on every action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(
    @Suppress("UNUSED_PARAMETER") navController: NavController,
    snackbarHostState: SnackbarHostState,
    viewModel: TeamViewModel = hiltViewModel(),
) {
    val me = LocalStaffMe.current
    val isAdmin = me?.role == "admin"

    val state by viewModel.state.collectAsState()
    val resources = LocalResources.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TeamEvent.Toast -> snackbarHostState.showSnackbar(resources.getString(event.messageRes))
                is TeamEvent.Error -> {
                    val fallback = resources.getString(R.string.staff_team_toast_add_failed)
                    val msg = event.error.userMessage().ifBlank { fallback }
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    TeamBody(
        state = state,
        isAdmin = isAdmin,
        onChangeRole = viewModel::changeRole,
        onAskRemove = viewModel::askRemove,
    )

    val removeTarget = state.removeTarget
    if (removeTarget != null) {
        MochiAlertDialog(
            onDismissRequest = viewModel::cancelRemove,
            title = stringResource(R.string.staff_team_remove_title),
            text = stringResource(R.string.staff_team_remove_desc),
            confirmText = stringResource(R.string.staff_team_remove_confirm),
            onConfirm = viewModel::confirmRemove,
            destructive = true,
            dismissText = stringResource(MochiR.string.common_cancel),
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 88.dp),
                ) {
                    items(state.members, key = { member -> member.id }) { member ->
                        MemberRow(
                            member = member,
                            isAdmin = isAdmin,
                            roleUpdating = state.roleUpdatingId == member.id,
                            onChangeRole = onChangeRole,
                            onAskRemove = onAskRemove,
                        )
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
    val displayName = member.name?.takeIf { it.isNotBlank() } ?: formatFingerprint(member.fingerprint)

    MochiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StaffUserAvatar(name = displayName, id = member.id, size = 32.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isAdmin) {
                    RoleDropdown(
                        current = member.role,
                        enabled = !roleUpdating,
                        onChange = { role -> onChangeRole(member, role) },
                    )
                    MochiIconButton(onClick = { onAskRemove(member) }) {
                        Icon(
                            Icons.Outlined.PersonRemove,
                            contentDescription = stringResource(R.string.staff_team_action_remove),
                        )
                    }
                } else {
                    StaffStatusBadge(status = member.role)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            val added = format.formatRelativeTime(member.added)
            if (member.addedby.isNotBlank()) {
                AddedByLine(member = member, added = added)
            } else {
                Text(
                    text = added,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AddedByLine(member: StaffMember, added: String) {
    val isSystem = member.addedby == "system"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(
                R.string.staff_team_added_by_label,
                stringResource(R.string.staff_team_col_added_by),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(4.dp))
        if (isSystem) {
            Text(
                text = stringResource(R.string.staff_team_added_by_system),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val name = member.addedbyName?.takeIf { value -> value.isNotBlank() }
                ?: formatFingerprint(member.addedbyFingerprint)
            StaffUserAvatar(name = name, id = member.addedby, size = 20.dp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Text(
            text = stringResource(R.string.staff_team_added_when, added),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun RoleDropdown(
    current: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        MochiOutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            contentPadding = PaddingValues(start = 14.dp, end = 6.dp),
        ) {
            Text(roleLabel(current))
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
        MochiDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ROLE_OPTIONS.forEach { value ->
                MochiDropdownMenuItem(
                    text = { Text(roleLabel(value)) },
                    onClick = {
                        expanded = false
                        if (value != current) onChange(value)
                    },
                    selected = current == value,
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
