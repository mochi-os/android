package org.mochios.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import org.mochios.android.R
import org.mochios.android.api.MochiError
import org.mochios.android.i18n.LocalFormat
import java.util.Calendar
import java.util.TimeZone

/**
 * A single chat message in a game-scoped chat panel. Mirrors
 * `apps/chess/web/src/api/games.ts`'s `GameMessage` plus the chat-panel
 * polymorphism (system / move / message).
 *
 * @param id        Stable identifier (used as LazyColumn key).
 * @param game      Game ID this message belongs to.
 * @param member    Sender's entity ID. Compared against
 *                  `currentUserIdentity` to decide bubble side.
 * @param name      Sender display name.
 * @param body      Plain message body. For move messages this carries the
 *                  game-specific notation (SAN for chess, move coords for
 *                  go, the placed word for words).
 * @param type      `"message"` for regular chat bubbles, `"move"` for
 *                  centred move strips, `"system"` for italic muted notices.
 * @param created   Epoch seconds.
 */
data class GameChatMessage(
    val id: String,
    val game: String,
    val member: String,
    val name: String,
    val body: String,
    val type: String,
    val created: Long,
)

/**
 * Chat panel for the three games (chess / go / words). Mirrors
 * `apps/chess/web/src/features/chess/components/chat-message-list.tsx` —
 * date-grouped, reverse-scroll-pinned, with renderer slots so each game can
 * customise its move and system rendering without duplicating the bubble
 * scaffold.
 *
 * @param messages              Newest-last (display order); the panel
 *                              auto-scrolls to the bottom on initial load
 *                              and on append.
 * @param currentUserIdentity   Identity of the logged-in user. Used to
 *                              decide bubble side for chat messages and
 *                              "You played …" framing for move messages.
 * @param hasMore               True when older pages are available; renders
 *                              the LoadMoreTrigger at the top.
 * @param isLoadingMore         True while an older page is being fetched.
 * @param onLoadMore            Called when the user nears the top.
 * @param onRetry               Called when the user taps the error-state
 *                              retry button.
 * @param moveMessageRenderer   Optional slot for game-specific move
 *                              rendering. Receives the message and an
 *                              `isSent` flag. When null, falls back to a
 *                              centred "<You|name> played <body>" line.
 * @param systemMessageRenderer Optional slot for game-specific system
 *                              messages. When null, renders `body` centred
 *                              and italic.
 */
@Composable
fun GameChatPanel(
    messages: List<GameChatMessage>,
    currentUserIdentity: String,
    isLoading: Boolean = false,
    isError: Boolean = false,
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onRetry: () -> Unit = {},
    moveMessageRenderer: ((GameChatMessage, Boolean) -> @Composable () -> Unit)? = null,
    systemMessageRenderer: ((GameChatMessage) -> @Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    when {
        isLoading && messages.isEmpty() -> {
            ChatSkeletons(modifier = modifier)
            return
        }
        isError && messages.isEmpty() -> {
            val mochiError = remember { MochiError.Unknown(null) }
            ErrorState(error = mochiError, onRetry = onRetry)
            return
        }
        messages.isEmpty() -> {
            EmptyChatPanel(modifier = modifier)
            return
        }
    }

    val listState = rememberLazyListState()
    val grouped = remember(messages) { groupMessagesByDay(messages) }

    // Pin to bottom on initial load and on append. The LazyColumn renders
    // newest-last so the last index is the "messages end" anchor; scrolling
    // to it places the newest message above the input. Web does the same
    // via `messagesEndRef.scrollIntoView`.
    val lastIndex = remember(grouped) { (grouped.size - 1).coerceAtLeast(0) }
    var lastSeen by remember { mutableStateOf(-1) }
    LaunchedEffect(grouped.size, lastIndex) {
        if (grouped.isNotEmpty() && grouped.size != lastSeen) {
            val wasInitial = lastSeen < 0
            // For the initial paint, jump (no animation). For subsequent
            // appends, smooth-scroll so the user sees the new message slide
            // in. Only auto-scroll if the user was already near the bottom
            // (within the last few items) — otherwise they're reading older
            // history and we shouldn't yank them down.
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

    // Fire onLoadMore when the user scrolls near the top. snapshotFlow keeps
    // the listener out of every recomposition.
    val nearTop by remember(listState) {
        derivedStateOf {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: return@derivedStateOf false
            first < 3
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { nearTop }
            .distinctUntilChanged()
            .collect { atTop ->
                if (atTop && hasMore && !isLoadingMore) {
                    onLoadMore()
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (hasMore) {
            item(key = "__load-more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoadingMore) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(onClick = onLoadMore) {
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
                            "system" -> {
                                val slot = systemMessageRenderer?.invoke(message)
                                if (slot != null) {
                                    slot()
                                } else {
                                    SystemMessageRow(message.body)
                                }
                            }
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
 * Pill-style composer paired with [GameChatPanel]. No attachments — games
 * never carry them in chat.
 */
@Composable
fun GameChatInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val canSend = text.isNotBlank() && !isSending

    val send = {
        if (canSend) {
            onSend()
            focusManager.clearFocus()
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(percent = 50))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(percent = 50),
                )
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.game_chat_input_placeholder),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(
                        MaterialTheme.colorScheme.primary,
                    ),
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = send,
                enabled = canSend,
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                ),
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.game_chat_send),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, end = 4.dp),
                textAlign = TextAlign.End,
            )
        }
    }
}

// --- internals ---

private sealed class GameChatEntry {
    data class DateHeader(val dayKey: String, val epochSeconds: Long) : GameChatEntry()
    data class Item(val message: GameChatMessage) : GameChatEntry()
}

private fun groupMessagesByDay(messages: List<GameChatMessage>): List<GameChatEntry> {
    val tz = TimeZone.getDefault()
    val out = mutableListOf<GameChatEntry>()
    var lastKey: String? = null
    for (msg in messages) {
        val cal = Calendar.getInstance(tz).apply { timeInMillis = msg.created * 1000L }
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
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
private fun SystemMessageRow(body: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = body,
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
    val subject = if (isSent) stringResource(R.string.game_chat_subject_you) else message.name
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.game_chat_move_played, subject, message.body),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ChatBubble(message: GameChatMessage, isSent: Boolean) {
    val format = LocalFormat.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
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
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                if (!isSent) {
                    Text(
                        text = message.name,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(2.dp))
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
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyChatPanel(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Message,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.game_chat_empty),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChatSkeletons(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.Bottom),
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
