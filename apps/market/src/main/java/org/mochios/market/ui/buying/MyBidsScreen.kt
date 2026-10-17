// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.buying

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.InfiniteList
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiTab
import org.mochios.android.ui.components.MochiTabRow
import org.mochios.market.R
import org.mochios.market.lib.formatPrice
import org.mochios.market.model.Bid
import org.mochios.market.model.Currency
import org.mochios.market.navigation.MarketApp
import org.mochios.market.ui.components.BidStatusBadge
import org.mochios.market.ui.components.MarketLayout
import org.mochios.market.ui.components.MarketListPadding
import org.mochios.market.ui.components.MarketListPrice
import org.mochios.market.ui.components.MarketListRow
import org.mochios.market.ui.components.MarketListSpacing

@Composable
fun MyBidsScreen(
    navController: NavController,
    viewModel: MyBidsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    MarketLayout(
        navController = navController,
        currentRoute = MarketApp.BIDS,
        titleRes = R.string.market_bids_title,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            BidsTabRow(active = state.filter, onSelected = { viewModel.setFilter(it) })
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    state.error != null -> ErrorState(error = state.error!!) { viewModel.setFilter(state.filter) }
                    state.bids.isEmpty() -> EmptyState(
                        icon = Icons.Default.Gavel,
                        title = stringResource(R.string.market_bids_empty),
                    )
                    else -> InfiniteList(
                        items = state.bids,
                        isLoading = state.isLoadingMore,
                        hasMore = state.bids.size < state.total,
                        onLoadMore = { viewModel.loadMore() },
                        contentPadding = MarketListPadding,
                        verticalArrangement = MarketListSpacing,
                    ) { bid ->
                        BidRow(
                            bid = bid,
                            onClick = { id, title ->
                                navController.navigate(MarketApp.listingDetail(id, title))
                            },
                            onCompletePurchase = { listingId ->
                                navController.navigate(MarketApp.checkout(listingId.toString()))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BidsTabRow(active: BidsFilter, onSelected: (BidsFilter) -> Unit) {
    val tabs = listOf(
        BidsFilter.ALL to R.string.market_bids_tab_all,
        BidsFilter.ACTIVE to R.string.market_bids_tab_active,
        BidsFilter.OUTBID to R.string.market_bids_tab_outbid,
        BidsFilter.WON to R.string.market_bids_tab_won,
        BidsFilter.LOST to R.string.market_bids_tab_lost,
    )
    val activeIndex = tabs.indexOfFirst { it.first == active }.coerceAtLeast(0)
    MochiTabRow(
        tabs = tabs.map { (_, labelRes) -> MochiTab(stringResource(labelRes)) },
        selectedIndex = activeIndex,
        onSelect = { index -> onSelected(tabs[index].first) },
    )
}

@Composable
private fun BidRow(
    bid: Bid,
    onClick: (String, String?) -> Unit,
    onCompletePurchase: (String) -> Unit,
) {
    val format = LocalFormat.current
    val currency = bid.currency ?: Currency.GBP
    val title = bid.title ?: stringResource(R.string.market_purchases_auction_label, bid.auction ?: "")
    val current = bid.currentBid
    val closes = bid.closes ?: 0L
    val subtitle = listOf(
        stringResource(R.string.market_bids_your_bid, formatPrice(bid.amount, currency)),
        when {
            closes > 0L -> remainingLabel(closes)
            bid.created > 0L -> format.formatTimestamp(bid.created)
            else -> ""
        },
    ).filter { part -> part.isNotBlank() }.joinToString(" · ")
    MarketListRow(
        title = title,
        onClick = { bid.listing?.let { listing -> onClick(listing, bid.title) } },
        subtitle = subtitle,
        badges = {
            BidStatusBadge(status = bid.status)
            if (bid.status == org.mochios.market.model.BidStatus.WON && bid.listing != null) {
                MochiButton(
                    onClick = { onCompletePurchase(bid.listing) },
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
            }
        },
        trailing = {
            MarketListPrice(
                text = formatPrice(current?.takeIf { amount -> amount > 0L } ?: bid.amount, currency),
            )
        },
    )
}

@Composable
private fun remainingLabel(closes: Long): String {
    // closes is epoch seconds. Tick once per second via a state var so
    // the row re-renders without rebuilding the screen.
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000L) }
    LaunchedEffect(closes) {
        while (now < closes) {
            delay(1_000L)
            now = System.currentTimeMillis() / 1000L
        }
    }
    val remaining = closes - now
    if (remaining <= 0L) return stringResource(R.string.market_bids_ended)
    val hours = remaining / 3600
    val minutes = (remaining % 3600) / 60
    val seconds = remaining % 60
    val pretty = when {
        hours >= 24 -> "${hours / 24}d ${hours % 24}h"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
    return stringResource(R.string.market_bids_ends_in, pretty)
}
