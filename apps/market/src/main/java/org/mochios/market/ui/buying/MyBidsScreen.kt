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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.InfiniteList
import org.mochios.android.ui.components.MochiScaffold
import org.mochios.market.R
import org.mochios.market.lib.formatPrice
import org.mochios.market.model.Bid
import org.mochios.market.model.Currency
import org.mochios.market.navigation.MarketApp
import org.mochios.market.ui.components.StatusBadge

/**
 * Buyer-side list of bids placed across all auctions.
 *
 * TabRow filters: All / Active / Outbid / Won / Lost (matches web's
 * `apps/market/web/src/features/buying/my-bids-page.tsx`). Each row
 * shows the auction title, the buyer's bid amount, the current high
 * bid (if any), and the time remaining when the auction is still
 * active. Won-and-unpaid rows surface a "Complete purchase" CTA that
 * routes the buyer to the checkout flow at the same listing id.
 */
@Composable
fun MyBidsScreen(
    navController: NavController,
    viewModel: MyBidsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    MochiScaffold(
        title = stringResource(R.string.market_bids_title),
        onBack = { navController.popBackStack() },
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
                    ) { bid ->
                        BidRow(
                            bid = bid,
                            onClick = { id ->
                                navController.navigate(MarketApp.listingDetail(id.toString()))
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
    TabRow(selectedTabIndex = activeIndex) {
        tabs.forEachIndexed { index, (filter, labelRes) ->
            Tab(
                selected = index == activeIndex,
                onClick = { onSelected(filter) },
                text = { Text(stringResource(labelRes)) },
            )
        }
    }
}

@Composable
private fun BidRow(
    bid: Bid,
    onClick: (String) -> Unit,
    onCompletePurchase: (String) -> Unit,
) {
    val format = LocalFormat.current
    val currency = bid.currency ?: Currency.GBP
    val title = bid.title ?: stringResource(R.string.market_purchases_auction_label, bid.auction ?: "")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { bid.listing?.let { onClick(it) } }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val current = bid.currentBid
            val yourBidLine = stringResource(R.string.market_bids_your_bid, formatPrice(bid.amount, currency))
            Text(
                buildString {
                    append(yourBidLine)
                    if (current != null && current > 0L) {
                        append(" · ")
                        append(stringResource(R.string.market_bids_current_bid, formatPrice(current, currency)))
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val closes = bid.closes ?: 0L
            if (closes > 0L) {
                val remaining = remainingLabel(closes)
                Text(
                    remaining,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (bid.created > 0L) {
                Text(
                    format.formatTimestamp(bid.created),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            StatusBadge(status = bid.status?.name?.lowercase() ?: "")
            if (bid.status == org.mochios.market.model.BidStatus.WON && bid.listing != null) {
                Spacer(Modifier.height(6.dp))
                Button(onClick = { onCompletePurchase(bid.listing) }) {
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
    }
}

/**
 * Live countdown label for the active rows. Recomposes every second
 * while [closes] is in the future; renders the "Auction ended" label
 * once the deadline passes.
 */
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
