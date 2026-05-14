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
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.FormatProvider
import org.mochios.android.i18n.PreferencesManager
import org.mochios.android.push.OemBackgroundHintDialog
import org.mochios.android.push.PendingDeepLink
import org.mochios.android.push.PushTransport
import org.mochios.android.push.RequestNotificationPermission
import org.mochios.android.ui.AppBootstrapHost
import org.mochios.android.ui.theme.MochiTheme
import org.mochios.android.update.UpdateInstaller
import org.mochios.chat.navigation.ChatApp
import org.mochios.chat.navigation.chatNavGraph
import org.mochios.feeds.navigation.FeedsApp
import org.mochios.feeds.navigation.feedsNavGraph
import org.mochios.forums.navigation.ForumsApp
import org.mochios.forums.navigation.forumsNavGraph
import org.mochios.projects.navigation.ProjectsApp
import org.mochios.projects.navigation.projectsNavGraph
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var okHttpClient: okhttp3.OkHttpClient

    // Latest alias / shortcut hint resolved from the launching intent.
    // Updated on every onNewIntent so swapping launcher icons (e.g.
    // Projects → Feeds) re-keys the NavHost below and lands the user
    // directly on the new feature's home, rather than flashing whatever
    // screen was on top of the previous feature's back stack. Compose's
    // mutableStateOf (vs a Flow) makes the write Snapshot-tracked so the
    // recomposition is scheduled inside the same Choreographer frame as
    // the onResume call — no extra coroutine dispatch delay before the
    // surface is repainted, which is what was leaving the old feature
    // visible for a frame.
    private var targetApp by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // No eager PushService.start here — push transport is server-driven.
        // After the user authenticates, the LaunchedEffect on `identity`
        // below calls PushTransport.configure() which either:
        //   - if the server has Firebase config: initialises FCM and skips
        //     PushService (no "listening for notifications" status notif)
        //   - else: starts PushService for the UnifiedPush fallback
        handleMochiUri(intent)
        targetApp = resolveTargetApp(intent)
        setContent {
            val themeAnchors by sessionManager.themeAnchors.collectAsState(initial = null)
            val isAuthenticated by sessionManager.isAuthenticated.collectAsState(initial = false)
            val userPrefs by preferencesManager.preferences.collectAsState()
            MochiTheme(themeAnchors = themeAnchors, preferences = userPrefs) {
                FormatProvider(manager = preferencesManager) {
                    RequestNotificationPermission()
                    OemBackgroundHintDialog()
                    LaunchedEffect(isAuthenticated) {
                        Log.i(TAG, "LaunchedEffect(isAuthenticated)=$isAuthenticated")
                        if (isAuthenticated) {
                            Log.i(TAG, "PushTransport.configure starting")
                            PushTransport.configure(applicationContext, sessionManager, okHttpClient)
                            Log.i(TAG, "PushTransport.configure returned")
                        }
                    }
                    val startApp = targetApp
                    AppBootstrapHost(
                        appName = startApp ?: "feeds",
                        oauthScheme = "mochi",
                        onLocaleChangeRequested = { recreate() },
                        prefetchApps = SUPER_APP_MOCHI_APPS,
                    ) { onLogout ->
                        // Re-key the NavHost when the alias / shortcut hint
                        // changes — swapping Projects → Feeds via the launcher
                        // (singleTask → onNewIntent) updates targetApp; without
                        // this `key()` the previous feature's back-stack top
                        // composes for one frame before LaunchedEffect can
                        // navigate, surfacing as a half-second flash of the
                        // wrong screen.
                        key(startApp) {
                            val navController = rememberNavController()
                            val pendingLink by PendingDeepLink.link.collectAsState()
                            LaunchedEffect(pendingLink) {
                                val link = pendingLink ?: return@LaunchedEffect
                                navigateToLink(navController, link)
                                PendingDeepLink.consume()
                            }
                            NavHost(navController = navController, startDestination = startDestinationFor(startApp)) {
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleMochiUri(intent)
        val resolved = resolveTargetApp(intent)
        if (resolved != null) targetApp = resolved
    }

    /**
     * The alias's `org.mochios.targetApp` meta-data (set on the per-feature
     * activity-aliases) is the primary signal. For shortcut intents the
     * component is `MainActivity` itself — no meta-data — so fall back to
     * the `EXTRA_APP_HINT` set by the per-feature "Add to home screen" path.
     */
    private fun resolveTargetApp(intent: Intent?): String? =
        resolveAliasTargetApp(intent?.component)
            ?: intent?.getStringExtra(EXTRA_APP_HINT)

    override fun onResume() {
        super.onResume()
        // When the daily worker has staged a newer APK in cacheDir/updates/,
        // hand it off to the system installer now. Android shows its own
        // confirmation dialog; we can't suppress that, but pre-downloading
        // means the user never sees the browser/file-picker chain.
        UpdateInstaller.promptIfPending(this)

        // Re-run the push-transport setup on every resume. The LaunchedEffect
        // in setContent only fires on isAuthenticated transitions (cold-start
        // path), which leaves the server-side row stuck if it gets deleted
        // out-of-band (e.g. the test-on-failure cleanup we added, the user
        // removing it manually, or a server reset). configure() is idempotent
        // — when transport is FCM, FcmRegistrar.connect's upsert just touches
        // the existing row; only if the row is missing does it land a fresh
        // one. Safe to call on every resume.
        lifecycleScope.launch {
            if (sessionManager.isAuthenticated.first()) {
                PushTransport.configure(applicationContext, sessionManager, okHttpClient)
            }
        }
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

    /**
     * mochi:<intent>?<query> — opaque URI; parse the SSP manually.
     *
     * Android's Uri.getQueryParameter throws UnsupportedOperationException on
     * opaque URIs (only HierarchicalUri implements it), so we split SSP into
     * <name>?<query-string> and parse the params ourselves. Use the encoded
     * form so percent-decoding is one pass per param value rather than once
     * at the top (which can confuse splitting on '?' / '&' if a literal byte
     * survives decoding).
     */
    private fun handleSystemIntent(uri: Uri) {
        val ssp = uri.encodedSchemeSpecificPart ?: return
        val qIndex = ssp.indexOf('?')
        val name = if (qIndex >= 0) ssp.substring(0, qIndex) else ssp
        val query = if (qIndex >= 0) ssp.substring(qIndex + 1) else ""
        val params = parseOpaqueQuery(query)
        when (name) {
            "notification" -> setNotificationDeepLink(params["link"])
            "oauth-return" -> applyOAuthReturn(params["code"], params["error"])
            else -> Log.w(TAG, "Unknown system intent in $uri")
        }
    }

    private fun parseOpaqueQuery(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        val out = mutableMapOf<String, String>()
        for (pair in query.split('&')) {
            val eq = pair.indexOf('=')
            val key = if (eq < 0) pair else pair.substring(0, eq)
            val value = if (eq < 0) "" else pair.substring(eq + 1)
            if (key.isEmpty()) continue
            out[Uri.decode(key)] = Uri.decode(value)
        }
        return out
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
        "chat" -> ChatApp.HOME
        "forums" -> ForumsApp.HOME
        "projects" -> ProjectsApp.HOME
        else -> FeedsApp.HOME
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val META_TARGET_APP = "org.mochios.targetApp"

        /** Intent extra a per-app `XxxListScreen.kt` shortcut sets to skip directory lookup. */
        const val EXTRA_APP_HINT = "app"

        private val LEGACY_SYSTEM_INTENT_AUTHORITIES = setOf("notification", "oauth-return")

        /**
         * Every Mochi-app the super-app bundles. The bootstrap path mints a JWT
         * for each so cross-feature navigation (notification deep-links, in-app
         * routing) doesn't surface "app token required" on the
         * first API call — only one of the four would otherwise get its token
         * minted (the cold-start alias's app).
         */
        private val SUPER_APP_MOCHI_APPS = listOf("feeds", "chat", "forums", "projects")
    }
}
