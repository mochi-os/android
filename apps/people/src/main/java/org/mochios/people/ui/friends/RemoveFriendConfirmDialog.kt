// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.friends

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.people.R

@Composable
fun RemoveFriendConfirmDialog(
    friendName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        title = stringResource(R.string.people_friends_remove),
        message = stringResource(R.string.people_friends_remove_confirm, friendName),
        confirmLabel = stringResource(R.string.people_friends_remove),
        dismissLabel = stringResource(R.string.people_common_cancel),
        isDestructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
