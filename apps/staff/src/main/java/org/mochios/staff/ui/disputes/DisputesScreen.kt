package org.mochios.staff.ui.disputes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.InfiniteList
import org.mochios.staff.R
import org.mochios.android.format.formatFingerprint
import org.mochios.android.format.formatPrice
import org.mochios.staff.model.Dispute
import org.mochios.staff.ui.components.FilterChipSpec
import org.mochios.staff.ui.components.FilterChipsRow
import org.mochios.staff.ui.components.StaffStatusBadge
import org.mochios.staff.ui.dialog.DisputeReviewDialog

/**
 * Staff "Disputes" screen.
 *
 * Android port of `apps/staff/web/src/features/disputes/disputes-page.tsx`.
 * One filter dropdown (status). Each row's "Review" / "View" button opens
 * [DisputeReviewDialog]. Stripe chargebacks (opener == "stripe") render
 * the dialog in read-only "View" mode regardless of status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisputesScreen(
    @Suppress("UNUSED_PARAMETER") navController: NavController,
    viewModel: DisputesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    val resolvedMsg = stringResource(R.string.staff_disputes_resolved_toast)
    val refundPositiveMsg = stringResource(R.string.staff_disputes_refund_must_be_positive)
    val refundExceedsMsg = stringResource(R.string.staff_disputes_refund_exceeds_total)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DisputesEvent.Resolved -> snackbar.showSnackbar(resolvedMsg)
                is DisputesEvent.Toast -> snackbar.showSnackbar(event.message)
                is DisputesEvent.RefundMustBePositive -> snackbar.showSnackbar(refundPositiveMsg)
                is DisputesEvent.RefundExceedsTotal -> snackbar.showSnackbar(refundExceedsMsg)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        DisputesBody(
            state = state,
            onStatusChange = viewModel::setStatus,
            onLoadMore = viewModel::loadMore,
            onOpenDispute = viewModel::openReview,
            onRetry = viewModel::reload,
        )
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    state.reviewDialog?.let { dispute ->
        DisputeReviewDialog(
            dispute = dispute,
            submitting = state.submitting,
            onDismiss = viewModel::dismissReview,
            onSubmit = { resolution, notes, refund ->
                viewModel.reviewDispute(resolution, notes, refund)
            },
            // Stripe chargebacks must be answered on Stripe's portal — the
            // dialog renders metadata + history only. Already-resolved
            // disputes (`resolved_buyer` / `resolved_seller`) take the same
            // path. See [DisputeStatus] for the canonical wire values.
            readOnly = dispute.opener == "stripe" || dispute.status.startsWith("resolved_"),
        )
    }
}

@Composable
private fun DisputesBody(
    state: DisputesUiState,
    onStatusChange: (String?) -> Unit,
    onLoadMore: () -> Unit,
    onOpenDispute: (Dispute) -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        FiltersRow(
            status = state.status,
            onStatusChange = onStatusChange,
        )
        ActiveFilterChips(
            status = state.status,
            onStatusChange = onStatusChange,
        )

        when {
            state.isLoading && state.disputes.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.error != null && state.disputes.isEmpty() ->
                ErrorState(error = state.error, onRetry = onRetry)

            state.disputes.isEmpty() -> EmptyState(
                icon = Icons.Default.Report,
                title = stringResource(R.string.staff_disputes_empty),
            )

            else -> InfiniteList(
                items = state.disputes,
                isLoading = state.isLoadingMore,
                hasMore = state.disputes.size < state.total,
                onLoadMore = onLoadMore,
            ) { dispute ->
                DisputeRow(
                    dispute = dispute,
                    onActionClick = { onOpenDispute(dispute) },
                )
            }
        }
    }
}

@Composable
private fun DisputeRow(
    dispute: Dispute,
    onActionClick: () -> Unit,
) {
    val format = LocalFormat.current
    val canReview = dispute.opener != "stripe"
        && dispute.status != "resolved_buyer"
        && dispute.status != "resolved_seller"
    val showAction = dispute.opener == "stripe" || canReview

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
    ) {
        Text(
            text = dispute.title.ifBlank { stringResource(R.string.staff_disputes_listing_label, dispute.listing) },
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))

        // Seller row.
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntityAvatar(
                name = dispute.sellerName.ifBlank { dispute.seller },
                seed = dispute.seller,
                size = 18.dp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = dispute.sellerName.ifBlank { formatFingerprint(dispute.seller) },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(2.dp))

        // Buyer row.
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntityAvatar(
                name = dispute.buyerName.ifBlank { dispute.buyer },
                seed = dispute.buyer,
                size = 18.dp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = dispute.buyerName.ifBlank { formatFingerprint(dispute.buyer) },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))

        // Status + total + reason.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StaffStatusBadge(status = dispute.status)
            Text(
                text = formatPrice(dispute.total, dispute.currency),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = disputeReasonText(dispute),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))

        // Created + action button.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = format.formatTimestamp(dispute.created),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (showAction) {
                OutlinedButton(onClick = onActionClick) {
                    Text(
                        if (dispute.opener == "stripe") stringResource(R.string.staff_disputes_view)
                        else stringResource(R.string.staff_disputes_review),
                    )
                }
            }
        }
    }
}

@Composable
private fun disputeReasonText(dispute: Dispute): String {
    return if (dispute.opener == "stripe") {
        stringResource(
            R.string.staff_disputes_dialog_title_chargeback,
            stripeReasonLabel(dispute.reason),
        )
    } else {
        disputeReasonLabel(dispute.reason)
    }
}

@Composable
private fun FiltersRow(
    status: String?,
    onStatusChange: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterDropdown(
            label = stringResource(R.string.staff_disputes_filter_status_label),
            current = status,
            options = disputeStatusOptions(),
            anyLabel = stringResource(R.string.staff_disputes_any_status),
            onSelect = onStatusChange,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FilterDropdown(
    label: String,
    current: String?,
    options: List<Pair<String, String>>,
    anyLabel: String,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.first == current }?.second ?: anyLabel
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "$label: $currentLabel", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(anyLabel) },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            options.forEach { (value, l) ->
                DropdownMenuItem(
                    text = { Text(l) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun disputeStatusOptions(): List<Pair<String, String>> = listOf(
    "open" to stringResource(R.string.staff_disputes_status_open),
    "responded" to stringResource(R.string.staff_disputes_status_responded),
    "reviewing" to stringResource(R.string.staff_disputes_status_reviewing),
    "resolved_buyer" to stringResource(R.string.staff_disputes_status_resolved_buyer),
    "resolved_seller" to stringResource(R.string.staff_disputes_status_resolved_seller),
    "escalated" to stringResource(R.string.staff_disputes_status_escalated),
)

/**
 * Removable chip for the active status filter. Renders nothing when the
 * filter is at the default `null` (All) value. Mirrors `ActiveFilterChips`
 * in [org.mochios.staff.ui.accounts.AccountsScreen].
 */
@Composable
private fun ActiveFilterChips(
    status: String?,
    onStatusChange: (String?) -> Unit,
) {
    val statusLabel = stringResource(R.string.staff_filter_label_status)
    val statusOpts = disputeStatusOptions()
    val chips = buildList {
        if (!status.isNullOrBlank()) {
            val value = statusOpts.firstOrNull { it.first == status }?.second ?: status
            add(FilterChipSpec(statusLabel, value) { onStatusChange(null) })
        }
    }
    FilterChipsRow(chips = chips)
}

@Composable
internal fun stripeReasonLabel(reason: String): String = when (reason) {
    "bank_cannot_process" -> stringResource(R.string.staff_disputes_stripe_bank_cannot_process)
    "check_returned" -> stringResource(R.string.staff_disputes_stripe_check_returned)
    "credit_not_processed" -> stringResource(R.string.staff_disputes_stripe_credit_not_processed)
    "customer_initiated" -> stringResource(R.string.staff_disputes_stripe_customer_initiated)
    "debit_not_authorized" -> stringResource(R.string.staff_disputes_stripe_debit_not_authorized)
    "duplicate" -> stringResource(R.string.staff_disputes_stripe_duplicate)
    "fraudulent" -> stringResource(R.string.staff_disputes_stripe_fraudulent)
    "general" -> stringResource(R.string.staff_disputes_stripe_general)
    "incorrect_account_details" -> stringResource(R.string.staff_disputes_stripe_incorrect_account_details)
    "insufficient_funds" -> stringResource(R.string.staff_disputes_stripe_insufficient_funds)
    "product_not_received" -> stringResource(R.string.staff_disputes_stripe_product_not_received)
    "product_unacceptable" -> stringResource(R.string.staff_disputes_stripe_product_unacceptable)
    "subscription_canceled" -> stringResource(R.string.staff_disputes_stripe_subscription_canceled)
    "unrecognized" -> stringResource(R.string.staff_disputes_stripe_unrecognized)
    else -> reason.replace('_', ' ')
}

@Composable
internal fun disputeReasonLabel(reason: String): String = when (reason) {
    "not_received" -> stringResource(R.string.staff_disputes_reason_not_received)
    "not_as_described" -> stringResource(R.string.staff_disputes_reason_not_as_described)
    "damaged" -> stringResource(R.string.staff_disputes_reason_damaged)
    "unauthorised" -> stringResource(R.string.staff_disputes_reason_unauthorised)
    "other" -> stringResource(R.string.staff_disputes_reason_other)
    else -> reason
}
