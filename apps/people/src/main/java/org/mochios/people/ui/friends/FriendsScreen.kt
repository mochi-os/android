// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.friends

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.people.R
import org.mochios.people.model.Friend
import org.mochios.people.ui.components.PeopleEmptyState
import org.mochios.people.ui.components.PeopleSidebar
import org.mochios.people.ui.components.PeopleSidebarSection
import org.mochios.android.R as MochiR

/**
 * Friends list — the People app's main entry point. Mirrors the web
 * `apps/people/web/src/features/friends/index.tsx`:
 *
 *  - Searchable + sorted list of confirmed friends.
 *  - Per-row Message / Remove actions.
 *  - Empty state when filter / list is empty.
 *  - Loading spinner before the first response lands.
 *  - Top-bar hamburger opens the shared [PeopleSidebar] drawer for switching
 *    sections (Friends / Invitations / Groups / Profile) — matches the web
 *    sidebar.
 *  - Top-bar search icon + ellipsis menu (logout).
 *  - FAB to open [AddFriendDialog].
 *  - Pull-to-refresh.
 *  - One-shot welcome banner on first visit (dismissable, persists server-side).
 *
 *  Deep-link: when [initialAction] == "add" we open the AddFriendDialog on
 *  first composition. That lets `mochi://people?action=add` (or a notification
 *  PendingIntent) drop the user directly into the invite flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onOpenPerson: (id: String) -> Unit,
    onSwitchSection: (PeopleSidebarSection) -> Unit,
    onOpenNotifications: () -> Unit,
    onLogout: () -> Unit,
    onOpenLink: (String) -> Unit = {},
    initialAction: String? = null,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showOverflow by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()

    // Deep-link entry: `?action=add` opens the dialog once on first compose.
    LaunchedEffect(initialAction) {
        if (initialAction == "add" && !uiState.addDialogOpen) {
            viewModel.openAddDialog()
        }
    }

    // Side-effect events from the ViewModel — toast strings and the chat
    // deep-link. The link is routed through onOpenLink (→ MainActivity's
    // navigateToLink), the same path the person-view "Message" button uses;
    // a raw `mochi://chat/...` Intent isn't handled by the app's router.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is FriendsEvent.Toast -> snackbarHostState.showSnackbar(event.message)
                is FriendsEvent.MessageFriend -> onOpenLink("chat/new?friend=${event.friendId}")
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            PeopleSidebar(
                current = PeopleSidebarSection.FRIENDS,
                onSelect = { section ->
                    drawerScope.launch { drawerState.close() }
                    if (section != PeopleSidebarSection.FRIENDS) onSwitchSection(section)
                },
            )
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.people_friends_title)) },
                    navigationIcon = {
                        IconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = stringResource(R.string.people_open_sidebar),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenNotifications) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = stringResource(MochiR.string.common_notifications),
                            )
                        }
                        Box {
                            IconButton(onClick = { showOverflow = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.people_friends_more),
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(MochiR.string.common_logout)) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                                    onClick = {
                                        showOverflow = false
                                        onLogout()
                                    },
                                )
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { viewModel.openAddDialog() }) {
                    Icon(
                        Icons.Default.PersonAdd,
                        contentDescription = stringResource(R.string.people_add_friend),
                    )
                }
            },
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Persistent search bar matching web (web shows it
                    // always in the header; we keep it inline below the
                    // top bar). Removed the icon toggle.
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        placeholder = { Text(stringResource(R.string.people_friends_search_placeholder)) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )

                    // Sort toggle (name vs recently added), matching web. Only
                    // shown once there are friends to order.
                    if (uiState.friends.isNotEmpty()) {
                        var sortMenuOpen by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Box {
                                TextButton(onClick = { sortMenuOpen = true }) {
                                    Icon(
                                        Icons.Default.Sort,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        when (uiState.sortBy) {
                                            FriendSortBy.RECENT ->
                                                stringResource(R.string.people_friends_sort_recent)
                                            FriendSortBy.NAME ->
                                                stringResource(R.string.people_friends_sort_name)
                                        }
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = sortMenuOpen,
                                    onDismissRequest = { sortMenuOpen = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.people_friends_sort_name)) },
                                        onClick = {
                                            viewModel.setSortBy(FriendSortBy.NAME)
                                            sortMenuOpen = false
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.people_friends_sort_recent)) },
                                        onClick = {
                                            viewModel.setSortBy(FriendSortBy.RECENT)
                                            sortMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.showWelcome) {
                        WelcomeBanner(onDismiss = { viewModel.dismissWelcome() })
                    }

                    FriendsContent(
                        state = uiState,
                        filteredFriends = viewModel.filteredFriends(),
                        onPersonTap = { onOpenPerson(it.id) },
                        onMessageFriend = { viewModel.messageFriend(it) },
                        onRemoveFriend = { viewModel.requestRemoveFriend(it) },
                        onRetryLoad = { viewModel.loadFriends() },
                    )
                }
            }
        }
    }

    if (uiState.addDialogOpen) {
        AddFriendDialog(
            state = uiState,
            onQueryChange = viewModel::updateAddSearchQuery,
            onRetry = viewModel::retryAddSearch,
            onOpenPreview = viewModel::openAddPreview,
            onClosePreview = viewModel::closeAddPreview,
            onRetryPreview = viewModel::retryAddPreview,
            onAddFriend = viewModel::addFriend,
            onDismiss = viewModel::closeAddDialog,
        )
    }

    val removing = uiState.removingFriend
    if (removing != null) {
        RemoveFriendConfirmDialog(
            friendName = removing.name,
            onConfirm = { viewModel.confirmRemoveFriend() },
            onDismiss = { viewModel.cancelRemoveFriend() },
        )
    }
}

@Composable
private fun WelcomeBanner(onDismiss: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.people_welcome_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.people_welcome_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.people_welcome_dismiss),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun FriendsContent(
    state: FriendsUiState,
    filteredFriends: List<Friend>,
    onPersonTap: (Friend) -> Unit,
    onMessageFriend: (Friend) -> Unit,
    onRemoveFriend: (Friend) -> Unit,
    onRetryLoad: () -> Unit,
) {
    when {
        state.isLoading && state.friends.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        state.error != null && state.friends.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.error.userMessage(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onRetryLoad) {
                        Text(stringResource(MochiR.string.common_retry))
                    }
                }
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (filteredFriends.isEmpty()) {
                    item(key = "__empty__") {
                        EmptyFriendsHint(searchQuery = state.searchQuery)
                    }
                } else {
                    items(filteredFriends, key = { it.id }) { friend ->
                        FriendRow(
                            friend = friend,
                            onTap = { onPersonTap(friend) },
                            onMessage = { onMessageFriend(friend) },
                            onRemove = { onRemoveFriend(friend) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFriendsHint(searchQuery: String) {
    PeopleEmptyState(
        icon = Icons.Default.Group,
        title = stringResource(R.string.people_friends_empty),
        subtitle = if (searchQuery.isNotBlank()) {
            stringResource(R.string.people_friends_try_adjusting)
        } else {
            stringResource(R.string.people_friends_add_to_start)
        },
        modifier = Modifier.padding(top = 64.dp),
    )
}

@Composable
private fun FriendRow(
    friend: Friend,
    onTap: () -> Unit,
    onMessage: () -> Unit,
    onRemove: () -> Unit,
) {
    val avatarUrl = "/people/${friend.id}/-/avatar"

    Card(
        onClick = onTap,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EntityAvatar(
                name = friend.name,
                src = avatarUrl,
                seed = friend.id,
                size = 40.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = friend.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onMessage) {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = stringResource(R.string.people_friends_message),
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.PersonRemove,
                    contentDescription = stringResource(R.string.people_friends_remove),
                )
            }
        }
    }
}
