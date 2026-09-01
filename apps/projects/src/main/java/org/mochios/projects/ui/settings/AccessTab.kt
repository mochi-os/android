// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.settings

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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.components.Section
import org.mochios.android.ui.components.toSubjectRule
import org.mochios.projects.R

// Levels offered when changing a rule's level, mirroring web's PROJECT_ACCESS_LEVELS
// select (no "owner" - ownership isn't reassigned this way).
private val ACCESS_LEVEL_KEYS = listOf("design", "write", "comment", "view", "none")

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

@Composable
fun AccessTab(
    uiState: ProjectSettingsUiState,
    viewModel: ProjectSettingsViewModel
) {
    var peopleQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

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
        AccessRulesSection(
            rules = uiState.accessRules.map { rule -> rule.toSubjectRule() },
            levels = ACCESS_LEVEL_KEYS,
            defaultLevel = "view",
            levelLabel = { level -> accessLevelLabel(level) },
            labels = AccessLabels(
                sectionTitle = stringResource(R.string.projects_settings_section_access),
                empty = stringResource(org.mochios.android.R.string.access_no_rules),
                dialogTitle = stringResource(org.mochios.android.R.string.access_add_rule_title),
                rowRoleLabel = stringResource(R.string.projects_access_select_role),
                dialogRoleLabel = stringResource(R.string.projects_access_select_role),
                tabUsers = stringResource(R.string.projects_access_tab_users),
                tabGroups = stringResource(R.string.projects_access_tab_groups),
                tabOther = stringResource(R.string.projects_access_tab_other),
                searchUsers = stringResource(R.string.projects_access_search_users),
                selectGroup = stringResource(R.string.projects_access_select_group),
                noGroups = stringResource(R.string.projects_access_no_groups),
                selectRule = stringResource(R.string.projects_access_select_rule),
                subjectAnyone = stringResource(R.string.projects_access_subject_anyone),
                subjectAuthenticated = stringResource(R.string.projects_access_subject_authenticated),
                anyoneDesc = stringResource(R.string.projects_access_anyone_desc),
                authenticatedDesc = stringResource(R.string.projects_access_authenticated_desc),
                selected = { name -> context.getString(R.string.projects_access_selected, name) }
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
}
