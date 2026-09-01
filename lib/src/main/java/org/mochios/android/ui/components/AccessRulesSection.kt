// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import org.mochios.android.R
import org.mochios.android.model.AccessRule

/**
 * One access rule as the settings tabs show it: who holds the grant, and what
 * they may do. Every feature stores more than this; only these four fields
 * reach the shared UI.
 *
 * @property subject Subject the rule grants to - a user id, `@group`, `+` for
 *   any signed-in visitor, or `*` for anyone.
 * @property level Operation the subject is granted, in the feature's own
 *   vocabulary.
 * @property name Resolved display name, when the server supplied one.
 * @property isOwner Whether this subject owns the resource.
 */
data class AccessSubjectRule(
    val subject: String,
    val level: String,
    val name: String? = null,
    val isOwner: Boolean = false
)

/** The shared shape of an [AccessRule], whose level lives in `operation`. */
fun AccessRule.toSubjectRule() = AccessSubjectRule(
    subject = subject,
    level = operation,
    name = name,
    isOwner = isOwner
)

/**
 * Someone the add-access dialog can grant to - a searched user, or a group.
 *
 * @property subject Subject string sent to the server, already prefixed the way
 *   the feature expects (`@id` for a group, a fingerprint or id for a user).
 * @property name Display name of the candidate.
 * @property detail Optional second line, such as a group's id.
 */
data class AccessCandidate(
    val subject: String,
    val name: String,
    val detail: String? = null
)

/**
 * Wording for [AccessRulesSection]. Each feature keeps its own translated
 * strings, so the caller resolves them and passes them in rather than the
 * library owning a second copy of every locale.
 *
 * @property sectionTitle Heading of the access section.
 * @property empty Placeholder shown when no rules exist.
 * @property dialogTitle Title of the add dialog.
 * @property dialogSubtitle Optional second line under the dialog title.
 * @property rowRoleLabel Optional label on a rule row's level picker.
 * @property dialogRoleLabel Optional label on the dialog's level picker.
 * @property tabUsers Label of the dialog's user segment.
 * @property tabGroups Label of the dialog's group segment.
 * @property tabOther Label of the dialog's wildcard segment.
 * @property searchUsers Heading above the user search field.
 * @property selectGroup Heading above the group list.
 * @property noGroups Placeholder when the feature has no groups.
 * @property selectRule Heading above the wildcard choices.
 * @property subjectAnyone Friendly name of the `*` subject.
 * @property subjectAuthenticated Friendly name of the `+` subject.
 * @property anyoneDesc Description under the `*` choice.
 * @property authenticatedDesc Description under the `+` choice.
 * @property selected Confirmation line naming the chosen subject.
 */
data class AccessLabels(
    val sectionTitle: String,
    val empty: String,
    val dialogTitle: String,
    val tabUsers: String,
    val tabGroups: String,
    val tabOther: String,
    val searchUsers: String,
    val selectGroup: String,
    val noGroups: String,
    val selectRule: String,
    val subjectAnyone: String,
    val subjectAuthenticated: String,
    val anyoneDesc: String,
    val authenticatedDesc: String,
    val selected: (subjectName: String) -> String,
    val dialogSubtitle: String? = null,
    val rowRoleLabel: String? = null,
    val dialogRoleLabel: String? = null
)

/**
 * The access-control section shared by every feature's settings screen: the
 * current rules with an inline level picker and a revoke button, and the dialog
 * that adds one. Rules are ordered owner first, then any signed-in visitor,
 * then anyone, then the rest in the order given.
 *
 * The feature owns its access vocabulary - CRM grants design and write, wikis
 * grant edit - so [levels] and [levelLabel] come from the caller, as does the
 * roster section that usually sits below this one.
 *
 * @param rules Rules to list, in any order.
 * @param levels Levels offered by both level pickers, highest grant first.
 * @param defaultLevel Level the add dialog starts on.
 * @param levelLabel Resolves a level to the feature's own wording.
 * @param labels Feature-specific wording.
 * @param users Candidates matching the dialog's current user search.
 * @param groups Groups the feature offers, loaded via [onLoadGroups].
 * @param onSearchUsers Called as the user search text changes.
 * @param onLoadGroups Called the first time the group segment is opened.
 * @param onSetAccess Called with a subject and the level to grant it.
 * @param onRevoke Called with the subject whose rule should be dropped.
 * @param isLoading Whether a first load is still in flight; shows a spinner in
 *   place of the empty text.
 */
@Composable
fun AccessRulesSection(
    rules: List<AccessSubjectRule>,
    levels: List<String>,
    defaultLevel: String,
    levelLabel: @Composable (String) -> String,
    labels: AccessLabels,
    users: List<AccessCandidate>,
    groups: List<AccessCandidate>,
    onSearchUsers: (String) -> Unit,
    onLoadGroups: () -> Unit,
    onSetAccess: (String, String) -> Unit,
    onRevoke: (String) -> Unit,
    isLoading: Boolean = false
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Section(
        title = labels.sectionTitle,
        headerAlignment = Alignment.CenterVertically,
        action = {
            MochiOutlinedButton(onClick = { showAddDialog = true }) {
                Text(stringResource(R.string.access_add_rule))
            }
        }
    ) {
        when {
            isLoading && rules.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            rules.isEmpty() -> {
                Text(
                    labels.empty,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            else -> {
                rules.sortedBy { rule -> subjectRank(rule) }.forEach { rule ->
                    AccessRuleRow(
                        rule = rule,
                        levels = levels,
                        levelLabel = levelLabel,
                        roleLabel = labels.rowRoleLabel,
                        subjectLabel = subjectLabel(rule, labels),
                        onLevelChange = { level -> onSetAccess(rule.subject, level) },
                        onRevoke = { onRevoke(rule.subject) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddAccessDialog(
            levels = levels,
            defaultLevel = defaultLevel,
            levelLabel = levelLabel,
            labels = labels,
            users = users,
            groups = groups,
            onSearchUsers = onSearchUsers,
            onLoadGroups = onLoadGroups,
            onConfirm = { subject, level ->
                onSetAccess(subject, level)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

/**
 * Sort key placing the owner first, then authenticated users, then anyone, then
 * every other subject in its existing order.
 */
private fun subjectRank(rule: AccessSubjectRule): Int = when {
    rule.isOwner -> 0
    rule.subject == "+" -> 1
    rule.subject == "*" -> 2
    else -> 3
}

/**
 * Display label for a subject, mapping the wildcard subjects to friendly names
 * and otherwise preferring the resolved name.
 */
private fun subjectLabel(rule: AccessSubjectRule, labels: AccessLabels): String = when (rule.subject) {
    "*" -> labels.subjectAnyone
    "+" -> labels.subjectAuthenticated
    else -> rule.name?.takeIf { name -> name.isNotBlank() } ?: rule.subject
}

/**
 * Leading icon for an access subject: globe for anyone, group for groups and
 * authenticated users, person otherwise.
 */
private fun subjectIcon(rule: AccessSubjectRule): ImageVector = when {
    rule.subject == "*" -> Icons.Default.Public
    rule.subject == "+" -> Icons.Default.Group
    rule.subject.startsWith("@") -> Icons.Default.Group
    else -> Icons.Default.Person
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccessRuleRow(
    rule: AccessSubjectRule,
    levels: List<String>,
    levelLabel: @Composable (String) -> String,
    roleLabel: String?,
    subjectLabel: String,
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
                text = subjectLabel,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (rule.isOwner) {
                Text(
                    text = stringResource(R.string.access_owner),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                )
            } else {
                MochiIconButton(onClick = onRevoke) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.access_revoke),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (!rule.isOwner) {
            var expanded by remember { mutableStateOf(false) }
            MochiDropdownField(
                value = levelLabel(rule.level),
                expanded = expanded,
                onExpandedChange = { value -> expanded = value },
                label = roleLabel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                levels.forEach { level ->
                    MochiDropdownMenuItem(
                        text = { Text(levelLabel(level)) },
                        onClick = {
                            expanded = false
                            if (level != rule.level) onLevelChange(level)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Add-access dialog: pick a subject (user search, group, or the `*`/`+`
 * wildcards under Other), then a level.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccessDialog(
    levels: List<String>,
    defaultLevel: String,
    levelLabel: @Composable (String) -> String,
    labels: AccessLabels,
    users: List<AccessCandidate>,
    groups: List<AccessCandidate>,
    onSearchUsers: (String) -> Unit,
    onLoadGroups: () -> Unit,
    onConfirm: (target: String, level: String) -> Unit,
    onDismiss: () -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    var userQuery by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf("") }
    var selectedName by remember { mutableStateOf("") }
    var level by remember { mutableStateOf(defaultLevel) }
    var levelExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(tab) {
        if (tab == 1 && groups.isEmpty()) {
            onLoadGroups()
        }
    }

    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = labels.dialogTitle,
        subtitle = labels.dialogSubtitle,
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
                        icon = { Icon(Icons.Outlined.Person, null, Modifier.size(18.dp)) },
                        label = { Text(labels.tabUsers) }
                    )
                    SegmentedButton(
                        selected = tab == 1,
                        onClick = { tab = 1; selectedSubject = ""; selectedName = "" },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        icon = { Icon(Icons.Default.Group, null, Modifier.size(18.dp)) },
                        label = { Text(labels.tabGroups) }
                    )
                    SegmentedButton(
                        selected = tab == 2,
                        onClick = { tab = 2; selectedSubject = ""; selectedName = "" },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        icon = { Icon(Icons.Default.Public, null, Modifier.size(18.dp)) },
                        label = { Text(labels.tabOther) }
                    )
                }

                Spacer(Modifier.height(16.dp))

                when (tab) {
                    0 -> {
                        Text(
                            text = labels.searchUsers,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        MochiTextField(
                            value = userQuery,
                            onValueChange = { value ->
                                userQuery = value
                                selectedSubject = ""
                                selectedName = ""
                                onSearchUsers(value)
                            },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (users.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            MochiCard(
                                modifier = Modifier.fillMaxWidth(),
                                colors = mochiDialogCardColors()
                            ) {
                                users.take(6).forEach { user ->
                                    SubjectOption(
                                        icon = Icons.Outlined.Person,
                                        title = user.name,
                                        subtitle = user.detail,
                                        selected = selectedSubject == user.subject,
                                        onClick = {
                                            selectedSubject = user.subject
                                            selectedName = user.name
                                        }
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        Text(
                            text = labels.selectGroup,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        if (groups.isEmpty()) {
                            Text(
                                text = labels.noGroups,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            MochiCard(
                                modifier = Modifier.fillMaxWidth(),
                                colors = mochiDialogCardColors()
                            ) {
                                groups.forEach { group ->
                                    SubjectOption(
                                        icon = Icons.Outlined.Group,
                                        title = group.name,
                                        subtitle = group.detail,
                                        selected = selectedSubject == group.subject,
                                        onClick = {
                                            selectedSubject = group.subject
                                            selectedName = group.name
                                        }
                                    )
                                }
                            }
                        }
                    }

                    else -> {
                        Text(
                            text = labels.selectRule,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        MochiCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = mochiDialogCardColors()
                        ) {
                            SubjectOption(
                                icon = Icons.Outlined.Group,
                                title = labels.subjectAuthenticated,
                                subtitle = labels.authenticatedDesc,
                                selected = selectedSubject == "+",
                                onClick = {
                                    selectedSubject = "+"
                                    selectedName = labels.subjectAuthenticated
                                }
                            )
                            SubjectOption(
                                icon = Icons.Outlined.Public,
                                title = labels.subjectAnyone,
                                subtitle = labels.anyoneDesc,
                                selected = selectedSubject == "*",
                                onClick = {
                                    selectedSubject = "*"
                                    selectedName = labels.subjectAnyone
                                }
                            )
                        }
                    }
                }

                if (selectedSubject.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = labels.selected(selectedName.ifBlank { selectedSubject }),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    MochiDropdownField(
                        value = levelLabel(level),
                        expanded = levelExpanded,
                        onExpandedChange = { value -> levelExpanded = value },
                        label = labels.dialogRoleLabel,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        levels.forEach { code ->
                            MochiDropdownMenuItem(
                                text = { Text(levelLabel(code)) },
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
        confirmText = stringResource(R.string.common_add),
        onConfirm = { onConfirm(selectedSubject, level) },
        confirmEnabled = selectedSubject.isNotBlank(),
        dismissText = stringResource(R.string.common_cancel),
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
