package org.mochios.mochi

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import kotlinx.coroutines.runBlocking
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
        handleMochiUri(intent)
        val startApp = resolveAliasTargetApp(intent?.component)
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
                        appName = startApp ?: "feeds",
                        oauthScheme = "mochi",
                        onLocaleChangeRequested = { recreate() },
                    ) { onLogout ->
                        val navController = rememberNavController()
                        val pendingLink by PendingDeepLink.link.collectAsState()
                        LaunchedEffect(pendingLink) {
                            val link = pendingLink ?: return@LaunchedEffect
                            navigateToLink(navController, link)
                            PendingDeepLink.consume()
                        }
                        NavHost(navController = navController, startDestination = startDestinationFor(startApp)) {
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
        handleMochiUri(intent)
        // Warm-start from a different alias (singleTask brings the existing
        // task to the front instead of recreating). Drop a synthetic /<app>
        // link into PendingDeepLink so the running NavHost re-routes.
        val targetApp = resolveAliasTargetApp(intent.component)
        if (targetApp != null) PendingDeepLink.set("/$targetApp")
    }

    /**
     * Dispatcher for the three [mochi: URI scheme][claude/plans/mochi-uri-scheme.md] shapes:
     *
     *  - `mochi:<intent>?<query>`                — 0 slashes, system intent
     *  - `mochi:/<entity>[/<sub>...]`            — 1 slash, entity in current session
     *  - `mochi://<peer>/<entity>[/<sub>...]`    — 2 slashes, entity on a libp2p peer
     *
     * Also tolerates the legacy hierarchical-with-authority shape for system
     * intents (`mochi://notification?...` / `mochi://oauth-return?...`) that
     * older OAuth-return server builds + older shortcut intents may still emit.
     */
    private fun handleMochiUri(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "mochi") return
        when {
            !uri.isHierarchical -> handleSystemIntent(uri)
            uri.authority.isNullOrEmpty() -> handleEntityIntent(intent, uri)
            uri.authority in LEGACY_SYSTEM_INTENT_AUTHORITIES -> handleLegacySystemIntent(uri)
            else -> handleCrossPeerEntityIntent(intent, uri)
        }
    }

    /** mochi:<intent>?<query> — opaque URI; parse the SSP manually. */
    private fun handleSystemIntent(uri: Uri) {
        val ssp = uri.schemeSpecificPart
        val name = ssp.substringBefore('?')
        when (name) {
            "notification" -> setNotificationDeepLink(uri.getQueryParameter("link"))
            "oauth-return" -> applyOAuthReturn(uri.getQueryParameter("code"), uri.getQueryParameter("error"))
            else -> Log.w(TAG, "Unknown system intent in $uri")
        }
    }

    /**
     * Legacy `mochi://notification?...` / `mochi://oauth-return?...` shapes.
     * Kept so OAuth callbacks issued by older server builds + shortcuts
     * created by earlier app versions keep working through the manifest's
     * new broad filter.
     */
    private fun handleLegacySystemIntent(uri: Uri) {
        when (uri.authority) {
            "notification" -> setNotificationDeepLink(uri.getQueryParameter("link"))
            "oauth-return" -> applyOAuthReturn(uri.getQueryParameter("code"), uri.getQueryParameter("error"))
        }
    }

    /**
     * mochi:/<entity>[/<sub>...] — entity in the current session.
     *
     * When the caller provides an `app` hint via Intent extras (e.g. the per-app
     * "Add to home screen" shortcut intents), use it to short-circuit the
     * entity → app lookup. Without a hint we'd need to query the current
     * server's directory to figure out which app owns the entity — left as a
     * follow-up; external entity URIs without a hint currently no-op.
     */
    private fun handleEntityIntent(intent: Intent, uri: Uri) {
        val segments = uri.pathSegments
        val entity = segments.firstOrNull() ?: return
        val sub = segments.drop(1)
        val app = intent.getStringExtra(EXTRA_APP_HINT)
        if (app != null) {
            val link = buildString {
                append('/').append(app).append('/').append(entity)
                for (s in sub) append('/').append(s)
            }
            PendingDeepLink.set(link)
        } else {
            Log.w(TAG, "Entity URI without app hint: $uri (directory lookup not yet implemented)")
        }
    }

    /**
     * mochi://<peer>/<entity>[/<sub>...] — entity on a specific libp2p peer.
     * Cross-peer routing isn't implemented yet — scheme slot reserved for the
     * eventual "share an entity from server A with a user on server B" flow.
     */
    private fun handleCrossPeerEntityIntent(intent: Intent, uri: Uri) {
        Log.w(TAG, "Cross-peer URI not yet supported: $uri")
    }

    private fun setNotificationDeepLink(link: String?) {
        link ?: return
        PendingDeepLink.set(link)
    }

    private fun applyOAuthReturn(code: String?, error: String?) {
        if (code == null && error == null) return
        runBlocking { sessionManager.setOAuthReturn(code, error) }
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

    private fun resolveAliasTargetApp(component: ComponentName?): String? {
        component ?: return null
        return try {
            val info = packageManager.getActivityInfo(component, PackageManager.GET_META_DATA)
            info.metaData?.getString(META_TARGET_APP)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun startDestinationFor(targetApp: String?): String = when (targetApp) {
        "feeds" -> FeedsApp.HOME
        "chat" -> ChatApp.HOME
        "forums" -> ForumsApp.HOME
        "projects" -> ProjectsApp.HOME
        else -> LAUNCHPAD
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val LAUNCHPAD = "launchpad"
        private const val META_TARGET_APP = "org.mochios.targetApp"

        /** Intent extra a per-app `XxxListScreen.kt` shortcut sets to skip directory lookup. */
        const val EXTRA_APP_HINT = "app"

        private val LEGACY_SYSTEM_INTENT_AUTHORITIES = setOf("notification", "oauth-return")
    }
}
