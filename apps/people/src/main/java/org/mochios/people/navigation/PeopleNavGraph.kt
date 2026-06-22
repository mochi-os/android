// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import org.mochios.people.ui.components.PeopleSidebarSection
import org.mochios.people.ui.friends.FriendsScreen
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

    const val FRIENDS = "people/friends?action={action}"
    const val INVITATIONS = "people/invitations"
    const val PROFILE = "people/profile"
    const val GROUPS = "people/groups"
    const val GROUP_DETAIL = "people/groups/{id}"
    const val PERSON_VIEW = "people/person/{id}"

    fun groupDetail(id: String) = "people/groups/$id"
    fun personView(id: String) = "people/person/$id"
    fun friends(action: String? = null): String = if (action.isNullOrBlank()) {
        "people/friends"
    } else {
        "people/friends?action=$action"
    }
}

/**
 * Map a sidebar selection to its nav route. Used by every top-level People
 * screen to power the shared [PeopleSidebar] drawer — the sidebar is the
 * only navigation between sections, so each section call replaces the
 * current entry rather than stacking (otherwise four taps left four entries
 * in the back stack).
 */
private fun NavController.openPeopleSection(section: PeopleSidebarSection) {
    val target = when (section) {
        PeopleSidebarSection.FRIENDS -> PeopleApp.friends()
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
                else -> PeopleApp.friends()
            }
            navController.navigate(target) {
                popUpTo(PeopleApp.ROUTER) { inclusive = true }
            }
        })
    }

    composable(
        route = PeopleApp.FRIENDS,
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
        FriendsScreen(
            onOpenPerson = { id -> navController.navigate(PeopleApp.personView(id)) },
            onSwitchSection = { navController.openPeopleSection(it) },
            onOpenNotifications = onOpenNotifications,
            onLogout = onLogout,
            onMessage = { friendId ->
                onOpenLink("chat/new?friend=${friendId}")
            },
            initialAction = action.ifBlank { null },
        )
    }

    composable(PeopleApp.INVITATIONS) {
        InvitationsScreen(
            onOpenPerson = { id -> navController.navigate(PeopleApp.personView(id)) },
            onSwitchSection = { navController.openPeopleSection(it) },
            onOpenNotifications = onOpenNotifications,
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
            onSwitchSection = { navController.openPeopleSection(it) },
            onOpenNotifications = onOpenNotifications,
        )
    }

    composable(
        route = PeopleApp.GROUP_DETAIL,
        arguments = listOf(navArgument("id") { type = NavType.StringType }),
    ) {
        GroupDetailScreen(
            onBack = { navController.popBackStack() },
            onOpenPerson = { id -> navController.navigate(PeopleApp.personView(id)) },
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
