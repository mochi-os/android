// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.chat.ui.chat

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.OwnedAddress
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatTimestamp
import org.mochios.android.launcher.AddToHomeItem
import org.mochios.android.model.Attachment
import org.mochios.android.model.ReactionCount
import org.mochios.android.model.ReactionType
import org.mochios.android.push.VisibleEntityEffect
import org.mochios.android.ui.components.DrawerMenuEntry
import org.mochios.android.ui.components.addressByAuthor
import org.mochios.android.ui.components.AttachmentGallery
import org.mochios.android.ui.components.ComposeBar
import org.mochios.android.ui.components.ComposeBarAttachments
import org.mochios.android.ui.components.ComposeBarDefaults
import org.mochios.android.ui.components.DrawerActionRow
import org.mochios.android.ui.components.DrawerItem
import org.mochios.android.ui.components.DrawerPlaceholderScreen
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.WhileStarted
import org.mochios.android.ui.components.EntityIconCircle
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiBottomSheet
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiListDrawer
import org.mochios.android.ui.components.MochiOutlinedButton
import org.mochios.android.ui.components.MochiSearchTopBar
import org.mochios.android.ui.components.MochiSheetHeader
import org.mochios.android.ui.components.MochiSheetPadding
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.android.ui.components.NotFoundState
import org.mochios.android.ui.components.NotificationBell
import org.mochios.android.ui.components.ReactionBar
import org.mochios.android.ui.components.Reading
import org.mochios.android.ui.components.ReplyComposerBanner
import org.mochios.android.ui.components.collectPartAsState
import org.mochios.android.ui.components.rememberCopier
import org.mochios.android.ui.components.rememberReplyFocus
import org.mochios.android.util.attachmentAddress
import org.mochios.android.util.segment
import org.mochios.chat.R
import org.mochios.chat.model.ChatDetail
import org.mochios.chat.model.ChatMessage
import org.mochios.chat.model.ChatStatus
import org.mochios.chat.model.ReceiptState
import org.mochios.chat.model.active
import org.mochios.chat.model.heading
import org.mochios.chat.model.personAvatarUrl
import org.mochios.chat.model.presence
import org.mochios.chat.model.receiptState
import org.mochios.chat.model.receipts
import org.mochios.chat.model.relayed
import org.mochios.chat.model.seenBy
import org.mochios.chat.model.threads
import org.mochios.chat.ui.chatlist.ChatListEvent
import org.mochios.chat.ui.chatlist.ChatListHeader
import org.mochios.chat.ui.chatlist.ChatListNoMatches
import org.mochios.chat.ui.chatlist.ChatListShown
import org.mochios.chat.ui.chatlist.ChatListViewModel
import org.mochios.chat.ui.chatlist.shown
import org.mochios.chat.ui.chatlist.unreadBadge
import org.mochios.chat.ui.chatlist.unreadDot
import org.mochios.chat.ui.encryption.VerificationWatcher
import org.mochios.chat.ui.privacy.PrivacyDialog
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
    onOpenNotifications: () -> Unit,
    listViewModel: ChatListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val drawerState = rememberDrawerState(
        if (chatId.isEmpty()) DrawerValue.Open else DrawerValue.Closed
    )
    val drawerScope = rememberCoroutineScope()
    val listUiState by listViewModel.uiState.collectAsState()
    var showPolicy by remember { mutableStateOf(false) }

    // The list as it is now whenever the drawer opens, and on a return from
    // settings, a new chat or the background: unread counts, order, a chat
    // just started with the user, a name changed.
    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.isOpen }
            .distinctUntilChanged()
            .drop(1)
            .filter { open -> open }
            .collect { listViewModel.onDrawerOpened() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { listViewModel.onScreenResumed() }
    val listShown = listUiState.shown()
    val pinnedChats by listViewModel.pinned.collectAsState()
    val markedChats by listViewModel.marked.collectAsState()
    val invitation = stringResource(R.string.chat_invitation)
    val pin = stringResource(R.string.chat_pin)
    val unpin = stringResource(R.string.chat_unpin)
    val markRead = stringResource(R.string.chat_mark_read)
    val markUnread = stringResource(R.string.chat_mark_unread)
    val unpinIcon = ImageVector.vectorResource(R.drawable.ic_push_pin_off)
    LaunchedEffect(Unit) {
        listViewModel.events.collect { event ->
            val message = when (event) {
                is ChatListEvent.Done -> resources.getString(event.message)
                is ChatListEvent.Failure -> event.error.userMessage()
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    val drawerItems = remember(listUiState, pinnedChats, markedChats, invitation, chatId) {
        listViewModel.filteredChats(open = chatId).map { chat ->
            // A conversation on another network: its people have no Mochi
            // avatar, so it shows the network's globe.
            val isDirect = chat.members == 2 && chat.other.isNotBlank() && chat.account.isEmpty()
            DrawerItem(
                id = chat.id,
                title = chat.name,
                subtitle = if (chat.status == ChatStatus.INVITED) invitation else null,
                unread = unreadBadge(chat, chatId),
                icon = when {
                    chat.account.isNotEmpty() -> Icons.Default.Public
                    chat.members > 2 -> Icons.Default.Groups
                    else -> Icons.Default.ChatBubbleOutline
                },
                trailingIcon = if (chat.id in pinnedChats) Icons.Outlined.PushPin else null,
                avatarUrl = if (isDirect) personAvatarUrl(chat.other) else null,
                seed = chat.id,
                dot = unreadDot(chat, chatId, markedChats),
                // The web list's menu: pin, and read or unread for an active chat.
                menu = buildList {
                    if (chat.id in pinnedChats) {
                        add(DrawerMenuEntry(unpin, unpinIcon) { listViewModel.togglePin(chat.id) })
                    } else {
                        add(DrawerMenuEntry(pin, Icons.Outlined.PushPin) { listViewModel.togglePin(chat.id) })
                    }
                    if (chat.status == ChatStatus.ACTIVE) {
                        if (chat.unread > 0 || chat.id in markedChats) {
                            add(DrawerMenuEntry(markRead, Icons.Outlined.DoneAll) { listViewModel.markRead(chat.id) })
                        } else {
                            add(DrawerMenuEntry(markUnread, Icons.Outlined.MarkEmailUnread) { listViewModel.markUnread(chat.id) })
                        }
                    }
                },
            )
        }
    }

    MochiListDrawer(
        drawerState = drawerState,
        header = {
            ChatListHeader(
                query = listUiState.query,
                filter = listUiState.filter,
                onQueryChange = listViewModel::setQuery,
                onFilterChange = listViewModel::setFilter,
            )
        },
        items = drawerItems,
        emptyState = {
            when (listShown) {
                ChatListShown.LISTED -> ChatListNoMatches(query = listUiState.query, filter = listUiState.filter)
                ChatListShown.LOADING -> Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                ChatListShown.FAILED -> ChatListFailed(
                    error = listUiState.error ?: MochiError.Unknown(),
                    onRetry = listViewModel::load,
                )
                ChatListShown.EMPTY -> Text(
                    text = stringResource(R.string.chat_list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        },
        selectedId = chatId,
        onItemClick = { item ->
            drawerScope.launch { drawerState.close() }
            if (item.id != chatId) onSelectChat(item.id)
        },
        actions = {
            DrawerActionRow(
                title = stringResource(R.string.chat_list_new),
                icon = Icons.Outlined.Add,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    onNewChat()
                },
            )
            DrawerActionRow(
                title = stringResource(R.string.chat_privacy_title),
                icon = Icons.Outlined.Shield,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    showPolicy = true
                },
            )
        },
    ) {
        if (chatId.isEmpty()) {
            // Worded from the list's own state: a list that failed to load is
            // not one with no chats in it.
            DrawerPlaceholderScreen(
                title = stringResource(R.string.chat_list_title),
                message = when (listShown) {
                    ChatListShown.LISTED -> stringResource(R.string.chat_list_select)
                    ChatListShown.EMPTY -> stringResource(R.string.chat_list_empty)
                    ChatListShown.FAILED -> listUiState.error?.userMessage().orEmpty()
                    ChatListShown.LOADING -> ""
                },
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

    if (showPolicy) {
        PrivacyDialog(onDismiss = { showPolicy = false })
    }

    // Matrix's emoji verifications, followed while the user has a Matrix conversation.
    VerificationWatcher(enabled = listUiState.chats.any { it.network == "matrix" })
}

/** The drawer's list could not be read: why, in the server's words, and a way to try again. */
@Composable
private fun ChatListFailed(error: MochiError, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = error.userMessage(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        MochiOutlinedButton(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(MochiR.string.common_retry))
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
    val uiState by viewModel.uiState.collectPartAsState { state -> state.withoutDrafts() }
    // The composer's text: the edit's while one is under way, else the draft.
    val draft = viewModel.uiState.collectPartAsState { state -> state.composed() }
    val threadDraft = viewModel.uiState.collectPartAsState { state -> state.threadComposed() }
    val upload by viewModel.upload.collectAsState()

    // A one-to-one conversation asks whether the other person is online while
    // it shows, and not behind the home screen: each ask reaches their server.
    val asksPresence = uiState.chat.presence()
    WhileStarted(uiState.chat.id, asksPresence) {
        if (asksPresence) viewModel.watchPresence()
    }

    // Clears this chat's tray rows on every return, and keeps a push about it
    // from posting at all while the screen is up - the server's mark-read does
    // not reach the status bar. A conversation you are looking at is read by
    // looking at it, so the row retires on the server too. Only while the
    // socket is up, and the ViewModel subscribes with the chat record's key
    // rather than the route's id.
    VisibleEntityEffect(
        "chat",
        chatId,
        socketKey = uiState.chat.key,
        marksRead = true,
    )
    // After that registration, on each return: what came while the screen was
    // behind another, or before it was registered, is read now it shows.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, uiState.chat.key) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.shown()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val listState = rememberLazyListState()
    val context = LocalContext.current
    val copier = rememberCopier()
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

    // Matches come from the server search (newest-first). The active match is
    // scrolled to and highlighted; the counter/navigation reflect the full set.
    val searchMatches = uiState.searchMatches
    val searchMatchIndex = uiState.searchMatchIndex
        .coerceIn(0, (searchMatches.size - 1).coerceAtLeast(0))
    val activeMatch = searchMatches.getOrNull(searchMatchIndex)
    val activeMatchId = activeMatch?.id

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // A failure over a loaded conversation: the page error would replace
    // nothing on screen, so it is shown once as a snackbar instead.
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    LaunchedEffect(uiState.notice) {
        val notice = uiState.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(notice.userMessage())
        viewModel.clearNotice()
    }

    // Whether the reader is at the newest message: set as each scroll ends,
    // so a message arriving, which scrolls nothing, finds it as it was.
    var following by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) following = !listState.canScrollForward
        }
    }
    // Read only by a reader at the newest message; one up the list has it
    // held until they come down.
    LaunchedEffect(following) { viewModel.atBottom(following) }
    // Messages that came while the reader was up the list, for the jump button.
    var newer by remember { mutableIntStateOf(0) }
    LaunchedEffect(following) { if (following) newer = 0 }
    val jumpScope = rememberCoroutineScope()

    // The messages the list last showed, so a change can be told apart: a
    // newer message follows to the bottom for a reader there, or one of the
    // user's own; paged-in history and deletions leave the reader where they are.
    var shown by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    LaunchedEffect(uiState.messages) {
        val appended = followsNewest(shown, uiState.messages)
        val previous = shown.lastOrNull()
        shown = uiState.messages
        if (!appended) return@LaunchedEffect
        val own = uiState.messages.lastOrNull()?.member == uiState.identity
        if (followsReader(following, own, first = previous == null, searching = uiState.searchOpen)) {
            // The list holds the load-older row plus every date header, not just
            // the messages, so messages.size - 1 lands short by headers + 1 and
            // the newest message stays off screen — which is what opening a chat
            // does. lastLazyIndex counts the same items the list emits.
            listState.animateScrollToItem(lastLazyIndex(grouped, uiState.more))
        } else {
            newer += arrivals(previous, uiState.messages)
        }
    }

    // The active match on screen: a reply in a thread opens its thread, a
    // message of the main timeline not yet loaded is looked for page by page.
    LaunchedEffect(activeMatch, uiState.messages) {
        val match = activeMatch ?: return@LaunchedEffect
        val idx = if (match.thread.isEmpty()) messageLazyIndex(grouped, uiState.more, match.id) else -1
        if (idx >= 0) listState.animateScrollToItem(idx) else viewModel.seekMatch()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            val members = uiState.chat.members
            val isGroup = members.size > 2
            val remote = uiState.chat.account.isNotEmpty()
            val peer = if (members.size == 2 && !remote) members.firstOrNull { it.id != uiState.identity } else null
            val peerAvatarUrl = peer?.let { personAvatarUrl(it.id) }
            val youLabel = stringResource(R.string.chat_members_you)
            val membersSubtitle = remember(members, uiState.identity, youLabel) {
                members.heading(uiState.identity, youLabel)
            }
            val standing = standingLine(uiState.chat, uiState.presence)
            val subtitle = membersSubtitle.ifBlank { standing.orEmpty() }
            if (uiState.searchOpen) {
                val matchPosition = if (searchMatches.isEmpty()) 0 else searchMatchIndex + 1
                val matchCount = searchMatches.size
                MochiSearchTopBar(
                    query = uiState.searchQuery,
                    placeholder = stringResource(R.string.chat_search_hint),
                    onQueryChange = { text -> viewModel.setSearchQuery(text) },
                    onClose = { viewModel.closeSearch() },
                ) {
                    // Stepping through hits is chat's alone — the other searches
                    // filter a list rather than walk one.
                    if (uiState.searchQuery.isNotBlank()) {
                        Text(
                            text = "$matchPosition/$matchCount",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MochiIconButton(
                            onClick = { viewModel.setSearchMatchIndex(searchMatchIndex + 1) },
                            enabled = matchPosition < matchCount,
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.chat_search_prev),
                            )
                        }
                        MochiIconButton(
                            onClick = { viewModel.setSearchMatchIndex(searchMatchIndex - 1) },
                            enabled = matchPosition > 1,
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.chat_search_next),
                            )
                        }
                    }
                }
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (peer != null) {
                                EntityAvatar(
                                    name = peer.name.ifBlank { uiState.chat.name },
                                    src = peerAvatarUrl,
                                    seed = peer.id,
                                    size = 32.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            } else if (remote) {
                                EntityIconCircle(
                                    seed = uiState.chat.id,
                                    icon = Icons.Default.Public,
                                    size = 32.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                            } else if (isGroup) {
                                EntityIconCircle(
                                    seed = uiState.chat.id,
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
                                if (subtitle.isNotBlank()) {
                                    Text(
                                        text = subtitle,
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
                                if (uiState.chat.status == ChatStatus.INVITED) {
                                    MochiDropdownMenuItem(
                                        text = { Text(stringResource(R.string.chat_invitation_decline)) },
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.decline()
                                        },
                                        leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                                    )
                                }
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
                                        onSettings(chatId)
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                                )
                                if (pinnable(uiState.chat)) {
                                    AddToHomeItem("chat", chatId, uiState.chat.name) { menuExpanded = false }
                                }
                                if (uiState.chat.status == ChatStatus.ACTIVE) {
                                    MochiDropdownMenuItem(
                                        text = { Text(stringResource(R.string.chat_settings_leave)) },
                                        onClick = {
                                            menuExpanded = false
                                            showLeaveDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
                                    )
                                } else if (uiState.chat.status != ChatStatus.INVITED) {
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
                            copier.copy(text, label = "messages")
                        }
                        viewModel.exitSelection()
                    },
                    onForward = { viewModel.forwardSelected() },
                    onDelete = {
                        val selected = uiState.messages.filter { it.id in uiState.selectedIds }
                        if (selected.any { it.member != uiState.identity }) {
                            snackbarScope.launch {
                                snackbarHostState.showSnackbar(deleteOwnOnlyMessage)
                            }
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
                    val showsReceipts = uiState.chat.receipts()
                    val opensThreads = uiState.chat.threads()
                    val avatars = remember(uiState.messages, uiState.chat.id) {
                        messageAvatars(uiState.messages, uiState.chat.id)
                    }
                    val byId = remember(uiState.messages) {
                        uiState.messages.associateBy { message -> message.id }
                    }
                    val me = uiState.identity
                    val receipts = remember(uiState.messages, uiState.receipts, me, showsReceipts) {
                        if (!showsReceipts) {
                            emptyMap()
                        } else {
                            uiState.messages
                                .filter { message -> message.member == me && !message.deleted }
                                .associate { message ->
                                    val state = receiptState(message.created, uiState.receipts, me)
                                    message.id to state
                                }
                        }
                    }
                    val seen = remember(uiState.messages, uiState.receipts, uiState.identity, isGroupChat(uiState.chat)) {
                        if (isGroupChat(uiState.chat)) seenBy(uiState.messages, uiState.receipts, uiState.identity) else null
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (uiState.more) {
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
                                is MessageListEntry.EarlierHistory -> {
                                    item(key = entry.message.id) {
                                        EarlierHistoryLine()
                                    }
                                }
                                is MessageListEntry.MessageItem -> {
                                    item(key = entry.message.id) {
                                        val own = entry.message.member == uiState.identity
                                        MessageBubble(
                                            message = entry.message,
                                            isOwn = own,
                                            receipt = receipts[entry.message.id],
                                            seen = seen?.takeIf { it.id == entry.message.id }?.names,
                                            // A deleted message keeps its thread: the
                                            // replies under it are still there to read.
                                            onThread = if (opensThreads && (!entry.message.deleted || entry.message.replies > 0)) {
                                                { viewModel.openThread(entry.message) }
                                            } else {
                                                null
                                            },
                                            isGroup = uiState.chat.members.size > 2,
                                            chatId = uiState.chat.id,
                                            avatar = avatars[entry.message.member],
                                            selectionMode = uiState.selectionMode,
                                            isSelected = entry.message.id in uiState.selectedIds,
                                            isSearchMatch = entry.message.id == activeMatchId,
                                            searchQuery = if (uiState.searchOpen) uiState.searchQuery else "",
                                            replyToMessage = entry.message.reply
                                                ?.let { rid -> byId[rid] },
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
                    // Up the list, the reader is not moved by what arrives: a
                    // button takes them down to it, saying how much came.
                    if (!following && !uiState.searchOpen) {
                        JumpButton(
                            newer = newer,
                            onClick = {
                                jumpScope.launch { listState.animateScrollToItem(lastLazyIndex(grouped, uiState.more)) }
                            },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                        )
                    }
                    }
                }
            }

            // Messages whose send failed while the composer held the next one.
            uiState.unsent.forEach { message ->
                key(message.key) {
                    UnsentRow(
                        message = message,
                        onRetry = { viewModel.retry(message.key) },
                        onUnencrypted = { viewModel.retry(message.key, unencrypted = true) },
                        onCopy = { copier.copy(message.text, label = "message") },
                        onDiscard = { viewModel.discard(message.key) },
                        retryEnabled = !uiState.isSending,
                    )
                }
            }

            if (uiState.chat.status == ChatStatus.INVITED) {
                InvitationBar(
                    answering = uiState.answering,
                    onAccept = viewModel::accept,
                    onDecline = viewModel::decline,
                )
            } else {
            if (uiState.chat.active() && uiState.chat.relayed()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.chat_relayed_unencrypted),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (uiState.chat.active() && uiState.typing.isNotEmpty()) {
                TypingLine(uiState.typing)
            }
            // An edit of a message of the main timeline has the composer; one
            // of a reply in a thread has the thread's.
            val editing = uiState.editing?.takeIf { message -> message.thread.isEmpty() }
            Reading(draft) { text ->
                ComposeBar(
                    value = text,
                    onValueChange = if (editing != null) viewModel::setEditDraft else viewModel::setDraft,
                    onSend = {
                        if (editing != null) {
                            viewModel.saveEdit()
                        } else {
                            viewModel.sendMessage()
                        }
                    },
                    placeholder = stringResource(R.string.chat_message_placeholder),
                    enabled = uiState.chat.id.isNotEmpty() &&
                        uiState.chat.status == ChatStatus.ACTIVE,
                    isSending = uiState.isSending,
                    upload = upload,
                    sendLabel = if (editing != null) {
                        stringResource(MochiR.string.common_save)
                    } else {
                        stringResource(R.string.chat_message_send)
                    },
                    windowInsets = ComposeBarDefaults.WindowInsets,
                    // The files picked wait, with the draft, while an edit has the composer.
                    attachments = if (editing != null) null else ComposeBarAttachments(
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
                    banner = {
                        Column {
                            if (editing != null) {
                                EditComposerPreview(onCancel = { viewModel.cancelEdit() })
                            } else {
                                uiState.replyingTo?.let { replied ->
                                    ReplyComposerBanner(
                                        label = stringResource(R.string.chat_replying_to, replied.name),
                                        preview = replied.body.ifBlank {
                                            stringResource(R.string.chat_reply_attachment)
                                        },
                                        cancelLabel = stringResource(MochiR.string.common_cancel),
                                        onCancel = { viewModel.cancelReply() },
                                    )
                                }
                                uiState.refusal?.let { refusal ->
                                    RefusalLine(
                                        refusal = refusal,
                                        onUnencrypted = { viewModel.sendMessage(unencrypted = true) },
                                        enabled = !uiState.isSending,
                                    )
                                }
                            }
                            LengthLine(text)
                        }
                    },
                    focusRequester = rememberReplyFocus(uiState.replyingTo?.id),
                )
            }
            }

            uiState.thread?.let { thread ->
                val replyEdit = uiState.editing?.takeIf { message -> message.thread == thread.root.id }
                ThreadSheet(
                    thread = thread,
                    chatId = uiState.chat.id,
                    // No reply goes under a deleted message, though its thread stays to read.
                    canWrite = uiState.chat.active() && !thread.root.deleted,
                    onDraft = if (replyEdit != null) viewModel::setEditDraft else viewModel::setThreadDraft,
                    onSend = if (replyEdit != null) viewModel::saveEdit else ({ viewModel.sendThreadReply() }),
                    onEarlier = viewModel::loadEarlierReplies,
                    onDismiss = viewModel::closeThread,
                    draft = threadDraft,
                    replies = ReplyActions(
                        identity = uiState.identity,
                        canWrite = uiState.chat.active(),
                        editing = replyEdit != null,
                        match = activeMatchId.takeIf { activeMatch?.thread == thread.root.id },
                        query = if (uiState.searchOpen) uiState.searchQuery else "",
                        onEdit = viewModel::startEdit,
                        onCancelEdit = viewModel::cancelEdit,
                        onDelete = { reply -> pendingDelete = listOf(reply.id) },
                        onReact = viewModel::react,
                        onForward = { reply -> viewModel.openForward(reply.id) },
                        onRetry = { key -> viewModel.retryReply(key) },
                        onUnencrypted = { key -> viewModel.retryReply(key, unencrypted = true) },
                        onSendUnencrypted = { viewModel.sendThreadReply(unencrypted = true) },
                        onCopy = { text -> copier.copy(text, label = "message") },
                        onDiscard = viewModel::discardReply,
                    ),
                )
            }

            if (uiState.forwardMessageIds.isNotEmpty()) {
                ChatForwardSheet(
                    chats = uiState.forwardChats,
                    friends = uiState.forwardFriends,
                    loading = uiState.forwardLoading,
                    forwarding = uiState.forwarding,
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
                // Leaving keeps a read-only copy here unless the user also asks
                // for it to go, which is the server's `delete` option.
                var deleteLocally by remember { mutableStateOf(false) }
                MochiAlertDialog(
                    onDismissRequest = { showLeaveDialog = false },
                    title = stringResource(R.string.chat_settings_leave_title),
                    content = {
                        Column {
                            Text(stringResource(R.string.chat_settings_leave_message))
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { deleteLocally = !deleteLocally },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = deleteLocally, onCheckedChange = { deleteLocally = it })
                                Text(stringResource(R.string.chat_settings_leave_and_delete))
                            }
                        }
                    },
                    confirmText = stringResource(R.string.chat_settings_leave),
                    onConfirm = {
                        showLeaveDialog = false
                        viewModel.leaveChat(deleteLocally)
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

/**
 * [text] with each match of [query] marked. The text itself is searched,
 * ignoring case: a lowercased copy can be longer than it (a capital dotted İ
 * lowercases to two characters), and its positions then fall past the end.
 */
internal fun highlightQuery(text: String, query: String): AnnotatedString {
    val needle = query.trim()
    if (needle.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var start = 0
        while (true) {
            val hit = text.indexOf(needle, start, ignoreCase = true)
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
    more: Boolean,
    messageId: String,
): Int {
    var index = if (more) 1 else 0
    for (entry in grouped) {
        if (entry is MessageListEntry.MessageItem && entry.message.id == messageId) return index
        index++
    }
    return -1
}

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

/**
 * Index of the last emitted item: the load-older row plus every entry in
 * [grouped].
 */
internal fun lastLazyIndex(grouped: List<MessageListEntry>, more: Boolean): Int {
    val leading = if (more) 1 else 0
    return (leading + grouped.size - 1).coerceAtLeast(0)
}

/**
 * Whether a message added at the newest end takes the reader down to it: on
 * opening, for one of the user's own, or for a reader [following] at the
 * newest; never one up the list, whose place stays. Not while [searching]:
 * the search's match has the list then.
 */
internal fun followsReader(following: Boolean, own: Boolean, first: Boolean, searching: Boolean): Boolean =
    (following || own || first) && !searching

/**
 * How many messages came after [previous] in [messages], the list as it now
 * is: what the jump button counts while the reader is up the list.
 */
internal fun arrivals(previous: ChatMessage?, messages: List<ChatMessage>): Int {
    val at = messages.indexOfLast { message -> message.id == previous?.id }
    return if (at < 0) 0 else messages.size - 1 - at
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageBubble(
    message: ChatMessage,
    isOwn: Boolean,
    receipt: ReceiptState?,
    seen: List<String>?,
    onThread: (() -> Unit)?,
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
    // The sender's avatar as the list addresses it (messageAvatars); null for
    // the message's own.
    avatar: OwnedAddress? = null,
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
                    src = avatar ?: messageAvatar(chatId, message.id),
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
                    } else if (awaitingKeys(message)) {
                        // An encrypted message whose keys have not come shows
                        // no content until they do.
                        Text(
                            text = encryptedText(message),
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        if (message.body.isNotEmpty()) {
                            Text(
                                text = remember(message.body, searchQuery) {
                                    highlightQuery(message.body, searchQuery)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isOwn) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                        val attachments = messageAttachments(chatId, message.attachments)
                        if (attachments.isNotEmpty()) {
                            if (message.body.isNotEmpty()) Spacer(modifier = Modifier.height(6.dp))
                            AttachmentGallery(
                                attachments = attachments,
                                // The server's `url`/`thumbnail_url` point at the
                                // flat `/chat/attachments/<id>` route, which does
                                // not serve the asset here. Always build the chat
                                // asset route instead: `/chat/<chatId>/-/attachments/<id>`.
                                urlBuilder = { att ->
                                    attachmentAddress("chat", chatId, att.id)
                                },
                                thumbnailUrlBuilder = { att ->
                                    attachmentAddress("chat", chatId, att.id, "thumbnail")
                                },
                                previewUrlBuilder = { att ->
                                    // previewUrl's presence signals the server
                                    // generates previews; the path itself is
                                    // rebuilt on the chat asset route (see above).
                                    attachmentAddress("chat", chatId, att.id, if (att.previewUrl != null) "preview" else "thumbnail")
                                }
                            )
                        }
                        message.quote?.let { quote ->
                            Spacer(modifier = Modifier.height(6.dp))
                            MessageQuote(quote)
                        }
                        MessageWarning(message, isOwn)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                        if (receipt != null) {
                            Spacer(Modifier.width(4.dp))
                            ReceiptTick(receipt)
                        }
                    }
                    // The thread, in the message's own bubble below a rule: its
                    // newest replies, and how many came before them; under a
                    // deleted message too, whose replies are still there. An
                    // older server sends every reply, of which the newest are kept.
                    val answers = remember(message.answers) { message.answers.takeLast(THREAD_ANSWERS) }
                    if (onThread != null && answers.isNotEmpty() && !selectionMode) {
                        ThreadReplies(
                            answers = answers,
                            earlier = (message.replies - answers.size).coerceAtLeast(0),
                            onOpen = onThread,
                            onMenu = { if (hasMenu) menuExpanded = true },
                        )
                    }
                }
            }
            if (hasMenu && !selectionMode) {
                MessageMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    onSelect = onStartSelect,
                    onReply = onReply,
                    onThread = onThread,
                    onForward = onForward,
                    onEdit = onEdit.takeIf { canEdit },
                    onDelete = onDelete.takeIf { canDelete },
                )
            }
            if (!message.deleted && !selectionMode) {
                MessageReactions(message, onReact, Modifier.align(Alignment.BottomEnd).padding(end = 6.dp))
            }
        }
        if (!seen.isNullOrEmpty()) {
            Text(
                text = stringResource(R.string.chat_seen_by, LocalFormat.current.formatList(seen)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** How many of a thread's newest replies its root's bubble shows. */
internal const val THREAD_ANSWERS = 3

/**
 * A message's thread inside its own bubble: how many [earlier] replies came
 * before those shown, then each of [answers] below a rule, with who wrote it,
 * opening the thread. A long press opens the message's menu, as it does
 * anywhere else on the bubble. Each reply is keyed by its id, so a reply
 * arriving recomposes the bubble's new row rather than every row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadReplies(answers: List<ChatMessage>, earlier: Int, onOpen: () -> Unit, onMenu: () -> Unit) {
    Column(modifier = Modifier.combinedClickable(onClick = onOpen, onLongClick = onMenu)) {
        if (earlier > 0) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = pluralStringResource(
                    R.plurals.chat_thread_earlier_count,
                    earlier,
                    LocalFormat.current.formatNumber(earlier),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(role = Role.Button, onClick = onOpen),
            )
        }
        answers.forEach { answer ->
            key(answer.id) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = answer.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (awaitingKeys(answer)) {
                    Text(
                        text = encryptedText(answer),
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = answer.body.ifBlank { stringResource(R.string.chat_reply_attachment) },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/**
 * A message's actions, the same for a message of the main timeline and a
 * reply in a thread: each shown where the conversation allows it, which a
 * null leaves out.
 */
@Composable
internal fun MessageMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelect: (() -> Unit)?,
    onReply: (() -> Unit)?,
    onThread: (() -> Unit)?,
    onForward: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    MochiDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        onSelect?.let { select ->
            MochiDropdownMenuItem(
                text = { Text(stringResource(R.string.chat_message_select)) },
                onClick = {
                    onDismiss()
                    select()
                },
                leadingIcon = { Icon(Icons.Outlined.CheckBox, contentDescription = null) },
            )
        }
        onReply?.let { reply ->
            MochiDropdownMenuItem(
                text = { Text(stringResource(R.string.chat_message_reply)) },
                onClick = {
                    onDismiss()
                    reply()
                },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = null) },
            )
        }
        onThread?.let { thread ->
            MochiDropdownMenuItem(
                text = { Text(stringResource(R.string.chat_thread_reply)) },
                onClick = {
                    onDismiss()
                    thread()
                },
                leadingIcon = { Icon(Icons.Outlined.Forum, contentDescription = null) },
            )
        }
        MochiDropdownMenuItem(
            text = { Text(stringResource(R.string.chat_message_forward)) },
            onClick = {
                onDismiss()
                onForward()
            },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Forward, contentDescription = null) },
        )
        onEdit?.let { edit ->
            MochiDropdownMenuItem(
                text = { Text(stringResource(MochiR.string.common_edit)) },
                onClick = {
                    onDismiss()
                    edit()
                },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
            )
        }
        onDelete?.let { delete ->
            MochiDropdownMenuItem(
                text = {
                    Text(
                        stringResource(MochiR.string.common_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    onDismiss()
                    delete()
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

/** A message's reactions, with the button that adds one, changes it or clears it. */
@Composable
internal fun MessageReactions(message: ChatMessage, onReact: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        // Single bar with pills + built-in add button, so the
        // add/change/clear affordance is consistent with feeds.
        ReactionBar(
            reactions = chatReactionCounts(message.reactions, message.reaction),
            onReact = onReact,
            onRemoveReaction = { onReact("none") },
            currentReaction = message.reaction?.let { key -> ReactionType.fromString(key) },
            maxVisible = 3,
        )
    }
}

/**
 * Under a Matrix message read from a device the user distrusted, or one
 * whose keys do not match those it was sent with: that it was, as the web
 * says it.
 */
@Composable
internal fun MessageWarning(message: ChatMessage, isOwn: Boolean) {
    if (message.warning.isEmpty() || message.deleted) return
    val tint = if (isOwn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.GppMaybe, contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(
                if (message.warning == "distrusted") R.string.chat_message_distrusted else R.string.chat_message_mismatched,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

/**
 * Down to the newest message, for a reader up the list, with how many came
 * since they left it beside the arrow.
 */
@Composable
private fun JumpButton(newer: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val format = LocalFormat.current
    val label = if (newer > 0) {
        pluralStringResource(R.plurals.chat_jump_newer, newer, format.formatNumber(newer))
    } else {
        stringResource(R.string.chat_jump_bottom)
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        modifier = modifier.semantics { contentDescription = label },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.KeyboardDoubleArrowDown, contentDescription = null, modifier = Modifier.size(20.dp))
            if (newer > 0) {
                Spacer(Modifier.width(4.dp))
                Text(text = format.formatNumber(newer), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * A message whose send failed while the composer held the next one: its
 * text, why it failed, and Retry, Copy and Discard; and where it was refused
 * for want of encryption, Send unencrypted.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UnsentRow(
    message: Unsent,
    onRetry: () -> Unit,
    onUnencrypted: () -> Unit,
    onCopy: () -> Unit,
    onDiscard: () -> Unit,
    retryEnabled: Boolean = true,
) {
    val format = LocalFormat.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.End,
    ) {
        MochiCard(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (message.text.isNotEmpty()) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (message.attachments.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = format.formatNumber(message.attachments.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
                Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = message.error.userMessage(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        // Wrapping: four actions, translated, are wider than a phone.
        FlowRow(horizontalArrangement = Arrangement.End, itemVerticalAlignment = Alignment.CenterVertically) {
            MochiTextButton(onClick = onRetry, enabled = retryEnabled) {
                Text(stringResource(MochiR.string.common_retry))
            }
            if (message.refused) {
                MochiTextButton(onClick = onUnencrypted, enabled = retryEnabled) {
                    Text(stringResource(R.string.chat_message_unencrypted))
                }
            }
            if (message.text.isNotEmpty()) {
                MochiTextButton(onClick = onCopy) {
                    Text(stringResource(MochiR.string.common_copy))
                }
            }
            MochiTextButton(onClick = onDiscard) {
                Text(stringResource(R.string.chat_message_discard))
            }
        }
    }
}

/**
 * Why the composer's message came back into it, where the send refused it for
 * want of encryption, in the server's words, and Send unencrypted.
 */
@Composable
internal fun RefusalLine(refusal: MochiError, onUnencrypted: () -> Unit, enabled: Boolean = true) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = refusal.userMessage(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        MochiTextButton(onClick = onUnencrypted, enabled = enabled) {
            Text(stringResource(R.string.chat_message_unencrypted))
        }
    }
}

/**
 * A message's length as it nears the most the server takes, counted as the
 * server counts it, and by how much it is over once it is: the send is refused
 * then, before any file goes up.
 */
@Composable
internal fun LengthLine(text: String) {
    val count = length(text)
    if (count < MESSAGE_COUNTED) return
    val format = LocalFormat.current
    val over = count - MESSAGE_MAXIMUM
    Text(
        text = if (over > 0) {
            pluralStringResource(R.plurals.chat_message_too_long, over, format.formatNumber(over))
        } else {
            stringResource(R.string.chat_message_length, format.formatNumber(count), format.formatNumber(MESSAGE_MAXIMUM))
        },
        style = MaterialTheme.typography.labelSmall,
        color = if (over > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
    )
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
internal fun SelectionBar(
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
            Icon(Icons.Default.Close, contentDescription = stringResource(MochiR.string.common_close))
        }
        Text(
            text = stringResource(R.string.chat_selection_title, LocalFormat.current.formatNumber(count)),
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
            )
        }
    }
}

/**
 * Strip above the composer showing that a message is being edited, with a
 * button to cancel the edit.
 */
@Composable
internal fun EditComposerPreview(onCancel: () -> Unit) {
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
            Icon(Icons.Default.Close, contentDescription = stringResource(MochiR.string.common_cancel))
        }
    }
}

/**
 * Server `{reaction: count}` map to [ReactionBar] rows, most-reacted first.
 */
private fun chatReactionCounts(counts: Map<String, Int>, mine: String?): List<ReactionCount> =
    counts.mapNotNull { (key, count) ->
        ReactionType.fromString(key)?.let { type ->
            ReactionCount(type = type, count = count, isMine = key.equals(mine, ignoreCase = true))
        }
    }.sortedByDescending { reaction -> reaction.count }

internal sealed class MessageListEntry {
    data class DateHeader(val dayKey: String, val epochSeconds: Long) : MessageListEntry()
    data class MessageItem(val message: ChatMessage) : MessageListEntry()

    /** A run of history from before the account was added to Mochi, shown as one line where it starts. */
    data class EarlierHistory(val message: ChatMessage) : MessageListEntry()
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
    forwarding: String?,
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
    MochiBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            MochiSheetHeader(
                title = stringResource(R.string.chat_forward_title),
                subtitle = stringResource(R.string.chat_forward_subtitle),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(MochiSheetPadding)
            ) {
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
                    else -> LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        if (filtered.isNotEmpty()) {
                            // Only label the sections when both are present.
                            if (filteredFriends.isNotEmpty()) {
                                item("chats-header") {
                                    ForwardSectionHeader(stringResource(R.string.chat_forward_chats))
                                }
                            }
                            items(filtered, key = { "chat-" + it.id }) { chat ->
                                ForwardRow(chat.name, busy = forwarding == chat.id, enabled = forwarding == null) { onSelect(chat) }
                            }
                        }
                        if (filteredFriends.isNotEmpty()) {
                            if (filtered.isNotEmpty()) {
                                item("friends-header") {
                                    ForwardSectionHeader(stringResource(R.string.chat_forward_friends))
                                }
                            }
                            items(filteredFriends, key = { "friend-" + it.id }) { friend ->
                                ForwardRow(friend.name, busy = forwarding == friend.id, enabled = forwarding == null) { onSelectFriend(friend) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A destination in the forward sheet. Once one is tapped every row is off
 * until the forward is done, the tapped one showing it is under way, so the
 * messages go once.
 */
@Composable
internal fun ForwardRow(name: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled || busy) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
    }
    HorizontalDivider()
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
    var previous: ChatMessage? = null
    for (msg in messages) {
        // A run of history from before the account was added stands as its
        // first message alone.
        val earlier = isEarlierHistory(msg)
        val continued = earlier && previous?.let(::isEarlierHistory) == true
        previous = msg
        if (continued) continue
        val cal = java.util.Calendar.getInstance(tz).apply { timeInMillis = msg.created * 1000L }
        val key = "${cal.get(java.util.Calendar.YEAR)}-" +
            "${cal.get(java.util.Calendar.MONTH) + 1}-" +
            "${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
        if (key != lastKey) {
            out += MessageListEntry.DateHeader(key, msg.created)
            lastKey = key
        }
        out += if (earlier) MessageListEntry.EarlierHistory(msg) else MessageListEntry.MessageItem(msg)
    }
    return out
}

/** Where a run of history from before the account was added to Mochi would start. */
@Composable
private fun EarlierHistoryLine() {
    Text(
        text = stringResource(R.string.chat_message_earlier),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        fontStyle = FontStyle.Italic,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The state with the composers' text left out, so typing changes nothing else. */
private fun ChatUiState.withoutDrafts(): ChatUiState =
    copy(draft = "", editDraft = "", thread = thread?.copy(draft = ""))

/** The main composer's text: the edit's while a message of the main timeline is being edited. */
internal fun ChatUiState.composed(): String =
    if (editing != null && editing.thread.isEmpty()) editDraft else draft

/** The thread's box's text: the edit's while one of its replies is being edited. */
internal fun ChatUiState.threadComposed(): String =
    if (editing != null && editing.thread.isNotEmpty()) editDraft else thread?.draft.orEmpty()

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

/**
 * Whether [chat] can go on the home screen: one the user is still in, with a
 * name for its shortcut. A chat left is still listed, for its history, but
 * is no place to come back to.
 */
internal fun pinnable(chat: ChatDetail): Boolean = chat.status == ChatStatus.ACTIVE && chat.name.isNotEmpty()

/**
 * Where a sender's avatar is served: under one of their messages in the chat.
 * Null when an id cannot be one segment of the address ([segment]).
 */
internal fun messageAvatar(chatId: String, messageId: String): OwnedAddress? {
    val chat = segment(chatId) ?: return null
    val message = segment(messageId) ?: return null
    return OwnedAddress.of("/chat/$chat/-/$message/asset/avatar")
}

/**
 * The attachments of a message in [chatId] that have an address on the chat
 * asset route. Chat builds every address from the id and never takes the
 * server's, so an id no segment can carry ([attachmentAddress]) has no image
 * and is left out.
 */
internal fun messageAttachments(chatId: String, attachments: List<Attachment>): List<Attachment> =
    attachments.filter { attachmentAddress("chat", chatId, it.id) != null }

/**
 * One avatar address per sender across the loaded messages, from each one's
 * first: the route names a message, and any message of the chat answers with
 * its sender's picture, so a list that built each message's own address had
 * the image cache fetch and store the same picture once per message.
 */
internal fun messageAvatars(messages: List<ChatMessage>, chatId: String): Map<String, OwnedAddress> =
    addressByAuthor(messages, { it.member }, { messageAvatar(chatId, it.id) })
