// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import org.mochios.staff.R
import org.mochios.staff.ui.accounts.AccountsScreen
import org.mochios.staff.ui.appeals.AppealsScreen
import org.mochios.staff.ui.categories.CategoriesScreen
import org.mochios.staff.ui.categories.CategoriesViewModel
import org.mochios.staff.ui.components.LocalStaffMe
import org.mochios.staff.ui.components.StaffLayout
import org.mochios.staff.ui.config.ConfigScreen
import org.mochios.staff.ui.dashboard.DashboardScreen
import org.mochios.staff.ui.disputes.DisputesScreen
import org.mochios.staff.ui.listings.ListingsScreen
import org.mochios.staff.ui.moderation.ModerationLogScreen
import org.mochios.staff.ui.reports.ReportsScreen
import org.mochios.staff.ui.reviews.ReviewsScreen
import org.mochios.staff.ui.team.TeamScreen
import org.mochios.staff.ui.team.TeamViewModel

/**
 * Route constants and helpers for the staff Android module.
 *
 * Staff is the operator / moderator console. It backs onto the Comptroller
 * marketplace backend via the `apps/staff/` Starlark proxy and is gated by
 * `me.role` (admin / moderator / support). Routes are class-level — staff
 * surfaces are global, not entity-scoped.
 *
 * Every route assumes a signed-in session with a role of admin, moderator, or
 * support; the sidebar's "Configuration" entry is admin-only.
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
    const val CONFIG = "staff/config"
    const val TEAM = "staff/team"
}

/**
 * Registers every staff route on the given [NavGraphBuilder]. Each route
 * wraps its screen body in [StaffLayout] so the drawer, topbar, role-aware
 * sidebar, and staff-events WebSocket subscription are mounted once per
 * navigation entry. Detail screens pull arguments via SavedStateHandle in
 * their ViewModels, so the composable bodies only forward the NavController.
 */
fun NavGraphBuilder.staffNavGraph(navController: NavController) {
    composable(StaffApp.HOME) {
        StaffRoute(navController, StaffApp.HOME, R.string.staff_sidebar_dashboard) {
            DashboardScreen(navController = navController)
        }
    }
    composable(StaffApp.ACCOUNTS) {
        StaffRoute(navController, StaffApp.ACCOUNTS, R.string.staff_sidebar_accounts) {
            AccountsScreen(navController = navController)
        }
    }
    composable(StaffApp.LISTINGS) {
        StaffRoute(navController, StaffApp.LISTINGS, R.string.staff_sidebar_listings) {
            ListingsScreen(navController = navController)
        }
    }
    composable(StaffApp.MODERATION) {
        StaffRoute(navController, StaffApp.MODERATION, R.string.staff_sidebar_moderation) {
            ModerationLogScreen(navController = navController)
        }
    }
    composable(StaffApp.REPORTS) {
        StaffRoute(navController, StaffApp.REPORTS, R.string.staff_sidebar_reports) {
            ReportsScreen(navController = navController)
        }
    }
    composable(StaffApp.DISPUTES) {
        StaffRoute(navController, StaffApp.DISPUTES, R.string.staff_sidebar_disputes) {
            DisputesScreen(navController = navController)
        }
    }
    composable(StaffApp.APPEALS) {
        StaffRoute(navController, StaffApp.APPEALS, R.string.staff_sidebar_appeals) {
            AppealsScreen(navController = navController)
        }
    }
    composable(StaffApp.REVIEWS) {
        StaffRoute(navController, StaffApp.REVIEWS, R.string.staff_sidebar_reviews) {
            ReviewsScreen(navController = navController)
        }
    }
    // Categories needs a topbar "Add" action; mount the VM at the route so
    // the screen body and the topbar share one instance.
    composable(StaffApp.CATEGORIES) {
        val viewModel: CategoriesViewModel = hiltViewModel()
        StaffLayout(
            navController = navController,
            currentRoute = StaffApp.CATEGORIES,
            title = stringResource(R.string.staff_sidebar_categories),
            topBarActions = {
                Button(
                    onClick = { viewModel.openCreate() },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.staff_categories_add))
                }
            },
        ) {
            CategoriesScreen(navController = navController, viewModel = viewModel)
        }
    }
    // Config is admin-only. The gate lives at the route level (mirroring
    // web's `beforeLoad` redirect on `/_authenticated/config`): a non-admin
    // landing here is silently sent to the dashboard, never seeing the
    // ConfigScreen body. We still wrap in StaffLayout so the gate can read
    // `LocalStaffMe` (which is only provided inside the layout), and so the
    // user still sees the staff topbar/sidebar/loading state while `me`
    // resolves.
    composable(StaffApp.CONFIG) {
        StaffRoute(navController, StaffApp.CONFIG, R.string.staff_sidebar_config) {
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
    // Team needs an admin-only "Add member" topbar action; mount the VM
    // at the route and gate the button on LocalStaffMe.current.role.
    composable(StaffApp.TEAM) {
        val viewModel: TeamViewModel = hiltViewModel()
        StaffLayout(
            navController = navController,
            currentRoute = StaffApp.TEAM,
            title = stringResource(R.string.staff_sidebar_team),
            topBarActions = {
                val isAdmin = LocalStaffMe.current?.role == "admin"
                if (isAdmin) {
                    Button(
                        onClick = { viewModel.openAddDialog() },
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Icon(
                            Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        Text(stringResource(R.string.staff_team_add_member))
                    }
                }
            },
        ) {
            TeamScreen(navController = navController, viewModel = viewModel)
        }
    }
}

/**
 * Helper that resolves the title string resource against the current
 * locale and forwards everything to [StaffLayout]. Screens that need to
 * inject top-bar action buttons (e.g. Team's "Add member" or Categories'
 * "Add") still need bespoke wiring — but most just need the boilerplate
 * absorbed here.
 */
@Composable
private fun StaffRoute(
    navController: NavController,
    currentRoute: String,
    @StringRes titleRes: Int,
    content: @Composable () -> Unit,
) {
    StaffLayout(
        navController = navController,
        currentRoute = currentRoute,
        title = stringResource(titleRes),
        content = content,
    )
}
