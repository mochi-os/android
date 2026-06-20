// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.buying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.InfiniteList
import org.mochios.android.ui.components.MochiScaffold
import org.mochios.market.R
import org.mochios.market.lib.formatPrice
import org.mochios.market.model.Currency
import org.mochios.market.model.Interval
import org.mochios.market.model.Subscription
import org.mochios.market.model.SubscriptionStatus
import org.mochios.market.navigation.MarketApp
import org.mochios.market.ui.components.StatusBadge

/**
 * Buyer-side list of active and past subscriptions.
 *
 * Mirrors `apps/market/web/src/features/buying/my-subscriptions-page.tsx`.
 * Each row shows the subscription title, the recurring amount with
 * interval suffix, the lifecycle [StatusBadge], and an overflow menu
 * with the available actions (pause / resume / reactivate / cancel).
 * Cancel routes through a [ConfirmDialog] before firing the API call so
 * a misclick on the small overflow target can't accidentally end the
 * subscription.
 */
@Composable
fun MySubscriptionsScreen(
    navController: NavController,
    viewModel: MySubscriptionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MySubscriptionsEvent.Toast -> snackbar.showSnackbar(event.message)
            }
        }
    }

    var pendingCancel by remember { mutableStateOf<Subscription?>(null) }

    MochiScaffold(
        title = stringResource(R.string.market_subscriptions_title),
        onBack = { navController.popBackStack() },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
            when {
                state.isLoading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.error != null -> ErrorState(error = state.error!!) { viewModel.load() }
                state.subscriptions.isEmpty() -> EmptyState(
                    icon = Icons.Default.Repeat,
                    title = stringResource(R.string.market_subscriptions_empty),
                    subtitle = stringResource(R.string.market_subscriptions_empty_body),
                )
                else -> InfiniteList(
                    items = state.subscriptions,
                    isLoading = state.isLoadingMore,
                    hasMore = state.subscriptions.size < state.total,
                    onLoadMore = { viewModel.loadMore() },
                ) { sub ->
                    SubscriptionRow(
                        sub = sub,
                        mutating = state.mutatingId == sub.id,
                        onTap = {
                            navController.navigate(
                                MarketApp.subscriptionDetail(sub.id.toString()),
                            )
                        },
                        onPause = { viewModel.pause(sub.id) },
                        onResume = { viewModel.resume(sub.id) },
                        onReactivate = { viewModel.reactivate(sub.id) },
                        onCancelRequested = { pendingCancel = sub },
                    )
                }
            }
        }
    }

    pendingCancel?.let { sub ->
        ConfirmDialog(
            title = stringResource(R.string.market_subscriptions_cancel_title),
            message = stringResource(R.string.market_subscriptions_cancel_body),
            confirmLabel = stringResource(R.string.market_subscriptions_cancel_confirm),
            isDestructive = true,
            onConfirm = {
                viewModel.cancel(sub.id)
                pendingCancel = null
            },
            onDismiss = { pendingCancel = null },
        )
    }
}

@Composable
private fun SubscriptionRow(
    sub: Subscription,
    mutating: Boolean,
    onTap: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReactivate: () -> Unit,
    onCancelRequested: () -> Unit,
) {
    val format = LocalFormat.current
    val currency = sub.currency ?: Currency.GBP
    val intervalText = if (sub.interval == Interval.YEARLY) {
        stringResource(R.string.market_subscriptions_per_year, formatPrice(sub.amount, currency))
    } else {
        stringResource(R.string.market_subscriptions_per_month, formatPrice(sub.amount, currency))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onTap)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                sub.title ?: "Subscription #${sub.id}",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$intervalText · ${format.formatTimestamp(sub.created)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (sub.cancelled > 0L &&
                (sub.status == SubscriptionStatus.ACTIVE || sub.status == SubscriptionStatus.PAUSED)
            ) {
                val msg = if (sub.ends > 0L) {
                    stringResource(R.string.market_subscriptions_cancels_on, format.formatTimestamp(sub.ends))
                } else {
                    stringResource(R.string.market_subscriptions_cancels_period_end)
                }
                Text(
                    msg,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        StatusBadge(status = sub.status?.name?.lowercase() ?: "")
        Box {
            var menu by remember { mutableStateOf(false) }
            IconButton(onClick = { menu = true }, enabled = !mutating) {
                Icon(
                    Icons.Default.MoreHoriz,
                    contentDescription = stringResource(R.string.market_subscriptions_actions_label),
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (sub.status == SubscriptionStatus.ACTIVE && sub.cancelled == 0L) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.market_subscriptions_action_pause)) },
                        onClick = { menu = false; onPause() },
                    )
                }
                if (sub.status == SubscriptionStatus.PAUSED && sub.cancelled == 0L) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.market_subscriptions_action_resume)) },
                        onClick = { menu = false; onResume() },
                    )
                }
                if (sub.cancelled > 0L && sub.status != SubscriptionStatus.CANCELLED) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.market_subscriptions_action_reactivate)) },
                        onClick = { menu = false; onReactivate() },
                    )
                } else if (sub.status != SubscriptionStatus.CANCELLED && sub.cancelled == 0L) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.market_subscriptions_action_cancel)) },
                        onClick = { menu = false; onCancelRequested() },
                    )
                }
            }
        }
    }
}
