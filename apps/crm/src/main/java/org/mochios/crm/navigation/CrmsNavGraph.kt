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
import org.mochios.crm.ui.design.ClassDetailScreen
import org.mochios.crm.ui.design.CreateClassScreen
import org.mochios.crm.ui.design.CreateFieldScreen
import org.mochios.crm.ui.design.CreateViewScreen
import org.mochios.crm.ui.design.DesignScreen
import org.mochios.crm.ui.design.EditViewScreen
import org.mochios.crm.ui.design.FieldDetailScreen
import org.mochios.crm.ui.design.OptionScreen
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
    // so they can't shadow the literal HOME / FIND_CRMS routes —
    // `crm/list` would otherwise match `crm/{crmId}` with
    // crmId='list' and route to the detail screen rendering NotFoundState.
    const val CRM = "crm/crm/{crmId}"
    const val CRM_OBJECT = "crm/crm/{crmId}/object/{objectId}"
    const val FIND_CRMS = "crm/discover"
    const val CREATE_CRM = "crm/create"
    const val CRM_SETTINGS = "crm/crm/{crmId}/settings"
    const val CRM_DESIGN = "crm/crm/{crmId}/design"
    const val CRM_DESIGN_CLASS = "crm/crm/{crmId}/design/class/{classId}"
    const val CRM_DESIGN_CREATE_CLASS = "crm/crm/{crmId}/design/create-class"
    const val CRM_DESIGN_VIEW = "crm/crm/{crmId}/design/view/{viewId}"
    const val CRM_DESIGN_CREATE_VIEW = "crm/crm/{crmId}/design/create-view"
    const val CRM_DESIGN_CREATE_FIELD = "crm/crm/{crmId}/design/class/{classId}/create-field"
    const val CRM_DESIGN_OPTION =
        "crm/crm/{crmId}/design/class/{classId}/field/{fieldId}/option?optionId={optionId}"
    const val CRM_ADD_COLUMN =
        "crm/crm/{crmId}/board/class/{classId}/field/{fieldId}/add-column"
    const val CRM_DESIGN_FIELD = "crm/crm/{crmId}/design/class/{classId}/field/{fieldId}"
    // Deliberately not `crm/crm/{crmId}/object/create`, which the CRM_OBJECT
    // pattern above also matches, with objectId='create'.
    const val CREATE_OBJECT = "crm/crm/{crmId}/create-object?field={field}&value={value}"

    fun crm(crmId: String) = "crm/crm/$crmId"
    fun crmObject(crmId: String, objectId: String) = "crm/crm/$crmId/object/$objectId"
    fun crmSettings(crmId: String) = "crm/crm/$crmId/settings"
    fun crmDesign(crmId: String) = "crm/crm/$crmId/design"
    fun crmDesignClass(crmId: String, classId: String) =
        "crm/crm/$crmId/design/class/$classId"
    fun crmDesignCreateClass(crmId: String) = "crm/crm/$crmId/design/create-class"
    fun crmDesignView(crmId: String, viewId: String) =
        "crm/crm/$crmId/design/view/$viewId"
    fun crmDesignCreateView(crmId: String) = "crm/crm/$crmId/design/create-view"
    fun crmDesignCreateField(crmId: String, classId: String) =
        "crm/crm/$crmId/design/class/$classId/create-field"
    fun crmDesignOption(crmId: String, classId: String, fieldId: String, optionId: String = "") =
        "crm/crm/$crmId/design/class/$classId/field/$fieldId/option?optionId=$optionId"
    fun crmAddColumn(crmId: String, classId: String, fieldId: String) =
        "crm/crm/$crmId/board/class/$classId/field/$fieldId/add-column"
    fun crmDesignField(crmId: String, classId: String, fieldId: String) =
        "crm/crm/$crmId/design/class/$classId/field/$fieldId"

    /**
     * Create-object route for [crmId], seeded with the one field value a board
     * column's "+" carries.
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
        route = CrmsApp.CRM,
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
                    popUpTo(CrmsApp.CRM) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onSelectAll = {
                navController.navigate(CrmsApp.crm(LastViewedStore.ALL)) {
                    popUpTo(CrmsApp.CRM) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onFindCrms = { navController.navigate(CrmsApp.FIND_CRMS) },
            onCreateCrm = { navController.navigate(CrmsApp.CREATE_CRM) },
            onSettings = { id -> navController.navigate(CrmsApp.crmSettings(id)) },
            onDesign = { id -> navController.navigate(CrmsApp.crmDesign(id)) },
            onAddColumn = { id, classId, fieldId ->
                navController.navigate(CrmsApp.crmAddColumn(id, classId, fieldId))
            },
            onCreateObject = { presetValues ->
                navController.navigate(CrmsApp.createObject(crmId, presetValues))
            },
            onOpenNotifications = onOpenNotifications,
            onLogout = onLogout,
        )
    }

    composable(
        route = CrmsApp.CRM_OBJECT,
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
                    popUpTo(CrmsApp.CRM) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onSelectAll = {
                navController.navigate(CrmsApp.crm(LastViewedStore.ALL)) {
                    popUpTo(CrmsApp.CRM) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onFindCrms = { navController.navigate(CrmsApp.FIND_CRMS) },
            onCreateCrm = { navController.navigate(CrmsApp.CREATE_CRM) },
            onSettings = { id -> navController.navigate(CrmsApp.crmSettings(id)) },
            onDesign = { id -> navController.navigate(CrmsApp.crmDesign(id)) },
            onAddColumn = { id, classId, fieldId ->
                navController.navigate(CrmsApp.crmAddColumn(id, classId, fieldId))
            },
            onCreateObject = { presetValues ->
                navController.navigate(CrmsApp.createObject(crmId, presetValues))
            },
            onLogout = onLogout,
            initialObjectId = backStackEntry.arguments?.getString("objectId"),
        )
    }

    composable(CrmsApp.FIND_CRMS) {
        FindCrmsScreen(
            onBack = { navController.popBackStack() },
            // Navigate rather than pop: the existing CRM entry's view model
            // still holds the pre-subscribe list.
            onCrmSubscribed = { crmId ->
                navController.navigate(CrmsApp.crm(crmId)) {
                    popUpTo(CrmsApp.FIND_CRMS) { inclusive = true }
                }
            },
        )
    }

    composable(CrmsApp.CREATE_CRM) {
        CreateCrmScreen(
            onBack = { navController.popBackStack() },
            // Navigate rather than pop: the existing CRM entry's view model
            // still holds the pre-create list.
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
            // Navigate rather than pop: the existing CRM entry's view model
            // still holds the pre-create objects.
            onCreated = { objectId ->
                navController.navigate(CrmsApp.crmObject(crmId, objectId)) {
                    popUpTo(CrmsApp.CREATE_OBJECT) { inclusive = true }
                }
            },
        )
    }

    composable(
        route = CrmsApp.CRM_SETTINGS,
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
                    popUpTo(CrmsApp.CRM) { inclusive = true }
                    launchSingleTop = true
                }
            },
        )
    }

    composable(
        route = CrmsApp.CRM_DESIGN,
        arguments = listOf(navArgument("crmId") { type = NavType.StringType })
    ) { backStackEntry ->
        val crmId = backStackEntry.arguments?.getString("crmId").orEmpty()
        DesignScreen(
            onBack = { navController.popBackStack() },
            onAddClass = { navController.navigate(CrmsApp.crmDesignCreateClass(crmId)) },
            onAddView = { navController.navigate(CrmsApp.crmDesignCreateView(crmId)) },
            onEditView = { viewId ->
                navController.navigate(CrmsApp.crmDesignView(crmId, viewId))
            },
            onClassClick = { classId ->
                navController.navigate(CrmsApp.crmDesignClass(crmId, classId))
            },
        )
    }

    composable(
        route = CrmsApp.CRM_DESIGN_CLASS,
        arguments = listOf(
            navArgument("crmId") { type = NavType.StringType },
            navArgument("classId") { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val crmId = backStackEntry.arguments?.getString("crmId").orEmpty()
        val classId = backStackEntry.arguments?.getString("classId").orEmpty()
        ClassDetailScreen(
            onBack = { navController.popBackStack() },
            onAddField = {
                navController.navigate(CrmsApp.crmDesignCreateField(crmId, classId))
            },
            onFieldClick = { fieldId ->
                navController.navigate(CrmsApp.crmDesignField(crmId, classId, fieldId))
            },
        )
    }

    composable(
        route = CrmsApp.CRM_DESIGN_FIELD,
        arguments = listOf(
            navArgument("crmId") { type = NavType.StringType },
            navArgument("classId") { type = NavType.StringType },
            navArgument("fieldId") { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val crmId = backStackEntry.arguments?.getString("crmId").orEmpty()
        val classId = backStackEntry.arguments?.getString("classId").orEmpty()
        val fieldId = backStackEntry.arguments?.getString("fieldId").orEmpty()
        FieldDetailScreen(
            onBack = { navController.popBackStack() },
            onAddOption = {
                navController.navigate(CrmsApp.crmDesignOption(crmId, classId, fieldId))
            },
            onEditOption = { optionId ->
                navController.navigate(
                    CrmsApp.crmDesignOption(crmId, classId, fieldId, optionId)
                )
            },
        )
    }

    composable(
        route = CrmsApp.CRM_DESIGN_OPTION,
        arguments = listOf(
            navArgument("crmId") { type = NavType.StringType },
            navArgument("classId") { type = NavType.StringType },
            navArgument("fieldId") { type = NavType.StringType },
            navArgument("optionId") {
                type = NavType.StringType
                defaultValue = ""
            }
        )
    ) {
        OptionScreen(
            onBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
        )
    }

    composable(
        route = CrmsApp.CRM_ADD_COLUMN,
        arguments = listOf(
            navArgument("crmId") { type = NavType.StringType },
            navArgument("classId") { type = NavType.StringType },
            navArgument("fieldId") { type = NavType.StringType }
        )
    ) {
        OptionScreen(
            onBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
            isColumn = true,
        )
    }

    composable(
        route = CrmsApp.CRM_DESIGN_CREATE_CLASS,
        arguments = listOf(navArgument("crmId") { type = NavType.StringType })
    ) { backStackEntry ->
        val crmId = backStackEntry.arguments?.getString("crmId").orEmpty()
        CreateClassScreen(
            onBack = { navController.popBackStack() },
            onCreated = { classId ->
                if (classId.isBlank()) {
                    navController.popBackStack()
                } else {
                    navController.navigate(CrmsApp.crmDesignClass(crmId, classId)) {
                        popUpTo(CrmsApp.CRM_DESIGN_CREATE_CLASS) { inclusive = true }
                    }
                }
            },
        )
    }

    composable(
        route = CrmsApp.CRM_DESIGN_VIEW,
        arguments = listOf(
            navArgument("crmId") { type = NavType.StringType },
            navArgument("viewId") { type = NavType.StringType }
        )
    ) {
        EditViewScreen(
            onBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
        )
    }

    composable(
        route = CrmsApp.CRM_DESIGN_CREATE_VIEW,
        arguments = listOf(navArgument("crmId") { type = NavType.StringType })
    ) {
        CreateViewScreen(
            onBack = { navController.popBackStack() },
            onCreated = { navController.popBackStack() },
        )
    }

    composable(
        route = CrmsApp.CRM_DESIGN_CREATE_FIELD,
        arguments = listOf(
            navArgument("crmId") { type = NavType.StringType },
            navArgument("classId") { type = NavType.StringType }
        )
    ) {
        CreateFieldScreen(
            onBack = { navController.popBackStack() },
            onCreated = { navController.popBackStack() },
        )
    }

}
