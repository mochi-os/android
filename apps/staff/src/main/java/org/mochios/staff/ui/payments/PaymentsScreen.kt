// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.payments

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.mochios.android.R as MochiR
import org.mochios.android.api.userMessage
import org.mochios.android.format.formatFingerprint
import org.mochios.android.format.formatPrice
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.InfiniteList
import org.mochios.android.ui.components.LoadingState
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.StatusBadge
import org.mochios.android.ui.components.StatusTone
import org.mochios.staff.R
import org.mochios.staff.model.OrphanedPayment
import org.mochios.staff.ui.components.StaffUserAvatar

/**
 * Port of `apps/staff/web/src/features/payments/payments-page.tsx`, as cards:
 * each payment by when, seller, amount and whether it was refunded. [admin]
 * gates the Refund button, which the Comptroller allows admins alone.
 */
@Composable
fun PaymentsScreen(
    admin: Boolean,
    viewModel: PaymentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PaymentsEvent.Toast -> snackbarHostState.showSnackbar(resources.getString(event.messageRes))
                is PaymentsEvent.Error -> {
                    val fallback = resources.getString(R.string.staff_payments_toast_refund_failed)
                    snackbarHostState.showSnackbar(event.error.userMessage().ifBlank { fallback })
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PaymentsBody(
            state = state,
            admin = admin,
            onRefund = viewModel::askRefund,
            onLoadMore = viewModel::loadMore,
            onRetry = viewModel::load,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    val pending = state.pendingRefund
    if (pending != null) {
        MochiAlertDialog(
            onDismissRequest = viewModel::cancelRefund,
            title = stringResource(R.string.staff_payments_refund_title),
            text = refundSummary(pending),
            confirmText = stringResource(R.string.staff_payments_refund),
            onConfirm = viewModel::confirmRefund,
            confirmLoading = state.submitting,
            dismissText = stringResource(MochiR.string.common_cancel),
        )
    }
}

/** What a refund gives back and from whom: the amount and the seller. */
@Composable
private fun refundSummary(payment: OrphanedPayment): String =
    listOf(amountText(payment), sellerName(payment))
        .filter { part -> part.isNotBlank() }
        .joinToString(" · ")

@Composable
private fun amountText(payment: OrphanedPayment): String =
    if (payment.currency.isBlank()) "" else formatPrice(payment.amount, payment.currency)

/** The seller by name, else fingerprint; "Unknown" when the payment names none. */
@Composable
private fun sellerName(payment: OrphanedPayment): String = when {
    payment.seller.isBlank() -> stringResource(R.string.staff_status_unknown)
    else -> payment.sellerName.ifBlank { formatFingerprint(payment.sellerFingerprint) }
}

@Composable
internal fun PaymentsBody(
    state: PaymentsUiState,
    admin: Boolean,
    onRefund: (OrphanedPayment) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    val error = state.error
    when {
        state.isLoading && state.payments.isEmpty() -> LoadingState()
        error != null && state.payments.isEmpty() -> ErrorState(error = error, onRetry = onRetry)
        state.payments.isEmpty() -> EmptyState(
            icon = Icons.Outlined.CreditCard,
            title = stringResource(R.string.staff_payments_empty),
        )
        else -> InfiniteList(
            items = state.payments,
            key = { payment -> payment.id },
            load = state.load,
            hasMore = state.paging.more,
            onLoadMore = onLoadMore,
        ) { payment ->
            PaymentCard(payment = payment, admin = admin, onRefund = onRefund)
        }
    }
}

@Composable
private fun PaymentCard(
    payment: OrphanedPayment,
    admin: Boolean,
    onRefund: (OrphanedPayment) -> Unit,
) {
    val format = LocalFormat.current
    val seller = sellerName(payment)
    MochiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (payment.seller.isNotBlank()) {
                    StaffUserAvatar(name = seller, id = payment.seller, size = 36.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = seller,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (payment.refunded) {
                    StatusBadge(label = stringResource(R.string.staff_status_refunded), tone = StatusTone.Neutral)
                } else {
                    StatusBadge(label = stringResource(R.string.staff_payments_not_refunded), tone = StatusTone.Waiting)
                }
                val amount = amountText(payment)
                if (amount.isNotEmpty()) {
                    Text(text = amount, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = format.formatTimestamp(payment.created),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (admin && !payment.refunded) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    MochiOutlinedButton(onClick = { onRefund(payment) }) {
                        Text(stringResource(R.string.staff_payments_refund))
                    }
                }
            }
        }
    }
}
