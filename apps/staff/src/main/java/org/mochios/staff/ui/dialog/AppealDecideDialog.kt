// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.mochios.staff.R
import org.mochios.staff.model.Appeal
import org.mochios.staff.ui.components.StaffAuditTimeline

/**
 * Modal dialog driving the moderator's "Decide appeal" flow.
 *
 * Android port of the dialog block in
 * `apps/staff/web/src/features/appeals/appeals-page.tsx`. Layout:
 *
 *   - Listing title + id.
 *   - Read-only appeal-reason card.
 *   - Decision dropdown: `upheld` (approve listing) or `denied` (keep
 *     listing rejected).
 *   - Notes textarea (optional).
 *   - Inline [StaffAuditTimeline] of the listing history.
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

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.staff_appeals_dialog_title)) },
        text = {
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

                DecisionDropdown(
                    decision = decision,
                    onDecisionChange = { decision = it },
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.staff_appeals_notes_label)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(4.dp))
                StaffAuditTimeline(
                    // The audit timeline is the LISTING's moderation history.
                    // "appeal" is not a valid audit kind (403); web uses
                    // kind='listing' with the appeal's listing id.
                    kind = "listing",
                    objectId = appeal.listing.toString(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting && decision.isNotBlank(),
                onClick = { onSubmit(decision, notes) },
            ) {
                Text(
                    if (submitting) stringResource(R.string.staff_appeals_submitting)
                    else stringResource(R.string.staff_appeals_submit),
                )
            }
        },
        dismissButton = {
            TextButton(
                enabled = !submitting,
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.staff_appeals_cancel))
            }
        },
    )
}

@Composable
private fun DecisionDropdown(
    decision: String,
    onDecisionChange: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val options = decisionOptions()
    val current = options.firstOrNull { it.first == decision }?.second
        ?: stringResource(R.string.staff_appeals_decision_placeholder)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.staff_appeals_decision_label),
            style = MaterialTheme.typography.labelMedium,
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = current, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            expanded = false
                            onDecisionChange(value)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun decisionOptions(): List<Pair<String, String>> = listOf(
    "upheld" to stringResource(R.string.staff_appeals_decision_upheld),
    "denied" to stringResource(R.string.staff_appeals_decision_denied),
)
