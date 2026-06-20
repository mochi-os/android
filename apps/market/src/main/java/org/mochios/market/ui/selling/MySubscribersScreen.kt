// Copyright © 2026 Mochi OÜ
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
import androidx.compose.material.icons.filled.Group
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
import org.mochios.market.model.Interval
import org.mochios.market.model.Subscription
import org.mochios.market.navigation.MarketApp
import org.mochios.market.ui.components.StatusBadge

/**
 * Seller's subscriber list at [MarketApp.SUBSCRIBERS]. Each row carries
 * the buyer name (tap → buyer profile), the listing title, the recurring
 * amount with interval suffix, a [StatusBadge], and a "Cancels on" hint
 * when the subscription has been scheduled to terminate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MySubscribersScreen(
    navController: NavController,
    viewModel: MySubscribersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.market_subscribers_title)) })
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.error != null && state.subscriptions.isEmpty() -> {
                    Text(
                        text = state.error!!.userMessage()
                            .ifEmpty { stringResource(R.string.market_subscribers_load_failed) },
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
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
                        HorizontalDivider(thickness = 0.5.dp)
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
    val amount = formatPrice(subscription.amount, currency)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = subscription.buyerName.orEmpty().ifBlank { subscription.buyer },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClickBuyer),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$amount $intervalSuffix",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        val title = subscription.title.orEmpty()
        if (title.isNotEmpty()) {
            Text(
                text = title,
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
            subscription.status?.let {
                StatusBadge(status = it.name.lowercase())
            }
            if (subscription.cancelled > 0L) {
                Text(
                    text = stringResource(
                        R.string.market_subscribers_cancels_on,
                        format.formatRelativeTime(subscription.cancelled),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
