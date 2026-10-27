// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.components

import androidx.activity.compose.LocalActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.mochios.android.ui.components.DrawerActionRow
import org.mochios.android.ui.components.DrawerTitle
import org.mochios.android.ui.components.MochiListDrawer
import org.mochios.people.R
import org.mochios.people.ui.sync.ContactsSyncRows

/**
 * Where the People drawer's rows lead, built once per route in the nav graph
 * so every screen's drawer goes to the same places.
 *
 * @property onOpenBook opens one address book.
 * @property onOpenAllContacts opens every contact.
 * @property onSwitchSection opens Invitations or Profile.
 * @property onOpenGroup opens one group.
 * @property onCreateBook opens the new address book screen.
 * @property onCreateGroup opens the new group screen.
 * @property onConnectDevice opens the connected devices screen.
 */
data class PeopleDrawerNavigation(
    val onOpenBook: (id: String) -> Unit,
    val onOpenAllContacts: () -> Unit,
    val onSwitchSection: (PeopleSidebarSection) -> Unit,
    val onOpenGroup: (id: String) -> Unit,
    val onCreateBook: () -> Unit,
    val onCreateGroup: () -> Unit,
    val onConnectDevice: () -> Unit,
)

/**
 * The People app's drawer, the same on every screen: Profile with the user's
 * avatar, All contacts, the address books and Invitations, the groups under a
 * Groups heading, then
 * Create address book, Create group, Connected devices and Sync to this phone.
 * [selectedId] is the row of the screen showing; tapping it only closes the
 * drawer. The books and groups are marked stale when the app leaves the
 * foreground and fetched again the next time the drawer opens.
 */
@Composable
fun PeopleDrawer(
    drawerState: DrawerState,
    selectedId: String,
    navigation: PeopleDrawerNavigation,
    viewModel: PeopleDrawerViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val books by viewModel.books.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val identity by viewModel.identity.collectAsState()
    val scope = rememberCoroutineScope()

    (LocalActivity.current as? LifecycleOwner)?.let { activity ->
        LifecycleEventEffect(Lifecycle.Event.ON_STOP, lifecycleOwner = activity) {
            viewModel.markStale()
        }
    }

    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.targetValue }
            .filter { value -> value == DrawerValue.Open }
            .collect { viewModel.load() }
    }

    MochiListDrawer(
        drawerState = drawerState,
        header = { DrawerTitle(stringResource(R.string.people_sidebar_header)) },
        topItems = listOf(peopleProfileItem(identity)),
        allItem = peopleAllContactsItem(),
        items = peopleDrawerItems(books, groups),
        selectedId = selectedId,
        onItemClick = { item ->
            scope.launch { drawerState.close() }
            if (item.id != selectedId) {
                val book = peopleDrawerBook(item.id)
                val group = peopleDrawerGroup(item.id)
                val section = peopleDrawerSection(item.id)
                when {
                    book != null -> navigation.onOpenBook(book)
                    group != null -> navigation.onOpenGroup(group)
                    section == PeopleSidebarSection.CONTACTS -> navigation.onOpenAllContacts()
                    section != null -> navigation.onSwitchSection(section)
                }
            }
        },
        actions = {
            DrawerActionRow(
                title = stringResource(R.string.people_books_create),
                icon = Icons.Outlined.Add,
                onClick = {
                    scope.launch { drawerState.close() }
                    navigation.onCreateBook()
                },
            )
            DrawerActionRow(
                title = stringResource(R.string.people_groups_create),
                icon = Icons.Outlined.GroupAdd,
                onClick = {
                    scope.launch { drawerState.close() }
                    navigation.onCreateGroup()
                },
            )
            DrawerActionRow(
                title = stringResource(R.string.people_device_title),
                icon = Icons.Outlined.Smartphone,
                onClick = {
                    scope.launch { drawerState.close() }
                    navigation.onConnectDevice()
                },
            )
            ContactsSyncRows()
        },
        content = content,
    )
}
