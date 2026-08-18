// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.friends

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.mochios.android.R as MochiR
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.people.R

/**
 * "Are you sure?" prompt that confirms a friend removal. Stays default-styled
 * (per the destructive-styling rule in CLAUDE.md the *trigger* button never
 * turns red — only this confirm step does).
 */
@Composable
fun RemoveFriendConfirmDialog(
    friendName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.people_friends_remove),
        text = stringResource(R.string.people_friends_remove_confirm, friendName),
        confirmText = stringResource(R.string.people_friends_remove),
        onConfirm = onConfirm,
        destructive = true,
        dismissText = stringResource(R.string.people_common_cancel),
    )
}
