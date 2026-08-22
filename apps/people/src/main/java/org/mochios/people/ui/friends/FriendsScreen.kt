// Copyright © 2026 Mochisoft OÜ
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
import androidx.compose.material.icons.automirrored.outlined.Logout
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.mochios.android.api.userMessage
import org.mochios.android.ui.components.AboutDialog
import org.mochios.android.ui.components.DrawerTitle
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiListDrawer
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.people.R
import org.mochios.people.model.Friend
import org.mochios.people.ui.components.PeopleSidebarSection
import org.mochios.people.ui.components.peopleDrawerItems
import org.mochios.people.ui.components.peopleDrawerSection
import org.mochios.android.R as MochiR

/**
 * Friends list, the People app's entry point. [initialAction] "add" (from
 * `mochi://people?action=add`) opens the add-friend screen on first
 * composition.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onOpenPerson: (id: String) -> Unit,
    onSwitchSection: (PeopleSidebarSection) -> Unit,
    onOpenNotifications: () -> Unit,
    onLogout: () -> Unit,
    onMessage: (String) -> Unit = {},
    onAddFriend: () -> Unit = {},
    initialAction: String? = null,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showOverflow by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()

    // Deep-link entry: `?action=add` opens the add-friend screen once. Saved
    // rather than remembered, so coming back from that screen — which composes
    // this one afresh with the same argument — doesn't bounce straight into it
    // again.
    var addOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialAction) {
        if (initialAction == "add" && !addOpened) {
            addOpened = true
            onAddFriend()
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
                is FriendsEvent.MessageFriend -> onMessage(event.friendId)
            }
        }
    }

    MochiListDrawer(
        drawerState = drawerState,
        header = { DrawerTitle(stringResource(R.string.people_sidebar_header)) },
        items = peopleDrawerItems(),
        selectedId = PeopleSidebarSection.FRIENDS.name,
        onItemClick = { item ->
            drawerScope.launch { drawerState.close() }
            val section = peopleDrawerSection(item.id)
            if (section != PeopleSidebarSection.FRIENDS) onSwitchSection(section)
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.people_friends_title)) },
                    navigationIcon = {
                        MochiIconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = stringResource(R.string.people_open_sidebar),
                            )
                        }
                    },
                    actions = {
                        MochiIconButton(onClick = onOpenNotifications) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = stringResource(MochiR.string.common_notifications),
                            )
                        }
                        Box {
                            MochiIconButton(onClick = { showOverflow = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.people_friends_more),
                                )
                            }
                            MochiDropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false },
                            ) {
                                MochiDropdownMenuItem(
                                    text = { Text(stringResource(MochiR.string.common_logout)) },
                                    onClick = {
                                        showOverflow = false
                                        onLogout()
                                    },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
                                )
                                MochiDropdownMenuItem(
                                    text = { Text(stringResource(MochiR.string.about_label)) },
                                    onClick = {
                                        showOverflow = false
                                        showAbout = true
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                                )
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onAddFriend) {
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
                    MochiTextField(
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
                                MochiTextButton(onClick = { sortMenuOpen = true }) {
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
                                MochiDropdownMenu(
                                    expanded = sortMenuOpen,
                                    onDismissRequest = { sortMenuOpen = false },
                                ) {
                                    MochiDropdownMenuItem(
                                        text = { Text(stringResource(R.string.people_friends_sort_name)) },
                                        onClick = {
                                            viewModel.setSortBy(FriendSortBy.NAME)
                                            sortMenuOpen = false
                                        },
                                        selected = uiState.sortBy == FriendSortBy.NAME,
                                    )
                                    MochiDropdownMenuItem(
                                        text = { Text(stringResource(R.string.people_friends_sort_recent)) },
                                        onClick = {
                                            viewModel.setSortBy(FriendSortBy.RECENT)
                                            sortMenuOpen = false
                                        },
                                        selected = uiState.sortBy == FriendSortBy.RECENT,
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

    val removing = uiState.removingFriend
    if (removing != null) {
        RemoveFriendConfirmDialog(
            friendName = removing.name,
            onConfirm = { viewModel.confirmRemoveFriend() },
            onDismiss = { viewModel.cancelRemoveFriend() },
        )
    }
    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

@Composable
private fun WelcomeBanner(onDismiss: () -> Unit) {
    MochiCard(
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
            MochiIconButton(onClick = onDismiss) {
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
            ErrorState(error = state.error, onRetry = onRetryLoad)
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
    EmptyState(
        icon = Icons.Default.Group,
        title = stringResource(R.string.people_friends_empty),
        subtitle = if (searchQuery.isNotBlank()) {
            stringResource(R.string.people_friends_try_adjusting)
        } else {
            stringResource(R.string.people_friends_add_to_start)
        },
        modifier = Modifier.padding(top = 64.dp, start = 32.dp, end = 32.dp),
        verticalArrangement = Arrangement.Top,
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

    MochiCard(
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
            MochiIconButton(onClick = onMessage) {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = stringResource(R.string.people_friends_message),
                )
            }
            MochiIconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.PersonRemove,
                    contentDescription = stringResource(R.string.people_friends_remove),
                )
            }
        }
    }
}
