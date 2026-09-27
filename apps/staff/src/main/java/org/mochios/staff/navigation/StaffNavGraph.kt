// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.mochios.android.ui.components.FilterButton
import org.mochios.android.ui.components.MochiFab
import org.mochios.android.ui.components.NotificationBell
import org.mochios.staff.R
import org.mochios.staff.ui.accounts.AccountsScreen
import org.mochios.staff.ui.accounts.AccountsViewModel
import org.mochios.staff.ui.appeals.AppealsScreen
import org.mochios.staff.ui.categories.CategoriesScreen
import org.mochios.staff.ui.categories.CategoriesViewModel
import org.mochios.staff.ui.categories.CategoryFormScreen
import org.mochios.staff.ui.components.LocalStaffMe
import org.mochios.staff.ui.components.StaffLayout
import org.mochios.staff.ui.config.ConfigScreen
import org.mochios.staff.ui.dashboard.DashboardScreen
import org.mochios.staff.ui.disputes.DisputesScreen
import org.mochios.staff.ui.listings.ListingsScreen
import org.mochios.staff.ui.listings.ListingsViewModel
import org.mochios.staff.ui.moderation.ModerationLogScreen
import org.mochios.staff.ui.reports.ReportsScreen
import org.mochios.staff.ui.reports.ReportsViewModel
import org.mochios.staff.ui.reviews.ReviewsScreen
import org.mochios.staff.ui.team.AddTeamMemberScreen
import org.mochios.staff.ui.team.TeamScreen
import org.mochios.staff.ui.team.TeamViewModel

/**
 * Staff routes; all class-level (no entity scope) and assume a signed-in staff
 * role.
 */
object StaffApp {
    const val HOME = "staff"
    const val ACCOUNTS = "staff/accounts"
    const val LISTINGS = "staff/listings"
    const val MODERATION = "staff/moderation"
    const val REPORTS = "staff/reports"
    const val DISPUTES = "staff/disputes"
    const val APPEALS = "staff/appeals"
    const val REVIEWS = "staff/reviews"
    const val CATEGORIES = "staff/categories"
    const val CATEGORY_NEW = "staff/categories/new"
    const val CATEGORY_EDIT = "staff/categories/edit/{id}"
    const val CONFIG = "staff/config"
    const val TEAM = "staff/team"
    const val TEAM_ADD = "staff/team/add"

    /** Route of the form that edits the category [id]. */
    fun categoryEdit(id: String) = "staff/categories/edit/${Uri.encode(id)}"
}

/**
 * Keys a form screen sets on the list below it before popping back, so the
 * list reloads only when something changed: a save, or a category to edit
 * that no longer exists.
 */
private const val CATEGORY_SAVED = "category_saved"
private const val TEAM_MEMBER_ADDED = "team_member_added"

fun NavGraphBuilder.staffNavGraph(
    navController: NavController,
    onOpenNotifications: () -> Unit = {},
) {
    composable(StaffApp.HOME) {
        StaffLayout(
            navController,
            StaffApp.HOME,
            R.string.staff_sidebar_dashboard,
            topBarActions = { NotificationBell(onClick = onOpenNotifications) },
        ) {
            DashboardScreen(navController = navController)
        }
    }
    composable(StaffApp.ACCOUNTS) {
        val viewModel: AccountsViewModel = hiltViewModel()
        val state by viewModel.state.collectAsState()
        var filtersOpen by rememberSaveable { mutableStateOf(false) }
        StaffLayout(
            navController = navController,
            currentRoute = StaffApp.ACCOUNTS,
            titleRes = R.string.staff_sidebar_accounts,
            topBarActions = {
                FilterButton(
                    active = listOfNotNull(state.status, state.seller).isNotEmpty(),
                    onClick = { filtersOpen = true },
                )
            },
        ) {
            AccountsScreen(
                navController = navController,
                viewModel = viewModel,
                filtersOpen = filtersOpen,
                onFiltersDismiss = { filtersOpen = false },
            )
        }
    }
    composable(StaffApp.LISTINGS) {
        val viewModel: ListingsViewModel = hiltViewModel()
        val state by viewModel.state.collectAsState()
        var filtersOpen by rememberSaveable { mutableStateOf(false) }
        StaffLayout(
            navController = navController,
            currentRoute = StaffApp.LISTINGS,
            titleRes = R.string.staff_sidebar_listings,
            topBarActions = {
                FilterButton(
                    active = listOfNotNull(state.status, state.moderation).isNotEmpty(),
                    onClick = { filtersOpen = true },
                )
            },
        ) {
            ListingsScreen(
                navController = navController,
                viewModel = viewModel,
                filtersOpen = filtersOpen,
                onFiltersDismiss = { filtersOpen = false },
            )
        }
    }
    composable(StaffApp.MODERATION) {
        StaffLayout(navController, StaffApp.MODERATION, R.string.staff_sidebar_moderation) {
            ModerationLogScreen(navController = navController)
        }
    }
    composable(StaffApp.REPORTS) {
        val viewModel: ReportsViewModel = hiltViewModel()
        val state by viewModel.state.collectAsState()
        var filtersOpen by rememberSaveable { mutableStateOf(false) }
        StaffLayout(
            navController = navController,
            currentRoute = StaffApp.REPORTS,
            titleRes = R.string.staff_sidebar_reports,
            topBarActions = {
                FilterButton(
                    active = listOfNotNull(state.type, state.status).isNotEmpty(),
                    onClick = { filtersOpen = true },
                )
            },
        ) {
            ReportsScreen(
                navController = navController,
                viewModel = viewModel,
                filtersOpen = filtersOpen,
                onFiltersDismiss = { filtersOpen = false },
            )
        }
    }
    composable(StaffApp.DISPUTES) {
        StaffLayout(navController, StaffApp.DISPUTES, R.string.staff_sidebar_disputes) {
            DisputesScreen(navController = navController)
        }
    }
    composable(StaffApp.APPEALS) {
        StaffLayout(navController, StaffApp.APPEALS, R.string.staff_sidebar_appeals) {
            AppealsScreen(navController = navController)
        }
    }
    composable(StaffApp.REVIEWS) {
        StaffLayout(navController, StaffApp.REVIEWS, R.string.staff_sidebar_reviews) {
            ReviewsScreen(navController = navController)
        }
    }
    // Mount the VM at the route so the screen body and the form's result
    // share one instance.
    composable(StaffApp.CATEGORIES) { entry ->
        val viewModel: CategoriesViewModel = hiltViewModel()
        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(entry) {
            entry.savedStateHandle.getStateFlow<Int?>(CATEGORY_SAVED, null).collect { message ->
                if (message != null) {
                    entry.savedStateHandle.remove<Int>(CATEGORY_SAVED)
                    viewModel.onSaved(message)
                }
            }
        }
        StaffLayout(
            navController = navController,
            currentRoute = StaffApp.CATEGORIES,
            titleRes = R.string.staff_sidebar_categories,
            snackbarHostState = snackbarHostState,
            floatingActionButton = {
                MochiFab(onClick = { navController.navigate(StaffApp.CATEGORY_NEW) }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.staff_categories_add),
                    )
                }
            },
        ) {
            CategoriesScreen(
                navController = navController,
                snackbarHostState = snackbarHostState,
                viewModel = viewModel,
            )
        }
    }
    composable(StaffApp.CATEGORY_NEW) {
        CategoryFormRoute(navController)
    }
    composable(
        route = StaffApp.CATEGORY_EDIT,
        arguments = listOf(navArgument("id") { type = NavType.StringType }),
    ) {
        CategoryFormRoute(navController)
    }
    // Admin gate at route level (web's `beforeLoad` redirect): non-admins are
    // sent to the dashboard. Stays inside StaffLayout because `LocalStaffMe` is
    // only provided there.
    composable(StaffApp.CONFIG) {
        StaffLayout(navController, StaffApp.CONFIG, R.string.staff_sidebar_config) {
            val me = LocalStaffMe.current
            when {
                me == null -> {
                    // Still loading — StaffLayout shows its loading state
                    // until `me` resolves, so the content slot is unreached
                    // in practice. Render nothing defensively.
                }
                me.role != "admin" -> {
                    LaunchedEffect(me) {
                        navController.navigate(StaffApp.HOME) {
                            popUpTo(StaffApp.CONFIG) { inclusive = true }
                        }
                    }
                }
                else -> ConfigScreen(navController = navController)
            }
        }
    }
    // Team needs an admin-only "Add member" FAB, gated on
    // LocalStaffMe.current.role.
    composable(StaffApp.TEAM) { entry ->
        val viewModel: TeamViewModel = hiltViewModel()
        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(entry) {
            entry.savedStateHandle.getStateFlow(TEAM_MEMBER_ADDED, false).collect { added ->
                if (added) {
                    entry.savedStateHandle.remove<Boolean>(TEAM_MEMBER_ADDED)
                    viewModel.onAdded()
                }
            }
        }
        StaffLayout(
            navController = navController,
            currentRoute = StaffApp.TEAM,
            titleRes = R.string.staff_sidebar_team,
            snackbarHostState = snackbarHostState,
            floatingActionButton = {
                val isAdmin = LocalStaffMe.current?.role == "admin"
                if (isAdmin) {
                    MochiFab(onClick = { navController.navigate(StaffApp.TEAM_ADD) }) {
                        Icon(
                            Icons.Default.PersonAdd,
                            contentDescription = stringResource(R.string.staff_team_add_member),
                        )
                    }
                }
            },
        ) {
            TeamScreen(
                navController = navController,
                snackbarHostState = snackbarHostState,
                viewModel = viewModel,
            )
        }
    }

    composable(StaffApp.TEAM_ADD) {
        AddTeamMemberScreen(
            onBack = { navController.popBackStack() },
            onAdded = {
                navController.previousBackStackEntry?.savedStateHandle?.set(TEAM_MEMBER_ADDED, true)
                navController.popBackStack()
            },
        )
    }
}

@Composable
private fun CategoryFormRoute(navController: NavController) {
    val listEntry = remember(navController) {
        runCatching { navController.getBackStackEntry(StaffApp.CATEGORIES) }.getOrNull()
    }
    val knownCategories = listEntry
        ?.let { entry -> hiltViewModel<CategoriesViewModel>(entry).state.value.categories }
        .orEmpty()
    CategoryFormScreen(
        knownCategories = knownCategories,
        onBack = { navController.popBackStack() },
        onDone = { message ->
            navController.previousBackStackEntry?.savedStateHandle?.set(CATEGORY_SAVED, message)
            navController.popBackStack()
        },
    )
}
