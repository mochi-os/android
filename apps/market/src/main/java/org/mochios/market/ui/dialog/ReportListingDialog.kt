// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiTextField
import org.mochios.market.R
import org.mochios.market.model.ReportReason

/**
 * Same order as `useReportReasons` in
 * `apps/market/web/src/config/constants.ts`.
 */
internal val LISTING_REPORT_REASONS: List<Pair<ReportReason, Int>> = listOf(
    ReportReason.PROHIBITED to R.string.market_report_reason_prohibited,
    ReportReason.COUNTERFEIT to R.string.market_report_reason_counterfeit,
    ReportReason.MISLEADING to R.string.market_report_reason_misleading,
    ReportReason.INAPPROPRIATE to R.string.market_report_reason_inappropriate,
    ReportReason.SPAM to R.string.market_report_reason_spam,
    ReportReason.OTHER to R.string.market_report_reason_other,
)

/**
 * The reasons that fit a conversation, as `useConversationReportReasons` in
 * `apps/market/web/src/config/constants.ts`.
 */
internal val CONVERSATION_REPORT_REASONS: List<Pair<ReportReason, Int>> =
    LISTING_REPORT_REASONS.filter { (reason, _) ->
        reason in setOf(
            ReportReason.PROHIBITED,
            ReportReason.INAPPROPRIATE,
            ReportReason.SPAM,
            ReportReason.OTHER,
        )
    }

/** Wire (lowercase) value for a [ReportReason]. */
private fun ReportReason.wire(): String = name.lowercase()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportListingDialog(
    open: Boolean,
    submitting: Boolean = false,
    errorMessage: String? = null,
    title: String = stringResource(R.string.market_report_dialog_title),
    reasons: List<Pair<ReportReason, Int>> = LISTING_REPORT_REASONS,
    onSubmit: (reason: String, details: String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!open) return

    var selectedReason by remember { mutableStateOf(reasons.first().first) }
    var details by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    // Reset transient state every time the dialog opens so a previous draft
    // doesn't leak across dismissals.
    LaunchedEffect(open) {
        if (open) {
            selectedReason = reasons.first().first
            details = ""
            expanded = false
        }
    }

    MochiAlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = title,
        content = {
            Column {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { if (!submitting) expanded = !expanded },
                ) {
                    MochiTextField(
                        value = stringResource(
                            reasons.first { it.first == selectedReason }.second,
                        ),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.market_report_dialog_reason_label)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        enabled = !submitting,
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    MochiDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        reasons.forEach { (reason, labelRes) ->
                            MochiDropdownMenuItem(
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
                MochiTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text(stringResource(R.string.market_report_dialog_details_label)) },
                    enabled = !submitting,
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!errorMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmText = if (submitting) {
            stringResource(R.string.market_report_dialog_submitting)
        } else {
            stringResource(R.string.market_report_dialog_submit)
        },
        onConfirm = { onSubmit(selectedReason.wire(), details.trim()) },
        confirmLoading = submitting,
        dismissText = stringResource(R.string.market_report_dialog_cancel),
        dismissEnabled = !submitting,
    )
}

