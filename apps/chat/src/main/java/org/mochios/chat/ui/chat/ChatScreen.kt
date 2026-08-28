// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.chat

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.android.push.SystemNotifications
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.model.ReactionCount
import org.mochios.android.model.ReactionType
import org.mochios.android.ui.components.AboutDialog
import org.mochios.android.ui.components.AttachmentGallery
import org.mochios.android.ui.components.ComposeBar
import org.mochios.android.ui.components.ComposeBarAttachments
import org.mochios.android.ui.components.ComposeBarDefaults
import org.mochios.android.ui.components.DrawerActionRow
import org.mochios.android.ui.components.DrawerItem
import org.mochios.android.ui.components.DrawerTitle
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.EntityIconCircle
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiBottomSheet
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiListDrawer
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.components.NotFoundState
import org.mochios.android.ui.components.NotificationBell
import org.mochios.android.ui.components.ReactionBar
import org.mochios.chat.R
import org.mochios.chat.model.ChatMessage
import org.mochios.chat.model.ChatStatus
import org.mochios.chat.ui.chatlist.ChatListViewModel
import org.mochios.chat.ui.router.CHAT_FEATURE
import org.mochios.android.R as MochiR

/**
 * Chat detail inside a [MochiListDrawer] holding the chat list; an empty
 * [chatId] opens the drawer over a placeholder.
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
            // The server's mark-read does not reach the status bar; clear this
            // chat's tray notifications too.
            SystemNotifications.cancelFor(context, "chat", chatId)
        }
    }

    val pinnedChats by listViewModel.pinned.collectAsState()
    val drawerItems = remember(listUiState.chats, pinnedChats) {
        listViewModel.filteredChats().map { chat ->
            val key = chat.fingerprint.ifEmpty { chat.id }
            val isDirect = chat.members == 2 && chat.other.isNotBlank()
            DrawerItem(
                id = key,
                title = chat.name,
                icon = if (chat.members > 2) Icons.Default.Groups else Icons.Default.ChatBubbleOutline,
                trailingIcon = if (key in pinnedChats) Icons.Outlined.PushPin else null,
                avatarUrl = if (isDirect) "/people/${chat.other}/-/avatar" else null,
                seed = key,
            )
        }
    }

    MochiListDrawer(
        drawerState = drawerState,
        header = { DrawerTitle(stringResource(R.string.chat_list_title)) },
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
                    MochiIconButton(onClick = onOpenDrawer) {
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
    // Editing pre-fills the composer with the current body and clears it again
    // on cancel, so a cancelled edit never leaks the old text into a new message.
    LaunchedEffect(uiState.editing?.id) {
        draft = uiState.editing?.body ?: ""
    }
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
    val messageZone = LocalFormat.current.timeZone
    val grouped = remember(uiState.messages, messageZone) {
        groupMessagesByDate(uiState.messages, messageZone)
    }

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
            // The list holds the load-older row plus every date header, not just
            // the messages, so messages.size - 1 lands short by headers + 1 and
            // the newest message stays off screen — which is what opening a chat
            // does. lastLazyIndex counts the same items the list emits.
            listState.animateScrollToItem(lastLazyIndex(grouped, uiState.hasMore))
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
                            } else if (isGroup) {
                                EntityIconCircle(
                                    seed = uiState.chat.fingerprint.ifEmpty { uiState.chat.id },
                                    icon = Icons.Default.Groups,
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
                        MochiIconButton(onClick = onOpenDrawer) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = stringResource(R.string.chat_list_title)
                            )
                        }
                    },
                    actions = {
                        NotificationBell(onClick = onOpenNotifications)
                        if (chatId.isNotEmpty()) {
                            MochiIconButton(onClick = { viewModel.openSearch() }) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = stringResource(R.string.chat_list_search)
                                )
                            }
                            MochiIconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(MochiR.string.common_more_options)
                                )
                            }
                            MochiDropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                MochiDropdownMenuItem(
                                    text = { Text(stringResource(
                                                if (uiState.isPinned) R.string.chat_unpin
                                                else R.string.chat_pin
                                            )) },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.togglePin()
                                    },
                                    leadingIcon = { Icon(if (uiState.isPinned) {
                                                ImageVector.vectorResource(R.drawable.ic_push_pin_off)
                                            } else {
                                                Icons.Outlined.PushPin
                                            }, contentDescription = null) },
                                )
                                MochiDropdownMenuItem(
                                    text = { Text(stringResource(R.string.chat_mark_read)) },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.markReadNow()
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.DoneAll, contentDescription = null) },
                                )
                                MochiDropdownMenuItem(
                                    text = { Text(stringResource(MochiR.string.settings_title)) },
                                    onClick = {
                                        menuExpanded = false
                                        onSettings(uiState.chat.fingerprint.ifEmpty { chatId })
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                                )
                                if (uiState.chat.status == ChatStatus.ACTIVE) {
                                    MochiDropdownMenuItem(
                                        text = { Text(stringResource(R.string.chat_settings_leave)) },
                                        onClick = {
                                            menuExpanded = false
                                            showLeaveDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
                                    )
                                } else {
                                    MochiDropdownMenuItem(
                                        text = { Text(stringResource(R.string.chat_settings_delete)) },
                                        onClick = {
                                            menuExpanded = false
                                            showDeleteDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
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
                // Consume as well as pad: the composer at the foot of this Box
                // consumes the navigation-bar inset itself and would otherwise
                // count it twice.
                .consumeWindowInsets(padding)
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
                    ErrorState(
                        error = uiState.error!!,
                        onRetry = viewModel::load,
                    )
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
                                        MochiTextButton(onClick = { viewModel.loadMoreOlder() }) {
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
                                            onEdit = { viewModel.startEdit(entry.message) },
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

            ComposeBar(
                value = draft,
                onValueChange = { draft = it },
                onSend = {
                    if (uiState.editing != null) {
                        viewModel.saveEdit(draft)
                    } else {
                        viewModel.sendMessage(draft)
                    }
                    draft = ""
                },
                placeholder = stringResource(R.string.chat_message_placeholder),
                enabled = uiState.chat.id.isNotEmpty() && uiState.chat.status == ChatStatus.ACTIVE,
                isSending = uiState.isSending,
                sendLabel = if (uiState.editing != null) {
                    stringResource(MochiR.string.common_save)
                } else {
                    stringResource(R.string.chat_message_send)
                },
                windowInsets = ComposeBarDefaults.WindowInsets,
                attachments = ComposeBarAttachments(
                    pending = uiState.pendingAttachments,
                    onAdd = { viewModel.addAttachments(it) },
                    onRemove = { viewModel.removeAttachment(it) },
                    resolveFileName = viewModel::fileName,
                    addLabel = stringResource(R.string.chat_attachment_add),
                    fallbackLabel = stringResource(R.string.chat_attachment_label),
                    removeLabel = stringResource(R.string.chat_attachment_remove),
                    onMove = { uri, dir -> viewModel.moveAttachment(uri, dir) },
                    moveUpLabel = stringResource(R.string.chat_attachment_move_up),
                    moveDownLabel = stringResource(R.string.chat_attachment_move_down),
                ),
                banner = uiState.editing?.let {
                    {
                        EditComposerPreview(onCancel = { viewModel.cancelEdit() })
                    }
                } ?: uiState.replyingTo?.let { replied ->
                    {
                        ReplyComposerPreview(
                            replied = replied,
                            onCancel = { viewModel.cancelReply() },
                        )
                    }
                },
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
                MochiAlertDialog(
                    onDismissRequest = { pendingDelete = null },
                    title = stringResource(R.string.chat_delete_confirm_title),
                    text = stringResource(R.string.chat_delete_confirm_body),
                    confirmText = stringResource(MochiR.string.common_delete),
                    onConfirm = {
                        viewModel.deleteMessages(ids)
                        pendingDelete = null
                    },
                    destructive = true,
                    dismissText = stringResource(MochiR.string.common_cancel),
                )
            }

            if (showLeaveDialog) {
                MochiAlertDialog(
                    onDismissRequest = { showLeaveDialog = false },
                    title = stringResource(R.string.chat_settings_leave_title),
                    text = stringResource(R.string.chat_settings_leave_message),
                    confirmText = stringResource(R.string.chat_settings_leave),
                    onConfirm = {
                        showLeaveDialog = false
                        viewModel.leaveChat()
                    },
                    destructive = true,
                    dismissText = stringResource(MochiR.string.common_cancel),
                )
            }

            if (showDeleteDialog) {
                MochiAlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = stringResource(R.string.chat_settings_delete_title),
                    text = stringResource(R.string.chat_settings_delete_message),
                    confirmText = stringResource(R.string.chat_settings_delete),
                    onConfirm = {
                        showDeleteDialog = false
                        viewModel.deleteChat()
                    },
                    destructive = true,
                    dismissText = stringResource(MochiR.string.common_cancel),
                )
            }
            }
        }
    }
}

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
 * LazyColumn index of [messageId], counting the load-older row and date
 * headers; -1 when not loaded.
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
 * Index of the last emitted item: the load-older row plus every entry in
 * [grouped].
 */
/**
 * Whether to offer Edit on [message].
 *
 * The server is the authority - it authorises on the author and refuses a
 * tombstone - so this only decides what to show. An attachment-only message
 * has no body to edit, and editing one would blank nothing and stamp it as
 * edited for every member.
 */
internal fun canEditMessage(message: ChatMessage, isOwn: Boolean): Boolean =
    isOwn && !message.deleted && message.body.isNotBlank()

internal fun lastLazyIndex(grouped: List<MessageListEntry>, hasMore: Boolean): Int {
    val leading = if (hasMore) 1 else 0
    return (leading + grouped.size - 1).coerceAtLeast(0)
}

/**
 * Find-in-conversation bar over the loaded messages; [matchPosition] is
 * 1-based, 0 when nothing matches.
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
            MochiIconButton(onClick = onClose) {
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
                        MochiIconButton(onClick = { onQueryChange("") }) {
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
                MochiIconButton(onClick = onUp, enabled = matchPosition < matchCount) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.chat_search_prev),
                    )
                }
                MochiIconButton(onClick = onDown, enabled = matchPosition > 1) {
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
    onEdit: () -> Unit,
) {
    val format = LocalFormat.current
    var menuExpanded by remember { mutableStateOf(false) }

    // Non-deleted messages get a context menu (reply / forward / select, plus
    // delete on your own). Tombstones have no actions.
    val canDelete = !message.deleted && isOwn
    val canEdit = canEditMessage(message, isOwn)
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
            MochiCard(
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
                                },
                                previewUrlBuilder = { att ->
                                    // previewUrl's presence signals the server
                                    // generates previews; the path itself is
                                    // rebuilt on the chat asset route (see above).
                                    if (att.previewUrl != null) {
                                        "/chat/$chatId/-/attachments/${att.id}/preview"
                                    } else {
                                        "/chat/$chatId/-/attachments/${att.id}/thumbnail"
                                    }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (message.edited > 0) {
                            format.formatTimestamp(message.created) +
                                " " + stringResource(R.string.chat_message_edited)
                        } else {
                            format.formatTimestamp(message.created)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (hasMenu && !selectionMode) {
                MochiDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_message_select)) },
                        onClick = {
                            menuExpanded = false
                            onStartSelect()
                        },
                        leadingIcon = { Icon(Icons.Outlined.CheckBox, contentDescription = null) },
                    )
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_message_reply)) },
                        onClick = {
                            menuExpanded = false
                            onReply()
                        },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = null) },
                    )
                    MochiDropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_message_forward)) },
                        onClick = {
                            menuExpanded = false
                            onForward()
                        },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Forward, contentDescription = null) },
                    )
                    if (canEdit) {
                        MochiDropdownMenuItem(
                            text = { Text(stringResource(MochiR.string.common_edit)) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        )
                    }
                    if (canDelete) {
                        MochiDropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(MochiR.string.common_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
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
        MochiIconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = null)
        }
        Text(
            text = stringResource(R.string.chat_selection_title, count),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        MochiIconButton(onClick = onCopy, enabled = count > 0) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = stringResource(MochiR.string.common_copy),
            )
        }
        MochiIconButton(onClick = onForward, enabled = count > 0) {
            Icon(
                Icons.AutoMirrored.Filled.Forward,
                contentDescription = stringResource(R.string.chat_message_forward),
            )
        }
        MochiIconButton(onClick = onDelete, enabled = count > 0) {
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
private fun EditComposerPreview(onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(MochiR.string.common_edit),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        MochiIconButton(onClick = onCancel) {
            Icon(Icons.Default.Close, contentDescription = null)
        }
    }
}

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
        MochiIconButton(onClick = onCancel) {
            Icon(Icons.Default.Close, contentDescription = null)
        }
    }
}

/**
 * Server `{reaction: count}` map to [ReactionBar] rows, most-reacted first.
 */
private fun chatReactionCounts(counts: Map<String, Int>, myReaction: String?): List<ReactionCount> =
    counts.mapNotNull { (key, count) ->
        ReactionType.fromString(key)?.let { type ->
            ReactionCount(type = type, count = count, isMine = key.equals(myReaction, ignoreCase = true))
        }
    }.sortedByDescending { reaction -> reaction.count }

internal sealed class MessageListEntry {
    data class DateHeader(val dayKey: String, val epochSeconds: Long) : MessageListEntry()
    data class MessageItem(val message: ChatMessage) : MessageListEntry()
}

/**
 * Forward sheet: other active chats plus friends without a direct chat; a
 * friend target uses their 1-on-1 chat, which the server creates or reuses.
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
    MochiBottomSheet(onDismissRequest = onDismiss) {
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
            MochiTextField(
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

/**
 * Split [messages] into day buckets with a header at each boundary. [zone] must
 * be the zone the header is rendered in (LocalFormat's, which defaults to UTC),
 * not the device's.
 */
internal fun groupMessagesByDate(
    messages: List<ChatMessage>,
    zone: java.util.TimeZone,
): List<MessageListEntry> {
    val tz = zone
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

