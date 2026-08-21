// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.staff.R
import org.mochios.staff.ui.accounts.AccountActionType
import org.mochios.staff.ui.accounts.PendingAccountAction
import org.mochios.staff.ui.components.StaffAuditTimeline

/**
 * Confirmation dialog for the four account moderation actions. SUSPEND and BAN
 * require a reason server-side.
 */
@Composable
fun AccountActionDialog(
    action: PendingAccountAction,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, notes: String) -> Unit,
) {
    var reason by rememberSaveable(action.account.id, action.type) { mutableStateOf("") }
    var notes by rememberSaveable(action.account.id, action.type) { mutableStateOf("") }

    val title = when (action.type) {
        AccountActionType.SUSPEND -> stringResource(R.string.staff_accounts_action_suspend_title)
        AccountActionType.UNSUSPEND -> stringResource(R.string.staff_accounts_action_unsuspend_title)
        AccountActionType.BAN -> stringResource(R.string.staff_accounts_action_ban_title)
        AccountActionType.UNBAN -> stringResource(R.string.staff_accounts_action_unban_title)
    }
    val confirmLabel = when (action.type) {
        AccountActionType.SUSPEND -> stringResource(R.string.staff_accounts_suspend)
        AccountActionType.UNSUSPEND -> stringResource(R.string.staff_accounts_unsuspend)
        AccountActionType.BAN -> stringResource(R.string.staff_accounts_ban)
        AccountActionType.UNBAN -> stringResource(R.string.staff_accounts_unban)
    }
    val requiresReason =
        action.type == AccountActionType.SUSPEND || action.type == AccountActionType.BAN
    val confirmEnabled = !submitting && (!requiresReason || reason.isNotBlank())

    val displayName = action.account.name.ifBlank {
        stringResource(R.string.staff_accounts_unnamed)
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = {
                        Text(
                            stringResource(
                                if (requiresReason) {
                                    R.string.staff_accounts_reason_label
                                } else {
                                    R.string.staff_accounts_reason_optional_label
                                },
                            ),
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.staff_accounts_notes_label)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                StaffAuditTimeline(
                    kind = "account",
                    objectId = action.account.id,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = confirmEnabled,
                onClick = { onSubmit(reason, notes) },
            ) {
                Text(
                    if (submitting) stringResource(R.string.staff_accounts_submitting)
                    else confirmLabel,
                )
            }
        },
        dismissButton = {
            TextButton(
                enabled = !submitting,
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.staff_accounts_cancel))
            }
        },
    )
}
