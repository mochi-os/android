package org.mochios.market.ui.buying

import android.content.ActivityNotFoundException
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiScaffold
import org.mochios.market.R
import org.mochios.market.lib.formatFingerprint
import org.mochios.market.lib.formatPrice
import org.mochios.market.model.Asset
import org.mochios.market.model.Currency
import org.mochios.market.model.Dispute
import org.mochios.market.model.Listing
import org.mochios.market.model.Order
import org.mochios.market.model.OrderStatus
import org.mochios.market.model.Review
import org.mochios.market.navigation.MarketApp
import org.mochios.market.ui.components.AuditTimeline
import org.mochios.market.ui.components.StatusBadge
import org.mochios.market.ui.dialog.RequestRefundDialog

/**
 * Buyer-side order detail screen.
 *
 * Layout:
 *  - Summary card: listing title + seller + dates + price breakdown.
 *  - Tracking row (when the seller has shipped).
 *  - Asset downloads (when the listing has digital files).
 *  - Action buttons: Confirm receipt, Message seller, Request refund.
 *  - Dispute section (if any).
 *  - Review form (when can_review) or submitted review (post-submit).
 *  - Collapsible AuditTimeline.
 */
@Composable
fun PurchaseDetailScreen(
    navController: NavController,
    viewModel: PurchaseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var refundDialogOpen by remember { mutableStateOf(false) }
    var auditExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PurchaseDetailEvent.Toast -> snackbar.showSnackbar(event.message)
                is PurchaseDetailEvent.OpenUrl -> openUrl(context, event.url, snackbar, scope)
                is PurchaseDetailEvent.DownloadAsset -> {
                    // Streaming the asset bytes is handled by another agent's
                    // download manager; the buyer-side screen surfaces a toast
                    // hint so the action is visible during integration.
                    snackbar.showSnackbar(
                        context.getString(R.string.market_asset_download)
                            + " #" + event.assetId,
                    )
                }
            }
        }
    }

    val detail = state.detail
    val title = detail?.listing?.title
        ?: stringResource(R.string.market_purchase_title)

    MochiScaffold(title = title, onBack = { navController.popBackStack() }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
            when {
                state.isLoading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.error != null -> ErrorState(error = state.error!!) { viewModel.load() }
                detail == null -> Text(
                    stringResource(R.string.market_purchase_not_found),
                    modifier = Modifier.padding(16.dp),
                )
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OrderSummaryCard(detail.order, detail.listing)
                    if (detail.order.carrier.isNotBlank() || detail.order.url.isNotBlank()) {
                        TrackingCard(detail.order)
                    }
                    if (detail.assets.isNotEmpty()) {
                        DigitalAssetsCard(detail.assets, onDownload = { viewModel.requestDownload(it) })
                    }
                    PrimaryActionsCard(
                        order = detail.order,
                        submitting = state.submitting,
                        onConfirm = { viewModel.confirmDelivery() },
                        onMessageSeller = {
                            navController.navigate(
                                MarketApp.messageThread(detail.listing.id.toString(), "new"),
                            )
                        },
                        onRequestRefund = { refundDialogOpen = true },
                    )
                    detail.dispute?.let { DisputeCard(it, detail.order.total, detail.order.currency ?: Currency.GBP) }
                    if (detail.canReview) {
                        WriteReviewForm(
                            submitting = state.submitting,
                            onSubmit = { rating, body -> viewModel.submitReview(rating, body) },
                        )
                    } else if (detail.review != null) {
                        WriteReviewForm(submitting = false, submitted = detail.review, onSubmit = { _, _ -> })
                    }
                    detail.peerReview?.let { PeerReviewCard(it) }
                    AuditCollapsible(
                        events = state.audit,
                        expanded = auditExpanded,
                        onToggle = { auditExpanded = !auditExpanded },
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (refundDialogOpen && detail != null) {
        RequestRefundDialog(
            orderTotal = detail.order.total,
            currency = detail.order.currency ?: Currency.GBP,
            submitting = state.submitting,
            onDismiss = { refundDialogOpen = false },
            onSubmit = { amount, reason, description ->
                refundDialogOpen = false
                viewModel.requestRefund(amount, reason, description)
            },
        )
    }
}


@Composable
private fun OrderSummaryCard(order: Order, listing: Listing) {
    val format = LocalFormat.current
    val currency = order.currency ?: Currency.GBP
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.outlinedCardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.market_purchase_summary),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(status = order.status?.name?.lowercase() ?: "")
            }
            Text(
                listing.title.ifBlank { "Order #${order.id}" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            SummaryRow(
                label = stringResource(R.string.market_purchase_order_label),
                value = "#${order.id}",
            )
            SummaryRow(
                label = stringResource(R.string.market_purchase_seller),
                value = order.sellerName?.takeIf { it.isNotBlank() } ?: formatFingerprint(order.seller),
            )
            SummaryRow(
                label = stringResource(R.string.market_purchase_delivery),
                value = order.delivery?.name?.lowercase() ?: "",
            )
            SummaryRow(
                label = stringResource(R.string.market_purchase_purchased),
                value = format.formatTimestamp(order.created),
            )
            HorizontalDivider()
            if (order.item > 0L) {
                SummaryRow(
                    label = stringResource(R.string.market_purchase_item),
                    value = formatPrice(order.item, currency),
                )
            }
            if (order.postage > 0L) {
                SummaryRow(
                    label = stringResource(R.string.market_purchase_shipping_line),
                    value = formatPrice(order.postage, currency),
                )
            }
            SummaryRow(
                label = stringResource(R.string.market_purchase_total),
                value = formatPrice(order.total, currency),
                emphasised = true,
            )
            if (order.refunded > 0L) {
                val labelRes = if (order.refunded < order.total) {
                    R.string.market_purchase_refunded_partial
                } else {
                    R.string.market_purchase_refunded
                }
                SummaryRow(
                    label = stringResource(labelRes),
                    value = formatPrice(order.refunded, currency),
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, emphasised: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = if (emphasised) MaterialTheme.typography.titleSmall
            else MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TrackingCard(order: Order) {
    val context = LocalContext.current
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.outlinedCardColors()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.LocalShipping, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                if (order.carrier.isNotBlank()) {
                    Text(order.carrier, style = MaterialTheme.typography.titleSmall)
                }
                if (order.tracking.isNotBlank()) {
                    Text(
                        order.tracking,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (order.url.isNotBlank()) {
                TextButton(onClick = {
                    try {
                        CustomTabsIntent.Builder().build().launchUrl(context, order.url.toUri())
                    } catch (_: ActivityNotFoundException) { /* no-op */ }
                }) {
                    Text(stringResource(R.string.market_purchase_track))
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun DigitalAssetsCard(assets: List<Asset>, onDownload: (Long) -> Unit) {
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.outlinedCardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.market_purchase_download_section),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                if (assets.size == 1) stringResource(R.string.market_purchase_download_one)
                else stringResource(R.string.market_purchase_download_many, assets.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            assets.forEach { asset ->
                OutlinedButton(
                    onClick = { onDownload(asset.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        asset.filename.ifBlank { "Asset #${asset.id}" },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryActionsCard(
    order: Order,
    submitting: Boolean,
    onConfirm: () -> Unit,
    onMessageSeller: () -> Unit,
    onRequestRefund: () -> Unit,
) {
    val showConfirm = order.status == OrderStatus.SHIPPED ||
        order.status == OrderStatus.DELIVERED ||
        (order.status == OrderStatus.PAID && order.delivery == org.mochios.market.model.DeliveryMethod.PICKUP)
    val canRequestRefund = order.status != OrderStatus.PENDING &&
        order.status != OrderStatus.REFUNDED &&
        order.status != OrderStatus.CANCELLED &&
        order.status != OrderStatus.DISPUTED &&
        order.refunded < order.total

    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.outlinedCardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showConfirm) {
                Button(
                    onClick = onConfirm,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) {
                    Text(stringResource(R.string.market_purchase_confirm_delivery))
                }
            }
            OutlinedButton(onClick = onMessageSeller, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.market_purchase_message_seller))
            }
            if (canRequestRefund) {
                TextButton(onClick = onRequestRefund, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.market_purchase_request_refund))
                }
            }
        }
    }
}

@Composable
private fun DisputeCard(dispute: Dispute, orderTotal: Long, currency: Currency) {
    val isChargeback = dispute.opener == "stripe"
    val reasonLabel = chargebackLabel(dispute.reason)
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.outlinedCardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isChargeback) stringResource(R.string.market_purchase_dispute_chargeback, reasonLabel)
                    else stringResource(R.string.market_purchase_dispute_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(status = dispute.status)
            }
            if (isChargeback) {
                Text(
                    stringResource(R.string.market_purchase_dispute_chargeback_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (dispute.reason.isNotBlank()) {
                    SummaryRow(
                        label = stringResource(R.string.market_purchase_dispute_reason),
                        value = dispute.reason,
                    )
                }
                if (dispute.description.isNotBlank()) {
                    Text(
                        stringResource(R.string.market_purchase_dispute_details),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(dispute.description, style = MaterialTheme.typography.bodyMedium)
                }
                if (dispute.response.isNotBlank()) {
                    Text(
                        stringResource(R.string.market_purchase_dispute_response),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(dispute.response, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (dispute.refundAmount > 0L) {
                SummaryRow(
                    label = if (dispute.refundAmount < orderTotal)
                        stringResource(R.string.market_purchase_refunded_partial)
                    else stringResource(R.string.market_purchase_refunded),
                    value = formatPrice(dispute.refundAmount, currency),
                )
            }
            if (dispute.resolution.isNotBlank()) {
                Text(
                    stringResource(R.string.market_purchase_dispute_resolution),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(dispute.resolution, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PeerReviewCard(review: Review) {
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.outlinedCardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(
                    R.string.market_purchase_review_from,
                    review.reviewerName ?: formatFingerprint(review.reviewer),
                ),
                style = MaterialTheme.typography.titleSmall,
            )
            if (review.text.isNotBlank()) {
                Text(review.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AuditCollapsible(
    events: List<org.mochios.market.model.AuditEvent>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    if (events.isEmpty()) return
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.outlinedCardColors()) {
        Column(Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Inventory, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.market_purchase_audit_section),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                }
            }
            if (expanded) {
                AuditTimeline(events = events, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
    }
}

@Composable
private fun chargebackLabel(reason: String): String {
    val mapped = when (reason) {
        "fraudulent" -> R.string.market_chargeback_reason_fraudulent
        "duplicate" -> R.string.market_chargeback_reason_duplicate
        "general" -> R.string.market_chargeback_reason_general
        "subscription_canceled" -> R.string.market_chargeback_reason_subscription_canceled
        "unrecognized" -> R.string.market_chargeback_reason_unrecognized
        "product_not_received" -> R.string.market_chargeback_reason_product_not_received
        "product_unacceptable" -> R.string.market_chargeback_reason_product_unacceptable
        "credit_not_processed" -> R.string.market_chargeback_reason_credit_not_processed
        "customer_initiated" -> R.string.market_chargeback_reason_customer_initiated
        "debit_not_authorized" -> R.string.market_chargeback_reason_debit_not_authorized
        "incorrect_account_details" -> R.string.market_chargeback_reason_incorrect_account_details
        "insufficient_funds" -> R.string.market_chargeback_reason_insufficient_funds
        "bank_cannot_process" -> R.string.market_chargeback_reason_bank_cannot_process
        "check_returned" -> R.string.market_chargeback_reason_check_returned
        else -> null
    }
    return if (mapped != null) stringResource(mapped) else reason.replace('_', ' ')
}

private fun openUrl(
    context: android.content.Context,
    url: String,
    snackbar: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    try {
        CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
    } catch (_: ActivityNotFoundException) {
        scope.launch { snackbar.showSnackbar("No browser available") }
    }
}
