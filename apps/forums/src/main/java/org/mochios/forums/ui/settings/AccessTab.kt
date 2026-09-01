// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.settings

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
import org.mochios.forums.R
import org.mochios.android.R as MochiR

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

@Composable
fun AccessTab(viewModel: ForumSettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var memberQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

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
        AccessRulesSection(
            rules = uiState.accessRules.map { rule -> rule.toSubjectRule() },
            levels = levels,
            defaultLevel = "view",
            levelLabel = { level -> accessLevelLabel(level) },
            labels = AccessLabels(
                sectionTitle = stringResource(R.string.forums_access_management),
                empty = stringResource(R.string.forums_access_empty),
                dialogTitle = stringResource(R.string.forums_access_add),
                tabUsers = stringResource(R.string.forums_access_tab_users),
                tabGroups = stringResource(R.string.forums_access_tab_groups),
                tabOther = stringResource(R.string.forums_access_tab_other),
                searchUsers = stringResource(R.string.forums_access_search_users),
                selectGroup = stringResource(R.string.forums_access_select_group),
                noGroups = stringResource(R.string.forums_access_no_groups),
                selectRule = stringResource(R.string.forums_access_select_rule),
                subjectAnyone = stringResource(R.string.forums_access_subject_anyone),
                subjectAuthenticated = stringResource(R.string.forums_access_subject_authenticated),
                anyoneDesc = stringResource(R.string.forums_access_anyone_desc),
                authenticatedDesc = stringResource(R.string.forums_access_authenticated_desc),
                selected = { name -> context.getString(R.string.forums_access_selected, name) }
            ),
            users = uiState.userSearchResults.map { user ->
                AccessCandidate(user.id, user.name.ifBlank { user.id })
            },
            groups = uiState.groups.map { group ->
                AccessCandidate("@${group.id}", group.name, group.id)
            },
            onSearchUsers = { query -> viewModel.searchUsers(query) },
            onLoadGroups = { viewModel.loadGroups() },
            onSetAccess = { subject, level -> viewModel.setAccess(subject, level) },
            onRevoke = { subject -> viewModel.revokeAccess(subject) }
        )

        Section(title = stringResource(R.string.forums_tab_members)) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                MochiTextField(
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
                            MochiIconButton(onClick = { viewModel.removeMember(member.id) }) {
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
}
