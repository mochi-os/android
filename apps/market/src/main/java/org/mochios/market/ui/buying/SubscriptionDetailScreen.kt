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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.ConfirmDialog
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiScaffold
import org.mochios.market.R
import org.mochios.market.lib.formatPrice
import org.mochios.market.model.Currency
import org.mochios.market.model.Interval
import org.mochios.market.model.Listing
import org.mochios.market.model.Subscription
import org.mochios.market.model.SubscriptionStatus
import org.mochios.market.navigation.MarketApp
import org.mochios.market.ui.components.StatusBadge

/**
 * Per-subscription detail screen. Mirrors a thinned-down version of
 * `apps/market/web/src/features/buying/subscription-detail-page.tsx` —
 * the web side hasn't shipped this yet, so the Android layout is the
 * canonical reference for what we want once the web port arrives.
 *
 * Layout (top → bottom):
 *  - Summary card: listing thumbnail, listing title (clickable through to
 *    [org.mochios.market.ui.listing.ListingDetailScreen]), seller avatar
 *    and name, status badge, interval / amount line.
 *  - Billing info card: next billing date (`subscription.ends` while the
 *    sub is live), latest charge amount, cancellation date if a pending
 *    cancellation has been recorded.
 *  - Billing history list: the Comptroller exposes neither
 *    `subscription.charges` nor `subscription.payments` on the wire today
 *    so the list renders an empty-state placeholder; the section header
 *    stays visible so the user can see where future history will appear.
 *  - Action button row: Pause / Resume / Reactivate / Cancel, gated by
 *    the lifecycle status the same way [MySubscriptionsScreen]'s overflow
 *    menu does. Cancel goes through a [ConfirmDialog].
 */
@Composable
fun SubscriptionDetailScreen(
    navController: NavController,
    viewModel: SubscriptionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var pendingCancel by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SubscriptionDetailEvent.Toast -> snackbar.showSnackbar(event.message)
            }
        }
    }

    val title = state.listing?.title
        ?: stringResource(R.string.market_subscription_detail_title)

    MochiScaffold(title = title, onBack = { navController.popBackStack() }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
            when {
                state.isLoading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                state.error != null -> ErrorState(error = state.error!!) { viewModel.load() }
                state.subscription == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.market_subscription_detail_not_found),
                        modifier = Modifier.padding(24.dp),
                    )
                }
                else -> {
                    val sub = state.subscription!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SummaryCard(
                            subscription = sub,
                            listing = state.listing,
                            onListingTap = state.listing?.let { listing ->
                                {
                                    navController.navigate(
                                        MarketApp.listingDetail(listing.id.toString()),
                                    )
                                }
                            },
                        )
                        BillingInfoCard(subscription = sub)
                        BillingHistorySection(subscription = sub)
                        ActionRow(
                            subscription = sub,
                            mutating = state.mutating,
                            onPause = viewModel::pause,
                            onResume = viewModel::resume,
                            onReactivate = viewModel::reactivate,
                            onCancelRequested = { pendingCancel = true },
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    if (pendingCancel) {
        ConfirmDialog(
            title = stringResource(R.string.market_subscriptions_cancel_title),
            message = stringResource(R.string.market_subscriptions_cancel_body),
            confirmLabel = stringResource(R.string.market_subscriptions_cancel_confirm),
            isDestructive = true,
            onConfirm = {
                viewModel.cancel()
                pendingCancel = false
            },
            onDismiss = { pendingCancel = false },
        )
    }
}

@Composable
private fun SummaryCard(
    subscription: Subscription,
    listing: Listing?,
    onListingTap: (() -> Unit)?,
) {
    val format = LocalFormat.current
    val currency = subscription.currency ?: Currency.GBP
    val context = LocalContext.current
    val baseUrl = remember { baseUrlForContext(context) }
    val thumbUrl = listing?.photo?.id?.takeIf { it.isNotBlank() }
        ?.let { "$baseUrl/market/-/photo/$it" }

    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.outlinedCardColors()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ListingThumbnail(thumbnailUrl = thumbUrl)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val titleText = listing?.title
                        ?: subscription.title
                        ?: stringResource(R.string.market_subscription_detail_title)
                    val titleModifier = if (onListingTap != null) {
                        Modifier.clickable { onListingTap() }
                    } else {
                        Modifier
                    }
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (onListingTap != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = titleModifier,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val intervalText = if (subscription.interval == Interval.YEARLY) {
                        stringResource(
                            R.string.market_subscriptions_per_year,
                            formatPrice(subscription.amount, currency),
                        )
                    } else {
                        stringResource(
                            R.string.market_subscriptions_per_month,
                            formatPrice(subscription.amount, currency),
                        )
                    }
                    Text(
                        text = intervalText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge(status = subscription.status?.name?.lowercase() ?: "")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                EntityAvatar(
                    name = subscription.seller,
                    seed = subscription.seller,
                    size = 32.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = stringResource(R.string.market_subscription_detail_seller),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = subscription.seller.ifEmpty {
                            stringResource(R.string.market_subscription_detail_seller)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (subscription.created > 0L) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.market_subscription_detail_started,
                        format.formatTimestamp(subscription.created),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BillingInfoCard(subscription: Subscription) {
    val format = LocalFormat.current
    val currency = subscription.currency ?: Currency.GBP
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.outlinedCardColors()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.market_subscription_detail_billing_info),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            // The Comptroller surfaces the period-end timestamp as
            // `subscription.ends`; while the subscription is live this is
            // also the next renewal date, and once cancelled it's the
            // final access date. Either way it's the right row to show.
            if (subscription.ends > 0L && subscription.cancelled == 0L) {
                Text(
                    text = stringResource(
                        R.string.market_subscription_detail_next_billing,
                        format.formatTimestamp(subscription.ends),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            // Latest charge amount: the Comptroller doesn't yet expose a
            // per-charge field, so we render the recurring amount as the
            // current value — replacing this with the most recent
            // `subscription.charges[0].amount` lookup once that ships.
            if (subscription.amount > 0L) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.market_subscription_detail_charge_amount,
                        formatPrice(subscription.amount, currency),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (subscription.cancelled > 0L) {
                Spacer(modifier = Modifier.height(4.dp))
                val cancelText = if (subscription.ends > 0L) {
                    stringResource(
                        R.string.market_subscription_detail_cancelled_on,
                        format.formatTimestamp(subscription.ends),
                    )
                } else {
                    stringResource(R.string.market_subscription_detail_pending_cancel)
                }
                Text(
                    text = cancelText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun BillingHistorySection(@Suppress("UNUSED_PARAMETER") subscription: Subscription) {
    // The Comptroller doesn't ship `subscription.charges` or
    // `subscription.payments` on the wire today. We render the section
    // shell so the user understands where this content will appear once
    // the server side lands, and the layout doesn't shift when it does.
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.outlinedCardColors()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.market_subscription_detail_billing_history),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.market_subscription_detail_billing_history_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionRow(
    subscription: Subscription,
    mutating: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReactivate: () -> Unit,
    onCancelRequested: () -> Unit,
) {
    val canPause = subscription.status == SubscriptionStatus.ACTIVE &&
        subscription.cancelled == 0L
    val canResume = subscription.status == SubscriptionStatus.PAUSED &&
        subscription.cancelled == 0L
    val canReactivate = subscription.cancelled > 0L &&
        subscription.status != SubscriptionStatus.CANCELLED
    val canCancel = subscription.status != SubscriptionStatus.CANCELLED &&
        subscription.cancelled == 0L

    if (!canPause && !canResume && !canReactivate && !canCancel) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (canPause) {
            OutlinedButton(
                onClick = onPause,
                enabled = !mutating,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.market_subscriptions_action_pause))
            }
        }
        if (canResume) {
            Button(
                onClick = onResume,
                enabled = !mutating,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.market_subscriptions_action_resume))
            }
        }
        if (canReactivate) {
            Button(
                onClick = onReactivate,
                enabled = !mutating,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.market_subscriptions_action_reactivate))
            }
        }
        if (canCancel) {
            OutlinedButton(
                onClick = onCancelRequested,
                enabled = !mutating,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.market_subscriptions_action_cancel))
            }
        }
    }
}

@Composable
private fun ListingThumbnail(thumbnailUrl: String?) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnailUrl != null) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context).data(thumbnailUrl).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Default.ImageNotSupported,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/**
 * Resolve the active session's server URL so we can build absolute
 * `/market/-/photo/{id}` URLs for the listing thumbnail. Mirrors the
 * helper in [org.mochios.market.ui.listing.ListingDetailScreen].
 */
private fun baseUrlForContext(context: android.content.Context): String {
    return EntryPointAccessors.fromApplication(
        context.applicationContext,
        SubscriptionDetailEntryPoint::class.java,
    ).sessionManager().getServerUrlBlocking().trimEnd('/')
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SubscriptionDetailEntryPoint {
    fun sessionManager(): SessionManager
}
