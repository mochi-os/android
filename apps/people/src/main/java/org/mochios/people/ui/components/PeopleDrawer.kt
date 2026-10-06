// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.ui.components

import androidx.activity.compose.LocalActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.mochios.android.ui.components.AboutDialog
import org.mochios.android.ui.components.DrawerActionRow
import org.mochios.android.ui.components.DrawerTitle
import org.mochios.android.ui.components.MochiListDrawer
import org.mochios.people.R
import org.mochios.people.ui.sync.ContactsSyncRows
import org.mochios.android.R as MochiR

/**
 * Where the People drawer's rows lead, built once per route in the nav graph
 * so every screen's drawer goes to the same places.
 *
 * @property onOpenBook opens one address book.
 * @property onOpenAllContacts opens every contact.
 * @property onSwitchSection opens Invitations, Groups or Profile.
 * @property onCreateBook opens the new address book screen.
 * @property onLogout logs the user out.
 */
data class PeopleDrawerNavigation(
    val onOpenBook: (id: String) -> Unit,
    val onOpenAllContacts: () -> Unit,
    val onSwitchSection: (PeopleSidebarSection) -> Unit,
    val onCreateBook: () -> Unit,
    val onLogout: () -> Unit,
)

/**
 * The People app's drawer, the same on every screen: All contacts, the address
 * books, Invitations, Groups and Profile, then Create address book, Sync to
 * this phone, Log out and About. [selectedId] is the row of the screen
 * showing; tapping it only closes the drawer. The books are marked stale when
 * the app leaves the foreground and fetched again the next time the drawer
 * opens.
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
    val scope = rememberCoroutineScope()
    var showAbout by remember { mutableStateOf(false) }

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
        allItem = peopleAllContactsItem(),
        items = peopleDrawerItems(books),
        selectedId = selectedId,
        onItemClick = { item ->
            scope.launch { drawerState.close() }
            if (item.id != selectedId) {
                val book = peopleDrawerBook(item.id)
                val section = peopleDrawerSection(item.id)
                when {
                    book != null -> navigation.onOpenBook(book)
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
            ContactsSyncRows()
            DrawerActionRow(
                title = stringResource(MochiR.string.common_logout),
                icon = Icons.AutoMirrored.Outlined.Logout,
                onClick = {
                    scope.launch { drawerState.close() }
                    navigation.onLogout()
                },
            )
            DrawerActionRow(
                title = stringResource(MochiR.string.about_label),
                icon = Icons.Outlined.Info,
                onClick = {
                    scope.launch { drawerState.close() }
                    showAbout = true
                },
            )
        },
        content = content,
    )

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}
