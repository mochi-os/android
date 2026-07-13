// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.mochios.staff.R
import org.mochios.staff.model.Account
import org.mochios.staff.ui.components.StaffAuditTimeline

/**
 * Read-only dialog showing the per-account audit history.
 *
 * Mirrors the second dialog in
 * `apps/staff/web/src/features/accounts/accounts-page.tsx`. The dialog body
 * delegates entirely to [StaffAuditTimeline]; the title shows the account's
 * display name (mirroring the web title slot).
 */
@Composable
fun AccountAuditDialog(
    account: Account,
    onDismiss: () -> Unit,
) {
    val displayName = account.name.ifBlank {
        stringResource(R.string.staff_accounts_unnamed)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.staff_accounts_history_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(text = displayName)
                StaffAuditTimeline(
                    kind = "account",
                    objectId = account.id,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.staff_accounts_close))
            }
        },
    )
}
