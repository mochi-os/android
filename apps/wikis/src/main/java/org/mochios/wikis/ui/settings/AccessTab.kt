// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.R as MochiR
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiDropdownField
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.components.Section
import org.mochios.android.ui.components.mochiDialogCardColors
import org.mochios.wikis.R

// What a wiki grants, coarsest first. A wiki has three levels where a project
// has six; ownership is not handed over from here in either.
private val ACCESS_LEVEL_KEYS = listOf("edit", "view", "none")

/**
 * Who a rule is about, read as a person, a group, or one of the two wildcards
 * the server understands. Mirrors the projects and forums access tabs.
 */
@Composable
private fun accessSubjectLabel(subject: AccessSubject): String = when (subject.subject) {
    "*" -> stringResource(R.string.wikis_access_subject_anyone)
    "+" -> stringResource(R.string.wikis_access_subject_authenticated)
    else -> subject.name?.takeIf { name -> name.isNotBlank() } ?: subject.subject
}

/** Localised label for a wiki access level (edit/view/none). */
@Composable
private fun wikiAccessLevelLabel(level: String): String = when (level) {
    "edit" -> stringResource(R.string.wikis_access_level_edit)
    "view" -> stringResource(R.string.wikis_access_level_view)
    "none" -> stringResource(R.string.wikis_access_level_none)
    "manage" -> stringResource(MochiR.string.access_level_manage)
    else -> level.replaceFirstChar { first -> first.uppercase() }
}

// Sort key placing the owner first, then authenticated users, then anyone, then
// every other subject in the order the view model settled on.
private fun subjectRank(subject: AccessSubject): Int = when {
    subject.isOwner -> 0
    subject.subject == "+" -> 1
    subject.subject == "*" -> 2
    else -> 3
}

/**
 * Leading icon for an access subject: globe for anyone, group for groups and
 * authenticated users, person otherwise.
 */
private fun subjectIcon(subject: AccessSubject): ImageVector = when {
    subject.subject == "*" -> Icons.Default.Public
    subject.subject == "+" -> Icons.Default.Group
    subject.subject.startsWith("@") -> Icons.Default.Group
    else -> Icons.Default.Person
}

/**
 * Who may read and write this wiki. One section listing the rules, each with
 * the level it grants, and an Add that names a user, a group, or a wildcard.
 *
 * @param parentViewModel The settings screen, which owns the snackbar.
 * @param viewModel The tab's own state.
 */
@Composable
fun AccessTab(
    parentViewModel: WikiSettingsViewModel,
    viewModel: AccessTabViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    // Relay tab snackbars through the host view model so the parent
    // Scaffold's SnackbarHost surfaces them.
    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { msg ->
            parentViewModel.emit(msg.messageRes, *msg.args.toTypedArray())
        }
    }

    if (state.isLoading && state.subjects.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Section(
            title = stringResource(R.string.wikis_settings_tab_access),
            headerAlignment = Alignment.CenterVertically,
            action = {
                MochiOutlinedButton(onClick = { showAddDialog = true }) {
                    Text(stringResource(MochiR.string.access_add_rule))
                }
            },
        ) {
            if (state.subjects.isEmpty()) {
                Text(
                    stringResource(MochiR.string.access_no_rules),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                // Owner, then authenticated users, then anyone, then the rest.
                val ordered = state.subjects.sortedBy { subject -> subjectRank(subject) }
                ordered.forEach { subject ->
                    AccessRuleRow(
                        subject = subject,
                        onLevelChange = { level -> viewModel.setAccess(subject.subject, level) },
                        onRevoke = { viewModel.revokeAccess(subject.subject) },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddAccessDialog(
            viewModel = viewModel,
            onDismiss = { showAddDialog = false },
            onAdd = { subject, level ->
                viewModel.setAccess(subject, level)
                showAddDialog = false
            },
        )
    }
}

/**
 * One rule: who it is about, and what they may do. The owner's row carries no
 * controls - ownership is neither levelled down nor revoked from here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccessRuleRow(
    subject: AccessSubject,
    onLevelChange: (String) -> Unit,
    onRevoke: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                subjectIcon(subject),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = accessSubjectLabel(subject),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subject.isOwner) {
                Text(
                    text = stringResource(MochiR.string.access_owner),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
            } else {
                MochiIconButton(onClick = onRevoke) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(MochiR.string.access_revoke),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!subject.isOwner) {
            var expanded by remember { mutableStateOf(false) }
            MochiDropdownField(
                value = wikiAccessLevelLabel(subject.level),
                expanded = expanded,
                onExpandedChange = { open -> expanded = open },
                label = stringResource(R.string.wikis_access_change_level),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ACCESS_LEVEL_KEYS.forEach { level ->
                    MochiDropdownMenuItem(
                        text = { Text(wikiAccessLevelLabel(level)) },
                        onClick = {
                            expanded = false
                            if (level != subject.level) onLevelChange(level)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Add-access dialog: pick a user, a group, or the `*` / `+` wildcards under
 * Other, then a level.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccessDialog(
    viewModel: AccessTabViewModel,
    onDismiss: () -> Unit,
    onAdd: (subject: String, level: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    // 0 = User, 1 = Group, 2 = Other.
    var tab by remember { mutableStateOf(0) }
    var userQuery by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf("") }
    var selectedName by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("edit") }
    var levelExpanded by remember { mutableStateOf(false) }

    val authenticatedName = stringResource(R.string.wikis_access_subject_authenticated)
    val anyoneName = stringResource(R.string.wikis_access_subject_anyone)

    // Load groups on the first switch into the Group segment.
    LaunchedEffect(tab) {
        if (tab == 1 && state.groups.isEmpty()) {
            viewModel.loadGroups()
        }
    }

    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(MochiR.string.access_add_rule_title),
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = tab == 0,
                        onClick = { tab = 0; selectedSubject = ""; selectedName = "" },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        icon = { Icon(Icons.Outlined.Person, null, Modifier.size(18.dp)) },
                        label = { Text(stringResource(R.string.wikis_access_tab_users)) },
                    )
                    SegmentedButton(
                        selected = tab == 1,
                        onClick = { tab = 1; selectedSubject = ""; selectedName = "" },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        icon = { Icon(Icons.Default.Group, null, Modifier.size(18.dp)) },
                        label = { Text(stringResource(R.string.wikis_access_tab_groups)) },
                    )
                    SegmentedButton(
                        selected = tab == 2,
                        onClick = { tab = 2; selectedSubject = ""; selectedName = "" },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        icon = { Icon(Icons.Default.Public, null, Modifier.size(18.dp)) },
                        label = { Text(stringResource(R.string.wikis_access_tab_other)) },
                    )
                }

                Spacer(Modifier.height(16.dp))

                when (tab) {
                    0 -> {
                        Text(
                            text = stringResource(R.string.wikis_access_add_subject_label),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        MochiTextField(
                            value = userQuery,
                            onValueChange = { value ->
                                userQuery = value
                                selectedSubject = ""
                                selectedName = ""
                                viewModel.searchUsers(value)
                            },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (state.userSearchResults.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            MochiCard(
                                modifier = Modifier.fillMaxWidth(),
                                colors = mochiDialogCardColors(),
                            ) {
                                state.userSearchResults.take(6).forEach { user ->
                                    // The fingerprint is the subject the server
                                    // knows a person by, where one is resolved.
                                    val subject = user.fingerprint ?: user.id
                                    SubjectOption(
                                        icon = Icons.Outlined.Person,
                                        title = user.name.ifBlank { user.id },
                                        subtitle = null,
                                        selected = selectedSubject == subject,
                                        onClick = {
                                            selectedSubject = subject
                                            selectedName = user.name.ifBlank { user.id }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        Text(
                            text = stringResource(R.string.wikis_access_tab_groups),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        if (state.groups.isEmpty()) {
                            Text(
                                text = stringResource(R.string.wikis_access_no_groups),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        } else {
                            MochiCard(
                                modifier = Modifier.fillMaxWidth(),
                                colors = mochiDialogCardColors(),
                            ) {
                                state.groups.forEach { group ->
                                    // Groups are subjects prefixed with @.
                                    val subject = "@${group.id}"
                                    SubjectOption(
                                        icon = Icons.Outlined.Group,
                                        title = group.name,
                                        subtitle = group.id,
                                        selected = selectedSubject == subject,
                                        onClick = {
                                            selectedSubject = subject
                                            selectedName = group.name
                                        },
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        Text(
                            text = stringResource(R.string.wikis_access_select_rule),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        MochiCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = mochiDialogCardColors(),
                        ) {
                            SubjectOption(
                                icon = Icons.Outlined.Group,
                                title = authenticatedName,
                                subtitle = stringResource(
                                    R.string.wikis_access_authenticated_desc
                                ),
                                selected = selectedSubject == "+",
                                onClick = {
                                    selectedSubject = "+"
                                    selectedName = authenticatedName
                                },
                            )
                            SubjectOption(
                                icon = Icons.Outlined.Public,
                                title = anyoneName,
                                subtitle = stringResource(R.string.wikis_access_anyone_desc),
                                selected = selectedSubject == "*",
                                onClick = {
                                    selectedSubject = "*"
                                    selectedName = anyoneName
                                },
                            )
                        }
                    }
                }

                // Step 2: the level, once there is a subject to grant it to.
                if (selectedSubject.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(
                            R.string.wikis_access_selected,
                            selectedName.ifBlank { selectedSubject },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    MochiDropdownField(
                        value = wikiAccessLevelLabel(level),
                        expanded = levelExpanded,
                        onExpandedChange = { open -> levelExpanded = open },
                        label = stringResource(R.string.wikis_access_level_label),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ACCESS_LEVEL_KEYS.forEach { code ->
                            MochiDropdownMenuItem(
                                text = { Text(wikiAccessLevelLabel(code)) },
                                onClick = {
                                    level = code
                                    levelExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmText = stringResource(MochiR.string.common_add),
        onConfirm = { onAdd(selectedSubject, level) },
        confirmEnabled = selectedSubject.isNotBlank(),
        dismissText = stringResource(MochiR.string.common_cancel),
    )
}

/** A selectable subject row inside the add dialog's option list. */
@Composable
private fun SubjectOption(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    Color.Transparent
                }
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
