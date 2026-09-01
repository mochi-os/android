// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.LabeledSelectField
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiTextField
import org.mochios.staff.R
import org.mochios.staff.model.Appeal
import org.mochios.staff.ui.components.StaffAuditTimeline

/**
 * Moderator dialog for deciding a listing appeal: `upheld` approves the
 * listing, `denied` keeps it rejected.
 */
@Composable
fun AppealDecideDialog(
    appeal: Appeal,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (decision: String, notes: String) -> Unit,
) {
    var decision by rememberSaveable(appeal.id) { mutableStateOf("") }
    var notes by rememberSaveable(appeal.id) { mutableStateOf("") }

    MochiAlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = stringResource(R.string.staff_appeals_dialog_title),
        content = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.staff_appeals_listing_with_id,
                        appeal.title.ifBlank { stringResource(R.string.staff_appeals_listing_label, appeal.listing) },
                        appeal.listing,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // Appeal-reason card.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.staff_appeals_reason_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = appeal.reason,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                LabeledSelectField(
                    label = stringResource(R.string.staff_appeals_decision_label),
                    placeholder = stringResource(R.string.staff_appeals_decision_placeholder),
                    options = decisionOptions(),
                    selected = decision,
                    onSelect = { value -> decision = value },
                )

                MochiTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.staff_appeals_notes_label)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(4.dp))
                StaffAuditTimeline(
                    // Audit kind is the LISTING's history: "appeal" is not a
                    // valid kind and 403s.
                    kind = "listing",
                    objectId = appeal.listing.toString(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmText = if (submitting) stringResource(R.string.staff_appeals_submitting)
            else stringResource(R.string.staff_appeals_submit),
        onConfirm = { onSubmit(decision, notes) },
        confirmEnabled = !submitting && decision.isNotBlank(),
        dismissText = stringResource(R.string.staff_appeals_cancel),
        onDismiss = onDismiss,
        dismissEnabled = !submitting,
    )
}

@Composable
private fun decisionOptions(): List<Pair<String, String>> = listOf(
    "upheld" to stringResource(R.string.staff_appeals_decision_upheld),
    "denied" to stringResource(R.string.staff_appeals_decision_denied),
)
