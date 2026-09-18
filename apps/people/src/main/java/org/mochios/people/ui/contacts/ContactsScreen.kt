// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.contacts

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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.mochios.android.ui.components.AboutDialog
import org.mochios.android.ui.components.DrawerActionRow
import org.mochios.android.ui.components.DrawerTitle
import org.mochios.android.ui.components.EmptyState
import org.mochios.android.ui.components.EntityListRow
import org.mochios.android.ui.components.ErrorState
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.components.MochiCard
import org.mochios.android.ui.components.MochiDropdownMenu
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiIconButton
import org.mochios.android.ui.components.MochiListDrawer
import org.mochios.android.ui.components.MochiTextButton
import org.mochios.android.ui.components.MochiTextField
import org.mochios.people.R
import org.mochios.people.model.Contact
import org.mochios.people.ui.components.PeopleSidebarSection
import org.mochios.people.ui.components.peopleAllContactsItem
import org.mochios.people.ui.components.peopleBookItemId
import org.mochios.people.ui.components.peopleDrawerBook
import org.mochios.people.ui.components.peopleDrawerItems
import org.mochios.people.ui.components.peopleDrawerSection
import org.mochios.people.ui.router.PeopleSection
import org.mochios.people.ui.router.RememberPeopleSection
import org.mochios.android.R as MochiR

/**
 * Contacts list, the People app's entry point. Shows one address book when the
 * route names one, every contact otherwise. [initialAction] "add" (from
 * `mochi://people?action=add`) opens the add-contact screen on first
 * composition.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onOpenContact: (id: String) -> Unit,
    onOpenBook: (id: String) -> Unit,
    onOpenAllContacts: () -> Unit,
    onCreateBook: () -> Unit,
    onSwitchSection: (PeopleSidebarSection) -> Unit,
    onOpenNotifications: () -> Unit,
    onLogout: () -> Unit,
    onMessage: (String) -> Unit = {},
    onAddContact: () -> Unit = {},
    initialAction: String? = null,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAbout by remember { mutableStateOf(false) }
    var showBookMenu by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()
    // The book this screen is showing, once the list has arrived.
    val book = uiState.books.firstOrNull { it.id == viewModel.book }

    RememberPeopleSection(PeopleSection.CONTACTS)

    // Deep-link entry: `?action=add` opens the add-contact screen once. Saved
    // rather than remembered, so coming back from that screen — which composes
    // this one afresh with the same argument — doesn't bounce straight into it
    // again.
    var addOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialAction) {
        if (initialAction == "add" && !addOpened) {
            addOpened = true
            onAddContact()
        }
    }

    // Side-effect events from the ViewModel. The chat link is routed through
    // onMessage (→ MainActivity's navigateToLink), the same path the
    // person-view "Message" button uses; a raw `mochi://chat/...` Intent isn't
    // handled by the app's router.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ContactsEvent.MessagePerson -> onMessage(event.person)
                ContactsEvent.BookDeleted -> onOpenAllContacts()
            }
        }
    }

    MochiListDrawer(
        drawerState = drawerState,
        header = { DrawerTitle(stringResource(R.string.people_sidebar_header)) },
        allItem = peopleAllContactsItem(),
        items = peopleDrawerItems(uiState.books),
        selectedId = if (viewModel.book.isBlank()) {
            PeopleSidebarSection.CONTACTS.name
        } else {
            peopleBookItemId(viewModel.book)
        },
        onItemClick = { item ->
            drawerScope.launch { drawerState.close() }
            val bookId = peopleDrawerBook(item.id)
            when {
                bookId != null -> if (bookId != viewModel.book) onOpenBook(bookId)
                else -> {
                    val section = peopleDrawerSection(item.id)
                    when {
                        section == null -> Unit
                        section != PeopleSidebarSection.CONTACTS -> onSwitchSection(section)
                        viewModel.book.isNotBlank() -> onOpenAllContacts()
                    }
                }
            }
        },
        actions = {
            DrawerActionRow(
                title = stringResource(R.string.people_books_create),
                icon = Icons.Outlined.Add,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    onCreateBook()
                },
            )
            DrawerActionRow(
                title = stringResource(MochiR.string.common_logout),
                icon = Icons.AutoMirrored.Outlined.Logout,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    onLogout()
                },
            )
            DrawerActionRow(
                title = stringResource(MochiR.string.about_label),
                icon = Icons.Outlined.Info,
                onClick = {
                    drawerScope.launch { drawerState.close() }
                    showAbout = true
                },
            )
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            book?.name?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.people_contacts_title),
                        )
                    },
                    navigationIcon = {
                        MochiIconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = stringResource(R.string.people_open_sidebar),
                            )
                        }
                    },
                    actions = {
                        // The book's own actions belong to the screen showing
                        // it: the drawer's rows carry no menu of their own.
                        if (book != null) {
                            Box {
                                MochiIconButton(onClick = { showBookMenu = true }) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.people_books_actions),
                                    )
                                }
                                MochiDropdownMenu(
                                    expanded = showBookMenu,
                                    onDismissRequest = { showBookMenu = false },
                                ) {
                                    MochiDropdownMenuItem(
                                        text = { Text(stringResource(R.string.people_books_rename)) },
                                        onClick = {
                                            showBookMenu = false
                                            viewModel.requestRenameBook(book)
                                        },
                                    )
                                    if (!book.isDefault) {
                                        MochiDropdownMenuItem(
                                            text = { Text(stringResource(R.string.people_books_delete)) },
                                            onClick = {
                                                showBookMenu = false
                                                viewModel.requestDeleteBook(book)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        MochiIconButton(onClick = onOpenNotifications) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = stringResource(MochiR.string.common_notifications),
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onAddContact) {
                    Icon(
                        Icons.Default.PersonAdd,
                        contentDescription = stringResource(R.string.people_add_contact_title),
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
                    MochiTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        placeholder = { Text(stringResource(R.string.people_contacts_search_placeholder)) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )

                    // Sort toggle (name vs recently added), matching web. Only
                    // shown once there are contacts to order.
                    if (uiState.contacts.isNotEmpty()) {
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
                                            ContactSortBy.RECENT ->
                                                stringResource(R.string.people_contacts_sort_recent)
                                            ContactSortBy.NAME ->
                                                stringResource(R.string.people_contacts_sort_name)
                                        }
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                MochiDropdownMenu(
                                    expanded = sortMenuOpen,
                                    onDismissRequest = { sortMenuOpen = false },
                                ) {
                                    MochiDropdownMenuItem(
                                        text = { Text(stringResource(R.string.people_contacts_sort_name)) },
                                        onClick = {
                                            viewModel.setSortBy(ContactSortBy.NAME)
                                            sortMenuOpen = false
                                        },
                                        selected = uiState.sortBy == ContactSortBy.NAME,
                                    )
                                    MochiDropdownMenuItem(
                                        text = { Text(stringResource(R.string.people_contacts_sort_recent)) },
                                        onClick = {
                                            viewModel.setSortBy(ContactSortBy.RECENT)
                                            sortMenuOpen = false
                                        },
                                        selected = uiState.sortBy == ContactSortBy.RECENT,
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.showWelcome) {
                        WelcomeBanner(onDismiss = { viewModel.dismissWelcome() })
                    }

                    ContactsContent(
                        state = uiState,
                        contacts = viewModel.filteredContacts(),
                        onOpenContact = { onOpenContact(it.id) },
                        onMessage = { viewModel.messageContact(it) },
                        onInvite = { viewModel.invite(it) },
                        onUnfriend = { viewModel.requestUnfriend(it) },
                        onDelete = { viewModel.requestDelete(it) },
                        onRetryLoad = { viewModel.loadContacts() },
                    )
                }
            }
        }
    }

    val unfriending = uiState.unfriending
    if (unfriending != null) {
        MochiAlertDialog(
            onDismissRequest = { viewModel.cancelUnfriend() },
            title = stringResource(R.string.people_contacts_unfriend),
            text = stringResource(R.string.people_contacts_unfriend_confirm, unfriending.name),
            confirmText = stringResource(R.string.people_contacts_unfriend),
            onConfirm = { viewModel.confirmUnfriend() },
            confirmLoading = uiState.isMutating,
            destructive = true,
            dismissText = stringResource(R.string.people_common_cancel),
        )
    }

    val deleting = uiState.deleting
    if (deleting != null) {
        MochiAlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = stringResource(R.string.people_contacts_delete),
            text = if (deleting.friend) {
                stringResource(R.string.people_contacts_delete_friend_confirm, deleting.name)
            } else {
                stringResource(R.string.people_contacts_delete_confirm, deleting.name)
            },
            confirmText = stringResource(R.string.people_contacts_delete),
            onConfirm = { viewModel.confirmDelete() },
            confirmLoading = uiState.isMutating,
            destructive = true,
            dismissText = stringResource(R.string.people_common_cancel),
        )
    }

    val renaming = uiState.renamingBook
    if (renaming != null) {
        var name by remember(renaming.id) { mutableStateOf(renaming.name) }
        MochiAlertDialog(
            onDismissRequest = { viewModel.cancelRenameBook() },
            title = stringResource(R.string.people_books_rename),
            confirmText = stringResource(R.string.people_common_save),
            onConfirm = { viewModel.confirmRenameBook(name) },
            confirmEnabled = name.isNotBlank(),
            confirmLoading = uiState.isMutating,
            dismissText = stringResource(R.string.people_common_cancel),
            content = {
                MochiTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.people_books_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }

    val deletingBook = uiState.deletingBook
    if (deletingBook != null) {
        MochiAlertDialog(
            onDismissRequest = { viewModel.cancelDeleteBook() },
            title = stringResource(R.string.people_books_delete),
            text = pluralStringResource(
                R.plurals.people_books_delete_confirm,
                deletingBook.count,
                deletingBook.name,
                deletingBook.count,
            ),
            confirmText = stringResource(R.string.people_books_delete),
            onConfirm = { viewModel.confirmDeleteBook() },
            confirmLoading = uiState.isMutating,
            destructive = true,
            dismissText = stringResource(R.string.people_common_cancel),
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
private fun ContactsContent(
    state: ContactsUiState,
    contacts: List<Contact>,
    onOpenContact: (Contact) -> Unit,
    onMessage: (Contact) -> Unit,
    onInvite: (Contact) -> Unit,
    onUnfriend: (Contact) -> Unit,
    onDelete: (Contact) -> Unit,
    onRetryLoad: () -> Unit,
) {
    when {
        state.isLoading && state.contacts.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        state.error != null && state.contacts.isEmpty() -> {
            ErrorState(error = state.error, onRetry = onRetryLoad)
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (contacts.isEmpty()) {
                    item(key = "__empty__") {
                        EmptyContactsHint(searchQuery = state.searchQuery)
                    }
                } else {
                    items(contacts, key = { it.id }) { contact ->
                        ContactRow(
                            contact = contact,
                            onTap = { onOpenContact(contact) },
                            onMessage = { onMessage(contact) },
                            onInvite = { onInvite(contact) },
                            onUnfriend = { onUnfriend(contact) },
                            onDelete = { onDelete(contact) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyContactsHint(searchQuery: String) {
    EmptyState(
        icon = Icons.Outlined.Contacts,
        title = stringResource(R.string.people_contacts_empty),
        subtitle = if (searchQuery.isNotBlank()) {
            stringResource(R.string.people_contacts_try_adjusting)
        } else {
            stringResource(R.string.people_contacts_add_to_start)
        },
        modifier = Modifier.padding(top = 64.dp, start = 32.dp, end = 32.dp),
        verticalArrangement = Arrangement.Top,
    )
}

@Composable
private fun ContactRow(
    contact: Contact,
    onTap: () -> Unit,
    onMessage: () -> Unit,
    onInvite: () -> Unit,
    onUnfriend: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val mochi = contact.person.isNotBlank()

    EntityListRow(
        name = contact.name,
        seed = contact.person.ifBlank { contact.id },
        icon = Icons.Outlined.Person,
        avatarUrl = if (mochi) "/people/${contact.person}/-/avatar" else null,
        subtitle = contact.directory.takeIf { it.isNotBlank() && it != contact.name },
        onClick = onTap,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (contact.friend) {
                    AssistChip(
                        onClick = onTap,
                        label = { Text(stringResource(R.string.people_contacts_friend)) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                    MochiIconButton(onClick = onMessage) {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat,
                            contentDescription = stringResource(R.string.people_contacts_message),
                        )
                    }
                }
                Box {
                    MochiIconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.people_contacts_actions),
                        )
                    }
                    MochiDropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        if (mochi && !contact.friend) {
                            MochiDropdownMenuItem(
                                text = { Text(stringResource(R.string.people_contacts_invite)) },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onInvite()
                                },
                            )
                        }
                        if (contact.friend) {
                            MochiDropdownMenuItem(
                                text = { Text(stringResource(R.string.people_contacts_unfriend)) },
                                leadingIcon = {
                                    Icon(Icons.Default.PersonRemove, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onUnfriend()
                                },
                            )
                        }
                        MochiDropdownMenuItem(
                            text = { Text(stringResource(R.string.people_contacts_delete)) },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        },
    )
}
