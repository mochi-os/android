// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import org.mochios.android.ui.components.LastViewedStore
import org.mochios.crm.ui.design.DesignScreen
import org.mochios.crm.ui.find.FindCrmsScreen
import org.mochios.crm.ui.crm.CreateObjectScreen
import org.mochios.crm.ui.crm.CrmScreen
import org.mochios.crm.ui.crmlist.CreateCrmScreen
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
    const val CREATE_CRM = "crm/create"
    const val PROJECT_SETTINGS = "crm/crm/{crmId}/settings"
    const val PROJECT_DESIGN = "crm/crm/{crmId}/design"
    // Deliberately not `crm/crm/{crmId}/object/create`, which the PROJECT_OBJECT
    // pattern above also matches, with objectId='create'.
    const val CREATE_OBJECT = "crm/crm/{crmId}/create-object?field={field}&value={value}"

    fun crm(crmId: String) = "crm/crm/$crmId"
    fun crmObject(crmId: String, objectId: String) = "crm/crm/$crmId/object/$objectId"
    fun crmSettings(crmId: String) = "crm/crm/$crmId/settings"
    fun crmDesign(crmId: String) = "crm/crm/$crmId/design"

    /**
     * The create-object form for [crmId], optionally seeded with the one field
     * value a board column's "+" carries. Field ids and option ids are opaque
     * server strings, so both are encoded before they go in the query.
     */
    fun createObject(crmId: String, presetValues: Map<String, String>): String {
        val preset = presetValues.entries.firstOrNull()
        val field = Uri.encode(preset?.key.orEmpty())
        val value = Uri.encode(preset?.value.orEmpty())
        return "crm/crm/$crmId/create-object?field=$field&value=$value"
    }
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
            onSelectAll = {
                navController.navigate(CrmsApp.crm(LastViewedStore.ALL)) {
                    popUpTo(CrmsApp.PROJECT) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onFindCrms = { navController.navigate(CrmsApp.FIND_PROJECTS) },
            onCreateCrm = { navController.navigate(CrmsApp.CREATE_CRM) },
            onSettings = { id -> navController.navigate(CrmsApp.crmSettings(id)) },
            onDesign = { id -> navController.navigate(CrmsApp.crmDesign(id)) },
            onCreateObject = { presetValues ->
                navController.navigate(CrmsApp.createObject(crmId, presetValues))
            },
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
            onSelectAll = {
                navController.navigate(CrmsApp.crm(LastViewedStore.ALL)) {
                    popUpTo(CrmsApp.PROJECT) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onFindCrms = { navController.navigate(CrmsApp.FIND_PROJECTS) },
            onCreateCrm = { navController.navigate(CrmsApp.CREATE_CRM) },
            onSettings = { id -> navController.navigate(CrmsApp.crmSettings(id)) },
            onDesign = { id -> navController.navigate(CrmsApp.crmDesign(id)) },
            onCreateObject = { presetValues ->
                navController.navigate(CrmsApp.createObject(crmId, presetValues))
            },
            onLogout = onLogout,
            initialObjectId = backStackEntry.arguments?.getString("objectId"),
        )
    }

    composable(CrmsApp.FIND_PROJECTS) {
        FindCrmsScreen(
            onBack = { navController.popBackStack() },
            // Drop discovery from the back stack and open the CRM just joined.
            // Popping back would land on the CRM entry that was already there,
            // whose list view model still holds the CRMs fetched before the
            // subscribe — so the new one wouldn't show until a manual refresh.
            // Navigating builds a fresh entry that reloads.
            onCrmSubscribed = { crmId ->
                navController.navigate(CrmsApp.crm(crmId)) {
                    popUpTo(CrmsApp.FIND_PROJECTS) { inclusive = true }
                }
            },
        )
    }

    composable(CrmsApp.CREATE_CRM) {
        CreateCrmScreen(
            onBack = { navController.popBackStack() },
            // Drop the create screen and open the CRM just made. Popping back
            // would land on the CRM entry that was already there, whose list
            // view model still holds the CRMs fetched before the create — so
            // the new one wouldn't show in the drawer until a manual refresh.
            // Navigating builds a fresh entry that reloads.
            onCreated = { crmId ->
                navController.navigate(CrmsApp.crm(crmId)) {
                    popUpTo(CrmsApp.CREATE_CRM) { inclusive = true }
                }
            },
        )
    }

    composable(
        route = CrmsApp.CREATE_OBJECT,
        arguments = listOf(
            navArgument("crmId") { type = NavType.StringType },
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
        val crmId = backStackEntry.arguments?.getString("crmId").orEmpty()
        CreateObjectScreen(
            onBack = { navController.popBackStack() },
            // Drop the create screen and open the object just made. Popping back
            // would land on the CRM entry that was already there, whose view
            // model still holds the objects fetched before the create — so the
            // new one wouldn't show until a manual refresh. Navigating builds a
            // fresh entry that reloads.
            onCreated = { objectId ->
                navController.navigate(CrmsApp.crmObject(crmId, objectId)) {
                    popUpTo(CrmsApp.CREATE_OBJECT) { inclusive = true }
                }
            },
        )
    }

    composable(
        route = CrmsApp.PROJECT_SETTINGS,
        arguments = listOf(navArgument("crmId") { type = NavType.StringType })
    ) {
        CrmSettingsScreen(
            onBack = { navController.popBackStack() },
            // The CRM is gone, so close settings and land on All. Popping back
            // to the router did nothing: it removes itself from the stack once
            // it resolves, so there was no entry to pop to and the user was
            // left sitting on the settings page of a deleted CRM.
            onCrmDeleted = {
                navController.navigate(CrmsApp.crm(LastViewedStore.ALL)) {
                    popUpTo(CrmsApp.PROJECT) { inclusive = true }
                    launchSingleTop = true
                }
            },
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
