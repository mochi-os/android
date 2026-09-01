// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.settings

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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.AccessCandidate
import org.mochios.android.ui.components.AccessLabels
import org.mochios.android.ui.components.AccessRulesSection
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.components.Section
import org.mochios.android.ui.components.toSubjectRule
import org.mochios.feeds.R
import org.mochios.android.R as MochiR

// Levels offered when changing/granting a rule's level, mirroring web's
// FEEDS_ACCESS_LEVELS select (highest grant first).
private val ACCESS_LEVEL_CHANGE_KEYS = listOf("comment", "react", "view", "none")

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
 * Access tab: access rules with inline level dropdowns, plus a Members section.
 * Subscription is subscriber-initiated, so there is no owner-side add - only
 * removal.
 */
@Composable
fun AccessTab(
    viewModel: FeedSettingsViewModel
) {
    val accessRules by viewModel.accessRules.collectAsState()
    val isLoading by viewModel.isLoadingAccess.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val members by viewModel.members.collectAsState()
    val searchResults by viewModel.userSearchResults.collectAsState()
    val groups by viewModel.groups.collectAsState()
    var memberQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

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
        AccessRulesSection(
            rules = accessRules.map { rule -> rule.toSubjectRule() },
            levels = ACCESS_LEVEL_CHANGE_KEYS,
            defaultLevel = "comment",
            levelLabel = { level -> feedsAccessLevelLabel(level) },
            labels = AccessLabels(
                sectionTitle = stringResource(R.string.feeds_access_management),
                empty = stringResource(MochiR.string.access_no_rules),
                dialogTitle = stringResource(R.string.feeds_access_add_title),
                dialogSubtitle = stringResource(R.string.feeds_access_add_subtitle),
                tabUsers = stringResource(R.string.feeds_user),
                tabGroups = stringResource(R.string.feeds_access_segment_group),
                tabOther = stringResource(R.string.feeds_access_segment_other),
                searchUsers = stringResource(R.string.feeds_access_search_users),
                selectGroup = stringResource(R.string.feeds_access_select_group),
                noGroups = stringResource(R.string.feeds_access_no_groups),
                selectRule = stringResource(R.string.feeds_access_select_rule),
                subjectAnyone = stringResource(R.string.feeds_access_subject_anyone),
                subjectAuthenticated = stringResource(R.string.feeds_access_subject_authenticated),
                anyoneDesc = stringResource(R.string.feeds_access_anyone_desc),
                authenticatedDesc = stringResource(R.string.feeds_access_authenticated_desc),
                selected = { name -> context.getString(R.string.feeds_access_selected, name) }
            ),
            users = searchResults.map { user ->
                AccessCandidate(user.id, user.name.ifBlank { user.id })
            },
            groups = groups.map { group ->
                AccessCandidate("@${group.id}", group.name, group.id)
            },
            onSearchUsers = { query -> viewModel.searchUsers(query) },
            onLoadGroups = { viewModel.loadGroups() },
            onSetAccess = { subject, level -> viewModel.setAccess(subject, level) },
            onRevoke = { subject -> viewModel.revokeAccess(subject) },
            isLoading = isLoading
        )

        if (permissions.manage) {
            Spacer(modifier = Modifier.height(16.dp))
            Section(
                title = stringResource(R.string.feeds_tab_members),
                headerAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    MochiTextField(
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
                                MochiIconButton(onClick = { viewModel.removeMember(member.id) }) {
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
}
