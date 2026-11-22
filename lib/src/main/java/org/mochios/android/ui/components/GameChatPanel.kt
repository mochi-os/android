// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.TimeZone
import org.mochios.android.R
import org.mochios.android.api.MochiError
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.theme.LocalSpacing
import org.mochios.android.ui.theme.spaced

/**
 * A single chat message in a game-scoped chat panel. [type] is "message",
 * "move" (body carries the game's notation) or "system"; [event] names the
 * system event kind for localisation and is empty on legacy rows.
 */
data class GameChatMessage(
    val id: String,
    val game: String,
    val member: String,
    val name: String,
    val body: String,
    val type: String,
    val event: String = "",
    val created: Long,
)

/**
 * Chat panel for chess / go / words: date-grouped, pinned to the bottom, with
 * a move renderer slot so each game words its own moves without duplicating
 * the bubble scaffold. [messages] is newest-last. [error] is shown only while
 * nothing is loaded.
 */
@Composable
fun GameChatPanel(
    messages: List<GameChatMessage>,
    currentUserIdentity: String,
    isLoading: Boolean = false,
    error: MochiError? = null,
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onRetry: () -> Unit = {},
    moveMessageRenderer: ((GameChatMessage, Boolean) -> @Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    when {
        isLoading && messages.isEmpty() -> {
            ChatSkeletons(modifier = modifier)
            return
        }
        error != null && messages.isEmpty() -> {
            ErrorState(error = error, onRetry = onRetry, modifier = modifier)
            return
        }
        messages.isEmpty() -> {
            EmptyChatPanel(modifier = modifier)
            return
        }
    }

    val listState = rememberLazyListState()
    // Bucketed in the zone the headers are written in, the user's, not the
    // device's: otherwise a message near midnight sits under the wrong date.
    val zone = LocalFormat.current.timeZone
    val grouped = remember(messages, zone) { groupMessagesByDay(messages, zone) }

    // Pin to bottom on initial load and on append. The LazyColumn renders
    // newest-last so the last index is the "messages end" anchor; scrolling
    // to it places the newest message above the input. Web does the same
    // via `messagesEndRef.scrollIntoView`.
    val lastIndex = remember(grouped) { (grouped.size - 1).coerceAtLeast(0) }
    var lastSeen by remember { mutableStateOf(-1) }
    LaunchedEffect(grouped.size, lastIndex) {
        if (grouped.isNotEmpty() && grouped.size != lastSeen) {
            val wasInitial = lastSeen < 0
            // Jump on the initial paint, animate on append, and only when the
            // user is already near the bottom - otherwise they are reading
            // history.
            val near = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?.let { it >= grouped.size - 3 } ?: true
            if (wasInitial) {
                listState.scrollToItem(lastIndex)
            } else if (near) {
                listState.animateScrollToItem(lastIndex)
            }
            lastSeen = grouped.size
        }
    }

    // Ask for the next older page while the top is in view. Keyed on what
    // the decision reads, so it is taken afresh: an effect started once would
    // keep the first composition's hasMore and isLoadingMore, asking again
    // after the history ran out and never once it turned up.
    val nearTop by remember(listState) {
        derivedStateOf {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: return@derivedStateOf false
            first < 3
        }
    }
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(nearTop, hasMore, isLoadingMore) {
        if (nearTop && hasMore && !isLoadingMore) currentOnLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = spaced(PaddingValues(horizontal = 12.dp, vertical = 8.dp)),
        verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.gap(2.dp)),
    ) {
        if (hasMore) {
            item(key = "__load-more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = LocalSpacing.current.gap(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoadingMore) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        MochiTextButton(onClick = onLoadMore) {
                            Text(
                                text = stringResource(R.string.game_chat_load_more),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }

        grouped.forEach { entry ->
            when (entry) {
                is GameChatEntry.DateHeader -> {
                    item(key = "__day-${entry.dayKey}") {
                        DateSeparator(entry.epochSeconds)
                    }
                }
                is GameChatEntry.Item -> {
                    val message = entry.message
                    val isSent = currentUserIdentity.isNotEmpty() &&
                        message.member == currentUserIdentity
                    item(key = message.id) {
                        when (message.type) {
                            "system" -> SystemMessageRow(message)
                            "move" -> {
                                val slot = moveMessageRenderer?.invoke(message, isSent)
                                if (slot != null) {
                                    slot()
                                } else {
                                    DefaultMoveRow(message, isSent)
                                }
                            }
                            else -> {
                                ChatBubble(message = message, isSent = isSent)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A game's chat as chess, go and words show it, in their side panel and their
 * phone sheet: the [GameChatPanel] over a one-line composer whose draft is
 * cleared only once [onSend] reports the message sent. A failed send keeps the
 * words for another try; the caller says why it failed.
 *
 * @param chat the holder's state.
 * @param message one of [chat]'s rows as the panel draws it.
 * @param onSend sends a trimmed body and calls back with whether it went.
 * @param draft the composer's text; hoisted by a screen whose two hosts share it.
 * @param composerWindowInsets the keyboard inset the composer takes: the
 *     default suits a sheet, which lifts its own content; a side panel in the
 *     screen body has to ask for it.
 */
@Composable
fun <T> GameChatColumn(
    chat: GameChatState<T>,
    message: (T) -> GameChatMessage,
    currentUserIdentity: String,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onSend: (String, (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    draft: MutableState<String> = rememberSaveable { mutableStateOf("") },
    composerWindowInsets: WindowInsets = ComposeBarDefaults.NoWindowInsets,
    moveMessageRenderer: ((GameChatMessage, Boolean) -> @Composable () -> Unit)? = null,
) {
    val messages = remember(chat.messages) { chat.messages.map(message) }
    var sending by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize()) {
        GameChatPanel(
            messages = messages,
            currentUserIdentity = currentUserIdentity,
            isLoading = chat.isLoading,
            error = chat.error,
            hasMore = chat.hasMore,
            isLoadingMore = chat.isLoadingMore,
            onLoadMore = onLoadMore,
            onRetry = onRetry,
            moveMessageRenderer = moveMessageRenderer,
            modifier = Modifier.weight(1f),
        )
        Reading(draft) { text ->
            ComposeBar(
                value = text,
                onValueChange = { draft.value = it },
                onSend = {
                    val body = draft.value
                    if (body.isNotBlank() && !sending) {
                        sending = true
                        onSend(body.trim()) { sent ->
                            sending = false
                            // Words typed while it was sending are kept.
                            if (sent && draft.value == body) draft.value = ""
                        }
                    }
                },
                isSending = sending,
                placeholder = stringResource(R.string.game_chat_input_placeholder),
                sendLabel = stringResource(R.string.game_chat_send),
                // One line, and the keyboard's action key sends: a game chat
                // message is a sentence, not a comment body.
                maxLines = 1,
                sendOnImeAction = true,
                windowInsets = composerWindowInsets,
            )
        }
    }
}

// --- internals ---

internal sealed class GameChatEntry {
    data class DateHeader(val dayKey: String, val epochSeconds: Long) : GameChatEntry()
    data class Item(val message: GameChatMessage) : GameChatEntry()
}

/** [messages] with a header before each day's first, the day taken in [zone]. */
internal fun groupMessagesByDay(messages: List<GameChatMessage>, zone: TimeZone): List<GameChatEntry> {
    val out = mutableListOf<GameChatEntry>()
    var lastKey: String? = null
    for (msg in messages) {
        val cal = Calendar.getInstance(zone).apply { timeInMillis = msg.created * 1000L }
        val key = "${cal.get(Calendar.YEAR)}-" +
            "${cal.get(Calendar.MONTH) + 1}-" +
            "${cal.get(Calendar.DAY_OF_MONTH)}"
        if (key != lastKey) {
            out += GameChatEntry.DateHeader(key, msg.created)
            lastKey = key
        }
        out += GameChatEntry.Item(msg)
    }
    return out
}

@Composable
private fun DateSeparator(epochSeconds: Long) {
    val format = LocalFormat.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = LocalSpacing.current.gap(6.dp)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = format.formatDate(epochSeconds),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SystemMessageRow(message: GameChatMessage) {
    // Localised per viewer from the event kind and the actor's name, as the
    // web's game-chat-message-list does; the stored body is the acting
    // player's language. A row without an event, or one naming nobody (a
    // resign frame from an older server), falls back to that body.
    val name = message.name
    val text = when {
        message.event == "resign" && name.isNotBlank() -> stringResource(R.string.game_system_resign, name)
        message.event == "draw_offer" -> stringResource(R.string.game_system_draw_offer, name)
        message.event == "draw_accept" -> stringResource(R.string.game_system_draw_accept)
        message.event == "draw_decline" -> stringResource(R.string.game_system_draw_decline, name)
        message.event == "score_accept" -> stringResource(R.string.game_system_score_accept, name)
        message.event == "score_agreed" -> stringResource(R.string.game_system_score_agreed)
        message.event == "score_resume" -> stringResource(R.string.game_system_score_resume, name)
        else -> message.body
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = LocalSpacing.current.gap(2.dp)),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DefaultMoveRow(message: GameChatMessage, isSent: Boolean) {
    // Two sentences, not "You" put in the player's place: the verb agrees
    // with its subject in most languages.
    val text = if (isSent) {
        stringResource(R.string.game_chat_move_played_you, message.body)
    } else {
        stringResource(R.string.game_chat_move_played, message.name, message.body)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = LocalSpacing.current.gap(1.dp)),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ChatBubble(message: GameChatMessage, isSent: Boolean) {
    val format = LocalFormat.current
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.gap(1.dp)),
        horizontalAlignment = if (isSent) Alignment.End else Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            val bubbleShape = if (isSent) {
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
            } else {
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
            }
            val bg = if (isSent) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            val fg = if (isSent) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(bubbleShape)
                    .background(bg)
                    .padding(horizontal = spacing.gap(10.dp), vertical = spacing.gap(6.dp)),
            ) {
                if (!isSent) {
                    Text(
                        text = message.name,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(spacing.gap(2.dp)))
                }
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = fg,
                )
                // Per-message timestamp at the bubble tail (web shows on
                // hover; phones have no hover so we show it always small
                // and muted).
                Text(
                    text = format.formatDateTime(message.created),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = spacing.gap(2.dp)),
                )
            }
        }
    }
}

@Composable
private fun EmptyChatPanel(modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier.fillMaxSize().padding(spacing.gap(16.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Message,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Spacer(Modifier.height(spacing.gap(8.dp)))
        Text(
            text = stringResource(R.string.game_chat_empty),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChatSkeletons(modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier.fillMaxSize().padding(spacing.gap(12.dp)),
        verticalArrangement = Arrangement.spacedBy(spacing.gap(8.dp), alignment = Alignment.Bottom),
    ) {
        repeat(3) { i ->
            val isLeft = i % 2 == 0
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isLeft) Arrangement.Start else Arrangement.End,
            ) {
                val shape = if (isLeft) {
                    RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 4.dp, bottomEnd = 12.dp)
                } else {
                    RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 4.dp)
                }
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .widthIn(min = 160.dp, max = 240.dp)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                )
            }
        }
    }
}
