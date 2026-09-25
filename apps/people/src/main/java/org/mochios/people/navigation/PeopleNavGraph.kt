// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import android.net.Uri
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import org.mochios.people.ui.components.PeopleSidebarSection
import org.mochios.people.ui.contacts.AddContactScreen
import org.mochios.people.ui.contacts.ContactEditScreen
import org.mochios.people.ui.contacts.ContactsScreen
import org.mochios.people.ui.contacts.CreateBookScreen
import org.mochios.people.ui.devices.ConnectDeviceScreen
import org.mochios.people.ui.groups.AddMemberScreen
import org.mochios.people.ui.groups.CreateGroupScreen
import org.mochios.people.ui.groups.GroupDetailScreen
import org.mochios.people.ui.groups.GroupsScreen
import org.mochios.people.ui.invitations.InvitationsScreen
import org.mochios.people.ui.person.PersonViewScreen
import org.mochios.people.ui.profile.ProfileScreen
import org.mochios.people.ui.router.PeopleRouter
import org.mochios.people.ui.router.PeopleSection

object PeopleApp {
    /** Router entry point — picks one of the section routes below. */
    const val HOME = "people/router"
    const val ROUTER = "people/router"

    const val CONTACTS = "people/contacts?action={action}"
    const val CONTACTS_ADD = "people/contacts/add?contact={contact}&name={name}"
    const val CONTACT_NEW = "people/contacts/new"
    const val CONTACT_EDIT = "people/contacts/{id}"
    const val BOOK_CREATE = "people/books/create"
    const val BOOK = "people/books/{id}"
    const val INVITATIONS = "people/invitations"
    const val PROFILE = "people/profile"
    const val GROUPS = "people/groups"
    const val GROUP_DETAIL = "people/groups/{id}"
    const val GROUP_CREATE = "people/groups/create"
    const val GROUP_ADD_MEMBER = "people/groups/{id}/add-member"
    const val PERSON_VIEW = "people/person/{id}"
    const val DEVICES = "people/devices"

    /** The add screen, on an ordinary search or opened to link one card to the person it finds. */
    fun contactsAdd(contact: String = "", name: String = ""): String =
        "people/contacts/add?contact=${Uri.encode(contact)}&name=${Uri.encode(name)}"

    fun groupDetail(id: String) = "people/groups/$id"
    fun groupAddMember(id: String) = "people/groups/$id/add-member"
    fun personView(id: String) = "people/person/$id"
    fun contactEdit(id: String) = "people/contacts/$id"
    fun book(id: String) = "people/books/$id"
    fun contacts(action: String? = null): String = if (action.isNullOrBlank()) {
        "people/contacts"
    } else {
        "people/contacts?action=$action"
    }
}

private fun NavController.openPeopleSection(section: PeopleSidebarSection) {
    val target = when (section) {
        PeopleSidebarSection.CONTACTS -> PeopleApp.contacts()
        PeopleSidebarSection.INVITATIONS -> PeopleApp.INVITATIONS
        PeopleSidebarSection.GROUPS -> PeopleApp.GROUPS
        PeopleSidebarSection.PROFILE -> PeopleApp.PROFILE
    }
    navigate(target) {
        // Pop back to the router so the user always lands on a single
        // section screen instead of accumulating siblings.
        popUpTo(PeopleApp.ROUTER) { inclusive = false }
        launchSingleTop = true
    }
}

/** Back to a freshly loaded contacts list, dropping whatever led here. */
private fun NavController.openContacts() {
    navigate(PeopleApp.contacts()) {
        popUpTo(PeopleApp.ROUTER) { inclusive = false }
        launchSingleTop = true
    }
}

fun NavGraphBuilder.peopleNavGraph(
    navController: NavController,
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onOpenLink: (String) -> Unit = {},
) {
    composable(PeopleApp.ROUTER) {
        PeopleRouter(onResolve = { section ->
            val target = when (section) {
                PeopleSection.INVITATIONS -> PeopleApp.INVITATIONS
                PeopleSection.GROUPS -> PeopleApp.GROUPS
                PeopleSection.PROFILE -> PeopleApp.PROFILE
                else -> PeopleApp.contacts()
            }
            navController.navigate(target) {
                popUpTo(PeopleApp.ROUTER) { inclusive = true }
            }
        })
    }

    composable(
        route = PeopleApp.CONTACTS,
        arguments = listOf(
            navArgument("action") {
                type = NavType.StringType
                defaultValue = ""
                nullable = false
            },
        ),
        deepLinks = listOf(
            navDeepLink { uriPattern = "mochi://people?action={action}" },
        ),
    ) { backStackEntry ->
        val action = backStackEntry.arguments?.getString("action").orEmpty()
        ContactsScreen(
            onOpenContact = { id -> navController.navigate(PeopleApp.contactEdit(id)) },
            onOpenBook = { id -> navController.navigate(PeopleApp.book(id)) },
            onOpenAllContacts = { navController.openContacts() },
            onCreateBook = { navController.navigate(PeopleApp.BOOK_CREATE) },
            onSwitchSection = { navController.openPeopleSection(it) },
            onOpenNotifications = onOpenNotifications,
            onLogout = onLogout,
            onMessage = { person -> onOpenLink("chat/new?friend=$person") },
            onAddContact = { navController.navigate(PeopleApp.contactsAdd()) },
            onConnectDevice = { navController.navigate(PeopleApp.DEVICES) },
            initialAction = action.ifBlank { null },
        )
    }

    composable(
        route = PeopleApp.BOOK,
        arguments = listOf(navArgument("id") { type = NavType.StringType }),
    ) {
        ContactsScreen(
            onOpenContact = { id -> navController.navigate(PeopleApp.contactEdit(id)) },
            onOpenBook = { id ->
                // Swapping books from inside one replaces it rather than
                // stacking on it, so Back still lands on the contacts list.
                navController.navigate(PeopleApp.book(id)) {
                    popUpTo(PeopleApp.BOOK) { inclusive = true }
                }
            },
            onOpenAllContacts = { navController.openContacts() },
            onCreateBook = { navController.navigate(PeopleApp.BOOK_CREATE) },
            onSwitchSection = { navController.openPeopleSection(it) },
            onOpenNotifications = onOpenNotifications,
            onLogout = onLogout,
            onMessage = { person -> onOpenLink("chat/new?friend=$person") },
            onAddContact = { navController.navigate(PeopleApp.contactsAdd()) },
            onConnectDevice = { navController.navigate(PeopleApp.DEVICES) },
        )
    }

    composable(PeopleApp.DEVICES) {
        ConnectDeviceScreen(onBack = { navController.popBackStack() })
    }

    composable(
        route = PeopleApp.CONTACTS_ADD,
        arguments = listOf(
            navArgument("contact") { type = NavType.StringType; defaultValue = "" },
            navArgument("name") { type = NavType.StringType; defaultValue = "" },
        ),
    ) {
        AddContactScreen(
            onBack = { navController.popBackStack() },
            onNewContact = { navController.navigate(PeopleApp.CONTACT_NEW) },
            // An accepted invite or a saved contact leaves the list behind
            // stale, and popping back would land on the entry that was already
            // there. Navigating builds a fresh one that reloads.
            onContactsChanged = { navController.openContacts() },
            // Linking was asked for from a contact's editor, which reloads on
            // the repository's change signal, so stepping back shows the switch on.
            onLinked = { navController.popBackStack() },
        )
    }

    // Registered ahead of CONTACT_EDIT so "new" is not read as a contact id.
    composable(PeopleApp.CONTACT_NEW) {
        ContactEditScreen(
            onBack = { navController.popBackStack() },
            onSaved = { navController.openContacts() },
            onDeleted = { navController.openContacts() },
        )
    }

    composable(
        route = PeopleApp.CONTACT_EDIT,
        arguments = listOf(navArgument("id") { type = NavType.StringType }),
    ) {
        ContactEditScreen(
            onBack = { navController.popBackStack() },
            onFindPerson = { contact, name -> navController.navigate(PeopleApp.contactsAdd(contact, name)) },
            // The list behind reloads on the repository's contactsChanged, so
            // stepping back to it shows the edit.
            onSaved = { navController.popBackStack() },
            onDeleted = { navController.popBackStack() },
        )
    }

    composable(PeopleApp.BOOK_CREATE) {
        CreateBookScreen(
            onBack = { navController.popBackStack() },
            // Drop the create screen and open the new book, so Back from it
            // lands on the contacts list.
            onCreated = { id ->
                navController.navigate(PeopleApp.book(id)) {
                    popUpTo(PeopleApp.BOOK_CREATE) { inclusive = true }
                }
            },
        )
    }

    composable(PeopleApp.INVITATIONS) {
        InvitationsScreen(
            onSwitchSection = { navController.openPeopleSection(it) },
        )
    }

    composable(PeopleApp.PROFILE) {
        ProfileScreen(
            onSwitchSection = { navController.openPeopleSection(it) },
            onLogout = onLogout,
            onOpenNotifications = onOpenNotifications,
        )
    }

    composable(PeopleApp.GROUPS) {
        GroupsScreen(
            onOpenGroup = { id -> navController.navigate(PeopleApp.groupDetail(id)) },
            onCreateGroup = { navController.navigate(PeopleApp.GROUP_CREATE) },
            onSwitchSection = { navController.openPeopleSection(it) },
            onOpenNotifications = onOpenNotifications,
        )
    }

    composable(PeopleApp.GROUP_CREATE) {
        CreateGroupScreen(
            onBack = { navController.popBackStack() },
            // Drop the create screen and open the new group, so Back from the
            // detail lands on the list — which reloads and shows the group.
            onCreated = { id ->
                navController.navigate(PeopleApp.groupDetail(id)) {
                    popUpTo(PeopleApp.GROUP_CREATE) { inclusive = true }
                }
            },
        )
    }

    composable(
        route = PeopleApp.GROUP_DETAIL,
        arguments = listOf(navArgument("id") { type = NavType.StringType }),
    ) { backStackEntry ->
        val groupId = backStackEntry.arguments?.getString("id").orEmpty()
        GroupDetailScreen(
            onBack = { navController.popBackStack() },
            onOpenPerson = { id -> navController.navigate(PeopleApp.personView(id)) },
            onAddMember = { navController.navigate(PeopleApp.groupAddMember(groupId)) },
        )
    }

    composable(
        route = PeopleApp.GROUP_ADD_MEMBER,
        arguments = listOf(navArgument("id") { type = NavType.StringType }),
    ) { backStackEntry ->
        val groupId = backStackEntry.arguments?.getString("id").orEmpty()
        AddMemberScreen(
            onBack = { navController.popBackStack() },
            // Rebuild the group in place once a member has joined: popping back
            // would land on the detail entry that was already there, whose view
            // model still holds the members fetched before the add.
            onAdded = {
                navController.navigate(PeopleApp.groupDetail(groupId)) {
                    popUpTo(PeopleApp.GROUP_DETAIL) { inclusive = true }
                }
            },
        )
    }

    composable(
        route = PeopleApp.PERSON_VIEW,
        arguments = listOf(navArgument("id") { type = NavType.StringType }),
    ) {
        PersonViewScreen(
            onBack = { navController.popBackStack() },
            onMessage = { personId, _ ->
                onOpenLink("chat/new?friend=$personId")
            },
        )
    }
}
