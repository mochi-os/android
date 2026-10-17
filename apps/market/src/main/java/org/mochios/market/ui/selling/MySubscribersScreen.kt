// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.selling

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import org.mochios.market.model.Interval
import org.mochios.market.model.Subscription
import org.mochios.market.navigation.MarketApp
import org.mochios.market.ui.components.MarketLayout
import org.mochios.market.ui.components.MarketListNote
import org.mochios.market.ui.components.MarketListPadding
import org.mochios.market.ui.components.MarketListPrice
import org.mochios.market.ui.components.MarketListRow
import org.mochios.market.ui.components.MarketListSpacing
import org.mochios.market.ui.components.MarketStatusBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MySubscribersScreen(
    navController: NavController,
    viewModel: MySubscribersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    MarketLayout(
        navController = navController,
        currentRoute = MarketApp.SUBSCRIBERS,
        titleRes = R.string.market_subscribers_title,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.error != null && state.subscriptions.isEmpty() -> {
                    // userMessage() comes back empty when the server sends an
                    // empty error body; keep the specific fallback rather than
                    // rendering an error state with no message in it.
                    ErrorState(
                        error = state.error!!.takeIf { it.userMessage().isNotEmpty() }
                            ?: MochiError.Unknown(stringResource(R.string.market_subscribers_load_failed)),
                        onRetry = viewModel::refresh,
                    )
                }
                state.subscriptions.isEmpty() && !state.isLoading -> {
                    EmptyState(
                        icon = Icons.Default.Group,
                        title = stringResource(R.string.market_subscribers_empty_title),
                        subtitle = stringResource(R.string.market_subscribers_empty_subtitle),
                    )
                }
                else -> {
                    InfiniteList(
                        items = state.subscriptions,
                        isLoading = state.isLoading,
                        hasMore = state.hasMore,
                        onLoadMore = viewModel::loadMore,
                        contentPadding = MarketListPadding,
                        verticalArrangement = MarketListSpacing,
                    ) { sub ->
                        SubscriberRow(
                            subscription = sub,
                            onClickBuyer = {
                                if (sub.buyer.isNotEmpty()) {
                                    navController.navigate(
                                        MarketApp.publicProfile(sub.buyer),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriberRow(subscription: Subscription, onClickBuyer: () -> Unit) {
    val format = LocalFormat.current
    val currency = subscription.currency ?: Currency.GBP
    val intervalSuffix = when (subscription.interval) {
        Interval.YEARLY -> stringResource(R.string.market_subscribers_interval_year)
        else -> stringResource(R.string.market_subscribers_interval_month)
    }
    val name = subscription.buyerName.orEmpty().ifBlank { subscription.buyer }
    MarketListRow(
        title = name,
        onClick = onClickBuyer,
        subtitle = subscription.title,
        badges = {
            subscription.status?.let { status ->
                MarketStatusBadge(status = status.name.lowercase())
            }
            if (subscription.cancelled > 0L) {
                Text(
                    text = stringResource(
                        R.string.market_subscribers_cancels_on,
                        format.formatRelativeTime(subscription.cancelled),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailing = {
            MarketListPrice(text = formatPrice(subscription.amount, currency))
            MarketListNote(text = intervalSuffix)
        },
    )
}
