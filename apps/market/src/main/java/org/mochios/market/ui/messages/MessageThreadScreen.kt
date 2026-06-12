package org.mochios.market.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.flow.collectLatest
import org.mochios.android.R as MochiR
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.ws.GameWsEvent
import org.mochios.android.ws.rememberGameWebSocket
import org.mochios.market.R
import org.mochios.market.lib.currencyDecimals
import org.mochios.market.lib.formatPrice
import org.mochios.market.model.Currency
import org.mochios.market.model.Message
import org.mochios.market.navigation.MarketApp

/**
 * One-conversation chat screen. Mirrors web's
 * `apps/market/web/src/features/messages/MessageThread`. Layout:
 *
 *  - TopAppBar shows the other party's display name plus a chip with the
 *    related listing's thumbnail + title (tap → listing detail).
 *  - LazyColumn (reverseLayout = true) renders messages with the latest at
 *    the bottom. My messages right-aligned in the primary tone, others left
 *    in the surfaceVariant tone.
 *  - Compose row at the bottom holds an OutlinedTextField + Send button.
 *
 * The WebSocket subscription uses lib's [rememberGameWebSocket] — the
 * helper is keyed by topic, so we pass `market-thread-<id>` as the key and
 * the underlying URL becomes `wss://server/_/websocket?key=...`. On every
 * incoming `Message` event we forward the parsed body up to the
 * ViewModel via [MessageThreadViewModel.ingestRemote]; the ViewModel
 * dedups on message id.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageThreadScreen(
    navController: NavController,
    viewModel: MessageThreadViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Subscribe to the per-thread WebSocket once the thread id is known. When
    // the screen is opened from a listing the route arg is "new", so we key on
    // the resolved thread id from state rather than the raw arg — the socket
    // attaches as soon as the thread is created/loaded. The helper closes the
    // socket on dispose, so navigating away unwinds it for us.
    val threadId = state.thread?.id?.takeIf { it != 0L }
    val socket = rememberGameWebSocket(
        gameKey = threadId?.let { "market-thread-$it" },
    )
    LaunchedEffect(socket, threadId) {
        socket?.events?.collectLatest { event ->
            val message = event.toMessage(threadId ?: 0L) ?: return@collectLatest
            viewModel.ingestRemote(message)
        }
    }

    // Auto-scroll to the latest on every new message (reverseLayout keeps
    // the bottom of the list pinned, so we scroll to index 0).
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is MessageThreadEvent.Error) {
                snackbarHostState.showSnackbar(event.error.userMessage())
            }
        }
    }

    val otherName = state.thread?.otherName.orEmpty().ifBlank {
        stringResource(R.string.market_messages_thread_title)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = otherName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ListingChip(
                title = state.listing.title,
                priceLabel = priceLabel(state.listing.price, state.listing.currency),
                onClick = {
                    if (viewModel.listingId.isNotBlank()) {
                        navController.navigate(
                            MarketApp.listingDetail(viewModel.listingId),
                        )
                    }
                },
            )

            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.isLoading && state.messages.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    state.error != null && state.messages.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = state.error!!.userMessage(),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                    else -> {
                        val mine = state.thread?.buyer.orEmpty()
                        // Reverse the list once so `reverseLayout = true`
                        // keeps the newest visible at the bottom of the
                        // viewport.
                        val reversed = remember(state.messages) {
                            state.messages.asReversed()
                        }
                        LazyColumn(
                            state = listState,
                            reverseLayout = true,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(reversed, key = { it.id }) { message ->
                                Bubble(message = message, isMine = message.sender == mine)
                            }
                        }
                    }
                }
            }

            ComposeRow(
                draft = state.draft,
                isSending = state.isSending,
                onDraftChange = viewModel::updateDraft,
                onSend = viewModel::sendMessage,
            )
        }
    }
}

@Composable
private fun ListingChip(
    title: String,
    priceLabel: String,
    onClick: () -> Unit,
) {
    if (title.isBlank()) return
    Surface(
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (priceLabel.isNotEmpty()) {
                    Text(
                        text = priceLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Bubble(message: Message, isMine: Boolean) {
    val format = LocalFormat.current
    val bg = if (isMine) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = if (isMine) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val align = if (isMine) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align,
    ) {
        Surface(
            color = bg,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = message.body,
                    color = fg,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.size(2.dp))
                Text(
                    text = format.formatRelativeTime(message.created),
                    color = fg.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun ComposeRow(
    draft: String,
    isSending: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = {
                    Text(stringResource(R.string.market_messages_compose_placeholder))
                },
                maxLines = 5,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = draft.isNotBlank() && !isSending,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.market_messages_send),
                )
            }
        }
    }
}

/**
 * Map a generic [GameWsEvent] (the lib helper is named for chess but works
 * for any keyed topic) onto a market [Message]. Returns null for events
 * that aren't message payloads or that lack a body.
 */
private fun GameWsEvent.toMessage(threadId: Long): Message? {
    if (type != "message") return null
    val text = body?.takeIf { it.isNotBlank() } ?: return null
    val rawId = (raw["id"] as? Number)?.toLong() ?: 0L
    return Message(
        id = rawId,
        thread = threadId,
        sender = member.orEmpty(),
        senderName = name.orEmpty(),
        body = text,
        read = 0L,
        created = created,
    )
}

private fun priceLabel(price: Long, currency: Currency?): String {
    if (price <= 0L) return ""
    val c = currency ?: return ""
    // currencyDecimals defends formatPrice from accidental misuse — we just
    // call into it.
    @Suppress("UNUSED_VARIABLE")
    val unused = currencyDecimals(c)
    return formatPrice(price, c)
}
