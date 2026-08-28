// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.mochios.android.R

/**
 * Confirm revoking an entity's RSS token, matching the web client's dialog.
 *
 * The message's second sentence is doing real work: revoking is also the only
 * way to reissue - minting returns the existing token unchanged, so an entity
 * whose token has leaked stays leaked until the row is cleared - and nothing
 * else in the interface says so.
 */
@Composable
fun RssRevokeDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    pending: Boolean = false,
) {
    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.rss_revoke_title),
        text = stringResource(R.string.rss_revoke_message),
        confirmText = stringResource(R.string.rss_revoke_confirm),
        onConfirm = onConfirm,
        confirmLoading = pending,
        destructive = true,
        dismissText = stringResource(R.string.common_cancel),
    )
}
