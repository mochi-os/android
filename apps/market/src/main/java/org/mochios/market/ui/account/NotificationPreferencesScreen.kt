package org.mochios.market.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import org.mochios.android.ui.components.MochiScaffold
import org.mochios.market.R

/**
 * Per-topic notification mute screen.
 *
 * Mirrors the 17 entries declared under `notifications.topic.*` in
 * `apps/market/labels/en.conf`, in the same order they appear in the
 * label file. Each row is a title + supporting line + Switch.
 *
 * Topic mute is currently a local-only stub backed by
 * [org.mochios.market.lib.NotificationPreferencesStore] — the
 * notifications app holds the canonical preference on the user's host
 * but doesn't expose a market-side API yet, and Mochi forbids
 * cross-app HTTP. The UI is fully wired so users can record their
 * choices today; client-side filters apply the mute at display time
 * until the server-side wrapper lands.
 */
@Composable
fun NotificationPreferencesScreen(
    navController: NavController,
    viewModel: NotificationPreferencesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    MochiScaffold(
        title = stringResource(R.string.market_notifications_title),
        onBack = { navController.popBackStack() },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.market_notifications_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    state.topics.forEachIndexed { index, topic ->
                        TopicRow(
                            topic = topic,
                            enabled = topic in state.enabled,
                            onToggle = { viewModel.setTopicEnabled(topic, it) },
                        )
                        if (index < state.topics.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicRow(
    topic: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val titleRes = topicTitleRes(topic)
    val descRes = topicDescRes(topic)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(descRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.padding(start = 12.dp))
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
        )
    }
}

/**
 * Map a dotted topic key to the matching `market_notifications_topic_*`
 * string resource. Keys are exactly the same as the labels declared
 * under `notifications.topic.*` in `apps/market/labels/en.conf`, with
 * `.` rewritten to `_` for the Android resource id.
 */
private fun topicTitleRes(topic: String): Int = when (topic) {
    "message" -> R.string.market_notifications_topic_message
    "order.seller" -> R.string.market_notifications_topic_order_seller
    "order.buyer" -> R.string.market_notifications_topic_order_buyer
    "bid.placed" -> R.string.market_notifications_topic_bid_placed
    "auction.outbid" -> R.string.market_notifications_topic_auction_outbid
    "auction.ended" -> R.string.market_notifications_topic_auction_ended
    "auction.cancelled" -> R.string.market_notifications_topic_auction_cancelled
    "subscription.seller" -> R.string.market_notifications_topic_subscription_seller
    "subscription.buyer" -> R.string.market_notifications_topic_subscription_buyer
    "listing.moderation" -> R.string.market_notifications_topic_listing_moderation
    "review.received" -> R.string.market_notifications_topic_review_received
    "review.responded" -> R.string.market_notifications_topic_review_responded
    "report.reporter" -> R.string.market_notifications_topic_report_reporter
    "report.target" -> R.string.market_notifications_topic_report_target
    "account.moderation" -> R.string.market_notifications_topic_account_moderation
    "account.stripe" -> R.string.market_notifications_topic_account_stripe
    else -> R.string.market_notifications_topic_message
}

private fun topicDescRes(topic: String): Int = when (topic) {
    "message" -> R.string.market_notifications_topic_message_desc
    "order.seller" -> R.string.market_notifications_topic_order_seller_desc
    "order.buyer" -> R.string.market_notifications_topic_order_buyer_desc
    "bid.placed" -> R.string.market_notifications_topic_bid_placed_desc
    "auction.outbid" -> R.string.market_notifications_topic_auction_outbid_desc
    "auction.ended" -> R.string.market_notifications_topic_auction_ended_desc
    "auction.cancelled" -> R.string.market_notifications_topic_auction_cancelled_desc
    "subscription.seller" -> R.string.market_notifications_topic_subscription_seller_desc
    "subscription.buyer" -> R.string.market_notifications_topic_subscription_buyer_desc
    "listing.moderation" -> R.string.market_notifications_topic_listing_moderation_desc
    "review.received" -> R.string.market_notifications_topic_review_received_desc
    "review.responded" -> R.string.market_notifications_topic_review_responded_desc
    "report.reporter" -> R.string.market_notifications_topic_report_reporter_desc
    "report.target" -> R.string.market_notifications_topic_report_target_desc
    "account.moderation" -> R.string.market_notifications_topic_account_moderation_desc
    "account.stripe" -> R.string.market_notifications_topic_account_stripe_desc
    else -> R.string.market_notifications_topic_message_desc
}
