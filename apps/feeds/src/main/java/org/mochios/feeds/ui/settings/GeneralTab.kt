// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.settings

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
import org.mochios.android.ui.components.ConfirmActionSection
import org.mochios.android.ui.components.IdentityRow
import org.mochios.android.ui.components.InlineTextEditor
import org.mochios.android.ui.components.Section
import org.mochios.android.ui.components.Truncate
import org.mochios.feeds.R
import org.mochios.feeds.model.Feed
import org.mochios.android.R as MochiR

@Composable
fun GeneralTab(
    viewModel: FeedSettingsViewModel,
    onFeedDeleted: () -> Unit
) {
    val feedInfo by viewModel.feedInfo.collectAsState()
    // The banner arrives with the feed information load; no separate fetch.
    val banner = feedInfo?.banner.orEmpty()
    var bannerDraft by remember(banner) { mutableStateOf(banner) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        feedInfo?.let { info ->
            FeedIdentitySection(
                feed = info,
                editable = true,
                onRename = { newName ->
                    viewModel.setFeedName(newName)
                    viewModel.saveFeedName()
                },
            )
        }

        BannerSection(
            title = stringResource(R.string.feeds_banner),
            description = stringResource(R.string.feeds_banner_description),
            hint = stringResource(R.string.feeds_banner_hint),
            clearLabel = stringResource(R.string.feeds_clear),
            draft = bannerDraft,
            stored = banner,
            onDraftChange = { value -> bannerDraft = value },
            onSave = { value -> viewModel.saveBanner(value) }
        )

        ConfirmActionSection(
            title = stringResource(R.string.feeds_delete_feed),
            buttonLabel = stringResource(MochiR.string.common_delete),
            confirmTitle = stringResource(R.string.feeds_delete_feed),
            confirmMessage = stringResource(R.string.feeds_delete_feed_confirm),
            confirmLabel = stringResource(MochiR.string.common_delete),
            onConfirm = { viewModel.deleteFeed { onFeedDeleted() } }
        )
    }
}

/**
 * Identity card shared by the owner General tab and the read-only non-manager
 * view.
 */
@Composable
fun FeedIdentitySection(
    feed: Feed,
    editable: Boolean,
    onRename: (String) -> Unit,
) {
    Section(title = stringResource(R.string.feeds_settings_section_identity)) {
        IdentityRow(label = stringResource(R.string.feeds_name)) {
            if (editable) {
                InlineTextEditor(
                    value = feed.name,
                    onSave = onRename,
                    editLabel = stringResource(R.string.feeds_settings_name_edit_cd),
                    saveLabel = stringResource(R.string.feeds_save_name),
                    cancelLabel = stringResource(R.string.feeds_settings_name_cancel_cd),
                    clearLabel = stringResource(R.string.feeds_clear),
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(feed.name)
            }
        }
        IdentityRow(label = stringResource(R.string.feeds_settings_field_entity_id)) {
            DataChip(value = feed.id, truncate = Truncate.MIDDLE)
        }
        if (feed.fingerprint.isNotBlank()) {
            IdentityRow(label = stringResource(R.string.feeds_settings_field_fingerprint)) {
                DataChip(value = feed.fingerprint, truncate = Truncate.MIDDLE)
            }
        }
        if (!feed.server.isNullOrBlank()) {
            IdentityRow(label = stringResource(R.string.feeds_settings_field_server)) {
                DataChip(value = feed.server!!, truncate = Truncate.MIDDLE)
            }
        }
    }
}
