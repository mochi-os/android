// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
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
import org.mochios.android.model.AccessRule
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.components.Section
import org.mochios.projects.R
import org.mochios.android.R as MochiR

private val ACCESS_LEVEL_KEYS = listOf("owner", "design", "write", "comment", "view", "none")

// Levels offered when changing an existing rule's level inline, mirroring web's
// inline level select (no "owner" — ownership isn't reassigned this way).
private val ACCESS_LEVEL_CHANGE_KEYS = ACCESS_LEVEL_KEYS.filter { key -> key != "owner" }

// Descriptive role label for a level, matching web's role select. Levels are
// cumulative, so each label spells out everything the role allows.
@Composable
private fun accessLevelLabel(value: String): String = when (value) {
    "owner" -> stringResource(R.string.projects_access_level_owner)
    "design" -> stringResource(R.string.projects_access_role_design)
    "write" -> stringResource(R.string.projects_access_role_write)
    "comment" -> stringResource(R.string.projects_access_role_comment)
    "view" -> stringResource(R.string.projects_access_role_view)
    "none" -> stringResource(R.string.projects_access_role_none)
    else -> value
}

/**
 * Display label for a subject, mapping the wildcard subjects to friendly names
 * and otherwise preferring the resolved name. Mirrors the forums Access tab.
 */
@Composable
private fun accessSubjectLabel(rule: AccessRule): String = when (rule.subject) {
    "*" -> stringResource(R.string.projects_access_subject_anyone)
    "+" -> stringResource(R.string.projects_access_subject_authenticated)
    else -> rule.name?.takeIf { name -> name.isNotBlank() } ?: rule.subject
}

// Sort key placing the owner first, then authenticated users, then anyone, then
// every other subject in its existing order.
private fun subjectRank(rule: AccessRule): Int = when {
    rule.isOwner -> 0
    rule.subject == "+" -> 1
    rule.subject == "*" -> 2
    else -> 3
}

/**
 * Access tab: an "Access management" [Section] listing every access rule with an
 * inline level dropdown and revoke, plus a merged "People" section listing every
 * person with access. Styled to match the forums Access tab — the section header
 * carries the add-rule action instead of a floating button.
 */
@Composable
fun AccessTab(
    uiState: ProjectSettingsUiState,
    viewModel: ProjectSettingsViewModel
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var peopleQuery by remember { mutableStateOf("") }

    val filteredPeople = if (peopleQuery.isBlank()) {
        uiState.people
    } else {
        uiState.people.filter { person -> person.name.contains(peopleQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Section(
            title = stringResource(R.string.projects_settings_section_access),
            headerAlignment = Alignment.CenterVertically,
            action = {
                OutlinedButton(onClick = { showAddDialog = true }) {
                    Text(stringResource(MochiR.string.access_add_rule))
                }
            }
        ) {
            if (uiState.accessRules.isEmpty()) {
                Text(
                    stringResource(MochiR.string.access_no_rules),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                // Owner, then authenticated users, then anyone, then the rest.
                val ordered = uiState.accessRules.sortedBy { rule -> subjectRank(rule) }
                ordered.forEach { rule ->
                    AccessRuleRow(
                        rule = rule,
                        levels = ACCESS_LEVEL_CHANGE_KEYS,
                        onLevelChange = { level -> viewModel.setAccess(rule.subject, level) },
                        onRevoke = { viewModel.revokeAccess(rule.subject) }
                    )
                }
            }
        }

        Section(title = stringResource(R.string.projects_settings_tab_people)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                MochiTextField(
                    value = peopleQuery,
                    onValueChange = { value -> peopleQuery = value },
                    placeholder = { Text(stringResource(R.string.projects_people_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (filteredPeople.isEmpty()) {
                    Text(
                        stringResource(R.string.projects_people_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    filteredPeople.forEach { person ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                person.name.ifBlank { person.id },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddAccessDialog(
            viewModel = viewModel,
            onConfirm = { subject, level ->
                viewModel.setAccess(subject, level)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

/**
 * One access rule: subject icon and name on the first line, with the owner label
 * or a revoke button trailing it, and a full-width level dropdown underneath for
 * every subject except the owner. Mirrors the forums Access tab row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccessRuleRow(
    rule: AccessRule,
    levels: List<String>,
    onLevelChange: (String) -> Unit,
    onRevoke: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                subjectIcon(rule),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = accessSubjectLabel(rule),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (rule.isOwner) {
                Text(
                    text = stringResource(MochiR.string.access_owner),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                )
            } else {
                IconButton(onClick = onRevoke) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(MochiR.string.access_revoke),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (!rule.isOwner) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { open -> expanded = open },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                MochiTextField(
                    value = accessLevelLabel(rule.operation),
                    onValueChange = { },
                    readOnly = true,
                    singleLine = true,
                    label = { Text(stringResource(R.string.projects_access_select_role)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    levels.forEach { level ->
                        MochiDropdownMenuItem(
                            text = { Text(accessLevelLabel(level)) },
                            onClick = {
                                expanded = false
                                if (level != rule.operation) onLevelChange(level)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Leading icon for an access subject: globe for anyone, group for groups and
 * authenticated users, person otherwise.
 */
private fun subjectIcon(rule: AccessRule): ImageVector = when {
    rule.subject == "*" -> Icons.Default.Public
    rule.subject == "+" -> Icons.Default.Group
    rule.subject.startsWith("@") -> Icons.Default.Group
    else -> Icons.Default.Person
}

/**
 * Add-access dialog, styled to match the forums dialog. Step 1: pick the subject
 * kind (User / Group / Other) via a segmented control and select a concrete
 * subject. Step 2: once a subject is selected, choose the role and confirm.
 *
 * The Other segment carries the `*` and `+` wildcards as option rows. Users are
 * searched live; groups are fetched once on the first switch into the Group
 * segment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccessDialog(
    viewModel: ProjectSettingsViewModel,
    onConfirm: (target: String, level: String) -> Unit,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    // 0 = User, 1 = Group, 2 = Other.
    var tab by remember { mutableStateOf(0) }
    var userQuery by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf("") }
    var selectedName by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("view") }
    var levelExpanded by remember { mutableStateOf(false) }

    val authenticatedName = stringResource(R.string.projects_access_subject_authenticated)
    val anyoneName = stringResource(R.string.projects_access_subject_anyone)

    // Load groups on the first switch into the Group segment.
    LaunchedEffect(tab) {
        if (tab == 1 && uiState.groups.isEmpty()) {
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
                    .verticalScroll(rememberScrollState())
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = tab == 0,
                        onClick = { tab = 0; selectedSubject = ""; selectedName = "" },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        icon = { Icon(Icons.Default.Person, null, Modifier.size(18.dp)) },
                        label = { Text(stringResource(R.string.projects_access_tab_users)) }
                    )
                    SegmentedButton(
                        selected = tab == 1,
                        onClick = { tab = 1; selectedSubject = ""; selectedName = "" },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        icon = { Icon(Icons.Default.Group, null, Modifier.size(18.dp)) },
                        label = { Text(stringResource(R.string.projects_access_tab_groups)) }
                    )
                    SegmentedButton(
                        selected = tab == 2,
                        onClick = { tab = 2; selectedSubject = ""; selectedName = "" },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        icon = { Icon(Icons.Default.Public, null, Modifier.size(18.dp)) },
                        label = { Text(stringResource(R.string.projects_access_tab_other)) }
                    )
                }

                Spacer(Modifier.height(16.dp))

                when (tab) {
                    0 -> {
                        Text(
                            text = stringResource(R.string.projects_access_search_users),
                            style = MaterialTheme.typography.labelLarge
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
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (uiState.userSearchResults.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Card(modifier = Modifier.fillMaxWidth()) {
                                uiState.userSearchResults.take(6).forEach { user ->
                                    SubjectOption(
                                        icon = Icons.Default.Person,
                                        title = user.name.ifBlank { user.id },
                                        subtitle = null,
                                        selected = selectedSubject == user.id,
                                        onClick = {
                                            selectedSubject = user.id
                                            selectedName = user.name.ifBlank { user.id }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        Text(
                            text = stringResource(R.string.projects_access_select_group),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        if (uiState.groups.isEmpty()) {
                            Text(
                                text = stringResource(R.string.projects_access_no_groups),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                uiState.groups.forEach { group ->
                                    // Groups are subjects prefixed with @.
                                    val subject = "@${group.id}"
                                    SubjectOption(
                                        icon = Icons.Default.Group,
                                        title = group.name,
                                        subtitle = group.id,
                                        selected = selectedSubject == subject,
                                        onClick = {
                                            selectedSubject = subject
                                            selectedName = group.name
                                        }
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        Text(
                            text = stringResource(R.string.projects_access_select_rule),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            SubjectOption(
                                icon = Icons.Default.Group,
                                title = authenticatedName,
                                subtitle = stringResource(R.string.projects_access_authenticated_desc),
                                selected = selectedSubject == "+",
                                onClick = {
                                    selectedSubject = "+"
                                    selectedName = authenticatedName
                                }
                            )
                            SubjectOption(
                                icon = Icons.Default.Public,
                                title = anyoneName,
                                subtitle = stringResource(R.string.projects_access_anyone_desc),
                                selected = selectedSubject == "*",
                                onClick = {
                                    selectedSubject = "*"
                                    selectedName = anyoneName
                                }
                            )
                        }
                    }
                }

                // Step 2: role, shown once a subject is chosen.
                if (selectedSubject.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(
                            R.string.projects_access_selected,
                            selectedName.ifBlank { selectedSubject }
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = levelExpanded,
                        onExpandedChange = { open -> levelExpanded = open }
                    ) {
                        MochiTextField(
                            value = accessLevelLabel(level),
                            onValueChange = { },
                            readOnly = true,
                            singleLine = true,
                            label = { Text(stringResource(R.string.projects_access_select_role)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = levelExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = levelExpanded,
                            onDismissRequest = { levelExpanded = false }
                        ) {
                            ACCESS_LEVEL_CHANGE_KEYS.forEach { code ->
                                MochiDropdownMenuItem(
                                    text = { Text(accessLevelLabel(code)) },
                                    onClick = {
                                        level = code
                                        levelExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmText = stringResource(MochiR.string.common_add),
        onConfirm = { onConfirm(selectedSubject, level) },
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
    onClick: () -> Unit
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
