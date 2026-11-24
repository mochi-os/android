// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.team

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.R as MochiR
import org.mochios.android.api.userMessage
import org.mochios.android.format.formatFingerprint
import org.mochios.android.ui.components.MetaRow
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.staff.R
import org.mochios.staff.ui.components.StaffUserAvatar

/**
 * Add-team-member screen: debounced directory search, then a role choice.
 * Search fires at two characters or more.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTeamMemberScreen(
    onBack: () -> Unit,
    onAdded: () -> Unit,
    viewModel: AddTeamMemberViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val canSubmit = state.selectedId != null && state.role.isNotBlank() && !state.submitting

    LaunchedEffect(state.added) {
        if (state.added) onAdded()
    }

    // The person and role, checked once more before the role is granted:
    // names are not unique, so the fingerprint is what tells two apart.
    if (state.confirming) {
        MochiAlertDialog(
            onDismissRequest = viewModel::cancelConfirm,
            title = stringResource(R.string.staff_team_dialog_add_title),
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StaffUserAvatar(name = state.selectedName.orEmpty(), id = state.selectedId.orEmpty(), size = 36.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        PersonLines(name = state.selectedName.orEmpty(), fingerprint = state.selectedFingerprint)
                    }
                    MetaRow(stringResource(R.string.staff_team_dialog_role), roleLabel(state.role))
                }
            },
            confirmText = stringResource(R.string.staff_team_dialog_add),
            onConfirm = viewModel::submit,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.staff_team_dialog_add_title)) },
                navigationIcon = {
                    MochiIconButton(onClick = onBack, enabled = !state.submitting) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    state.error?.let { error ->
                        Text(
                            text = error.userMessage().ifBlank {
                                stringResource(R.string.staff_team_toast_add_failed)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    MochiButton(
                        onClick = viewModel::confirm,
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (state.submitting) {
                                stringResource(R.string.staff_team_dialog_adding)
                            } else {
                                stringResource(R.string.staff_team_dialog_add)
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.staff_team_dialog_person),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MochiTextField(
                value = state.search,
                onValueChange = viewModel::setSearch,
                placeholder = {
                    Text(stringResource(R.string.staff_team_dialog_search_placeholder))
                },
                singleLine = true,
                enabled = !state.submitting,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.staff_team_dialog_role),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RoleDropdown(
                current = state.role,
                enabled = !state.submitting,
                onChange = viewModel::setRole,
            )
            if (state.searching || state.search.length >= 2) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when {
                        state.searching -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                        state.results.isEmpty() -> {
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
                                items(state.results, key = { person -> person.id }) { person ->
                                    val selected = state.selectedId == person.id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (selected) {
                                                    MaterialTheme.colorScheme.secondaryContainer
                                                } else {
                                                    Color.Transparent
                                                },
                                            )
                                            .clickable(enabled = !state.submitting) {
                                                viewModel.selectPerson(person)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        StaffUserAvatar(name = person.name, id = person.id, size = 28.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        PersonLines(
                                            name = person.name,
                                            fingerprint = person.fingerprint,
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
        }
    }
}

/** A person's name over their punctuated fingerprint, the one thing that tells two of the same name apart. */
@Composable
private fun PersonLines(name: String, fingerprint: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = name.ifBlank { stringResource(R.string.staff_accounts_unnamed) },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val punctuated = formatFingerprint(fingerprint)
        if (punctuated.isNotEmpty()) {
            Text(
                text = punctuated,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
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
    val label = if (current.isBlank()) {
        stringResource(R.string.staff_team_dialog_role_placeholder)
    } else {
        roleLabel(current)
    }
    Box {
        MochiOutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        MochiDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ROLE_OPTIONS.forEach { value ->
                MochiDropdownMenuItem(
                    text = { Text(roleLabel(value)) },
                    onClick = {
                        expanded = false
                        onChange(value)
                    },
                    selected = current == value,
                )
            }
        }
    }
}
