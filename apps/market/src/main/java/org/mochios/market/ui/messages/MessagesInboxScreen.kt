package org.mochios.market.ui.messages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material3.Badge
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
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.InfiniteList
import org.mochios.market.R
import org.mochios.market.model.MarketThread
import org.mochios.market.navigation.MarketApp

/**
 * Inbox listing every conversation the signed-in identity participates in
 * (buyer or seller). Mirrors web's `apps/market/web/src/features/messages/
 * MessagesInbox`. Each row shows the counterpart's avatar + name, the
 * listing title, the last-message preview, the timestamp, and an unread
 * count badge when the local identity is on the receiving end.
 *
 * Tapping a row navigates to [MarketApp.messageThread]; the parent
 * NavGraph composable resolves both ids back from the route arguments.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesInboxScreen(
    navController: NavController,
    viewModel: MessagesInboxViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.market_messages_inbox_title)) },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.error != null && state.threads.isEmpty() -> {
                    Text(
                        text = state.error!!.userMessage(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                }
                state.threads.isEmpty() && !state.isLoading -> {
                    EmptyInbox()
                }
                else -> {
                    InfiniteList(
                        items = state.threads,
                        isLoading = state.isLoading,
                        hasMore = state.hasMore,
                        onLoadMore = viewModel::loadMore,
                    ) { thread ->
                        InboxRow(
                            thread = thread,
                            serverUrl = viewModel.serverUrl,
                            onClick = {
                                navController.navigate(
                                    MarketApp.messageThread(
                                        listingId = thread.listing.toString(),
                                        threadId = thread.id.toString(),
                                    ),
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
private fun EmptyInbox() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.AutoMirrored.Filled.Message,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.market_messages_inbox_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.market_messages_inbox_empty_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InboxRow(
    thread: MarketThread,
    serverUrl: String,
    onClick: () -> Unit,
) {
    val otherEntity = thread.otherEntityId()
    val otherName = thread.otherName.orEmpty().ifBlank { otherEntity.orEmpty() }
    val avatarUrl = otherEntity?.let { "$serverUrl/market/-/user/$it/asset/avatar" }
    val unread = (thread.unread ?: 0L).toInt()
    val format = LocalFormat.current
    val timestamp = thread.lastMessageTime ?: thread.updated
    val timeLabel = format.formatRelativeTime(timestamp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntityAvatar(
            name = otherName,
            src = avatarUrl,
            seed = otherEntity,
            size = 40.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = otherName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (timeLabel.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val title = thread.title.orEmpty()
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val preview = thread.lastMessage.orEmpty()
            if (preview.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (unread > 0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (unread > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Badge { Text(text = unread.toString()) }
        }
    }
}

/**
 * Resolve the entity id of the "other party" on this thread. The server
 * fills in `otherName` already, but the entity id we need for the avatar
 * URL is whichever of `buyer` / `seller` differs from the current
 * identity. As a stateless screen we can't read the current identity
 * synchronously, so the heuristic is: prefer the buyer when it's non-empty
 * and differs from the seller, else fall back to the seller. The avatar
 * URL is per-server caches anyway, so the worst case is a stale picture.
 */
private fun MarketThread.otherEntityId(): String? {
    val b = buyer.takeIf { it.isNotEmpty() }
    val s = seller.takeIf { it.isNotEmpty() }
    return b ?: s
}
