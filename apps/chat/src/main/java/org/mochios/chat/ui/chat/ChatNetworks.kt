// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import org.mochios.chat.R
import org.mochios.chat.model.ChatDetail
import org.mochios.chat.model.ChatMessage
import org.mochios.chat.model.ChatPresence
import org.mochios.chat.model.ChatQuote
import org.mochios.chat.model.ReceiptState
import org.mochios.chat.model.presence

/** Whether a conversation is a group, which names each message's sender and who has read it. */
internal fun isGroupChat(chat: ChatDetail): Boolean = chat.members.size > 2

/** An encrypted message whose keys have not come: no text and no files until they do. */
internal fun awaitingKeys(message: ChatMessage): Boolean =
    message.encrypted != 0 && message.body.isEmpty() && message.attachments.isEmpty()

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
        network = quote.network,
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

/** A message's thread over the conversation: the message, its replies, and a reply box. */
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
) {
    MochiBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            ThreadContent(thread, chatId, canWrite, onDraft, onSend, onEarlier)
        }
    }
}

@Composable
internal fun ColumnScope.ThreadContent(
    thread: ThreadState,
    chatId: String,
    canWrite: Boolean,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onEarlier: () -> Unit,
) {
    MochiSheetHeader(title = stringResource(R.string.chat_thread_title))
    LazyColumn(
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
            else -> items(thread.replies, key = { it.id }) { reply -> ThreadMessage(reply, chatId) }
        }
    }
    if (canWrite) {
        ComposeBar(
            value = thread.draft,
            onValueChange = onDraft,
            onSend = onSend,
            placeholder = stringResource(R.string.chat_thread_reply),
            isSending = thread.isSending,
            sendLabel = stringResource(R.string.chat_message_send),
        )
    }
}

@Composable
private fun ThreadMessage(message: ChatMessage, chatId: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                text = stringResource(R.string.chat_message_encrypted),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                if (message.attachments.isNotEmpty()) {
                    AttachmentGallery(
                        attachments = message.attachments,
                        urlBuilder = { att -> "/chat/$chatId/-/attachments/${att.id}" },
                        thumbnailUrlBuilder = { att -> "/chat/$chatId/-/attachments/${att.id}/thumbnail" },
                        previewUrlBuilder = { att ->
                            if (att.previewUrl != null) {
                                "/chat/$chatId/-/attachments/${att.id}/preview"
                            } else {
                                "/chat/$chatId/-/attachments/${att.id}/thumbnail"
                            }
                        },
                    )
                }
                if (message.body.isNotEmpty()) {
                    Text(text = message.body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
