package org.mochios.mochi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.FormatProvider
import org.mochios.android.i18n.PreferencesManager
import org.mochios.android.push.MochiPushClient
import org.mochios.android.push.OemBackgroundHintDialog
import org.mochios.android.push.PendingDeepLink
import org.mochios.android.push.PushService
import org.mochios.android.push.RequestNotificationPermission
import org.mochios.android.ui.AppBootstrapHost
import org.mochios.android.ui.theme.MochiTheme
import org.mochios.chat.navigation.ChatApp
import org.mochios.chat.navigation.chatNavGraph
import org.mochios.feeds.navigation.FeedsApp
import org.mochios.feeds.navigation.feedsNavGraph
import org.mochios.forums.navigation.ForumsApp
import org.mochios.forums.navigation.forumsNavGraph
import org.mochios.mochi.ui.launchpad.LaunchPadScreen
import org.mochios.projects.navigation.ProjectsApp
import org.mochios.projects.navigation.projectsNavGraph
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PushService.start(this)
        handleNotificationIntent(intent)
        setContent {
            val themeAnchors by sessionManager.themeAnchors.collectAsState(initial = null)
            val identity by sessionManager.boundIdentity.collectAsState(initial = null)
            val userPrefs by preferencesManager.preferences.collectAsState()
            MochiTheme(themeAnchors = themeAnchors, preferences = userPrefs) {
                FormatProvider(manager = preferencesManager) {
                    RequestNotificationPermission()
                    OemBackgroundHintDialog()
                    LaunchedEffect(identity) {
                        identity?.let { MochiPushClient.register(applicationContext, it) }
                    }
                    AppBootstrapHost(
                        appName = "feeds",
                        oauthScheme = null,
                        onLocaleChangeRequested = { recreate() },
                    ) { onLogout ->
                        val navController = rememberNavController()
                        val pendingLink by PendingDeepLink.link.collectAsState()
                        LaunchedEffect(pendingLink) {
                            val link = pendingLink ?: return@LaunchedEffect
                            navigateToLink(navController, link)
                            PendingDeepLink.consume()
                        }
                        NavHost(navController = navController, startDestination = LAUNCHPAD) {
                            composable(LAUNCHPAD) {
                                LaunchPadScreen(
                                    onAppSelected = { route -> navController.navigate(route) },
                                )
                            }
                            feedsNavGraph(navController, onLogout = onLogout)
                            chatNavGraph(navController, onLogout = onLogout)
                            forumsNavGraph(navController, onLogout = onLogout)
                            projectsNavGraph(navController, onLogout = onLogout)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "mochi" || data.host != "notification") return
        val link = data.getQueryParameter("link") ?: return
        PendingDeepLink.set(link)
    }

    private fun navigateToLink(navController: NavController, link: String) {
        val parts = link.trimStart('/').split('/')
        val firstSegment = parts.firstOrNull()?.lowercase() ?: return
        val id = parts.getOrNull(1)
        when (firstSegment) {
            "feeds" -> {
                navController.navigate(FeedsApp.HOME) { launchSingleTop = true }
                if (id != null) navController.navigate(FeedsApp.feed(id)) { launchSingleTop = true }
            }
            "chat" -> {
                navController.navigate(ChatApp.HOME) { launchSingleTop = true }
                if (id != null) navController.navigate(ChatApp.chat(id)) { launchSingleTop = true }
            }
            "forums" -> {
                navController.navigate(ForumsApp.HOME) { launchSingleTop = true }
                if (id != null) navController.navigate(ForumsApp.forum(id)) { launchSingleTop = true }
            }
            "projects" -> {
                navController.navigate(ProjectsApp.HOME) { launchSingleTop = true }
                if (id != null) navController.navigate(ProjectsApp.project(id)) { launchSingleTop = true }
            }
        }
    }

    companion object {
        private const val LAUNCHPAD = "launchpad"
    }
}
