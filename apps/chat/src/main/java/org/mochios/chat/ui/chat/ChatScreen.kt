// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.chat

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.android.push.SystemNotifications
import org.mochios.android.ui.components.AboutDialog
import org.mochios.android.ui.components.DrawerActionRow
import org.mochios.android.ui.components.FeatureDrawerItem
import org.mochios.android.ui.components.FeatureListDrawer
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.android.ui.components.NotFoundState
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.model.ReactionCount
import org.mochios.android.model.ReactionType
import org.mochios.android.ui.components.AttachmentGallery
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.NotificationBell
import org.mochios.android.ui.components.ReactionBar
import org.mochios.chat.R
import org.mochios.chat.model.ChatMessage
import org.mochios.chat.model.ChatStatus
import org.mochios.chat.ui.chatlist.ChatListViewModel
import org.mochios.chat.ui.router.CHAT_FEATURE
import org.mochios.android.R as MochiR

/**
 * Chat detail screen wrapped in a [FeatureListDrawer]. The drawer holds
 * the user's chat list (so swiping in from the left switches chats
 * directly without an intervening list page) plus actions (New chat,
 * Logout). When [chatId] is empty (first launch with no recorded
 * last-viewed), the drawer auto-opens over a "pick a chat" placeholder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    onSelectChat: (String) -> Unit,
    onNewChat: () -> Unit,
    onSettings: (String) -> Unit,
    onChatDeleted: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onLogout: () -> Unit,
    listViewModel: ChatListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(
        if (chatId.isEmpty()) DrawerValue.Open else DrawerValue.Closed
    )
    val drawerScope = rememberCoroutineScope()
    val listUiState by listViewModel.uiState.collectAsState()
    var showAbout by remember { mutableStateOf(false) }

    // Persist last-viewed so the next cold start lands here. Empty id is
    // the "no chat selected" sentinel — don't write that or we'd wipe a
    // real prior selection.
    LaunchedEffect(chatId) {
        if (chatId.isNotBlank()) {
            LastViewedStore.set(context, CHAT_FEATURE, chatId)
            // Dismiss any system-tray push notifications targeting this
            // chat. The server-side clear/object call inside chat.star's
            // action_view marks the bell row read but doesn't reach the
            // status bar; this closes that gap so opening a chat
            // directly (without tapping the push) also clears the tray.
            SystemNotifications.cancelFor(context, "chat", chatId)
        }
    }

    val pinnedChats by listViewModel.pinned.collectAsState()
    val drawerItems = remember(listUiState.chats, pinnedChats) {
        listViewModel.filteredChats().map { chat ->
            val key = chat.fingerprint.ifEmpty { chat.id }
            FeatureDrawerItem(
                id = key,
                title = chat.name,
                icon = Icons.Default.ChatBubbleOutline,
                trailingIcon = if (key in pinnedChats) Icons.Outlined.PushPin else null,
            )
        }
    }

    FeatureListDrawer(
        drawerState = drawerState,
        items = drawerItems,
        selectedId = chatId,
        onItemClick = { item ->
            drawerScope.launch { drawerState.close() }
            if (item.id != chatId) onSelectChat(item.id)
        },
        actions = {
            DrawerActionRow(
                title = stringResource(R.string.chat_list_new),
                icon = Icons.Default.Add,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    onNewChat()
                },
            )
            DrawerActionRow(
                title = stringResource(R.string.chat_list_logout),
                icon = Icons.AutoMirrored.Filled.Logout,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    onLogout()
                },
            )
            DrawerActionRow(
                title = stringResource(MochiR.string.about_label),
                icon = Icons.Default.Info,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    showAbout = true
                },
            )
        },
    ) {
        if (chatId.isEmpty()) {
            ChatDrawerPlaceholder(
                onOpenDrawer = { drawerScope.launch { drawerState.open() } },
            )
        } else {
            ChatContent(
                chatId = chatId,
                onOpenDrawer = { drawerScope.launch { drawerState.open() } },
                onSettings = onSettings,
                onChatDeleted = onChatDeleted,
                onOpenNotifications = onOpenNotifications,
            )
        }
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatDrawerPlaceholder(onOpenDrawer: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chat_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.chat_list_title))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.chat_list_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatContent(
    chatId: String,
    onOpenDrawer: () -> Unit,
    onSettings: (String) -> Unit,
    onChatDeleted: () -> Unit,
    onOpenNotifications: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val copiedMessage = stringResource(MochiR.string.common_copied)
    val deleteOwnOnlyMessage = stringResource(R.string.chat_delete_own_only)
    // Messages awaiting delete confirmation (single from the menu, or the whole
    // selection); null when no confirm dialog is open.
    var pendingDelete by remember { mutableStateOf<List<String>?>(null) }
    // Whether the top-bar overflow (three-dot) menu is expanded.
    var menuExpanded by remember { mutableStateOf(false) }
    // Whether the leave-chat / delete-locally confirmation dialogs are open.
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val grouped = remember(uiState.messages) { groupMessagesByDate(uiState.messages) }

    LaunchedEffect(uiState.chatDeleted) {
        if (uiState.chatDeleted) onChatDeleted()
    }

    // Match ids come from the server search (newest-first). The active match is
    // scrolled to and highlighted; the counter/navigation reflect the full set.
    val searchMatchIds = uiState.searchMatchIds
    val searchMatchIndex = uiState.searchMatchIndex
        .coerceIn(0, (searchMatchIds.size - 1).coerceAtLeast(0))
    val activeMatchId = searchMatchIds.getOrNull(searchMatchIndex)

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(uiState.messages.size) {
        // Don't yank the list to the bottom while the user is navigating search
        // matches — the match-scroll effect below owns positioning then.
        if (uiState.messages.isNotEmpty() && !uiState.searchOpen) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    LaunchedEffect(activeMatchId, uiState.messages) {
        val id = activeMatchId ?: return@LaunchedEffect
        val idx = messageLazyIndex(grouped, uiState.hasMore, id)
        when {
            idx >= 0 -> listState.animateScrollToItem(idx)
            // The match lives in older history that isn't loaded yet. Page back
            // one chunk; this effect re-runs as messages grow, so it keeps
            // paging until the match appears (or there's nothing older left).
            uiState.hasMore && !uiState.isLoadingMore -> viewModel.loadMoreOlder()
        }
    }

    Scaffold(
        topBar = {
            val members = uiState.chat.members
            val isGroup = members.size > 2
            val peer = if (members.size == 2) members.firstOrNull { it.id != uiState.identity } else null
            val peerAvatarUrl = peer?.let { "/people/${it.id}/-/avatar" }
            val youLabel = stringResource(R.string.chat_members_you)
            val membersSubtitle = remember(members, uiState.identity, youLabel) {
                if (!isGroup) "" else {
                    val ordered = mutableListOf<String>()
                    members.firstOrNull { it.id == uiState.identity }?.let { ordered += youLabel }
                    members.filter { it.id != uiState.identity }.forEach { ordered += it.name }
                    ordered.joinToString(", ")
                }
            }
            if (uiState.searchOpen) {
                ChatSearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = { text -> viewModel.setSearchQuery(text) },
                    onClose = { viewModel.closeSearch() },
                    matchPosition = if (searchMatchIds.isEmpty()) 0 else searchMatchIndex + 1,
                    matchCount = searchMatchIds.size,
                    onUp = { viewModel.setSearchMatchIndex(searchMatchIndex + 1) },
                    onDown = { viewModel.setSearchMatchIndex(searchMatchIndex - 1) },
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (peerAvatarUrl != null) {
                                EntityAvatar(
                                    name = peer.name.ifBlank { uiState.chat.name },
                                    src = peerAvatarUrl,
                                    seed = peer.id,
                                    size = 32.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Column {
                                Text(
                                    text = uiState.chat.name.ifBlank {
                                        stringResource(R.string.chat_messages_loading)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (membersSubtitle.isNotBlank()) {
                                    Text(
                                        text = membersSubtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = stringResource(R.string.chat_list_title)
                            )
                        }
                    },
                    actions = {
                        NotificationBell(onClick = onOpenNotifications)
                        if (chatId.isNotEmpty()) {
                            IconButton(onClick = { viewModel.openSearch() }) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = stringResource(R.string.chat_list_search)
                                )
                            }
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(MochiR.string.common_more_options)
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                if (uiState.isPinned) R.string.chat_unpin
                                                else R.string.chat_pin
                                            )
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (uiState.isPinned) {
                                                ImageVector.vectorResource(R.drawable.ic_push_pin_off)
                                            } else {
                                                Icons.Outlined.PushPin
                                            },
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.togglePin()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chat_mark_read)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.DoneAll, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.markReadNow()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(MochiR.string.settings_title)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Settings, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onSettings(uiState.chat.fingerprint.ifEmpty { chatId })
                                    }
                                )
                                if (uiState.chat.status == ChatStatus.ACTIVE) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.chat_settings_leave)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Logout,
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            showLeaveDialog = true
                                        }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.chat_settings_delete)) },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.Delete, contentDescription = null)
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            showDeleteDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
            if (uiState.selectionMode) {
                SelectionBar(
                    count = uiState.selectedIds.size,
                    onClose = { viewModel.exitSelection() },
                    onCopy = {
                        val text = uiState.messages
                            .filter { it.id in uiState.selectedIds }
                            .map { it.body }
                            .filter { it.isNotBlank() }
                            .joinToString("\n")
                        if (text.isNotBlank()) {
                            clipboard.setText(AnnotatedString(text))
                            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                        }
                        viewModel.exitSelection()
                    },
                    onForward = { viewModel.forwardSelected() },
                    onDelete = {
                        val selected = uiState.messages.filter { it.id in uiState.selectedIds }
                        if (selected.any { it.member != uiState.identity }) {
                            Toast.makeText(context, deleteOwnOnlyMessage, Toast.LENGTH_SHORT).show()
                        } else {
                            pendingDelete = uiState.selectedIds.toList()
                        }
                    },
                )
            }
            when {
                uiState.isLoading && uiState.messages.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error is MochiError.NotFoundError && uiState.messages.isEmpty() -> {
                    NotFoundState(
                        title = stringResource(R.string.chat_chat_not_found),
                        onBack = onOpenDrawer,
                    )
                }
                uiState.error != null && uiState.messages.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = uiState.error!!.userMessage(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                uiState.messages.isEmpty() -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.chat_messages_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (uiState.hasMore) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (uiState.isLoadingMore) {
                                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                                    } else {
                                        TextButton(onClick = { viewModel.loadMoreOlder() }) {
                                            Text(stringResource(R.string.chat_messages_load_more))
                                        }
                                    }
                                }
                            }
                        }
                        grouped.forEach { entry ->
                            when (entry) {
                                is MessageListEntry.DateHeader -> {
                                    item(key = "date-${entry.dayKey}") {
                                        DateSeparator(entry.epochSeconds)
                                    }
                                }
                                is MessageListEntry.MessageItem -> {
                                    item(key = entry.message.id) {
                                        MessageBubble(
                                            message = entry.message,
                                            isOwn = entry.message.member == uiState.identity,
                                            isGroup = uiState.chat.members.size > 2,
                                            chatId = uiState.chat.id,
                                            selectionMode = uiState.selectionMode,
                                            isSelected = entry.message.id in uiState.selectedIds,
                                            isSearchMatch = entry.message.id == activeMatchId,
                                            searchQuery = if (uiState.searchOpen) uiState.searchQuery else "",
                                            replyToMessage = entry.message.replyTo?.let { rid ->
                                                uiState.messages.firstOrNull { it.id == rid }
                                            },
                                            onStartSelect = { viewModel.enterSelection(entry.message.id) },
                                            onToggleSelect = { viewModel.toggleSelection(entry.message.id) },
                                            onReply = { viewModel.startReply(entry.message) },
                                            onDelete = { pendingDelete = listOf(entry.message.id) },
                                            onReact = { reaction -> viewModel.react(entry.message.id, reaction) },
                                            onForward = { viewModel.openForward(entry.message.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            uiState.replyingTo?.let { replied ->
                ReplyComposerPreview(
                    replied = replied,
                    onCancel = { viewModel.cancelReply() },
                )
            }

            ComposerBar(
                value = draft,
                onValueChange = { draft = it },
                isSending = uiState.isSending,
                enabled = uiState.chat.id.isNotEmpty() && uiState.chat.status == ChatStatus.ACTIVE,
                pendingAttachments = uiState.pendingAttachments,
                onAddAttachments = { viewModel.addAttachments(it) },
                onRemoveAttachment = { viewModel.removeAttachment(it) },
                onMoveAttachment = { uri, dir -> viewModel.moveAttachment(uri, dir) },
                onSend = {
                    viewModel.sendMessage(draft)
                    draft = ""
                }
            )

            if (uiState.forwardMessageIds.isNotEmpty()) {
                ChatForwardSheet(
                    chats = uiState.forwardChats,
                    friends = uiState.forwardFriends,
                    loading = uiState.forwardLoading,
                    onDismiss = { viewModel.closeForward() },
                    onSelect = { chat -> viewModel.forwardToChat(chat.id) },
                    onSelectFriend = { friend -> viewModel.forwardToFriend(friend.id) },
                )
            }

            pendingDelete?.let { ids ->
                AlertDialog(
                    onDismissRequest = { pendingDelete = null },
                    title = { Text(stringResource(R.string.chat_delete_confirm_title)) },
                    text = { Text(stringResource(R.string.chat_delete_confirm_body)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteMessages(ids)
                                pendingDelete = null
                            }
                        ) {
                            Text(
                                stringResource(MochiR.string.common_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDelete = null }) {
                            Text(stringResource(MochiR.string.common_cancel))
                        }
                    },
                )
            }

            if (showLeaveDialog) {
                AlertDialog(
                    onDismissRequest = { showLeaveDialog = false },
                    title = { Text(stringResource(R.string.chat_settings_leave_title)) },
                    text = { Text(stringResource(R.string.chat_settings_leave_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showLeaveDialog = false
                                viewModel.leaveChat()
                            }
                        ) {
                            Text(
                                stringResource(R.string.chat_settings_leave),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLeaveDialog = false }) {
                            Text(stringResource(MochiR.string.common_cancel))
                        }
                    },
                )
            }

            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text(stringResource(R.string.chat_settings_delete_title)) },
                    text = { Text(stringResource(R.string.chat_settings_delete_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteDialog = false
                                viewModel.deleteChat()
                            }
                        ) {
                            Text(
                                stringResource(R.string.chat_settings_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text(stringResource(MochiR.string.common_cancel))
                        }
                    },
                )
            }
            }
        }
    }
}

/**
 * Build an [AnnotatedString] of [text] with every case-insensitive occurrence
 * of [query] painted on a yellow background (black text for contrast), used to
 * highlight search hits inline. Returns the plain text when [query] is blank.
 */
private fun highlightQuery(text: String, query: String): AnnotatedString {
    val needle = query.trim()
    if (needle.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        val haystack = text.lowercase()
        val lowerNeedle = needle.lowercase()
        var start = 0
        while (true) {
            val hit = haystack.indexOf(lowerNeedle, start)
            if (hit < 0) {
                append(text.substring(start))
                break
            }
            append(text.substring(start, hit))
            withStyle(SpanStyle(background = Color(0xFFFFEB3B), color = Color.Black)) {
                append(text.substring(hit, hit + needle.length))
            }
            start = hit + needle.length
        }
    }
}

/**
 * The LazyColumn item index of a message id within the loaded list, accounting
 * for the leading "load older" item and date separators. Returns -1 when the
 * message isn't currently loaded (so we can't scroll to it).
 */
private fun messageLazyIndex(
    grouped: List<MessageListEntry>,
    hasMore: Boolean,
    messageId: String,
): Int {
    var index = if (hasMore) 1 else 0
    for (entry in grouped) {
        if (entry is MessageListEntry.MessageItem && entry.message.id == messageId) return index
        index++
    }
    return -1
}

/**
 * In-conversation "find" app bar: an auto-focused query field over the loaded
 * messages, plus a match counter and up/down navigation that scroll to and
 * highlight each hit. The conversation stays visible behind it (no results
 * overlay), mirroring a find-in-page bar.
 *
 * @param matchPosition 1-based index of the active match (0 when none).
 * @param matchCount total number of matches.
 * @param onUp jump to the previous (older) match.
 * @param onDown jump to the next (newer) match.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    matchPosition: Int,
    matchCount: Int,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(MochiR.string.common_back),
                )
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.chat_search_hint)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(MochiR.string.common_close),
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
        actions = {
            if (query.isNotBlank()) {
                Text(
                    text = "$matchPosition/$matchCount",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onUp, enabled = matchPosition < matchCount) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.chat_search_prev),
                    )
                }
                IconButton(onClick = onDown, enabled = matchPosition > 1) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.chat_search_next),
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    isOwn: Boolean,
    isGroup: Boolean,
    chatId: String,
    selectionMode: Boolean,
    isSelected: Boolean,
    isSearchMatch: Boolean,
    searchQuery: String,
    replyToMessage: ChatMessage?,
    onStartSelect: () -> Unit,
    onToggleSelect: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onReact: (String) -> Unit,
    onForward: () -> Unit,
) {
    val format = LocalFormat.current
    var menuExpanded by remember { mutableStateOf(false) }

    // Non-deleted messages get a context menu (reply / forward / select, plus
    // delete on your own). Tombstones have no actions.
    val canDelete = !message.deleted && isOwn
    val hasMenu = !message.deleted

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected || isSearchMatch) {
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                } else {
                    Modifier
                }
            )
            .then(
                if (selectionMode) Modifier.clickable(onClick = onToggleSelect) else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
    ) {
        if (isGroup && !isOwn) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            ) {
                EntityAvatar(
                    name = message.name,
                    src = "/chat/$chatId/-/${message.id}/asset/avatar",
                    seed = message.member,
                    size = 16.dp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = message.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isOwn) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    // Reserve a strip below the bubble for the reaction button,
                    // which floats just under the bottom-right corner so it never
                    // sits on the message text (even for short messages).
                    .padding(bottom = if (selectionMode) 0.dp else 18.dp)
                    .then(
                        when {
                            selectionMode -> Modifier.clickable(onClick = onToggleSelect)
                            hasMenu -> Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = { menuExpanded = true },
                            )
                            else -> Modifier
                        }
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (!isOwn && !isGroup) {
                        Text(
                            text = message.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    if (replyToMessage != null && !message.deleted) {
                        ReplyQuote(replied = replyToMessage, isOwn = isOwn)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (message.deleted) {
                        Text(
                            text = stringResource(R.string.chat_message_deleted),
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        if (message.body.isNotEmpty()) {
                            Text(
                                text = highlightQuery(message.body, searchQuery),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isOwn) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                        if (message.attachments.isNotEmpty()) {
                            if (message.body.isNotEmpty()) Spacer(modifier = Modifier.height(6.dp))
                            AttachmentGallery(
                                attachments = message.attachments,
                                // The server's `url`/`thumbnail_url` point at the
                                // flat `/chat/attachments/<id>` route, which does
                                // not serve the asset here. Always build the chat
                                // asset route instead: `/chat/<chatId>/-/attachments/<id>`.
                                urlBuilder = { att ->
                                    "/chat/$chatId/-/attachments/${att.id}"
                                },
                                thumbnailUrlBuilder = { att ->
                                    "/chat/$chatId/-/attachments/${att.id}/thumbnail"
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = format.formatTimestamp(message.created),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (hasMenu && !selectionMode) {
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_message_select)) },
                        leadingIcon = { Icon(Icons.Outlined.CheckBox, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onStartSelect()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_message_reply)) },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onReply()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_message_forward)) },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onForward()
                        },
                    )
                    if (canDelete) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(MochiR.string.common_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            if (!message.deleted && !selectionMode) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Single bar with pills + built-in add button, so the
                    // add/change/clear affordance is consistent with feeds.
                    ReactionBar(
                        reactions = chatReactionCounts(message.reactionCounts, message.myReaction),
                        onReact = onReact,
                        onRemoveReaction = { onReact("none") },
                        currentReaction = message.myReaction?.let { key ->
                            ReactionType.fromString(key)
                        },
                        maxVisible = 3
                    )
                }
            }
        }
    }
}

/**
 * Quoted preview of the message a bubble is replying to: an accent bar, the
 * original sender's name, and a one-line snippet of its body (or "Attachment").
 */
@Composable
private fun ReplyQuote(replied: ChatMessage, isOwn: Boolean) {
    val accent = if (isOwn) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }
    val preview = replied.body.ifBlank { stringResource(R.string.chat_reply_attachment) }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                text = replied.name,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Contextual top strip shown while multi-selecting: a close button, the count,
 * and batch Forward / Delete actions for the current selection.
 */
@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = null)
        }
        Text(
            text = stringResource(R.string.chat_selection_title, count),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onCopy, enabled = count > 0) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = stringResource(MochiR.string.common_copy),
            )
        }
        IconButton(onClick = onForward, enabled = count > 0) {
            Icon(
                Icons.AutoMirrored.Filled.Forward,
                contentDescription = stringResource(R.string.chat_message_forward),
            )
        }
        IconButton(onClick = onDelete, enabled = count > 0) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = stringResource(MochiR.string.common_delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Strip above the composer showing the message being replied to, with a button
 * to cancel the reply.
 */
@Composable
private fun ReplyComposerPreview(replied: ChatMessage, onCancel: () -> Unit) {
    val preview = replied.body.ifBlank { stringResource(R.string.chat_reply_attachment) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Reply,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.chat_replying_to, replied.name),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Close, contentDescription = null)
        }
    }
}

/**
 * Adapt the chat server's reaction shape — a `{reaction: count}` map plus the
 * viewer's own reaction — into the lib [ReactionBar]'s `List<ReactionCount>`,
 * ordered most-reacted first so the visible pills show the top reactions.
 */
private fun chatReactionCounts(counts: Map<String, Int>, myReaction: String?): List<ReactionCount> =
    counts.mapNotNull { (key, count) ->
        ReactionType.fromString(key)?.let { type ->
            ReactionCount(type = type, count = count, isMine = key.equals(myReaction, ignoreCase = true))
        }
    }.sortedByDescending { reaction -> reaction.count }

private sealed class MessageListEntry {
    data class DateHeader(val dayKey: String, val epochSeconds: Long) : MessageListEntry()
    data class MessageItem(val message: ChatMessage) : MessageListEntry()
}

/**
 * Forward bottom sheet: a filterable list of the user's other active chats and
 * of friends without an existing direct chat. Tapping a chat forwards the open
 * messages there; tapping a friend forwards into their 1-on-1 chat, which the
 * server creates or reuses atomically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatForwardSheet(
    chats: List<org.mochios.chat.model.Chat>,
    friends: List<org.mochios.chat.model.Friend>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (org.mochios.chat.model.Chat) -> Unit,
    onSelectFriend: (org.mochios.chat.model.Friend) -> Unit,
) {
    var filter by remember { mutableStateOf("") }
    val filtered = remember(chats, filter) {
        if (filter.isBlank()) chats
        else chats.filter { it.name.contains(filter.trim(), ignoreCase = true) }
    }
    val filteredFriends = remember(friends, filter) {
        if (filter.isBlank()) friends
        else friends.filter { it.name.contains(filter.trim(), ignoreCase = true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.chat_forward_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.chat_forward_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text(stringResource(R.string.chat_forward_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                filtered.isEmpty() && filteredFriends.isEmpty() -> Text(
                    text = stringResource(R.string.chat_forward_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                else -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    if (filtered.isNotEmpty()) {
                        // Only label the sections when both are present.
                        if (filteredFriends.isNotEmpty()) {
                            item("chats-header") {
                                ForwardSectionHeader(stringResource(R.string.chat_forward_chats))
                            }
                        }
                        items(filtered, key = { "chat-" + it.id }) { chat ->
                            Text(
                                text = chat.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(chat) }
                                    .padding(vertical = 12.dp),
                            )
                            HorizontalDivider()
                        }
                    }
                    if (filteredFriends.isNotEmpty()) {
                        if (filtered.isNotEmpty()) {
                            item("friends-header") {
                                ForwardSectionHeader(stringResource(R.string.chat_forward_friends))
                            }
                        }
                        items(filteredFriends, key = { "friend-" + it.id }) { friend ->
                            Text(
                                text = friend.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectFriend(friend) }
                                    .padding(vertical = 12.dp),
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForwardSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
    )
}

private fun groupMessagesByDate(messages: List<ChatMessage>): List<MessageListEntry> {
    val tz = java.util.TimeZone.getDefault()
    val out = mutableListOf<MessageListEntry>()
    var lastKey: String? = null
    for (msg in messages) {
        val cal = java.util.Calendar.getInstance(tz).apply { timeInMillis = msg.created * 1000L }
        val key = "${cal.get(java.util.Calendar.YEAR)}-" +
            "${cal.get(java.util.Calendar.MONTH) + 1}-" +
            "${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
        if (key != lastKey) {
            out += MessageListEntry.DateHeader(key, msg.created)
            lastKey = key
        }
        out += MessageListEntry.MessageItem(msg)
    }
    return out
}

@Composable
private fun DateSeparator(epochSeconds: Long) {
    val format = LocalFormat.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = format.formatDate(epochSeconds),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ComposerBar(
    value: String,
    onValueChange: (String) -> Unit,
    isSending: Boolean,
    enabled: Boolean,
    pendingAttachments: List<android.net.Uri>,
    onAddAttachments: (List<android.net.Uri>) -> Unit,
    onRemoveAttachment: (android.net.Uri) -> Unit,
    onMoveAttachment: (android.net.Uri, Int) -> Unit,
    onSend: () -> Unit,
) {
    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        onAddAttachments(uris)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        if (pendingAttachments.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                pendingAttachments.forEachIndexed { index, uri ->
                    androidx.compose.material3.AssistChip(
                        onClick = { onRemoveAttachment(uri) },
                        label = {
                            Text(
                                uri.lastPathSegment?.takeLast(20)
                                    ?: stringResource(R.string.chat_attachment_label),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        leadingIcon = if (pendingAttachments.size > 1) {
                            {
                                Row {
                                    if (index > 0) {
                                        IconButton(
                                            onClick = { onMoveAttachment(uri, -1) },
                                            modifier = Modifier.width(20.dp).height(20.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.ExpandLess,
                                                contentDescription = stringResource(R.string.chat_attachment_move_up),
                                                modifier = Modifier.width(14.dp).height(14.dp),
                                            )
                                        }
                                    }
                                    if (index < pendingAttachments.lastIndex) {
                                        IconButton(
                                            onClick = { onMoveAttachment(uri, 1) },
                                            modifier = Modifier.width(20.dp).height(20.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.ExpandMore,
                                                contentDescription = stringResource(R.string.chat_attachment_move_down),
                                                modifier = Modifier.width(14.dp).height(14.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        } else null,
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.chat_attachment_remove),
                                modifier = Modifier.width(14.dp).height(14.dp),
                            )
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { filePickerLauncher.launch("*/*") },
                enabled = enabled,
            ) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = stringResource(R.string.chat_attachment_add),
                )
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.chat_message_placeholder)) },
                enabled = enabled,
                maxLines = 4,
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = enabled && !isSending && (value.isNotBlank() || pendingAttachments.isNotEmpty()),
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.chat_message_send),
                    )
                }
            }
        }
    }
}
