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
import org.mochios.projects.ui.projectlist.ProjectListScreen
import org.mochios.projects.ui.settings.ProjectSettingsScreen

object ProjectsApp {
    const val HOME = "projects/list"
    const val PROJECT_LIST = "projects/list"
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
) {
    composable(ProjectsApp.PROJECT_LIST) {
        ProjectListScreen(
            onProjectClick = { projectId -> navController.navigate(ProjectsApp.project(projectId)) },
            onFindProjects = { navController.navigate(ProjectsApp.FIND_PROJECTS) },
            onLogout = onLogout,
        )
    }

    composable(
        route = ProjectsApp.PROJECT,
        arguments = listOf(navArgument("projectId") { type = NavType.StringType })
    ) {
        ProjectScreen(
            onBack = { navController.popBackStack() },
            onSettings = { projectId -> navController.navigate(ProjectsApp.projectSettings(projectId)) },
            onDesign = { projectId -> navController.navigate(ProjectsApp.projectDesign(projectId)) },
            onViewDiff = { projectId, repo, source, target ->
                navController.navigate(ProjectsApp.diffViewer(projectId, repo, source, target))
            },
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
    ) {
        ProjectScreen(
            onBack = { navController.popBackStack() },
            onSettings = { projectId -> navController.navigate(ProjectsApp.projectSettings(projectId)) },
            onDesign = { projectId -> navController.navigate(ProjectsApp.projectDesign(projectId)) },
            onViewDiff = { projectId, repo, source, target ->
                navController.navigate(ProjectsApp.diffViewer(projectId, repo, source, target))
            },
            initialObjectId = it.arguments?.getString("objectId"),
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
            onProjectDeleted = { navController.popBackStack(ProjectsApp.PROJECT_LIST, inclusive = false) },
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
