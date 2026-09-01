// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.ui.components.AccessCandidate
import org.mochios.android.ui.components.AccessLabels
import org.mochios.android.ui.components.AccessRulesSection
import org.mochios.android.ui.components.AccessSubjectRule
import org.mochios.wikis.R
import org.mochios.android.R as MochiR

private val ACCESS_LEVEL_KEYS = listOf("edit", "view", "none")

@Composable
private fun wikiAccessLevelLabel(level: String): String = when (level) {
    "edit" -> stringResource(R.string.wikis_access_level_edit)
    "view" -> stringResource(R.string.wikis_access_level_view)
    "none" -> stringResource(R.string.wikis_access_level_none)
    "manage" -> stringResource(MochiR.string.access_level_manage)
    else -> level.replaceFirstChar { first -> first.uppercase() }
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
    val context = LocalContext.current

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
        AccessRulesSection(
            rules = state.subjects.map { subject ->
                AccessSubjectRule(
                    subject = subject.subject,
                    level = subject.level,
                    name = subject.name,
                    isOwner = subject.isOwner
                )
            },
            levels = ACCESS_LEVEL_KEYS,
            defaultLevel = "edit",
            levelLabel = { level -> wikiAccessLevelLabel(level) },
            labels = AccessLabels(
                sectionTitle = stringResource(R.string.wikis_settings_tab_access),
                empty = stringResource(MochiR.string.access_no_rules),
                dialogTitle = stringResource(MochiR.string.access_add_rule_title),
                rowRoleLabel = stringResource(R.string.wikis_access_change_level),
                dialogRoleLabel = stringResource(R.string.wikis_access_level_label),
                tabUsers = stringResource(R.string.wikis_access_tab_users),
                tabGroups = stringResource(R.string.wikis_access_tab_groups),
                tabOther = stringResource(R.string.wikis_access_tab_other),
                searchUsers = stringResource(R.string.wikis_access_add_subject_label),
                selectGroup = stringResource(R.string.wikis_access_tab_groups),
                noGroups = stringResource(R.string.wikis_access_no_groups),
                selectRule = stringResource(R.string.wikis_access_select_rule),
                subjectAnyone = stringResource(R.string.wikis_access_subject_anyone),
                subjectAuthenticated = stringResource(R.string.wikis_access_subject_authenticated),
                anyoneDesc = stringResource(R.string.wikis_access_anyone_desc),
                authenticatedDesc = stringResource(R.string.wikis_access_authenticated_desc),
                selected = { name -> context.getString(R.string.wikis_access_selected, name) }
            ),
            // The fingerprint is the subject the server knows a person by,
            // where one is resolved.
            users = state.userSearchResults.map { user ->
                AccessCandidate(user.fingerprint ?: user.id, user.name.ifBlank { user.id })
            },
            groups = state.groups.map { group ->
                AccessCandidate("@${group.id}", group.name, group.id)
            },
            onSearchUsers = { query -> viewModel.searchUsers(query) },
            onLoadGroups = { viewModel.loadGroups() },
            onSetAccess = { subject, level -> viewModel.setAccess(subject, level) },
            onRevoke = { subject -> viewModel.revokeAccess(subject) }
        )
    }
}
