package org.mochios.chat.ui.chat

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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import org.mochios.android.model.resolveAttachmentUrl
import org.mochios.android.ui.components.AttachmentGallery
import org.mochios.chat.R
import org.mochios.chat.model.ChatMessage
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
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.chat.name.ifBlank { stringResource(R.string.chat_messages_loading) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                    if (uiState.chat.id.isNotEmpty()) {
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
                        items(uiState.messages, key = { it.id }) { message ->
                            MessageBubble(
                                message = message,
                                isOwn = message.member == uiState.identity,
                                serverUrl = viewModel.serverUrl,
                                chatId = uiState.chat.id
                            )
                        }
                    }
                }
            }

            ComposerBar(
                value = draft,
                onValueChange = { draft = it },
                isSending = uiState.isSending,
                enabled = uiState.chat.id.isNotEmpty() && uiState.chat.left == 0,
                pendingAttachments = uiState.pendingAttachments,
                onAddAttachments = { viewModel.addAttachments(it) },
                onRemoveAttachment = { viewModel.removeAttachment(it) },
                onMoveAttachment = { uri, dir -> viewModel.moveAttachment(uri, dir) },
                onSend = {
                    viewModel.sendMessage(draft)
                    draft = ""
                }
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isOwn: Boolean,
    serverUrl: String,
    chatId: String
) {
    val format = LocalFormat.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isOwn) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!isOwn) {
                    Text(
                        text = message.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
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
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = format.formatTimestamp(message.created),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
