// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import org.mochios.projects.ui.design.DesignScreen
import org.mochios.projects.ui.find.FindProjectsScreen
import org.mochios.projects.ui.`object`.DiffViewerScreen
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.projects.ui.project.CreateObjectScreen
import org.mochios.projects.ui.project.ProjectScreen
import org.mochios.projects.ui.projectlist.CreateProjectScreen
import org.mochios.projects.ui.router.ProjectsRouter
import org.mochios.projects.ui.settings.ProjectSettingsScreen

object ProjectsApp {
    const val HOME = "projects/router"
    const val ROUTER = "projects/router"
    // Detail routes use a `project/` discriminator after the feature prefix
    // so they can't shadow the literal HOME / FIND_PROJECTS routes —
    // `projects/list` would otherwise match `projects/{projectId}` with
    // projectId='list' and route to the detail screen rendering NotFoundState.
    const val PROJECT = "projects/project/{projectId}"
    const val PROJECT_OBJECT = "projects/project/{projectId}/object/{objectId}"
    const val FIND_PROJECTS = "projects/discover"
    const val CREATE_PROJECT = "projects/create"
    const val PROJECT_SETTINGS = "projects/project/{projectId}/settings"
    const val PROJECT_DESIGN = "projects/project/{projectId}/design"
    const val DIFF_VIEWER = "projects/project/{projectId}/diff/{repo}?source={source}&target={target}"
    // Deliberately not `projects/project/{projectId}/object/create`, which the
    // PROJECT_OBJECT pattern above also matches, with objectId='create'.
    const val CREATE_OBJECT =
        "projects/project/{projectId}/create-object?parent={parent}&field={field}&value={value}"

    fun project(projectId: String) = "projects/project/$projectId"
    fun projectObject(projectId: String, objectId: String) = "projects/project/$projectId/object/$objectId"
    fun projectSettings(projectId: String) = "projects/project/$projectId/settings"
    fun projectDesign(projectId: String) = "projects/project/$projectId/design"
    fun diffViewer(projectId: String, repo: String, source: String, target: String) =
        "projects/project/$projectId/diff/$repo?source=$source&target=$target"

    /**
     * The create-object form for [projectId], optionally seeded with the parent
     * an "Add child" started from and the one field value a board column's "+"
     * carries. Object, field and option ids are opaque server strings, so each
     * is encoded before it goes in the query.
     */
    fun createObject(
        projectId: String,
        parent: String? = null,
        presetValues: Map<String, String> = emptyMap(),
    ): String {
        val preset = presetValues.entries.firstOrNull()
        val parentArg = Uri.encode(parent.orEmpty())
        val field = Uri.encode(preset?.key.orEmpty())
        val value = Uri.encode(preset?.value.orEmpty())
        return "projects/project/$projectId/create-object" +
            "?parent=$parentArg&field=$field&value=$value"
    }
}

fun NavGraphBuilder.projectsNavGraph(
    navController: NavController,
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit = {},
) {
    composable(ProjectsApp.ROUTER) {
        ProjectsRouter(onResolve = { projectId ->
            navController.navigate(ProjectsApp.project(projectId)) {
                popUpTo(ProjectsApp.ROUTER) { inclusive = true }
            }
        })
    }

    composable(
        route = ProjectsApp.PROJECT,
        arguments = listOf(navArgument("projectId") {
            type = NavType.StringType
            defaultValue = ""
            nullable = false
        })
    ) { backStackEntry ->
        val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
        ProjectScreen(
            projectId = projectId,
            onSelectProject = { id ->
                navController.navigate(ProjectsApp.project(id)) {
                    popUpTo(ProjectsApp.PROJECT) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onSelectAll = {
                navController.navigate(ProjectsApp.project(LastViewedStore.ALL)) {
                    popUpTo(ProjectsApp.PROJECT) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onFindProjects = { navController.navigate(ProjectsApp.FIND_PROJECTS) },
            onCreateProject = { navController.navigate(ProjectsApp.CREATE_PROJECT) },
            onSettings = { id -> navController.navigate(ProjectsApp.projectSettings(id)) },
            onDesign = { id -> navController.navigate(ProjectsApp.projectDesign(id)) },
            onViewDiff = { id, repo, source, target ->
                navController.navigate(ProjectsApp.diffViewer(id, repo, source, target))
            },
            onCreateObject = { parent, presetValues ->
                navController.navigate(
                    ProjectsApp.createObject(projectId, parent, presetValues)
                )
            },
            onOpenNotifications = onOpenNotifications,
            onLogout = onLogout,
        )
    }

    composable(
        route = ProjectsApp.PROJECT_OBJECT,
        arguments = listOf(
            navArgument("projectId") { type = NavType.StringType },
            navArgument("objectId") { type = NavType.StringType }
        ),
        deepLinks = listOf(
            navDeepLink { uriPattern = "https://{host}/projects/{projectId}/{objectId}" }
        )
    ) { backStackEntry ->
        val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
        ProjectScreen(
            projectId = projectId,
            onSelectProject = { id ->
                navController.navigate(ProjectsApp.project(id)) {
                    popUpTo(ProjectsApp.PROJECT) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onSelectAll = {
                navController.navigate(ProjectsApp.project(LastViewedStore.ALL)) {
                    popUpTo(ProjectsApp.PROJECT) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onFindProjects = { navController.navigate(ProjectsApp.FIND_PROJECTS) },
            onCreateProject = { navController.navigate(ProjectsApp.CREATE_PROJECT) },
            onSettings = { id -> navController.navigate(ProjectsApp.projectSettings(id)) },
            onDesign = { id -> navController.navigate(ProjectsApp.projectDesign(id)) },
            onViewDiff = { id, repo, source, target ->
                navController.navigate(ProjectsApp.diffViewer(id, repo, source, target))
            },
            onCreateObject = { parent, presetValues ->
                navController.navigate(
                    ProjectsApp.createObject(projectId, parent, presetValues)
                )
            },
            onLogout = onLogout,
            initialObjectId = backStackEntry.arguments?.getString("objectId"),
        )
    }

    composable(ProjectsApp.FIND_PROJECTS) {
        FindProjectsScreen(
            onBack = { navController.popBackStack() },
            // Drop discovery from the back stack and open the project just
            // joined. Popping back would land on the project entry that was
            // already there, whose list view model still holds the projects
            // fetched before the subscribe — so the new one wouldn't show until
            // a manual refresh. Navigating builds a fresh entry that reloads.
            onProjectSubscribed = { projectId ->
                navController.navigate(ProjectsApp.project(projectId)) {
                    popUpTo(ProjectsApp.FIND_PROJECTS) { inclusive = true }
                }
            },
        )
    }

    composable(ProjectsApp.CREATE_PROJECT) {
        CreateProjectScreen(
            onBack = { navController.popBackStack() },
            // Drop the create screen and open the project just made. Popping back
            // would land on the project entry that was already there, whose list
            // view model still holds the projects fetched before the create — so
            // the new one wouldn't show in the drawer until a manual refresh.
            // Navigating builds a fresh entry that reloads.
            onCreated = { projectId ->
                navController.navigate(ProjectsApp.project(projectId)) {
                    popUpTo(ProjectsApp.CREATE_PROJECT) { inclusive = true }
                }
            },
        )
    }

    composable(
        route = ProjectsApp.CREATE_OBJECT,
        arguments = listOf(
            navArgument("projectId") { type = NavType.StringType },
            navArgument("parent") {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument("field") {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument("value") {
                type = NavType.StringType
                defaultValue = ""
            },
        )
    ) { backStackEntry ->
        val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
        CreateObjectScreen(
            onBack = { navController.popBackStack() },
            // Drop the create screen and open the object just made, which is
            // where the create dialog used to leave the user. Popping back would
            // land on the project entry that was already there, whose view model
            // still holds the objects fetched before the create — so the new one
            // wouldn't show until a manual refresh. Navigating builds a fresh
            // entry that reloads.
            onCreated = { objectId ->
                navController.navigate(ProjectsApp.projectObject(projectId, objectId)) {
                    popUpTo(ProjectsApp.CREATE_OBJECT) { inclusive = true }
                }
            },
        )
    }

    composable(
        route = ProjectsApp.PROJECT_SETTINGS,
        arguments = listOf(navArgument("projectId") { type = NavType.StringType })
    ) {
        ProjectSettingsScreen(
            onBack = { navController.popBackStack() },
            // The project is gone, so close settings and land on All. Popping
            // back to the router did nothing: it removes itself from the stack
            // once it resolves, so there was no entry to pop to and the user
            // was left sitting on the settings page of a deleted project.
            onProjectDeleted = {
                navController.navigate(ProjectsApp.project(LastViewedStore.ALL)) {
                    popUpTo(ProjectsApp.PROJECT) { inclusive = true }
                    launchSingleTop = true
                }
            },
        )
    }

    composable(
        route = ProjectsApp.PROJECT_DESIGN,
        arguments = listOf(navArgument("projectId") { type = NavType.StringType })
    ) {
        DesignScreen(
            onBack = { navController.popBackStack() },
        )
    }

    composable(
        route = ProjectsApp.DIFF_VIEWER,
        arguments = listOf(
            navArgument("projectId") { type = NavType.StringType },
            navArgument("repo") { type = NavType.StringType },
            navArgument("source") { type = NavType.StringType; defaultValue = "" },
            navArgument("target") { type = NavType.StringType; defaultValue = "" }
        )
    ) {
        DiffViewerScreen(
            onBack = { navController.popBackStack() },
        )
    }
}
