package org.mochios.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import org.mochios.settings.ui.home.SettingsHomeScreen
import org.mochios.settings.ui.notifications.NotificationsScreen
import org.mochios.settings.ui.preferences.UserSettingsScreen
import org.mochios.settings.ui.profile.ProfileScreen
import org.mochios.settings.ui.security.SecurityScreen

object SettingsApp {
    const val HOME = "settings/home"
    const val PROFILE = "settings/profile"
    const val PREFERENCES = "settings/preferences"
    const val NOTIFICATIONS = "settings/notifications"
    const val SECURITY = "settings/security"
}

fun NavGraphBuilder.settingsNavGraph(
    navController: NavController,
    onLogout: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    composable(SettingsApp.HOME) {
        SettingsHomeScreen(
            onBack = { navController.popBackStack() },
            onOpenProfile = { navController.navigate(SettingsApp.PROFILE) },
            onOpenPreferences = { navController.navigate(SettingsApp.PREFERENCES) },
            onOpenNotifications = { navController.navigate(SettingsApp.NOTIFICATIONS) },
            onOpenSecurity = { navController.navigate(SettingsApp.SECURITY) },
            onLogout = onLogout,
        )
    }
    composable(SettingsApp.SECURITY) {
        SecurityScreen(onBack = { navController.popBackStack() })
    }
    composable(SettingsApp.PROFILE) {
        ProfileScreen(onBack = { navController.popBackStack() })
    }
    composable(SettingsApp.PREFERENCES) {
        UserSettingsScreen(onBack = { navController.popBackStack() })
    }
    composable(SettingsApp.NOTIFICATIONS) {
        NotificationsScreen(
            onBack = { navController.popBackStack() },
            onOpenLink = onOpenLink,
        )
    }
}
