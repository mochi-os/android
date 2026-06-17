package org.mochios.chat.ui.chat

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage
import org.mochios.android.push.SystemNotifications
import org.mochios.android.ui.components.AboutDialog
import org.mochios.android.ui.components.FeatureDrawerItem
import org.mochios.android.ui.components.FeatureListDrawer
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.android.ui.components.NotFoundState
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.model.ReactionCount
import org.mochios.android.model.ReactionType
import org.mochios.android.model.resolveAttachmentUrl
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

    val drawerItems = remember(listUiState.chats) {
        listViewModel.filteredChats().map { chat ->
            FeatureDrawerItem(
                id = chat.fingerprint.ifEmpty { chat.id },
                title = chat.name,
                icon = Icons.Default.ChatBubbleOutline,
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
            ListItem(
                modifier = Modifier.clickable {
                    drawerScope.launch { drawerState.close() }
                    onNewChat()
                },
                headlineContent = { Text(stringResource(R.string.chat_list_new)) },
                leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
            ListItem(
                modifier = Modifier.clickable {
                    drawerScope.launch { drawerState.close() }
                    onLogout()
                },
                headlineContent = { Text(stringResource(R.string.chat_list_logout)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
            ListItem(
                modifier = Modifier.clickable {
                    drawerScope.launch { drawerState.close() }
                    showAbout = true
                },
                headlineContent = { Text(stringResource(MochiR.string.about_label)) },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
        },
    ) {
        if (chatId.isEmpty()) {
            ChatDrawerPlaceholder(
                onOpenDrawer = { drawerScope.launch { drawerState.open() } },
            )
        } else {
            ChatContent(
                onOpenDrawer = { drawerScope.launch { drawerState.open() } },
                onSettings = onSettings,
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
    onOpenDrawer: () -> Unit,
    onSettings: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val grouped = remember(uiState.messages) { groupMessagesByDate(uiState.messages) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            val members = uiState.chat.members
            val isGroup = members.size > 2
            val peer = if (members.size == 2) members.firstOrNull { it.id != uiState.identity } else null
            val peerAvatarUrl = peer?.let {
                if (viewModel.serverUrl.isNotBlank()) "${viewModel.serverUrl}/people/${it.id}/-/avatar" else null
            }
            val youLabel = stringResource(R.string.chat_members_you)
            val membersSubtitle = remember(members, uiState.identity, youLabel) {
                if (!isGroup) "" else {
                    val ordered = mutableListOf<String>()
                    members.firstOrNull { it.id == uiState.identity }?.let { ordered += youLabel }
                    members.filter { it.id != uiState.identity }.forEach { ordered += it.name }
                    ordered.joinToString(", ")
                }
            }
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
                                text = uiState.chat.name.ifBlank { stringResource(R.string.chat_messages_loading) },
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
                    if (uiState.chat.id.isNotEmpty()) {
                        IconButton(onClick = { viewModel.openSearch() }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.chat_search_hint)
                            )
                        }
                        IconButton(onClick = { onSettings(uiState.chat.fingerprint.ifEmpty { uiState.chat.id }) }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.chat_settings_title)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
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
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
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
                                            serverUrl = viewModel.serverUrl,
                                            chatId = uiState.chat.id,
                                            onDelete = { viewModel.deleteMessages(listOf(entry.message.id)) },
                                            onReact = { reaction -> viewModel.react(entry.message.id, reaction) },
                                            onForward = { viewModel.openForward(entry.message.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
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

            if (uiState.searchOpen) {
                ChatSearchSheet(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    results = uiState.searchResults,
                    loading = uiState.searchLoading,
                    onDismiss = { viewModel.closeSearch() },
                    onResultClick = { result ->
                        val idx = messageLazyIndex(grouped, uiState.hasMore, result.id)
                        viewModel.closeSearch()
                        if (idx >= 0) scope.launch { listState.animateScrollToItem(idx) }
                    },
                )
            }

            if (uiState.forwardMessageId != null) {
                ChatForwardSheet(
                    chats = uiState.forwardChats,
                    loading = uiState.forwardLoading,
                    onDismiss = { viewModel.closeForward() },
                    onSelect = { chat -> viewModel.forwardToChat(chat.id) },
                )
            }
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
 * Search-in-chat bottom sheet: a query field over the server search endpoint
 * plus a results list (sender / excerpt / time). Tapping a result jumps to the
 * message if it's loaded. Mirrors the web chat-search header's capability.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSearchSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<org.mochios.chat.model.ChatSearchResult>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onResultClick: (org.mochios.chat.model.ChatSearchResult) -> Unit,
) {
    val format = LocalFormat.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.chat_search_hint)) },
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
                query.trim().length >= 2 && results.isEmpty() -> Text(
                    text = stringResource(R.string.chat_search_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                else -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(results, key = { it.id }) { result ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onResultClick(result) }
                                .padding(vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = result.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = format.formatTimestamp(result.created),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = result.excerpt.ifBlank { result.body },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    isOwn: Boolean,
    isGroup: Boolean,
    serverUrl: String,
    chatId: String,
    onDelete: () -> Unit,
    onReact: (String) -> Unit,
    onForward: () -> Unit
) {
    val format = LocalFormat.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMessage = stringResource(MochiR.string.common_copied)
    var menuExpanded by remember { mutableStateOf(false) }

    // A long-press menu is only worth showing when there's an action: you can
    // copy any non-empty message, and delete your own. Deleted tombstones have
    // no actions.
    val canCopy = !message.deleted && message.body.isNotEmpty()
    val canForward = !message.deleted
    val canDelete = !message.deleted && isOwn
    val hasMenu = canCopy || canForward || canDelete

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
    ) {
        if (isGroup && !isOwn) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            ) {
                EntityAvatar(
                    name = message.name,
                    src = "$serverUrl/chat/$chatId/-/${message.id}/asset/avatar",
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
                    .then(
                        if (hasMenu) {
                            Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = { menuExpanded = true },
                            )
                        } else {
                            Modifier
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
                                text = message.body,
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
                                urlBuilder = { att ->
                                    resolveAttachmentUrl(serverUrl, att.url ?: "/chat/$chatId/-/attachments/${att.id}")
                                },
                                thumbnailUrlBuilder = { att ->
                                    resolveAttachmentUrl(serverUrl, att.thumbnailUrl ?: "/chat/$chatId/-/attachments/${att.id}/thumbnail")
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
            if (hasMenu) {
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (canCopy) {
                        DropdownMenuItem(
                            text = { Text(stringResource(MochiR.string.common_copy)) },
                            onClick = {
                                clipboard.setText(AnnotatedString(message.body))
                                Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                                menuExpanded = false
                            },
                        )
                    }
                    if (canForward) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_message_forward)) },
                            onClick = {
                                onForward()
                                menuExpanded = false
                            },
                        )
                    }
                    if (canDelete) {
                        DropdownMenuItem(
                            text = { Text(stringResource(MochiR.string.common_delete)) },
                            onClick = {
                                onDelete()
                                menuExpanded = false
                            },
                        )
                    }
                }
            }
        }
        if (!message.deleted) {
            Spacer(modifier = Modifier.height(2.dp))
            ReactionBar(
                reactions = chatReactionCounts(message.reactionCounts, message.myReaction),
                onReact = onReact,
                onRemoveReaction = { onReact("none") },
            )
        }
    }
}

/**
 * Adapt the chat server's reaction shape — a `{reaction: count}` map plus the
 * viewer's own reaction — into the lib [ReactionBar]'s `List<ReactionCount>`.
 * Unknown reaction keys are dropped; pills are ordered by the canonical
 * [ReactionType] order for stability.
 */
private fun chatReactionCounts(counts: Map<String, Int>, myReaction: String?): List<ReactionCount> =
    counts.mapNotNull { (key, count) ->
        ReactionType.fromString(key)?.let { type ->
            ReactionCount(type = type, count = count, isMine = key.equals(myReaction, ignoreCase = true))
        }
    }.sortedBy { it.type.ordinal }

private sealed class MessageListEntry {
    data class DateHeader(val dayKey: String, val epochSeconds: Long) : MessageListEntry()
    data class MessageItem(val message: ChatMessage) : MessageListEntry()
}

/**
 * Forward-to-chat bottom sheet: a filterable list of the user's other active
 * chats; tapping one forwards the open message there. (Forward-to-friend is a
 * newer web feature not yet on web main, so it's deferred here for parity.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatForwardSheet(
    chats: List<org.mochios.chat.model.Chat>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (org.mochios.chat.model.Chat) -> Unit,
) {
    var filter by remember { mutableStateOf("") }
    val filtered = remember(chats, filter) {
        if (filter.isBlank()) chats
        else chats.filter { it.name.contains(filter.trim(), ignoreCase = true) }
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
                filtered.isEmpty() -> Text(
                    text = stringResource(R.string.chat_forward_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                else -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(filtered, key = { it.id }) { chat ->
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
            }
        }
    }
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
