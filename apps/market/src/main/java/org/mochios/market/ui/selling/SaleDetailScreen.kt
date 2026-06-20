// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.selling

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.market.R
import org.mochios.market.lib.formatPrice
import org.mochios.market.model.Currency
import org.mochios.market.model.Dispute
import org.mochios.market.model.Order
import org.mochios.market.model.OrderDetailResponse
import org.mochios.market.model.OrderStatus
import org.mochios.market.model.Review
import org.mochios.market.navigation.MarketApp
import org.mochios.market.ui.components.AuditTimeline
import org.mochios.market.ui.components.StatusBadge
import org.mochios.market.ui.dialog.DisputeResponseDialog
import org.mochios.market.ui.dialog.IssueRefundDialog
import org.mochios.market.ui.dialog.ShipOrderDialog
import org.mochios.android.R as MochiR

/**
 * Sale detail (seller view) at [MarketApp.SALE_DETAIL]. Lays out the order
 * receipt, fee breakdown, shipping actions for physical orders, refund
 * trigger, dispute timeline + response, review response, and the audit
 * timeline at the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleDetailScreen(
    navController: NavController,
    viewModel: SaleDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var shipDialogOpen by remember { mutableStateOf(false) }
    var refundDialogOpen by remember { mutableStateOf(false) }
    var disputeDialogOpen by remember { mutableStateOf(false) }
    var auditExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SaleDetailEvent.Toast -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val shipFailedFallback = stringResource(R.string.market_sale_ship_failed)
    val shipSuccessText = stringResource(R.string.market_sale_ship_success)
    val refundFailedFallback = stringResource(R.string.market_refund_dialog_failed)
    val disputeFailedFallback = stringResource(R.string.market_dispute_dialog_failed)
    val reviewFailedFallback = stringResource(R.string.market_sale_review_response_failed)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.market_sale_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.market_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val detail = state.order
            when {
                state.isLoading && detail == null -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
                state.error != null && detail == null -> Text(
                    text = state.error!!.userMessage()
                        .ifEmpty { stringResource(R.string.market_sale_load_failed) },
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                detail != null -> SaleDetailBody(
                    detail = detail,
                    state = state,
                    auditExpanded = auditExpanded,
                    onToggleAudit = { auditExpanded = !auditExpanded },
                    onOpenShip = { shipDialogOpen = true },
                    onOpenRefund = { refundDialogOpen = true },
                    onOpenDispute = { disputeDialogOpen = true },
                    onRespondReview = { text ->
                        viewModel.respondToReview(text, reviewFailedFallback)
                    },
                    onMessageBuyer = {
                        navController.navigate(
                            MarketApp.messageThread(
                                listingId = detail.listing.id.toString(),
                                threadId = "new",
                            ),
                        )
                    },
                )
            }
        }
    }

    val detail = state.order
    if (detail != null) {
        ShipOrderDialog(
            open = shipDialogOpen,
            initialCarrier = detail.order.carrier,
            initialTracking = detail.order.tracking,
            initialUrl = detail.order.url,
            submitting = state.shipSubmitting,
            errorMessage = state.shipError,
            onSubmit = { carrier, tracking, url ->
                viewModel.shipOrder(carrier, tracking, url, shipSuccessText)
                shipDialogOpen = false
            },
            onDismiss = { shipDialogOpen = false },
        )
        IssueRefundDialog(
            open = refundDialogOpen,
            currency = detail.order.currency ?: Currency.GBP,
            priorRefundedAmount = if (detail.order.refunded > 0L) detail.order.total else 0L,
            priorRefunds = detail.refunds,
            submitting = state.refundSubmitting,
            errorMessage = state.refundError,
            onSubmit = { amount, reason, description ->
                viewModel.refundOrder(amount, reason, description, refundFailedFallback)
                refundDialogOpen = false
            },
            onDismiss = { refundDialogOpen = false },
        )
        val dispute = detail.dispute
        if (dispute != null) {
            DisputeResponseDialog(
                open = disputeDialogOpen,
                disputeStatus = dispute.status,
                existingEvidence = detail.evidence,
                submitting = state.disputeSubmitting,
                errorMessage = state.disputeError,
                onSubmit = { response, _ ->
                    // The API uses a single body string; evidence uploads are
                    // wired through `mochi.attachment.*` on the comptroller
                    // when the staff-side surface lands. For the seller's
                    // first-pass response we forward the text only and
                    // surface evidence count as future work — the dialog
                    // already collects the URIs so a follow-up wiring is
                    // additive, not destructive.
                    viewModel.respondToDispute(response, disputeFailedFallback)
                    disputeDialogOpen = false
                },
                onDismiss = { disputeDialogOpen = false },
            )
        }
    }
}

@Composable
private fun SaleDetailBody(
    detail: OrderDetailResponse,
    state: SaleDetailUiState,
    auditExpanded: Boolean,
    onToggleAudit: () -> Unit,
    onOpenShip: () -> Unit,
    onOpenRefund: () -> Unit,
    onOpenDispute: () -> Unit,
    onRespondReview: (String) -> Unit,
    onMessageBuyer: () -> Unit,
) {
    val order = detail.order
    val listing = detail.listing
    val currency = order.currency ?: Currency.GBP
    val format = LocalFormat.current

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // ---- Summary ----
        item("summary") {
            Card(colors = CardDefaults.outlinedCardColors(), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val photoId = listing.photo?.id
                    if (!photoId.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data("/market/-/photo/$photoId/thumbnail")
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(72.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = listing.title.ifBlank { order.title.orEmpty() },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        order.status?.let { StatusBadge(status = it.name.lowercase()) }
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = stringResource(R.string.market_sale_buyer_label) + ": " +
                                order.buyerName.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.market_sale_order_id_label) + ": #" +
                                order.id,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (order.created > 0L) {
                            Text(
                                text = stringResource(R.string.market_sale_ordered_label) + ": " +
                                    format.formatRelativeTime(order.created),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (order.shipped > 0L) {
                            Text(
                                text = stringResource(R.string.market_sale_shipped_label) + ": " +
                                    format.formatRelativeTime(order.shipped),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (order.completed > 0L) {
                            Text(
                                text = stringResource(R.string.market_sale_completed_label) + ": " +
                                    format.formatRelativeTime(order.completed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // ---- Fees ----
        item("fees") {
            FeeBreakdownPanel(order = order, currency = currency)
        }

        // ---- Shipping actions (physical / shipping deliveries only) ----
        if (
            order.delivery == org.mochios.market.model.DeliveryMethod.SHIPPING &&
            (order.status == OrderStatus.PENDING || order.status == OrderStatus.PAID)
        ) {
            item("shipping-actions") {
                Card(
                    colors = CardDefaults.outlinedCardColors(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.market_sale_section_shipping),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Button(
                            onClick = onOpenShip,
                            enabled = !state.shipSubmitting,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                Icons.Default.LocalShipping,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.market_sale_ship_action))
                        }
                    }
                }
            }
        }

        // ---- Refund action ----
        if (
            order.status == OrderStatus.PAID ||
            order.status == OrderStatus.SHIPPED ||
            order.status == OrderStatus.DELIVERED ||
            order.status == OrderStatus.COMPLETED ||
            order.status == OrderStatus.DISPUTED
        ) {
            item("refund-action") {
                Card(
                    colors = CardDefaults.outlinedCardColors(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.market_sale_section_refund),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        OutlinedButton(
                            onClick = onOpenRefund,
                            enabled = !state.refundSubmitting,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.market_sale_refund_action))
                        }
                    }
                }
            }
        }

        // ---- Dispute section ----
        detail.dispute?.let { dispute ->
            item("dispute") {
                DisputePanel(
                    dispute = dispute,
                    onRespond = onOpenDispute,
                )
            }
        }

        // ---- Review section ----
        detail.review?.let { review ->
            item("review") {
                ReviewPanel(
                    review = review,
                    submitting = state.reviewResponseSubmitting,
                    errorMessage = state.reviewError,
                    onRespond = onRespondReview,
                )
            }
        }

        // ---- Audit timeline (collapsible) ----
        item("audit-toggle") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleAudit)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (auditExpanded) {
                        stringResource(R.string.market_sale_audit_collapse)
                    } else {
                        stringResource(R.string.market_sale_audit_expand)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (auditExpanded) Icons.Default.ExpandLess
                    else Icons.Default.ExpandMore,
                    contentDescription = null,
                )
            }
            if (auditExpanded) {
                AuditTimeline(events = state.audit)
            }
        }

        // ---- Message buyer ----
        item("message-buyer") {
            Spacer(modifier = Modifier.size(8.dp))
            OutlinedButton(
                onClick = onMessageBuyer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.market_sale_message_buyer))
            }
        }
    }
}

@Composable
private fun FeeBreakdownPanel(order: Order, currency: Currency) {
    Card(colors = CardDefaults.outlinedCardColors(), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.market_sale_section_fees),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.size(8.dp))
            FeeRow(
                label = stringResource(R.string.market_sale_fee_gross),
                value = formatPrice(order.total, currency),
            )
            FeeRow(
                label = stringResource(R.string.market_sale_fee_mochi),
                value = "−" + formatPrice(order.fee, currency),
            )
            // Stripe processor fee isn't included on the order row (Stripe's
            // dashboard owns the canonical number). We surface the gap as a
            // help line under the breakdown rather than quote a stale figure.
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            FeeRow(
                label = stringResource(R.string.market_sale_fee_payout),
                value = formatPrice(order.payout, currency),
                emphasised = true,
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = stringResource(R.string.market_sale_fee_stripe_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FeeRow(label: String, value: String, emphasised: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (emphasised) MaterialTheme.typography.titleSmall
            else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = if (emphasised) MaterialTheme.typography.titleSmall
            else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasised) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun DisputePanel(dispute: Dispute, onRespond: () -> Unit) {
    Card(colors = CardDefaults.outlinedCardColors(), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.market_sale_section_dispute),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(status = dispute.status)
            }
            Spacer(modifier = Modifier.size(4.dp))
            if (dispute.opener == "stripe" && dispute.reason.isNotBlank()) {
                Text(
                    text = stringResource(
                        R.string.market_sale_dispute_stripe_reason,
                        dispute.reason,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (dispute.evidenceDue > 0L) {
                Text(
                    text = stringResource(
                        R.string.market_sale_dispute_evidence_due,
                        dispute.evidenceDue.toString(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (dispute.description.isNotBlank()) {
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = dispute.description,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (dispute.response.isNotBlank()) {
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = dispute.response,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
            Button(
                onClick = onRespond,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.market_sale_dispute_respond))
            }
        }
    }
}

@Composable
private fun ReviewPanel(
    review: Review,
    submitting: Boolean,
    errorMessage: String?,
    onRespond: (String) -> Unit,
) {
    Card(colors = CardDefaults.outlinedCardColors(), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.market_sale_section_review),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = review.text,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (review.response.isNotBlank()) {
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text = stringResource(R.string.market_sale_review_response_existing),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = review.response,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                var draft by remember(review.id) { mutableStateOf("") }
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.market_sale_review_no_response_yet),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.size(8.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = {
                        Text(stringResource(R.string.market_sale_review_response_label))
                    },
                    enabled = !submitting,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                Button(
                    enabled = !submitting && draft.isNotBlank(),
                    onClick = { onRespond(draft.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                    }
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        text = if (submitting) {
                            stringResource(R.string.market_sale_review_response_submitting)
                        } else {
                            stringResource(R.string.market_sale_review_response_submit)
                        },
                    )
                }
            }
        }
    }
}
