// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.staff.R
import org.mochios.staff.ui.components.StaffAuditTimeline
import org.mochios.staff.ui.listings.ListingActionType
import org.mochios.staff.ui.listings.PendingListingAction

/**
 * Modal confirmation dialog for approve / reject / remove listing
 * actions. Mirrors the dialog block in
 * `apps/staff/web/src/features/listings/listings-page.tsx`:
 *
 *   - Title reflects the action type.
 *   - Reason field is shown for REJECT and REMOVE only (required on web;
 *     the dialog blocks submission with a blank value).
 *   - Notes field is shown for every action (optional).
 *   - The per-listing audit timeline lives inline via
 *     [StaffAuditTimeline] so the moderator sees prior actions before
 *     clicking through.
 *   - Submission is delegated to [onSubmit]; the parent ViewModel performs
 *     the API call and drives optimistic removal from the list.
 *
 * The dialog uses Material's plain `AlertDialog` rather than the M3
 * `ModalBottomSheet` — the web dialog is a centred modal, and Android
 * dialogs render with the same affordance on phone form factors.
 */
@Composable
fun ListingActionDialog(
    action: PendingListingAction,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, notes: String) -> Unit,
) {
    var reason by rememberSaveable(action.listing.id, action.type) { mutableStateOf("") }
    var notes by rememberSaveable(action.listing.id, action.type) { mutableStateOf("") }

    val title = when (action.type) {
        ListingActionType.APPROVE -> stringResource(R.string.staff_listings_action_approve_title)
        ListingActionType.REJECT -> stringResource(R.string.staff_listings_action_reject_title)
        ListingActionType.REMOVE -> stringResource(R.string.staff_listings_action_remove_title)
    }
    val confirmLabel = when (action.type) {
        ListingActionType.APPROVE -> stringResource(R.string.staff_listings_approve)
        ListingActionType.REJECT -> stringResource(R.string.staff_listings_reject)
        ListingActionType.REMOVE -> stringResource(R.string.staff_listings_remove)
    }
    val requiresReason =
        action.type == ListingActionType.REJECT || action.type == ListingActionType.REMOVE
    val confirmEnabled = !submitting && (!requiresReason || reason.isNotBlank())

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${action.listing.title} (#${action.listing.id})",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (requiresReason) {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text(stringResource(R.string.staff_listings_reason_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.staff_listings_notes_label)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                StaffAuditTimeline(
                    kind = "listing",
                    objectId = action.listing.id.toString(),
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
                    if (submitting) stringResource(R.string.staff_listings_submitting)
                    else confirmLabel,
                )
            }
        },
        dismissButton = {
            TextButton(
                enabled = !submitting,
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.staff_listings_cancel))
            }
        },
    )
}
