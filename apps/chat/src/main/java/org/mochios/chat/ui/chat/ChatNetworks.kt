// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.chat

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.ui.components.AttachmentGallery
import org.mochios.android.ui.components.ComposeBar
import org.mochios.android.ui.components.MochiBottomSheet
import org.mochios.android.ui.components.MochiButton
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiSheetHeader
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.android.ui.components.QuoteCard
import org.mochios.android.ui.components.Reading
import org.mochios.android.util.attachmentAddress
import org.mochios.chat.R
import org.mochios.chat.model.ChatDetail
import org.mochios.chat.model.ChatMessage
import org.mochios.chat.model.ChatPresence
import org.mochios.chat.model.ChatQuote
import org.mochios.chat.model.ReceiptState
import org.mochios.chat.model.presence
import org.mochios.android.R as MochiR

/** Whether a conversation is a group, which names each message's sender and who has read it. */
internal fun isGroupChat(chat: ChatDetail): Boolean = chat.members.size > 2

/** An encrypted message whose keys have not come: no text and no files until they do. */
internal fun awaitingKeys(message: ChatMessage): Boolean =
    message.encrypted != 0 && message.body.isEmpty() && message.attachments.isEmpty()

/** A message encrypted before the account was added to Mochi, which no device of Mochi's can ever read. */
internal fun isEarlierHistory(message: ChatMessage): Boolean =
    message.encrypted == 2 && !message.deleted && message.body.isEmpty() && message.attachments.isEmpty()

/** What an encrypted message with nothing to show says in its place. */
@Composable
internal fun encryptedText(message: ChatMessage): String = stringResource(sealed(message.encrypted))

/**
 * The line for a message that cannot be read, by how it came
 * ([ChatMessage.encrypted]), as the web says it: why it cannot be, where
 * that is known, and otherwise that it is encrypted.
 */
@StringRes
internal fun sealed(encrypted: Int): Int = when (encrypted) {
    3 -> R.string.chat_message_excluded
    4 -> R.string.chat_message_changed
    5 -> R.string.chat_message_distrusted
    else -> R.string.chat_message_encrypted
}

/**
 * The line under a one-to-one conversation's name: whether the other person
 * is online as they share it, else when they were last seen; on another
 * network the account it is had through, where its network says nothing.
 */
@Composable
internal fun standingLine(chat: ChatDetail, presence: ChatPresence?): String? {
    val address = chat.address.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.chat_as_address, it) }
    if (!chat.presence() || presence == null || presence.status.isEmpty()) return address
    val label = when (presence.status) {
        "online" -> stringResource(R.string.chat_presence_online)
        "away" -> stringResource(R.string.chat_presence_away)
        "busy" -> stringResource(R.string.chat_presence_busy)
        else -> null
    }
    if (label != null) return if (presence.message.isNotEmpty()) "$label · ${presence.message}" else label
    if (chat.account.isNotEmpty()) return address
    return if (presence.seen > 0) {
        stringResource(R.string.chat_last_seen, LocalFormat.current.formatTimestamp(presence.seen))
    } else {
        null
    }
}

/** The tick on the user's own message: one when sent, two when delivered, two in the primary colour when read. */
@Composable
internal fun ReceiptTick(state: ReceiptState) {
    val label = when (state) {
        ReceiptState.READ -> stringResource(R.string.chat_receipt_read)
        ReceiptState.DELIVERED -> stringResource(R.string.chat_receipt_delivered)
        ReceiptState.SENT -> stringResource(R.string.chat_receipt_sent)
    }
    Icon(
        imageVector = if (state == ReceiptState.SENT) Icons.Default.Done else Icons.Default.DoneAll,
        contentDescription = label,
        modifier = Modifier.size(14.dp),
        tint = if (state == ReceiptState.READ) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A post on another network that a message's first link quotes. */
@Composable
internal fun MessageQuote(quote: ChatQuote) {
    val author = quote.author
    QuoteCard(
        url = quote.url,
        text = quote.text,
        image = quote.image,
        name = author?.name.orEmpty(),
        address = author?.address.orEmpty(),
        avatar = author?.avatar.orEmpty(),
        seed = author?.id.orEmpty(),
    )
}

/** Who is typing, above the composer. */
@Composable
internal fun TypingLine(names: List<String>) {
    val text = if (names.size == 1) {
        stringResource(R.string.chat_typing_one, names.first())
    } else {
        stringResource(R.string.chat_typing_many, LocalFormat.current.formatList(names))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
    )
}

/** In place of the composer, an invitation to a room on another network: accepted, the account joins it; declined, it goes. */
@Composable
internal fun InvitationBar(answering: Boolean, onAccept: () -> Unit, onDecline: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.chat_invitation_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            MochiOutlinedButton(onClick = onDecline, enabled = !answering) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.chat_invitation_decline_short))
            }
            MochiButton(onClick = onAccept, enabled = !answering) {
                if (answering) {
                    CircularProgressIndicator(modifier = Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                }
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.chat_invitation_accept))
            }
        }
    }
}

/**
 * What a reply in the open thread offers - the same actions as a message of
 * the main timeline, as far as a reply has them - and the thread's own
 * unsent replies, edit and search match.
 *
 * @param identity the user, whose own replies may be edited and deleted.
 * @param canWrite whether the conversation takes changes at all.
 * @param editing whether the box holds an edit of one of the replies.
 * @param match the reply the search is on, highlighted with [query].
 */
internal data class ReplyActions(
    val identity: String = "",
    val canWrite: Boolean = false,
    val editing: Boolean = false,
    val match: String? = null,
    val query: String = "",
    val onEdit: (ChatMessage) -> Unit = {},
    val onCancelEdit: () -> Unit = {},
    val onDelete: (ChatMessage) -> Unit = {},
    val onReact: (String, String) -> Unit = { _, _ -> },
    val onForward: (ChatMessage) -> Unit = {},
    val onRetry: (Long) -> Unit = {},
    /** Send unsent replies refused for want of encryption unencrypted. */
    val onUnencrypted: (Long) -> Unit = {},
    /** Send the box's reply unencrypted, after the send refused it for want of encryption. */
    val onSendUnencrypted: () -> Unit = {},
    val onCopy: (String) -> Unit = {},
    val onDiscard: (Long) -> Unit = {},
)

/**
 * A message's thread over the conversation: the message, its replies, and a reply box.
 *
 * @param draft The reply box's text, read only by the box so typing leaves the
 * thread alone; null reads it from [thread].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadSheet(
    thread: ThreadState,
    chatId: String,
    canWrite: Boolean,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onEarlier: () -> Unit,
    onDismiss: () -> Unit,
    draft: State<String>? = null,
    replies: ReplyActions = ReplyActions(),
) {
    MochiBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            ThreadContent(thread, chatId, canWrite, onDraft, onSend, onEarlier, draft, replies)
        }
    }
}

/** How many pages of earlier replies are read looking for the search's match in a thread. */
private const val THREAD_SEARCH_PAGES = 20

@Composable
internal fun ColumnScope.ThreadContent(
    thread: ThreadState,
    chatId: String,
    canWrite: Boolean,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onEarlier: () -> Unit,
    draft: State<String>? = null,
    replies: ReplyActions = ReplyActions(),
) {
    val listState = rememberLazyListState()
    // The search's match in the thread on screen, earlier replies read for it
    // a page at a time, for at most so many pages.
    var pages by remember(replies.match) { mutableIntStateOf(0) }
    LaunchedEffect(replies.match, thread.replies, thread.isLoading) {
        val match = replies.match ?: return@LaunchedEffect
        if (thread.isLoading) return@LaunchedEffect
        val at = thread.replies.indexOfFirst { reply -> reply.id == match }
        when {
            at >= 0 -> listState.animateScrollToItem(1 + (if (thread.more) 1 else 0) + at)
            thread.more && !thread.isLoadingMore && pages < THREAD_SEARCH_PAGES -> {
                pages++
                onEarlier()
            }
        }
    }
    MochiSheetHeader(title = stringResource(R.string.chat_thread_title))
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("root") {
            ThreadMessage(thread.root, chatId)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
        }
        if (thread.more) {
            item("earlier") {
                if (thread.isLoadingMore) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    MochiTextButton(onClick = onEarlier) {
                        Text(stringResource(R.string.chat_thread_earlier))
                    }
                }
            }
        }
        when {
            thread.isLoading -> item("loading") { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
            thread.error != null && thread.replies.isEmpty() -> item("error") {
                Text(text = thread.error.userMessage(), color = MaterialTheme.colorScheme.error)
            }
            else -> items(thread.replies, key = { it.id }) { reply -> ThreadMessage(reply, chatId, replies) }
        }
        items(thread.unsent, key = { "unsent-" + it.key }) { reply ->
            UnsentRow(
                message = reply,
                onRetry = { replies.onRetry(reply.key) },
                onUnencrypted = { replies.onUnencrypted(reply.key) },
                onCopy = { replies.onCopy(reply.text) },
                onDiscard = { replies.onDiscard(reply.key) },
                retryEnabled = !thread.isSending,
            )
        }
    }
    if (canWrite || replies.editing) {
        Reading(draft ?: rememberUpdatedState(thread.draft)) { text ->
            ComposeBar(
                value = text,
                onValueChange = onDraft,
                onSend = onSend,
                placeholder = stringResource(R.string.chat_thread_reply),
                isSending = thread.isSending,
                sendLabel = if (replies.editing) stringResource(MochiR.string.common_save) else stringResource(R.string.chat_message_send),
                banner = {
                    Column {
                        if (replies.editing) {
                            EditComposerPreview(onCancel = replies.onCancelEdit)
                        } else {
                            thread.refusal?.let { refusal ->
                                RefusalLine(refusal, onUnencrypted = replies.onSendUnencrypted, enabled = !thread.isSending)
                            }
                        }
                        LengthLine(text)
                    }
                },
            )
        }
    }
}

/**
 * A message of the thread: the root, plain, or a reply with [actions], which
 * offer on a long press what the main timeline offers on a message, and its
 * reactions beneath.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ThreadMessage(message: ChatMessage, chatId: String, actions: ReplyActions? = null) {
    var menu by remember { mutableStateOf(false) }
    val own = actions != null && message.member == actions.identity
    val offered = actions != null && !message.deleted
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (actions?.match == message.id) {
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                } else {
                    Modifier
                },
            )
            .then(if (offered) Modifier.combinedClickable(onClick = {}, onLongClick = { menu = true }) else Modifier),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = message.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(
                text = LocalFormat.current.formatTimestamp(message.created),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            message.deleted -> Text(
                text = stringResource(R.string.chat_message_deleted),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            awaitingKeys(message) -> Text(
                text = encryptedText(message),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                val attachments = messageAttachments(chatId, message.attachments)
                if (attachments.isNotEmpty()) {
                    AttachmentGallery(
                        attachments = attachments,
                        urlBuilder = { att -> attachmentAddress("chat", chatId, att.id) },
                        thumbnailUrlBuilder = { att -> attachmentAddress("chat", chatId, att.id, "thumbnail") },
                        previewUrlBuilder = { att ->
                            attachmentAddress("chat", chatId, att.id, if (att.previewUrl != null) "preview" else "thumbnail")
                        },
                    )
                }
                if (message.body.isNotEmpty()) {
                    Text(
                        text = remember(message.body, actions?.query) { highlightQuery(message.body, actions?.query.orEmpty()) },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                MessageWarning(message, isOwn = false)
            }
        }
        if (offered) {
            MessageReactions(message, onReact = { reaction -> actions.onReact(message.id, reaction) })
            Box {
                MessageMenu(
                    expanded = menu,
                    onDismiss = { menu = false },
                    onSelect = null,
                    onReply = null,
                    onThread = null,
                    onForward = { actions.onForward(message) },
                    onEdit = { actions.onEdit(message) }.takeIf { own && actions.canWrite && canEditMessage(message, own) },
                    onDelete = { actions.onDelete(message) }.takeIf { own && actions.canWrite },
                )
            }
        }
    }
}
