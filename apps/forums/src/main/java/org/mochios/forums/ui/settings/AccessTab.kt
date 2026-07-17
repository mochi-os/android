// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.settings

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import org.mochios.forums.R
import org.mochios.android.R as MochiR

/**
 * Access tab: an "Access Management" [Section] listing every access rule with
 * an inline level dropdown and revoke, plus a merged "Members" section for
 * searching and removing forum members. Styled to match feeds' Access tab: the
 * section header carries the add-rule action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessTab(viewModel: ForumSettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var memberQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadAccess()
        viewModel.loadMembers()
    }

    val filteredMembers = if (memberQuery.isBlank()) {
        uiState.members
    } else {
        uiState.members.filter { member -> member.name.contains(memberQuery, ignoreCase = true) }
    }

    // The server lists the grantable levels; "none" is ours to add, so a subject
    // can be denied outright without being revoked. Matches feeds, whose level
    // list ends in "none" too.
    val levels = uiState.accessLevels.ifEmpty { ACCESS_LEVEL_FALLBACK_KEYS } + "none"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Section(
            title = stringResource(R.string.forums_access_management),
            description = stringResource(R.string.forums_access_management_description),
            action = {
                // Outlined, primary-tinted — the same shape as the delete action
                // on the General tab, which tints itself error instead.
                OutlinedButton(onClick = { showAdd = true }) {
                    // The shared "Add rule" label; the dialog keeps the longer
                    // "Add access rule" as its title.
                    Text(stringResource(MochiR.string.access_add_rule))
                }
            },
        ) {
            if (uiState.accessRules.isEmpty()) {
                Text(
                    stringResource(R.string.forums_access_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                // Owner always sits at the top; the rest keep their order.
                val ordered = uiState.accessRules.sortedByDescending { rule -> rule.isOwner }
                ordered.forEach { rule ->
                    AccessRuleRow(
                        rule = rule,
                        levels = levels,
                        onLevelChange = { level -> viewModel.setAccess(rule.subject, level) },
                        onRevoke = { viewModel.revokeAccess(rule.subject) },
                    )
                }
            }
        }

        Section(title = stringResource(R.string.forums_tab_members)) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                OutlinedTextField(
                    value = memberQuery,
                    onValueChange = { value -> memberQuery = value },
                    placeholder = { Text(stringResource(R.string.forums_members_search)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (filteredMembers.isEmpty()) {
                    Text(
                        stringResource(R.string.forums_members_empty),
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
                            Spacer(Modifier.width(12.dp))
                            Text(
                                member.name.ifBlank { member.id },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { viewModel.removeMember(member.id) }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(
                                        MochiR.string.common_delete
                                    ),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddAccessDialog(
            viewModel = viewModel,
            levels = levels,
            onConfirm = { target, level ->
                viewModel.setAccess(target, level)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
}

/**
 * One access rule: subject icon and name on the first line, with the owner label
 * or a revoke button trailing it, and a full-width level dropdown underneath for
 * every subject except the owner.
 *
 * @param levels selectable levels, highest first, as the server reported them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccessRuleRow(
    rule: AccessRule,
    levels: List<String>,
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
                subjectIcon(rule),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
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
                onExpandedChange = { open -> expanded = open },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                OutlinedTextField(
                    value = accessLevelLabel(rule.operation),
                    onValueChange = { },
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
                    levels.forEach { level ->
                        DropdownMenuItem(
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
 * Display label for a subject, mapping the wildcard subjects to friendly names
 * and otherwise preferring the resolved name.
 */
@Composable
private fun accessSubjectLabel(rule: AccessRule): String = when (rule.subject) {
    "*" -> stringResource(R.string.forums_access_subject_anyone)
    "+" -> stringResource(R.string.forums_access_subject_authenticated)
    else -> rule.name?.takeIf { name -> name.isNotBlank() } ?: rule.subject
}

// Grant-independent level label. Callers pass the rule's *effective* level
// ("none" for a deny rule), so this maps each level — including "none" — to its
// label, and the inline level-change dropdown can reuse it per option. Levels
// are cumulative, so each label spells out everything the level allows.
@Composable
private fun accessLevelLabel(operation: String): String = when (operation) {
    "view" -> stringResource(R.string.forums_access_level_view_full)
    "vote" -> stringResource(R.string.forums_access_level_vote_full)
    "comment" -> stringResource(R.string.forums_access_level_comment_full)
    "post" -> stringResource(R.string.forums_access_level_post_full)
    "moderate" -> stringResource(R.string.forums_access_level_moderate_full)
    "none" -> stringResource(R.string.forums_access_level_none_full)
    else -> operation
}

// The forum's levels come from `-/access`; this stands in only if that load
// failed, so a dropdown is never empty. Highest-to-lowest, matching web's order.
private val ACCESS_LEVEL_FALLBACK_KEYS = listOf("moderate", "post", "comment", "vote", "view")

/**
 * Add-access dialog, styled to match feeds'. Step 1: pick the subject kind
 * (User / Group / Other) via a segmented control and select a concrete subject.
 * Step 2: once a subject is selected, choose the level and confirm with Add.
 *
 * The Other segment carries what web's `AccessDialog` calls the manual target —
 * the `*` and `+` wildcards as option rows, plus a free-text field for any other
 * entity id or `@group`. Users are searched live via the forums `users/search`
 * proxy; groups are fetched once on first switch into the Group segment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccessDialog(
    viewModel: ForumSettingsViewModel,
    levels: List<String>,
    onConfirm: (target: String, level: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    // 0 = User, 1 = Group, 2 = Other.
    var tab by remember { mutableStateOf(0) }
    var userQuery by remember { mutableStateOf("") }
    var manualTarget by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf("") }
    var selectedName by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("view") }
    var levelExpanded by remember { mutableStateOf(false) }

    val authenticatedName = stringResource(R.string.forums_access_subject_authenticated)
    val anyoneName = stringResource(R.string.forums_access_subject_anyone)

    // Load groups on first switch into the Group segment.
    LaunchedEffect(tab) {
        if (tab == 1 && uiState.groups.isEmpty()) {
            viewModel.loadGroups()
        }
    }

    // A typed target wins over a picked wildcard on the Other segment, so the
    // field and the option rows can't disagree about what gets added.
    val effectiveTarget = when {
        tab == 2 && manualTarget.isNotBlank() -> manualTarget.trim()
        else -> selectedSubject
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.forums_access_add)) },
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
                        label = { Text(stringResource(R.string.forums_access_tab_users)) },
                    )
                    SegmentedButton(
                        selected = tab == 1,
                        onClick = { tab = 1; selectedSubject = ""; selectedName = "" },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        icon = { Icon(Icons.Default.Group, null, Modifier.size(18.dp)) },
                        label = { Text(stringResource(R.string.forums_access_tab_groups)) },
                    )
                    SegmentedButton(
                        selected = tab == 2,
                        onClick = { tab = 2; selectedSubject = ""; selectedName = "" },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        icon = { Icon(Icons.Default.Public, null, Modifier.size(18.dp)) },
                        label = { Text(stringResource(R.string.forums_access_tab_other)) },
                    )
                }

                Spacer(Modifier.height(16.dp))

                when (tab) {
                    0 -> {
                        Text(
                            text = stringResource(R.string.forums_access_search_users),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.height(8.dp))
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
                        if (uiState.userSearchResults.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                uiState.userSearchResults.take(6).forEach { user ->
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
                    }

                    1 -> {
                        Text(
                            text = stringResource(R.string.forums_access_select_group),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        if (uiState.groups.isEmpty()) {
                            Text(
                                text = stringResource(R.string.forums_access_no_groups),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        } else {
                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                uiState.groups.forEach { group ->
                                    // Groups are subjects prefixed with @.
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
                            text = stringResource(R.string.forums_access_select_rule),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            SubjectOption(
                                icon = Icons.Default.Group,
                                title = authenticatedName,
                                subtitle = stringResource(
                                    R.string.forums_access_authenticated_desc
                                ),
                                selected = selectedSubject == "+",
                                onClick = {
                                    selectedSubject = "+"
                                    selectedName = authenticatedName
                                    manualTarget = ""
                                },
                            )
                            SubjectOption(
                                icon = Icons.Default.Public,
                                title = anyoneName,
                                subtitle = stringResource(R.string.forums_access_anyone_desc),
                                selected = selectedSubject == "*",
                                onClick = {
                                    selectedSubject = "*"
                                    selectedName = anyoneName
                                    manualTarget = ""
                                },
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = manualTarget,
                            onValueChange = { value ->
                                manualTarget = value
                                if (value.isNotBlank()) {
                                    selectedSubject = ""
                                    selectedName = ""
                                }
                            },
                            label = { Text(stringResource(R.string.forums_access_target)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(R.string.forums_access_target_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Step 2: level, shown once a subject is chosen.
                if (effectiveTarget.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(
                            R.string.forums_access_selected,
                            selectedName.ifBlank { effectiveTarget },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = levelExpanded,
                        onExpandedChange = { open -> levelExpanded = open },
                    ) {
                        OutlinedTextField(
                            value = accessLevelLabel(level),
                            onValueChange = { },
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
                            levels.forEach { code ->
                                DropdownMenuItem(
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
        confirmButton = {
            Button(
                onClick = { onConfirm(effectiveTarget, level) },
                enabled = effectiveTarget.isNotBlank(),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
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
