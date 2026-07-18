// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.mochios.android.ui.components.Section
import org.mochios.feeds.R
import org.mochios.android.R as MochiR

// Levels offered when changing/granting a rule's level, mirroring web's
// FEEDS_ACCESS_LEVELS select (highest grant first).
private val ACCESS_LEVEL_CHANGE_KEYS = listOf("comment", "react", "view", "none")

/**
 * Access tab: an "Access Management" card listing each subject (owner, groups,
 * authenticated users, anyone, individual users) with its access level, plus a
 * "Members" section for filtering, adding, and removing members. The owner row
 * is read-only; every other row exposes an inline level dropdown and a remove
 * button. Each section header carries its own add action.
 */
@Composable
fun AccessTab(
    viewModel: FeedSettingsViewModel
) {
    val accessRules by viewModel.accessRules.collectAsState()
    val isLoading by viewModel.isLoadingAccess.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val members by viewModel.members.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var memberQuery by remember { mutableStateOf("") }

    // Members are managed here, so only load them for a viewer who can.
    LaunchedEffect(permissions.manage) {
        if (permissions.manage) {
            viewModel.loadMembers()
        }
    }

    // Filtering is local to the loaded list — the same as forums, and it keeps
    // typing responsive without a round trip per keystroke.
    val filteredMembers = if (memberQuery.isBlank()) {
        members
    } else {
        members.filter { member -> member.name.contains(memberQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Section(
            title = stringResource(R.string.feeds_access_management),
            description = stringResource(R.string.feeds_access_management_desc),
            action = {
                // Outlined, primary-tinted — the same shape as the delete action
                // on the General tab, which tints itself error instead.
                OutlinedButton(onClick = { showAddDialog = true }) {
                    Text(stringResource(MochiR.string.access_add_rule))
                }
            },
        ) {
            when {
                isLoading && accessRules.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                accessRules.isEmpty() -> {
                    Text(
                        text = stringResource(MochiR.string.access_no_rules),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }

                else -> {
                    // Owner always sits at the top; the rest keep their order.
                    val ordered = accessRules.sortedByDescending { rule -> rule.isOwner }
                    ordered.forEach { rule ->
                        AccessRuleRow(
                            rule = rule,
                            onLevelChange = { level -> viewModel.setAccess(rule.subject, level) },
                            onRevoke = { viewModel.revokeAccess(rule.subject) },
                        )
                    }
                }
            }
        }

        if (permissions.manage) {
            Spacer(modifier = Modifier.height(16.dp))
            Section(
                title = stringResource(R.string.feeds_tab_members),
                headerAlignment = Alignment.CenterVertically,
                action = {
                    OutlinedButton(onClick = { showAddMemberDialog = true }) {
                        Text(stringResource(R.string.feeds_add_member))
                    }
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    OutlinedTextField(
                        value = memberQuery,
                        onValueChange = { value -> memberQuery = value },
                        placeholder = { Text(stringResource(R.string.feeds_members_search)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (filteredMembers.isEmpty()) {
                        Text(
                            text = stringResource(R.string.feeds_no_members),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        filteredMembers.forEach { member ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = member.name.ifBlank { member.id },
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { viewModel.removeMember(member.id) }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.feeds_remove),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
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

    if (showAddMemberDialog) {
        AddMemberDialog(
            viewModel = viewModel,
            onDismiss = { showAddMemberDialog = false },
            onAdd = { memberEntityId ->
                viewModel.addMember(memberEntityId)
                showAddMemberDialog = false
            },
        )
    }
}

/** Add-member dialog: search users and pick one to add to the feed. */
@Composable
private fun AddMemberDialog(
    viewModel: FeedSettingsViewModel,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf("") }
    var selectedName by remember { mutableStateOf("") }
    val searchResults by viewModel.userSearchResults.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feeds_add_member)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { value ->
                        query = value
                        selectedSubject = ""
                        selectedName = ""
                        viewModel.searchUsers(value)
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (searchResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        searchResults.take(6).forEach { user ->
                            SubjectOption(
                                icon = Icons.Default.Person,
                                title = user.name,
                                subtitle = null,
                                selected = selectedSubject == user.id,
                                onClick = {
                                    selectedSubject = user.id
                                    selectedName = user.name
                                },
                            )
                        }
                    }
                }
                if (selectedSubject.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.feeds_access_selected, selectedName),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(selectedSubject) },
                enabled = selectedSubject.isNotEmpty(),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(MochiR.string.common_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccessRuleRow(
    rule: AccessRule,
    onLevelChange: (String) -> Unit,
    onRevoke: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        // Subject line: icon + name, with the owner label or remove action
        // trailing it.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                subjectIcon(rule),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = accessSubjectLabel(rule),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (rule.isOwner) {
                Text(
                    text = stringResource(MochiR.string.access_owner),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
            } else {
                IconButton(onClick = onRevoke) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(MochiR.string.access_revoke),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Editable subjects get a full-width level dropdown on its own line,
        // matching the member filter field rather than indenting under the name.
        if (!rule.isOwner) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                OutlinedTextField(
                    value = feedsAccessLevelLabel(rule.operation),
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    ACCESS_LEVEL_CHANGE_KEYS.forEach { level ->
                        DropdownMenuItem(
                            text = { Text(feedsAccessLevelLabel(level)) },
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

/** Leading icon for an access subject: globe for anyone, group for groups and
 *  authenticated users, person otherwise. */
private fun subjectIcon(rule: AccessRule): ImageVector = when {
    rule.subject == "*" -> Icons.Default.Public
    rule.subject == "+" -> Icons.Default.Group
    rule.subject.startsWith("@") -> Icons.Default.Group
    else -> Icons.Default.Person
}

/** Display label for a subject, mapping the wildcard subjects to friendly
 *  names and otherwise preferring the resolved name. */
@Composable
private fun accessSubjectLabel(rule: AccessRule): String = when (rule.subject) {
    "*" -> stringResource(R.string.feeds_access_subject_anyone)
    "+" -> stringResource(R.string.feeds_access_subject_authenticated)
    else -> rule.name?.takeIf { it.isNotBlank() } ?: rule.subject
}

/** Hierarchical access-level label shown in the dropdowns. */
@Composable
private fun feedsAccessLevelLabel(level: String): String = when (level) {
    "comment" -> stringResource(R.string.feeds_access_level_comment_full)
    "react" -> stringResource(R.string.feeds_access_level_react_full)
    "view" -> stringResource(R.string.feeds_access_level_view_full)
    "none" -> stringResource(R.string.feeds_access_level_none_full)
    else -> level.replaceFirstChar { char -> char.uppercase() }
}

/**
 * Add-access dialog. Step 1: pick the subject kind (User / Group / Other) via a
 * segmented control and select a concrete subject. Step 2: once a subject is
 * selected, choose the permission level and confirm with Add.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccessDialog(
    viewModel: FeedSettingsViewModel,
    onDismiss: () -> Unit,
    onAdd: (subject: String, level: String) -> Unit,
) {
    // 0 = User, 1 = Group, 2 = Other.
    var tab by remember { mutableStateOf(0) }
    var userQuery by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf("") }
    var selectedName by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("comment") }
    var levelExpanded by remember { mutableStateOf(false) }
    val searchResults by viewModel.userSearchResults.collectAsState()
    val groups by viewModel.groups.collectAsState()

    val authenticatedName = stringResource(R.string.feeds_access_subject_authenticated)
    val anyoneName = stringResource(R.string.feeds_access_subject_anyone)

    LaunchedEffect(tab) {
        if (tab == 1 && groups.isEmpty()) {
            viewModel.loadGroups()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.feeds_access_add_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.feeds_access_add_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
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
                        icon = { Icon(Icons.Default.Person, null, Modifier.size(18.dp)) },
                        label = { Text(stringResource(R.string.feeds_user)) },
                    )
                    SegmentedButton(
                        selected = tab == 1,
                        onClick = { tab = 1; selectedSubject = ""; selectedName = "" },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        icon = { Icon(Icons.Default.Group, null, Modifier.size(18.dp)) },
                        label = { Text(stringResource(R.string.feeds_access_segment_group)) },
                    )
                    SegmentedButton(
                        selected = tab == 2,
                        onClick = { tab = 2; selectedSubject = ""; selectedName = "" },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        icon = { Icon(Icons.Default.Public, null, Modifier.size(18.dp)) },
                        label = { Text(stringResource(R.string.feeds_access_segment_other)) },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (tab) {
                    0 -> {
                        Text(
                            text = stringResource(R.string.feeds_access_search_users),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
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
                        if (searchResults.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                searchResults.take(6).forEach { user ->
                                    val subject = user.id
                                    SubjectOption(
                                        icon = Icons.Default.Person,
                                        title = user.name,
                                        subtitle = null,
                                        selected = selectedSubject == subject,
                                        onClick = {
                                            selectedSubject = subject
                                            selectedName = user.name
                                        },
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        Text(
                            text = stringResource(R.string.feeds_access_select_group),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (groups.isEmpty()) {
                            Text(
                                text = stringResource(R.string.feeds_access_no_groups),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        } else {
                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                groups.forEach { group ->
                                    val subject = "@${group.id}"
                                    SubjectOption(
                                        icon = Icons.Default.Group,
                                        title = group.name,
                                        subtitle = group.id.toString(),
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
                            text = stringResource(R.string.feeds_access_select_rule),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            SubjectOption(
                                icon = Icons.Default.Group,
                                title = authenticatedName,
                                subtitle = stringResource(R.string.feeds_access_authenticated_desc),
                                selected = selectedSubject == "+",
                                onClick = {
                                    selectedSubject = "+"
                                    selectedName = authenticatedName
                                },
                            )
                            SubjectOption(
                                icon = Icons.Default.Public,
                                title = anyoneName,
                                subtitle = stringResource(R.string.feeds_access_anyone_desc),
                                selected = selectedSubject == "*",
                                onClick = {
                                    selectedSubject = "*"
                                    selectedName = anyoneName
                                },
                            )
                        }
                    }
                }

                // Step 2: permission, shown once a subject is chosen.
                if (selectedSubject.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.feeds_access_selected, selectedName),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = levelExpanded,
                        onExpandedChange = { levelExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = feedsAccessLevelLabel(level),
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = levelExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(
                            expanded = levelExpanded,
                            onDismissRequest = { levelExpanded = false },
                        ) {
                            ACCESS_LEVEL_CHANGE_KEYS.forEach { lvl ->
                                DropdownMenuItem(
                                    text = { Text(feedsAccessLevelLabel(lvl)) },
                                    onClick = {
                                        level = lvl
                                        levelExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(selectedSubject, level) },
                enabled = selectedSubject.isNotEmpty(),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(MochiR.string.common_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MochiR.string.common_cancel))
            }
        },
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
        Spacer(modifier = Modifier.width(12.dp))
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
