// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.selling

import android.content.ActivityNotFoundException
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.util.webUri
import org.mochios.market.R
import org.mochios.market.lib.formatPrice
import org.mochios.market.model.Currency
import org.mochios.market.model.DeliveryMethod
import org.mochios.market.model.Dispute
import org.mochios.market.model.Order
import org.mochios.market.model.OrderDetailResponse
import org.mochios.market.model.OrderStatus
import org.mochios.market.model.Review
import org.mochios.market.navigation.MarketApp
import org.mochios.market.ui.buying.WriteReviewForm
import org.mochios.market.ui.components.AuditHistoryCard
import org.mochios.market.ui.components.MarketStatusBadge
import org.mochios.market.ui.components.RatingStarGold
import org.mochios.market.ui.components.RatingStars
import org.mochios.market.ui.components.disputeReasonLabel
import org.mochios.market.ui.dialog.DisputeResponseDialog
import org.mochios.market.ui.dialog.IssueRefundDialog
import org.mochios.market.ui.dialog.ShipOrderDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleDetailScreen(
    navController: NavController,
    viewModel: SaleDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var shipDialogOpen by remember { mutableStateOf(false) }
    var refundDialogOpen by remember { mutableStateOf(false) }
    var disputeDialogOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SaleDetailEvent.Toast -> snackbarHostState.showSnackbar(event.message)
                // Dialogs close here rather than on submit, so a failed
                // mutation leaves the dialog up to show its error.
                is SaleDetailEvent.Completed -> when (event.dialog) {
                    SaleDialog.SHIP -> shipDialogOpen = false
                    SaleDialog.REFUND -> refundDialogOpen = false
                    SaleDialog.DISPUTE -> disputeDialogOpen = false
                }
            }
        }
    }

    val shipFailedFallback = stringResource(R.string.market_sale_ship_failed)
    val shipSuccessText = stringResource(R.string.market_sale_ship_success)
    val refundFailedFallback = stringResource(R.string.market_refund_dialog_failed)
    val disputeFailedFallback = stringResource(R.string.market_dispute_dialog_failed)
    val reviewFailedFallback = stringResource(R.string.market_sale_review_response_failed)
    val reviewSubmitFailedFallback = stringResource(R.string.market_purchase_review_failed)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.order?.listing?.title.orEmpty()
                            .ifBlank { state.order?.order?.title.orEmpty() }
                            .ifBlank { stringResource(R.string.market_sale_title) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    MochiIconButton(onClick = { navController.popBackStack() }) {
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
                state.error != null && detail == null -> ErrorState(
                    message = state.error!!.userMessage()
                        .ifEmpty { stringResource(R.string.market_sale_load_failed) },
                    onRetry = viewModel::load,
                )
                detail != null -> SaleDetailBody(
                    detail = detail,
                    state = state,
                    onOpenShip = { shipDialogOpen = true },
                    onOpenRefund = { refundDialogOpen = true },
                    onOpenDispute = { disputeDialogOpen = true },
                    onRespondReview = { text ->
                        viewModel.respondToReview(text, reviewFailedFallback)
                    },
                    onSubmitReview = { rating, body ->
                        viewModel.submitReview(rating, body, reviewSubmitFailedFallback)
                    },
                    onOpenBuyer = { account ->
                        if (account.isNotBlank()) {
                            navController.navigate(MarketApp.publicProfile(account))
                        }
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
                viewModel.shipOrder(carrier, tracking, url, shipSuccessText, shipFailedFallback)
            },
            onDismiss = { shipDialogOpen = false },
        )
        IssueRefundDialog(
            open = refundDialogOpen,
            currency = detail.order.currency ?: Currency.GBP,
            // `refunded` is the cumulative refunded amount in minor units, not
            // a flag — the comptroller accumulates into it — so it is the value
            // itself that the seller needs when deciding how much more to
            // refund. Treating it as a flag reported the whole order total.
            priorRefundedAmount = detail.order.refunded,
            priorRefunds = detail.refunds,
            submitting = state.refundSubmitting,
            errorMessage = state.refundError,
            onSubmit = { amount, reason, description ->
                viewModel.refundOrder(amount, reason, description, refundFailedFallback)
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
                    // Evidence URIs are collected but not sent: the API takes a
                    // single body string and has no evidence upload yet.
                    viewModel.respondToDispute(response, disputeFailedFallback)
                },
                onDismiss = { disputeDialogOpen = false },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SaleDetailBody(
    detail: OrderDetailResponse,
    state: SaleDetailUiState,
    onOpenShip: () -> Unit,
    onOpenRefund: () -> Unit,
    onOpenDispute: () -> Unit,
    onRespondReview: (String) -> Unit,
    onSubmitReview: (Int, String) -> Unit,
    onOpenBuyer: (String) -> Unit,
    onMessageBuyer: () -> Unit,
) {
    val order = detail.order
    val canShip = order.delivery == DeliveryMethod.SHIPPING &&
        (order.status == OrderStatus.PENDING || order.status == OrderStatus.PAID)
    val canRefund = order.refunded < order.total && (
        order.status == OrderStatus.PAID ||
            order.status == OrderStatus.SHIPPED ||
            order.status == OrderStatus.DELIVERED ||
            order.status == OrderStatus.COMPLETED ||
            order.status == OrderStatus.DISPUTED
        )

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item("summary") {
            SaleSummaryCard(order = order, onOpenBuyer = onOpenBuyer)
        }

        item("actions") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MochiOutlinedButton(onClick = onMessageBuyer) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.market_sale_message_buyer))
                }
                if (canShip) {
                    MochiButton(onClick = onOpenShip, enabled = !state.shipSubmitting) {
                        Icon(
                            Icons.Default.LocalShipping,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.market_sale_ship_action))
                    }
                }
                if (canRefund) {
                    MochiOutlinedButton(onClick = onOpenRefund, enabled = !state.refundSubmitting) {
                        Text(stringResource(R.string.market_sale_refund_action))
                    }
                }
            }
        }

        if (order.delivery == DeliveryMethod.SHIPPING && order.addressLine1.isNotBlank()) {
            item("address") {
                ShippingAddressCard(order = order)
            }
        }

        detail.dispute?.let { dispute ->
            item("dispute") {
                DisputePanel(
                    dispute = dispute,
                    onRespond = onOpenDispute,
                )
            }
        }

        val ownReview = detail.review
        if (ownReview != null) {
            item("review-buyer") {
                OwnReviewCard(review = ownReview)
            }
        } else if (detail.canReview) {
            item("review-buyer") {
                WriteReviewForm(
                    submitting = state.reviewSubmitting,
                    onSubmit = onSubmitReview,
                )
            }
        }

        detail.peerReview?.let { review ->
            item("review") {
                ReviewPanel(
                    review = review,
                    buyerName = order.buyerName.orEmpty().ifBlank { review.reviewerName.orEmpty() },
                    submitting = state.reviewResponseSubmitting,
                    errorMessage = state.reviewError,
                    onOpenBuyer = { onOpenBuyer(order.buyer.ifBlank { review.reviewer }) },
                    onRespond = onRespondReview,
                )
            }
        }

        if (state.audit.isNotEmpty()) {
            item("history") {
                AuditHistoryCard(events = state.audit)
            }
        }
    }
}

/**
 * The order's facts as label and value rows, as the web's sale page lays them
 * out: status, buyer, total, payout, any refund, delivery and purchase time.
 */
@Composable
private fun SaleSummaryCard(order: Order, onOpenBuyer: (String) -> Unit) {
    val currency = order.currency ?: Currency.GBP
    val format = LocalFormat.current
    MochiCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            order.status?.let { status ->
                SummaryRow(label = stringResource(R.string.market_listings_filter_status)) {
                    MarketStatusBadge(status = status.name.lowercase())
                }
            }
            val buyer = order.buyerName.orEmpty().ifBlank { order.buyerFingerprint.orEmpty() }
            if (buyer.isNotBlank()) {
                SummaryRow(label = stringResource(R.string.market_sale_buyer_label)) {
                    Text(
                        text = buyer,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = TextDecoration.Underline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onOpenBuyer(order.buyer) },
                    )
                }
            }
            SummaryRow(label = stringResource(R.string.market_purchase_total)) {
                Text(
                    text = formatPrice(order.total, currency),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            SummaryRow(label = stringResource(R.string.market_fee_preview_payout)) {
                Text(
                    text = formatPrice(order.payout, currency),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (order.refunded > 0L) {
                SummaryRow(label = stringResource(R.string.market_purchase_refunded)) {
                    Text(
                        text = formatPrice(order.refunded, currency),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            order.delivery?.let { delivery ->
                SummaryRow(label = stringResource(R.string.market_purchase_delivery)) {
                    SummaryValue(text = stringResource(deliveryLabel(delivery)))
                }
            }
            if (order.created > 0L) {
                SummaryRow(label = stringResource(R.string.market_purchase_purchased)) {
                    SummaryValue(text = format.formatDateTime(order.created))
                }
            }
            if (order.shipped > 0L) {
                SummaryRow(label = stringResource(R.string.market_sale_shipped_label)) {
                    SummaryValue(text = format.formatDateTime(order.shipped))
                }
            }
            if (order.carrier.isNotBlank()) {
                SummaryRow(label = stringResource(R.string.market_sale_carrier_label)) {
                    SummaryValue(text = order.carrier)
                }
            }
            TrackingRow(order = order)
        }
    }
}

/**
 * The order's tracking number, opening the seller's tracking link when it is
 * a web address; with a link but no number, the link alone as Track package.
 */
@Composable
private fun TrackingRow(order: Order) {
    val context = LocalContext.current
    val target = webUri(order.url)
    val text = order.tracking.ifBlank {
        if (target != null) {
            stringResource(R.string.market_purchase_track)
        } else {
            ""
        }
    }
    if (text.isBlank()) {
        return
    }
    SummaryRow(label = stringResource(R.string.market_sale_tracking_label)) {
        if (target == null) {
            SummaryValue(text = text)
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = TextDecoration.Underline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable {
                    try {
                        CustomTabsIntent.Builder().build().launchUrl(context, target)
                    } catch (_: ActivityNotFoundException) {
                    }
                },
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        value()
    }
}

@Composable
private fun SummaryValue(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge)
}

private fun deliveryLabel(delivery: DeliveryMethod): Int = when (delivery) {
    DeliveryMethod.SHIPPING -> R.string.market_checkout_delivery_shipping
    DeliveryMethod.PICKUP -> R.string.market_checkout_delivery_pickup
    DeliveryMethod.DOWNLOAD -> R.string.market_checkout_delivery_download
}

@Composable
private fun ShippingAddressCard(order: Order) {
    val locality = listOf(order.addressCity, order.addressRegion)
        .filter { part -> part.isNotBlank() }
        .joinToString(", ")
    val lines = listOf(
        order.addressName,
        order.addressLine1,
        order.addressLine2,
        listOf(locality, order.addressPostcode)
            .filter { part -> part.isNotBlank() }
            .joinToString(" "),
        order.addressCountry,
    ).filter { line -> line.isNotBlank() }
    MochiCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.market_checkout_address_heading),
                style = MaterialTheme.typography.titleMedium,
            )
            lines.forEach { line ->
                Text(text = line, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ReviewHeading(title: @Composable () -> Unit, rating: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            title()
        }
        RatingStars(
            rating = rating.toFloat(),
            showCount = false,
            size = 20.dp,
            tint = RatingStarGold,
        )
    }
}

/** The seller's own review of the buyer, read-only once it is sent. */
@Composable
private fun OwnReviewCard(review: Review) {
    MochiCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReviewHeading(
                title = {
                    Text(
                        text = stringResource(R.string.market_sale_review_of_buyer),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                rating = review.rating,
            )
            if (review.text.isNotBlank()) {
                Text(text = review.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun DisputePanel(dispute: Dispute, onRespond: () -> Unit) {
    MochiCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.market_sale_section_dispute),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                MarketStatusBadge(status = dispute.status)
            }
            Spacer(modifier = Modifier.size(4.dp))
            if (dispute.opener == "stripe" && dispute.reason.isNotBlank()) {
                Text(
                    text = stringResource(
                        R.string.market_sale_dispute_stripe_reason,
                        disputeReasonLabel(dispute.reason, dispute.opener),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (dispute.evidenceDue > 0L) {
                Text(
                    text = stringResource(
                        R.string.market_sale_dispute_evidence_due,
                        LocalFormat.current.formatDateTime(dispute.evidenceDue),
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
            MochiButton(
                onClick = onRespond,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.market_sale_dispute_respond))
            }
        }
    }
}

/**
 * The buyer's review of this sale, headed "Review from" with the buyer's name
 * linking to their profile, and a box to respond to it until a response is sent.
 */
@Composable
private fun ReviewPanel(
    review: Review,
    buyerName: String,
    submitting: Boolean,
    errorMessage: String?,
    onOpenBuyer: () -> Unit,
    onRespond: (String) -> Unit,
) {
    MochiCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReviewHeading(
                title = {
                    if (buyerName.isBlank()) {
                        Text(
                            text = stringResource(R.string.market_sale_review_buyer),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    } else {
                        ReviewFromText(name = buyerName, onClick = onOpenBuyer)
                    }
                },
                rating = review.rating,
            )
            if (review.text.isNotBlank()) {
                Text(text = review.text, style = MaterialTheme.typography.bodyLarge)
            }
            if (review.response.isNotBlank()) {
                Column {
                    Text(
                        text = stringResource(R.string.market_sale_review_response_existing),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = review.response,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                var draft by remember(review.id) { mutableStateOf("") }
                MochiTextField(
                    value = draft,
                    onValueChange = { value -> draft = value },
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
                MochiButton(
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
                            Icons.AutoMirrored.Filled.Send,
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

/** "Review from [name]" with only the name underlined and tappable. */
@Composable
private fun ReviewFromText(name: String, onClick: () -> Unit) {
    val template = stringResource(R.string.market_purchase_review_from, NAME_SLOT)
    val slot = template.indexOf(NAME_SLOT)
    val text = buildAnnotatedString {
        if (slot < 0) {
            append(template)
            return@buildAnnotatedString
        }
        append(template.substring(0, slot))
        withLink(
            LinkAnnotation.Clickable(
                tag = "buyer",
                styles = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline)),
                linkInteractionListener = { _ -> onClick() },
            ),
        ) {
            append(name)
        }
        append(template.substring(slot + NAME_SLOT.length))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
    )
}

private const val NAME_SLOT = "\uE000"
