package org.mochios.market.ui.buying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.InfiniteList
import org.mochios.android.ui.components.MochiScaffold
import org.mochios.market.R
import org.mochios.market.lib.formatFingerprint
import org.mochios.market.lib.formatPrice
import org.mochios.market.model.Bid
import org.mochios.market.model.Currency
import org.mochios.market.model.Order
import org.mochios.market.navigation.MarketApp
import org.mochios.market.ui.components.StatusBadge

/**
 * Buyer-side order list.
 *
 * Top strip: pending-auction-completion CTAs (status = `"won"` bids
 * whose payment Stripe checkout hasn't run yet). Tapping opens the
 * checkout flow at the same [MarketApp.CHECKOUT] route with the
 * listing id; the checkout VM detects the won bid and posts to
 * `orders/auction`.
 *
 * Below: paginated list of orders. Tap → [MarketApp.purchaseDetail].
 */
@Composable
fun MyPurchasesScreen(
    navController: NavController,
    viewModel: MyPurchasesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    MochiScaffold(
        title = stringResource(R.string.market_purchases_title),
        onBack = { navController.popBackStack() },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isInitialLoading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.error != null -> ErrorState(error = state.error!!, onRetry = { viewModel.load() })
                state.orders.isEmpty() && state.wonBids.isEmpty() -> EmptyState(
                    icon = Icons.Default.ShoppingCart,
                    title = stringResource(R.string.market_purchases_empty),
                )
                else -> PurchasesList(
                    state = state,
                    onLoadMore = { viewModel.loadMore() },
                    onOrderClick = { id ->
                        navController.navigate(MarketApp.purchaseDetail(id.toString()))
                    },
                    onCompletePurchase = { listingId ->
                        navController.navigate(MarketApp.checkout(listingId.toString()))
                    },
                )
            }
        }
    }
}

@Composable
private fun PurchasesList(
    state: MyPurchasesUiState,
    onLoadMore: () -> Unit,
    onOrderClick: (String) -> Unit,
    onCompletePurchase: (String) -> Unit,
) {
    // Build a synthetic item list so the InfiniteList can render the
    // won-bid strip above the order rows in a single LazyColumn pass.
    // Each entry is one of: WonBid, OrderRow.
    val rows = remember(state.wonBids, state.orders) {
        buildList<PurchasesRow> {
            state.wonBids.forEach { add(PurchasesRow.WonBid(it)) }
            state.orders.forEach { add(PurchasesRow.OrderRow(it)) }
        }
    }
    InfiniteList(
        items = rows,
        isLoading = state.isLoadingMore,
        hasMore = state.orders.size < state.total,
        onLoadMore = onLoadMore,
    ) { row ->
        when (row) {
            is PurchasesRow.WonBid -> WonBidRow(row.bid, onCompletePurchase)
            is PurchasesRow.OrderRow -> OrderRowCard(row.order, onOrderClick)
        }
    }
}

private sealed interface PurchasesRow {
    data class WonBid(val bid: Bid) : PurchasesRow
    data class OrderRow(val order: Order) : PurchasesRow
}

@Composable
private fun WonBidRow(bid: Bid, onCompletePurchase: (String) -> Unit) {
    val listingId = bid.listing ?: return
    val currency = bid.currency ?: Currency.GBP
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .clickable { onCompletePurchase(listingId) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Gavel,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Spacer(Modifier.padding(end = 8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bid.title
                    ?: stringResource(R.string.market_purchases_auction_label, bid.auction ?: ""),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.market_purchases_won_for, formatPrice(bid.amount, currency)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        Button(onClick = { onCompletePurchase(listingId) }) {
            Icon(
                Icons.Default.ShoppingCart,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.market_purchases_complete_purchase))
        }
    }
}

@Composable
private fun OrderRowCard(order: Order, onClick: (String) -> Unit) {
    val format = LocalFormat.current
    val currency = order.currency ?: Currency.GBP
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick(order.id) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                order.title ?: stringResource(R.string.market_purchases_order_label, order.id),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sellerLabel = order.sellerName?.takeIf { it.isNotBlank() }
                ?: formatFingerprint(order.seller)
            Text(
                "$sellerLabel · ${format.formatTimestamp(order.created)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatPrice(order.total, currency))
            if (order.refunded > 0 && order.refunded < order.total) {
                Text(
                    stringResource(R.string.market_purchases_refunded_partial, formatPrice(order.refunded, currency)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            StatusBadge(status = order.status?.name?.lowercase() ?: "")
        }
    }
}

