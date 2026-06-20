// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.market.R
import org.mochios.market.model.ReportReason

/**
 * Reasons enumerated in the same order the web SPA's report dialog presents
 * them. The wire value (lowercase) is sent verbatim to `/market/-/reports/create`;
 * the label-resource id is resolved at render time so the visible text is
 * localised. Mirrors `useReportReasons` in `apps/market/web/src/config/constants.ts`.
 */
private val REPORT_REASONS: List<Pair<ReportReason, Int>> = listOf(
    ReportReason.PROHIBITED to R.string.market_report_reason_prohibited,
    ReportReason.COUNTERFEIT to R.string.market_report_reason_counterfeit,
    ReportReason.MISLEADING to R.string.market_report_reason_misleading,
    ReportReason.INAPPROPRIATE to R.string.market_report_reason_inappropriate,
    ReportReason.SPAM to R.string.market_report_reason_spam,
    ReportReason.OTHER to R.string.market_report_reason_other,
)

/** Wire (lowercase) value for a [ReportReason]. */
private fun ReportReason.wire(): String = name.lowercase()

/**
 * Report-listing dialog mirroring the inline report form on the web listing
 * page. Renders a dropdown over [ReportReason] and a free-text details field;
 * Submit calls back to the caller with the chosen reason and details.
 *
 * Persistence (writing to [org.mochios.market.lib.ReportedStore]) and the
 * API call happen in [org.mochios.market.ui.listing.ListingDetailViewModel.reportListing]
 * — the dialog only collects the form values and asks for confirmation.
 *
 * @param onSubmit Fires on Submit with `(reason, details)`. The reason is the
 *                 lowercase wire value (e.g. `"prohibited"`); details is the
 *                 trimmed free-text content (may be empty).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportListingDialog(
    open: Boolean,
    submitting: Boolean = false,
    onSubmit: (reason: String, details: String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!open) return

    var selectedReason by remember { mutableStateOf(REPORT_REASONS.first().first) }
    var details by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    // Reset transient state every time the dialog opens so a previous draft
    // doesn't leak across dismissals.
    LaunchedEffect(open) {
        if (open) {
            selectedReason = REPORT_REASONS.first().first
            details = ""
            expanded = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.market_report_dialog_title)) },
        text = {
            Column {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { if (!submitting) expanded = !expanded },
                ) {
                    OutlinedTextField(
                        value = stringResource(
                            REPORT_REASONS.first { it.first == selectedReason }.second,
                        ),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.market_report_dialog_reason_label)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        enabled = !submitting,
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        REPORT_REASONS.forEach { (reason, labelRes) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(labelRes)) },
                                onClick = {
                                    selectedReason = reason
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text(stringResource(R.string.market_report_dialog_details_label)) },
                    enabled = !submitting,
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = { onSubmit(selectedReason.wire(), details.trim()) },
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(R.string.market_report_dialog_submitting))
                } else {
                    Text(stringResource(R.string.market_report_dialog_submit))
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !submitting,
                onClick = { if (!submitting) onDismiss() },
            ) {
                Text(stringResource(R.string.market_report_dialog_cancel))
            }
        },
    )
}

