// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.buying

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.InfiniteList
import org.mochios.android.ui.components.MochiButton
import org.mochios.market.R
import org.mochios.market.lib.formatPrice
import org.mochios.market.model.Bid
import org.mochios.market.model.Currency
import org.mochios.market.model.Order
import org.mochios.market.navigation.MarketApp
import org.mochios.market.ui.components.MarketLayout
import org.mochios.market.ui.components.MarketListNote
import org.mochios.market.ui.components.MarketListPadding
import org.mochios.market.ui.components.MarketListPrice
import org.mochios.market.ui.components.MarketListRow
import org.mochios.market.ui.components.MarketListSpacing
import org.mochios.market.ui.components.MarketStatusBadge

@Composable
fun MyPurchasesScreen(
    navController: NavController,
    viewModel: MyPurchasesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    MarketLayout(
        navController = navController,
        currentRoute = MarketApp.PURCHASES,
        titleRes = R.string.market_purchases_title,
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
        contentPadding = MarketListPadding,
        verticalArrangement = MarketListSpacing,
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
    MarketListRow(
        title = bid.title
            ?: stringResource(R.string.market_purchases_auction_label, bid.auction ?: ""),
        onClick = { onCompletePurchase(listingId) },
        subtitle = stringResource(
            R.string.market_purchases_won_for,
            formatPrice(bid.amount, currency),
        ),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        badges = {
            MochiButton(
                onClick = { onCompletePurchase(listingId) },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp),
            ) {
                Icon(
                    Icons.Default.ShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.market_purchases_complete_purchase))
            }
        },
    )
}

@Composable
private fun OrderRowCard(order: Order, onClick: (String) -> Unit) {
    val format = LocalFormat.current
    val currency = order.currency ?: Currency.GBP
    val seller = order.sellerName?.takeIf { name -> name.isNotBlank() }
        ?: order.sellerFingerprint.orEmpty()
    val subtitle = listOf(seller, format.formatTimestamp(order.created))
        .filter { part -> part.isNotBlank() }
        .joinToString(" · ")
    MarketListRow(
        title = order.title ?: stringResource(R.string.market_purchases_order_label, order.id),
        onClick = { onClick(order.id) },
        subtitle = subtitle,
        badges = order.status?.let { status ->
            { MarketStatusBadge(status = status.name.lowercase()) }
        },
        trailing = {
            MarketListPrice(text = formatPrice(order.total, currency))
            if (order.refunded > 0 && order.refunded < order.total) {
                MarketListNote(
                    text = stringResource(
                        R.string.market_purchases_refunded_partial,
                        formatPrice(order.refunded, currency),
                    ),
                )
            }
        },
    )
}

