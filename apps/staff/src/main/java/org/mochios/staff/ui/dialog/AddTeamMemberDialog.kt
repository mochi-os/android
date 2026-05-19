package org.mochios.staff.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.staff.R
import org.mochios.staff.ui.team.ROLE_OPTIONS
import org.mochios.staff.ui.team.TeamUiState
import org.mochios.staff.ui.team.roleLabel

/**
 * Add-team-member dialog. Mirrors the web's `Add team member` dialog in
 * `apps/staff/web/src/features/team/team-page.tsx`:
 *
 *  - Debounced search field (300ms; ≥2 chars triggers
 *    `searchDirectory(q)` via the ViewModel).
 *  - Results LazyColumn — each tap highlights the row as the selected
 *    person and lights up a check icon.
 *  - Role dropdown (Admin / Moderator / Support).
 *  - Submit is disabled until both a person and a role have been chosen.
 *
 * Search state, the selected person, and the chosen role all live on the
 * [TeamViewModel] so the dialog can be a pure functional composable.
 */
@Composable
fun AddTeamMemberDialog(
    state: TeamUiState,
    onSearchChange: (String) -> Unit,
    onSelectPerson: (id: String, name: String) -> Unit,
    onRoleChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    serverUrl: String,
) {
    val canSubmit = state.addSelectedId != null &&
        state.addRole.isNotBlank() &&
        !state.submitting

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.staff_team_dialog_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.staff_team_dialog_person),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.addSearch,
                    onValueChange = onSearchChange,
                    placeholder = {
                        Text(stringResource(R.string.staff_team_dialog_search_placeholder))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.addSearching || (state.addSearch.length >= 2)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                    ) {
                        when {
                            state.addSearching -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                            state.addResults.isEmpty() -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.staff_team_dialog_no_results),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            else -> {
                                LazyColumn {
                                    items(state.addResults, key = { it.id }) { person ->
                                        val selected = state.addSelectedId == person.id
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    if (selected) {
                                                        MaterialTheme.colorScheme.secondaryContainer
                                                    } else {
                                                        androidx.compose.ui.graphics.Color.Transparent
                                                    },
                                                )
                                                .clickable { onSelectPerson(person.id, person.name) }
                                                .padding(horizontal = 8.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            EntityAvatar(
                                                name = person.name,
                                                src = "$serverUrl/staff/-/user/${person.id}/asset/avatar",
                                                seed = person.id,
                                                size = 28.dp,
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = person.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f),
                                            )
                                            if (selected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.staff_team_dialog_role),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RoleDropdown(
                    current = state.addRole,
                    onChange = onRoleChange,
                )
            }
        },
        confirmButton = {
            Button(onClick = onSubmit, enabled = canSubmit) {
                Text(
                    if (state.submitting) {
                        stringResource(R.string.staff_team_dialog_adding)
                    } else {
                        stringResource(R.string.staff_team_dialog_add)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.staff_team_dialog_cancel))
            }
        },
    )
}

@Composable
private fun RoleDropdown(
    current: String,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (current.isBlank()) {
        stringResource(R.string.staff_team_dialog_role_placeholder)
    } else {
        roleLabel(current)
    }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ROLE_OPTIONS.forEach { value ->
                DropdownMenuItem(
                    text = { Text(roleLabel(value)) },
                    onClick = {
                        expanded = false
                        onChange(value)
                    },
                )
            }
        }
    }
}

