// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.selling

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.InfiniteList
import org.mochios.market.R
import org.mochios.market.lib.formatPrice
import org.mochios.market.model.Currency
import org.mochios.market.model.Order
import org.mochios.market.navigation.MarketApp
import org.mochios.market.ui.components.StatusBadge

/**
 * Seller's order list at [MarketApp.SALES]. Each row shows the listing
 * title, buyer name, total, [StatusBadge] and a relative timestamp.
 * Tapping a row opens [MarketApp.saleDetail].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MySalesScreen(
    navController: NavController,
    viewModel: MySalesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.market_sales_title)) })
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.error != null && state.orders.isEmpty() -> {
                    Text(
                        text = state.error!!.userMessage()
                            .ifEmpty { stringResource(R.string.market_sales_load_failed) },
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                }
                state.orders.isEmpty() && !state.isLoading -> {
                    EmptyState(
                        icon = Icons.Default.PointOfSale,
                        title = stringResource(R.string.market_sales_empty_title),
                        subtitle = stringResource(R.string.market_sales_empty_subtitle),
                    )
                }
                else -> {
                    InfiniteList(
                        items = state.orders,
                        isLoading = state.isLoading,
                        hasMore = state.hasMore,
                        onLoadMore = viewModel::loadMore,
                    ) { order ->
                        SaleRow(
                            order = order,
                            onClick = {
                                navController.navigate(
                                    MarketApp.saleDetail(order.id.toString()),
                                )
                            },
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SaleRow(order: Order, onClick: () -> Unit) {
    val format = LocalFormat.current
    val total = order.currency?.let { formatPrice(order.total, it) }
        ?: formatPrice(order.total, Currency.GBP)
    val timestamp = order.updated.takeIf { it > 0L } ?: order.created
    val timeLabel = if (timestamp > 0L) format.formatRelativeTime(timestamp) else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = order.title.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val buyer = order.buyerName.orEmpty()
            if (buyer.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.market_sale_buyer_label) + " · " + buyer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                order.status?.let { StatusBadge(status = it.name.lowercase()) }
                if (timeLabel.isNotEmpty()) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = total,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
