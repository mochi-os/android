// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import org.mochios.android.format.formatFingerprint
import org.mochios.android.format.formatPrice
import org.mochios.staff.model.Report
import org.mochios.staff.ui.components.StaffAuditTimeline

/**
 * Modal dialog driving the moderator's "Take action on report" flow.
 *
 * Android port of the action-dialog block in
 * `apps/staff/web/src/features/reports/reports-page.tsx`.
 *
 *   - Metadata card up top: type, target (listing title or user
 *     fingerprint), seller / price for listing reports, reporter,
 *     reason, reporter's free-text details.
 *   - Action dropdown (`dismiss` / `warn` / `remove` / `suspend` /
 *     `ban`). Shown only in edit mode.
 *   - Notes textarea (optional).
 *   - Inline [StaffAuditTimeline] of the report history.
 *
 * Pass [readOnly] = true when the report is already resolved (the
 * caller checks `report.status != "pending"`) to render the same
 * metadata + history without the action picker / notes field — the
 * footer collapses to a single Close button and the title swaps to
 * the "View" variant.
 */
@Composable
fun ReportActionDialog(
    report: Report,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (action: String, notes: String) -> Unit,
    readOnly: Boolean = false,
) {
    var action by rememberSaveable(report.id) { mutableStateOf("") }
    var notes by rememberSaveable(report.id) { mutableStateOf("") }

    val title = if (readOnly) stringResource(R.string.staff_reports_dialog_title_view)
    else stringResource(R.string.staff_reports_dialog_title_action)

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetadataCard(report = report)

                if (!readOnly) {
                    ActionDropdown(
                        action = action,
                        onActionChange = { action = it },
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(stringResource(R.string.staff_reports_notes_label)) },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(4.dp))
                StaffAuditTimeline(
                    kind = "report",
                    objectId = report.id.toString(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            if (!readOnly) {
                TextButton(
                    enabled = !submitting && action.isNotBlank(),
                    onClick = { onSubmit(action, notes) },
                ) {
                    Text(
                        if (submitting) stringResource(R.string.staff_reports_submitting)
                        else stringResource(R.string.staff_reports_submit),
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !submitting,
                onClick = onDismiss,
            ) {
                Text(
                    if (readOnly) stringResource(R.string.staff_reports_close)
                    else stringResource(R.string.staff_reports_cancel),
                )
            }
        },
    )
}

@Composable
private fun MetadataCard(report: Report) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MetaRow(stringResource(R.string.staff_reports_meta_type), report.type)
        MetaRow(
            stringResource(R.string.staff_reports_meta_target),
            targetText(report),
        )
        if (report.type == "listing" && report.listing != null) {
            MetaRow(
                stringResource(R.string.staff_reports_meta_seller),
                report.sellerName.ifBlank { formatFingerprint(report.listing.seller) },
            )
            MetaRow(
                stringResource(R.string.staff_reports_meta_price),
                formatPrice(report.listing.price, report.listing.currency),
            )
        }
        MetaRow(
            stringResource(R.string.staff_reports_meta_reporter),
            report.reporterName.ifBlank { formatFingerprint(report.reporter) },
        )
        MetaRow(
            stringResource(R.string.staff_reports_meta_reason),
            reasonLabel(report.reason),
        )
        if (report.details.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.staff_reports_meta_details),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = report.details,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActionDropdown(
    action: String,
    onActionChange: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val options = reportActionOptions()
    val current = options.firstOrNull { it.first == action }?.second
        ?: stringResource(R.string.staff_reports_action_placeholder)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.staff_reports_action_label),
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
                            onActionChange(value)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun reportActionOptions(): List<Pair<String, String>> = listOf(
    "dismiss" to stringResource(R.string.staff_reports_action_dismiss),
    "warn" to stringResource(R.string.staff_reports_action_warn),
    "remove" to stringResource(R.string.staff_reports_action_remove),
    "suspend" to stringResource(R.string.staff_reports_action_suspend),
    "ban" to stringResource(R.string.staff_reports_action_ban),
)

@Composable
private fun targetText(report: Report): String = when (report.type) {
    "listing" -> report.listing?.title
        ?: stringResource(R.string.staff_reports_listing_label, report.target)
    else -> report.targetName.ifBlank { formatFingerprint(report.target) }
}

@Composable
private fun reasonLabel(reason: String): String = when (reason) {
    "prohibited" -> stringResource(R.string.staff_reports_reason_prohibited)
    "counterfeit" -> stringResource(R.string.staff_reports_reason_counterfeit)
    "misleading" -> stringResource(R.string.staff_reports_reason_misleading)
    "inappropriate" -> stringResource(R.string.staff_reports_reason_inappropriate)
    "spam" -> stringResource(R.string.staff_reports_reason_spam)
    "other" -> stringResource(R.string.staff_reports_reason_other)
    else -> reason
}
