package org.mochios.projects.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import org.mochios.projects.ui.design.DesignScreen
import org.mochios.projects.ui.find.FindProjectsScreen
import org.mochios.projects.ui.`object`.DiffViewerScreen
import org.mochios.projects.ui.project.ProjectScreen
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
    const val PROJECT_SETTINGS = "projects/project/{projectId}/settings"
    const val PROJECT_DESIGN = "projects/project/{projectId}/design"
    const val DIFF_VIEWER = "projects/project/{projectId}/diff/{repo}?source={source}&target={target}"

    fun project(projectId: String) = "projects/project/$projectId"
    fun projectObject(projectId: String, objectId: String) = "projects/project/$projectId/object/$objectId"
    fun projectSettings(projectId: String) = "projects/project/$projectId/settings"
    fun projectDesign(projectId: String) = "projects/project/$projectId/design"
    fun diffViewer(projectId: String, repo: String, source: String, target: String) =
        "projects/project/$projectId/diff/$repo?source=$source&target=$target"
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
            onFindProjects = { navController.navigate(ProjectsApp.FIND_PROJECTS) },
            onSettings = { id -> navController.navigate(ProjectsApp.projectSettings(id)) },
            onDesign = { id -> navController.navigate(ProjectsApp.projectDesign(id)) },
            onViewDiff = { id, repo, source, target ->
                navController.navigate(ProjectsApp.diffViewer(id, repo, source, target))
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
            onFindProjects = { navController.navigate(ProjectsApp.FIND_PROJECTS) },
            onSettings = { id -> navController.navigate(ProjectsApp.projectSettings(id)) },
            onDesign = { id -> navController.navigate(ProjectsApp.projectDesign(id)) },
            onViewDiff = { id, repo, source, target ->
                navController.navigate(ProjectsApp.diffViewer(id, repo, source, target))
            },
            onLogout = onLogout,
            initialObjectId = backStackEntry.arguments?.getString("objectId"),
        )
    }

    composable(ProjectsApp.FIND_PROJECTS) {
        FindProjectsScreen(
            onBack = { navController.popBackStack() },
            onProjectSubscribed = { navController.popBackStack() },
        )
    }

    composable(
        route = ProjectsApp.PROJECT_SETTINGS,
        arguments = listOf(navArgument("projectId") { type = NavType.StringType })
    ) {
        ProjectSettingsScreen(
            onBack = { navController.popBackStack() },
            onProjectDeleted = { navController.popBackStack(ProjectsApp.ROUTER, inclusive = false) },
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
