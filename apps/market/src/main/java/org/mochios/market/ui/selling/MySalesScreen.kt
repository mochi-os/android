// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.selling

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.InfiniteList
import org.mochios.market.R
import org.mochios.market.lib.formatPrice
import org.mochios.market.model.Currency
import org.mochios.market.model.Order
import org.mochios.market.navigation.MarketApp
import org.mochios.market.ui.components.MarketLayout
import org.mochios.market.ui.components.MarketListPadding
import org.mochios.market.ui.components.MarketListPrice
import org.mochios.market.ui.components.MarketListRow
import org.mochios.market.ui.components.MarketListSpacing
import org.mochios.market.ui.components.MarketStatusBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MySalesScreen(
    navController: NavController,
    viewModel: MySalesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    MarketLayout(
        navController = navController,
        currentRoute = MarketApp.SALES,
        titleRes = R.string.market_sales_title,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.error != null && state.orders.isEmpty() -> {
                    // userMessage() comes back empty when the server sends an
                    // empty error body; keep the specific fallback rather than
                    // rendering an error state with no message in it.
                    ErrorState(
                        error = state.error!!.takeIf { it.userMessage().isNotEmpty() }
                            ?: MochiError.Unknown(stringResource(R.string.market_sales_load_failed)),
                        onRetry = viewModel::refresh,
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
                        contentPadding = MarketListPadding,
                        verticalArrangement = MarketListSpacing,
                    ) { order ->
                        SaleRow(
                            order = order,
                            onClick = {
                                navController.navigate(
                                    MarketApp.saleDetail(order.id.toString()),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SaleRow(order: Order, onClick: () -> Unit) {
    val format = LocalFormat.current
    val timestamp = order.updated.takeIf { time -> time > 0L } ?: order.created
    val subtitle = listOf(
        order.buyerName.orEmpty(),
        if (timestamp > 0L) format.formatRelativeTime(timestamp) else "",
    ).filter { part -> part.isNotBlank() }.joinToString(" · ")
    MarketListRow(
        title = order.title.orEmpty(),
        onClick = onClick,
        subtitle = subtitle,
        badges = order.status?.let { status ->
            { MarketStatusBadge(status = status.name.lowercase()) }
        },
        trailing = {
            MarketListPrice(text = formatPrice(order.total, order.currency ?: Currency.GBP))
        },
    )
}
