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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.LabeledSelectField
import org.mochios.android.ui.components.MetaRow
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiTextField
import org.mochios.staff.R
import org.mochios.android.format.formatPrice
import org.mochios.staff.model.Dispute
import org.mochios.staff.ui.components.StaffAuditTimeline
import org.mochios.staff.ui.disputes.disputeReasonLabel
import org.mochios.staff.ui.disputes.stripeReasonLabel
import java.util.Locale

/**
 * Moderator dialog for reviewing a dispute. [readOnly] drops the resolution /
 * refund / notes fields - used for Stripe chargebacks, which must be answered
 * on Stripe's portal, and for already-resolved disputes.
 */
@Composable
fun DisputeReviewDialog(
    dispute: Dispute,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (resolution: String, notes: String, refundAmountMinor: Long?) -> Unit,
    readOnly: Boolean = false,
) {
    val isStripe = dispute.opener == "stripe"
    var resolution by rememberSaveable(dispute.id) { mutableStateOf("") }
    var notes by rememberSaveable(dispute.id) { mutableStateOf("") }
    var refundInput by rememberSaveable(dispute.id) { mutableStateOf("") }

    // Stripe chargebacks keep their existing reason-bearing title;
    // already-resolved manual disputes fall through to the generic
    // "View dispute" title.
    val title = when {
        isStripe -> stringResource(
            R.string.staff_disputes_dialog_title_chargeback,
            stripeReasonLabel(dispute.reason),
        )
        readOnly -> stringResource(R.string.staff_disputes_dialog_title_view)
        else -> stringResource(R.string.staff_disputes_dialog_title_review)
    }

    MochiAlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = title,
        content = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetadataCard(dispute = dispute)

                if (!readOnly) {
                    LabeledSelectField(
                        label = stringResource(R.string.staff_disputes_resolution_label),
                        placeholder = stringResource(
                            R.string.staff_disputes_resolution_placeholder
                        ),
                        options = resolutionOptions(),
                        selected = resolution,
                        onSelect = { value -> resolution = value },
                    )
                    if (resolution == "resolved_buyer") {
                        // The full refund and placeholder reflect what's left to
                        // refund (total minus anything already refunded), matching
                        // web's `remaining` and the clamp in the view model.
                        val remaining = (dispute.total - dispute.orderRefunded).coerceAtLeast(0)
                        MochiTextField(
                            value = refundInput,
                            onValueChange = { refundInput = it },
                            label = {
                                Text(
                                    stringResource(
                                        R.string.staff_disputes_refund_amount_label,
                                        dispute.currency.uppercase(Locale.ROOT),
                                    ),
                                )
                            },
                            placeholder = {
                                Text(formatPrice(remaining, dispute.currency))
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(
                                R.string.staff_disputes_refund_help,
                                formatPrice(remaining, dispute.currency),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MochiTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(stringResource(R.string.staff_disputes_notes_label)) },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(4.dp))
                StaffAuditTimeline(
                    kind = "dispute",
                    objectId = dispute.id.toString(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        // A dispute that is already settled is read only, so it keeps only the close button.
        confirmText = when {
            readOnly -> null
            submitting -> stringResource(R.string.staff_disputes_resolving)
            else -> stringResource(R.string.staff_disputes_resolve)
        },
        onConfirm = {
            val refund = if (resolution == "resolved_buyer" && refundInput.isNotBlank()) {
                parseRefundMinor(refundInput, dispute.currency)
            } else null
            onSubmit(resolution, notes, refund)
        },
        confirmEnabled = resolution.isNotBlank(),
        confirmLoading = submitting,
        dismissText = if (readOnly) stringResource(R.string.staff_disputes_close)
            else stringResource(R.string.staff_disputes_cancel),
        onDismiss = onDismiss,
        dismissEnabled = !submitting,
    )
}

@Composable
private fun MetadataCard(dispute: Dispute) {
    val format = LocalFormat.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MetaRow(
            stringResource(R.string.staff_disputes_meta_listing),
            dispute.title.ifBlank { stringResource(R.string.staff_disputes_listing_label, dispute.listing) },
        )
        MetaRow(
            stringResource(R.string.staff_disputes_meta_total),
            formatPrice(dispute.total, dispute.currency),
        )
        // Prior refunds against this order, so the moderator sees what's left.
        if (dispute.orderRefunded > 0) {
            MetaRow(
                stringResource(R.string.staff_disputes_meta_already_refunded),
                formatPrice(dispute.orderRefunded, dispute.currency),
            )
        }
        val reasonValue = if (dispute.opener == "stripe") {
            stringResource(
                R.string.staff_disputes_dialog_title_chargeback,
                stripeReasonLabel(dispute.reason),
            )
        } else {
            disputeReasonLabel(dispute.reason)
        }
        MetaRow(stringResource(R.string.staff_disputes_meta_reason), reasonValue)

        // Stripe chargeback fee + refund-state summary.
        if (dispute.opener == "stripe" && dispute.fee > 0) {
            val feeText = buildString {
                append(formatPrice(dispute.fee, dispute.currency))
                when {
                    dispute.feeRefunded >= dispute.fee && dispute.feeRefunded > 0 ->
                        append(" (${stringResource(R.string.staff_disputes_meta_chargeback_fee_refunded)})")
                    dispute.feeRefunded in 1L until dispute.fee ->
                        append(" (")
                        .append(
                            stringResource(
                                R.string.staff_disputes_meta_chargeback_fee_partial,
                                formatPrice(dispute.feeRefunded.toLong(), dispute.currency),
                            ),
                        )
                        .append(')')
                    dispute.status == "resolved_buyer" && dispute.feeRefunded == 0 ->
                        append(" (${stringResource(R.string.staff_disputes_meta_chargeback_fee_kept)})")
                }
            }
            MetaRow(stringResource(R.string.staff_disputes_meta_chargeback_fee), feeText)
        }

        if (dispute.opener == "stripe" && dispute.status == "open" && dispute.evidenceDue > 0) {
            MetaRow(
                stringResource(R.string.staff_disputes_meta_evidence_due),
                format.formatDateTime(dispute.evidenceDue),
            )
        }

        // Refunded summary on resolved-buyer disputes (regardless of opener).
        if (dispute.status == "resolved_buyer" && dispute.refundAmount > 0) {
            val isPartial = dispute.refundAmount < dispute.total
            val label = if (isPartial) {
                stringResource(R.string.staff_disputes_meta_refunded_partial)
            } else {
                stringResource(R.string.staff_disputes_meta_refunded)
            }
            val value = buildString {
                append(formatPrice(dispute.refundAmount, dispute.currency))
                if (isPartial) {
                    append(' ')
                    append(
                        stringResource(
                            R.string.staff_disputes_meta_refunded_of,
                            formatPrice(dispute.total, dispute.currency),
                        ),
                    )
                }
            }
            MetaRow(label, value)
        }

        if (dispute.opener != "stripe") {
            if (dispute.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.staff_disputes_meta_buyer_details),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = dispute.description, style = MaterialTheme.typography.bodySmall)
            }
            if (dispute.response.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.staff_disputes_meta_seller_response),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = dispute.response, style = MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    text = stringResource(R.string.staff_disputes_meta_seller_no_response),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun resolutionOptions(): List<Pair<String, String>> = listOf(
    "resolved_buyer" to stringResource(R.string.staff_disputes_resolution_buyer),
    "resolved_seller" to stringResource(R.string.staff_disputes_resolution_seller),
)

internal fun parseRefundMinor(input: String, currency: String): Long? {
    val trimmed = input.trim().replace(',', '.')
    if (trimmed.isEmpty()) return null
    val decimals = if (currency.equals("jpy", ignoreCase = true)) 0 else 2
    val num = trimmed.toDoubleOrNull() ?: return null
    val factor = if (decimals == 0) 1.0 else 100.0
    return kotlin.math.round(num * factor).toLong()
}
