package org.mochios.mochi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.FormatProvider
import org.mochios.android.i18n.PreferencesManager
import org.mochios.android.push.OemBackgroundHintDialog
import org.mochios.android.push.PushService
import org.mochios.android.push.RequestNotificationPermission
import org.mochios.android.ui.AppBootstrapHost
import org.mochios.android.ui.theme.MochiTheme
import org.mochios.chat.navigation.chatNavGraph
import org.mochios.feeds.navigation.feedsNavGraph
import org.mochios.forums.navigation.forumsNavGraph
import org.mochios.mochi.ui.launchpad.LaunchPadScreen
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PushService.start(this)
        setContent {
            val themeAnchors by sessionManager.themeAnchors.collectAsState(initial = null)
            val userPrefs by preferencesManager.preferences.collectAsState()
            MochiTheme(themeAnchors = themeAnchors, preferences = userPrefs) {
                FormatProvider(manager = preferencesManager) {
                    RequestNotificationPermission()
                    OemBackgroundHintDialog()
                    AppBootstrapHost(
                        appName = "feeds",
                        oauthScheme = null,
                        onLocaleChangeRequested = { recreate() },
                    ) { onLogout ->
                        val navController = rememberNavController()
                        NavHost(navController = navController, startDestination = LAUNCHPAD) {
                            composable(LAUNCHPAD) {
                                LaunchPadScreen(
                                    onAppSelected = { route -> navController.navigate(route) },
                                )
                            }
                            feedsNavGraph(navController, onLogout = onLogout)
                            chatNavGraph(navController, onLogout = onLogout)
                            forumsNavGraph(navController, onLogout = onLogout)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val LAUNCHPAD = "launchpad"
    }
}
