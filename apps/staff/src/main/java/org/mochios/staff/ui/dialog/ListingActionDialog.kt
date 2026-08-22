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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiTextField
import org.mochios.staff.R
import org.mochios.staff.ui.components.StaffAuditTimeline
import org.mochios.staff.ui.listings.ListingActionType
import org.mochios.staff.ui.listings.PendingListingAction

/**
 * Confirmation dialog for approve / reject / remove on a listing. REJECT and
 * REMOVE require a reason.
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

    MochiAlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = title,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${action.listing.title} (#${action.listing.id})",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (requiresReason) {
                    MochiTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text(stringResource(R.string.staff_listings_reason_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                MochiTextField(
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
        confirmText = if (submitting) stringResource(R.string.staff_listings_submitting)
            else confirmLabel,
        onConfirm = { onSubmit(reason, notes) },
        confirmEnabled = confirmEnabled,
        dismissText = stringResource(R.string.staff_listings_cancel),
        onDismiss = onDismiss,
        dismissEnabled = !submitting,
    )
}
