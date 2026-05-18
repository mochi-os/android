package org.mochios.crm.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import org.mochios.crm.ui.design.DesignScreen
import org.mochios.crm.ui.find.FindCrmsScreen
import org.mochios.crm.ui.crm.CrmScreen
import org.mochios.crm.ui.router.CrmsRouter
import org.mochios.crm.ui.settings.CrmSettingsScreen

object CrmsApp {
    const val HOME = "crm/router"
    const val ROUTER = "crm/router"
    // Detail routes use a `crm/` discriminator after the feature prefix
    // so they can't shadow the literal HOME / FIND_PROJECTS routes —
    // `crm/list` would otherwise match `crm/{crmId}` with
    // crmId='list' and route to the detail screen rendering NotFoundState.
    const val PROJECT = "crm/crm/{crmId}"
    const val PROJECT_OBJECT = "crm/crm/{crmId}/object/{objectId}"
    const val FIND_PROJECTS = "crm/discover"
    const val PROJECT_SETTINGS = "crm/crm/{crmId}/settings"
    const val PROJECT_DESIGN = "crm/crm/{crmId}/design"

    fun crm(crmId: String) = "crm/crm/$crmId"
    fun crmObject(crmId: String, objectId: String) = "crm/crm/$crmId/object/$objectId"
    fun crmSettings(crmId: String) = "crm/crm/$crmId/settings"
    fun crmDesign(crmId: String) = "crm/crm/$crmId/design"
}

fun NavGraphBuilder.crmsNavGraph(
    navController: NavController,
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit = {},
) {
    composable(CrmsApp.ROUTER) {
        CrmsRouter(onResolve = { crmId ->
            navController.navigate(CrmsApp.crm(crmId)) {
                popUpTo(CrmsApp.ROUTER) { inclusive = true }
            }
        })
    }

    composable(
        route = CrmsApp.PROJECT,
        arguments = listOf(navArgument("crmId") {
            type = NavType.StringType
            defaultValue = ""
            nullable = false
        })
    ) { backStackEntry ->
        val crmId = backStackEntry.arguments?.getString("crmId").orEmpty()
        CrmScreen(
            crmId = crmId,
            onSelectCrm = { id ->
                navController.navigate(CrmsApp.crm(id)) {
                    popUpTo(CrmsApp.PROJECT) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onFindCrms = { navController.navigate(CrmsApp.FIND_PROJECTS) },
            onSettings = { id -> navController.navigate(CrmsApp.crmSettings(id)) },
            onDesign = { id -> navController.navigate(CrmsApp.crmDesign(id)) },
            onOpenNotifications = onOpenNotifications,
            onLogout = onLogout,
        )
    }

    composable(
        route = CrmsApp.PROJECT_OBJECT,
        arguments = listOf(
            navArgument("crmId") { type = NavType.StringType },
            navArgument("objectId") { type = NavType.StringType }
        ),
        deepLinks = listOf(
            navDeepLink { uriPattern = "https://{host}/crm/{crmId}/{objectId}" }
        )
    ) { backStackEntry ->
        val crmId = backStackEntry.arguments?.getString("crmId").orEmpty()
        CrmScreen(
            crmId = crmId,
            onSelectCrm = { id ->
                navController.navigate(CrmsApp.crm(id)) {
                    popUpTo(CrmsApp.PROJECT) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onFindCrms = { navController.navigate(CrmsApp.FIND_PROJECTS) },
            onSettings = { id -> navController.navigate(CrmsApp.crmSettings(id)) },
            onDesign = { id -> navController.navigate(CrmsApp.crmDesign(id)) },
            onLogout = onLogout,
            initialObjectId = backStackEntry.arguments?.getString("objectId"),
        )
    }

    composable(CrmsApp.FIND_PROJECTS) {
        FindCrmsScreen(
            onBack = { navController.popBackStack() },
            onCrmSubscribed = { navController.popBackStack() },
        )
    }

    composable(
        route = CrmsApp.PROJECT_SETTINGS,
        arguments = listOf(navArgument("crmId") { type = NavType.StringType })
    ) {
        CrmSettingsScreen(
            onBack = { navController.popBackStack() },
            onCrmDeleted = { navController.popBackStack(CrmsApp.ROUTER, inclusive = false) },
        )
    }

    composable(
        route = CrmsApp.PROJECT_DESIGN,
        arguments = listOf(navArgument("crmId") { type = NavType.StringType })
    ) {
        DesignScreen(
            onBack = { navController.popBackStack() },
        )
    }

}
