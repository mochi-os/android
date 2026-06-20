// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.navigation

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import org.mochios.settings.ui.account.AccountScreen
import org.mochios.settings.ui.accounts.ConnectedAccountsScreen
import org.mochios.settings.ui.display.DisplayScreen
import org.mochios.settings.ui.document.DocumentScreen
import org.mochios.settings.ui.domains.DomainsScreen
import org.mochios.settings.ui.home.SettingsHomeScreen
import org.mochios.settings.ui.interests.InterestsScreen
import org.mochios.settings.ui.login.LoginScreen
import org.mochios.settings.ui.notificationprefs.NotificationPrefsScreen
import org.mochios.settings.ui.notifications.NotificationsScreen
import org.mochios.settings.ui.preferences.UserSettingsScreen
import org.mochios.settings.ui.replication.ReplicationScreen
import org.mochios.settings.ui.sessions.SessionsScreen
import org.mochios.settings.ui.tokens.TokensScreen
import org.mochios.settings.ui.systemdocuments.SystemDocumentsScreen
import org.mochios.settings.ui.systemreplication.SystemReplicationScreen
import org.mochios.settings.ui.systemsettings.SystemSettingsScreen
import org.mochios.settings.ui.systemstatus.SystemStatusScreen
import org.mochios.settings.ui.systemusers.SystemUsersScreen

object SettingsApp {
    const val HOME = "settings/home"
    const val ACCOUNT = "settings/account"
    const val LOGIN = "settings/login"
    const val PREFERENCES = "settings/preferences"
    const val DISPLAY = "settings/display"
    const val NOTIFICATIONS = "settings/notifications"
    const val NOTIFICATION_PREFS = "settings/notification-prefs"
    const val SESSIONS = "settings/sessions"
    const val TOKENS = "settings/tokens"
    const val REPLICATION = "settings/replication"
    const val SYSTEM_REPLICATION = "settings/system/replication"
    const val SYSTEM_SETTINGS = "settings/system/settings"
    const val SYSTEM_STATUS = "settings/system/status"
    const val SYSTEM_USERS = "settings/system/users"
    const val INTERESTS = "settings/interests"
    const val ACCOUNTS = "settings/accounts"
    const val DOMAINS = "settings/domains"
    const val DOCUMENT = "settings/document/{kind}"
    const val SYSTEM_DOCUMENTS = "settings/system/documents"

    fun document(kind: String) = "settings/document/$kind"
}

fun NavGraphBuilder.settingsNavGraph(
    navController: NavController,
    onLogout: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    composable(SettingsApp.HOME) {
        val homeViewModel: org.mochios.settings.ui.home.SettingsHomeViewModel = hiltViewModel()
        val homeState by homeViewModel.state.collectAsState()
        SettingsHomeScreen(
            isAdmin = homeState.isAdmin,
            hasDomainAccess = homeState.hasDomainAccess,
            onOpenAccount = { navController.navigate(SettingsApp.ACCOUNT) },
            onOpenLogin = { navController.navigate(SettingsApp.LOGIN) },
            onOpenPreferences = { navController.navigate(SettingsApp.PREFERENCES) },
            onOpenDisplay = { navController.navigate(SettingsApp.DISPLAY) },
            onOpenNotifications = { navController.navigate(SettingsApp.NOTIFICATIONS) },
            onOpenNotificationPrefs = { navController.navigate(SettingsApp.NOTIFICATION_PREFS) },
            onOpenSessions = { navController.navigate(SettingsApp.SESSIONS) },
            onOpenTokens = { navController.navigate(SettingsApp.TOKENS) },
            onOpenReplication = { navController.navigate(SettingsApp.REPLICATION) },
            onOpenSystemReplication = { navController.navigate(SettingsApp.SYSTEM_REPLICATION) },
            onOpenSystemSettings = { navController.navigate(SettingsApp.SYSTEM_SETTINGS) },
            onOpenSystemStatus = { navController.navigate(SettingsApp.SYSTEM_STATUS) },
            onOpenSystemUsers = { navController.navigate(SettingsApp.SYSTEM_USERS) },
            onOpenInterests = { navController.navigate(SettingsApp.INTERESTS) },
            onOpenAccounts = { navController.navigate(SettingsApp.ACCOUNTS) },
            onOpenDomains = { navController.navigate(SettingsApp.DOMAINS) },
            onOpenDocument = { kind -> navController.navigate(SettingsApp.document(kind)) },
            onOpenSystemDocuments = { navController.navigate(SettingsApp.SYSTEM_DOCUMENTS) },
            onLogout = onLogout,
        )
    }
    composable(SettingsApp.ACCOUNT) {
        AccountScreen(onBack = { navController.popBackStack() }, onClosed = onLogout)
    }
    composable(SettingsApp.LOGIN) {
        LoginScreen(onBack = { navController.popBackStack() })
    }
    composable(SettingsApp.SESSIONS) {
        SessionsScreen(onBack = { navController.popBackStack() })
    }
    composable(SettingsApp.TOKENS) {
        TokensScreen(onBack = { navController.popBackStack() })
    }
    composable(SettingsApp.PREFERENCES) {
        UserSettingsScreen(onBack = { navController.popBackStack() })
    }
    composable(SettingsApp.DISPLAY) {
        DisplayScreen(onBack = { navController.popBackStack() })
    }
    composable(SettingsApp.NOTIFICATIONS) {
        NotificationsScreen(
            onBack = { navController.popBackStack() },
            onOpenLink = onOpenLink,
        )
    }
    composable(SettingsApp.NOTIFICATION_PREFS) {
        NotificationPrefsScreen(onBack = { navController.popBackStack() })
    }
    composable(SettingsApp.REPLICATION) {
        ReplicationScreen(onBack = { navController.popBackStack() }, onLeft = onLogout)
    }
    composable(SettingsApp.SYSTEM_REPLICATION) {
        SystemReplicationScreen(onBack = { navController.popBackStack() })
    }
    composable(SettingsApp.SYSTEM_SETTINGS) {
        SystemSettingsScreen(onBack = { navController.popBackStack() })
    }
    composable(SettingsApp.SYSTEM_STATUS) {
        SystemStatusScreen(onBack = { navController.popBackStack() })
    }
    composable(SettingsApp.SYSTEM_USERS) {
        SystemUsersScreen(onBack = { navController.popBackStack() })
    }
    composable(SettingsApp.INTERESTS) {
        InterestsScreen(onBack = { navController.popBackStack() })
    }
    composable(SettingsApp.ACCOUNTS) {
        ConnectedAccountsScreen(onBack = { navController.popBackStack() })
    }
    composable(SettingsApp.DOMAINS) {
        DomainsScreen(onBack = { navController.popBackStack() })
    }
    composable(
        SettingsApp.DOCUMENT,
        arguments = listOf(navArgument("kind") { type = NavType.StringType }),
    ) { entry ->
        DocumentScreen(
            kind = entry.arguments?.getString("kind").orEmpty(),
            onBack = { navController.popBackStack() },
        )
    }
    composable(SettingsApp.SYSTEM_DOCUMENTS) {
        SystemDocumentsScreen(onBack = { navController.popBackStack() })
    }
}
