// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.BannerSection
import org.mochios.android.ui.components.DataChip
import org.mochios.android.ui.components.DeleteSection
import org.mochios.android.ui.components.IdentityRow
import org.mochios.android.ui.components.InlineTextEditor
import org.mochios.android.ui.components.Section
import org.mochios.android.ui.components.Truncate
import org.mochios.forums.R
import org.mochios.forums.model.Forum
import org.mochios.android.R as MochiR

@Composable
fun GeneralTab(
    viewModel: ForumSettingsViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    var bannerDraft by remember(uiState.forum.banner) { mutableStateOf(uiState.forum.banner) }

    // Banner, mode, account and prompts all come from the forum-information load;
    // the AI accounts and prompt defaults are chained off it in the ViewModel.

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ForumIdentitySection(
            forum = uiState.forum,
            editable = true,
            onRename = { name -> viewModel.rename(name) },
        )

        BannerSection(
            title = stringResource(R.string.forums_tab_banner),
            description = stringResource(R.string.forums_banner_description),
            hint = stringResource(R.string.forums_banner_placeholder),
            clearLabel = stringResource(R.string.forums_clear),
            draft = bannerDraft,
            stored = uiState.forum.banner,
            onDraftChange = { value -> bannerDraft = value },
            onSave = { value -> viewModel.saveBanner(value) }
        )

        DeleteSection(
            title = stringResource(R.string.forums_settings_delete),
            buttonLabel = stringResource(MochiR.string.common_delete),
            confirmTitle = stringResource(R.string.forums_settings_delete_title),
            confirmMessage = stringResource(R.string.forums_settings_delete_message),
            confirmLabel = stringResource(R.string.forums_settings_delete),
            onDelete = { viewModel.delete() }
        )
    }
}

/**
 * Identity card shared by the owner General tab and the read-only non-manager
 * view.
 */
@Composable
fun ForumIdentitySection(
    forum: Forum,
    editable: Boolean,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Section(
        title = stringResource(R.string.forums_settings_section_identity),
        modifier = modifier,
    ) {
        IdentityRow(label = stringResource(R.string.forums_settings_field_name)) {
            if (editable) {
                InlineTextEditor(
                    value = forum.name,
                    onSave = onRename,
                    editLabel = stringResource(R.string.forums_settings_name_edit_cd),
                    saveLabel = stringResource(R.string.forums_settings_save_name),
                    cancelLabel = stringResource(R.string.forums_settings_name_cancel_cd),
                    clearLabel = stringResource(R.string.forums_settings_name_clear_cd),
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(forum.name)
            }
        }
        IdentityRow(label = stringResource(R.string.forums_settings_field_entity_id)) {
            DataChip(value = forum.id, truncate = Truncate.MIDDLE)
        }
        if (forum.fingerprint.isNotBlank()) {
            IdentityRow(
                label = stringResource(R.string.forums_settings_field_fingerprint_label)
            ) {
                DataChip(value = forum.fingerprint, truncate = Truncate.MIDDLE)
            }
        }
        if (forum.server.isNotBlank()) {
            IdentityRow(label = stringResource(R.string.forums_settings_field_server)) {
                DataChip(value = forum.server, truncate = Truncate.MIDDLE)
            }
        }
    }
}
