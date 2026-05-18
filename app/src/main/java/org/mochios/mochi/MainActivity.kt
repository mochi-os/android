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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import org.mochios.crm.navigation.CrmsApp
import org.mochios.crm.navigation.crmsNavGraph
import org.mochios.feeds.navigation.FeedsApp
import org.mochios.feeds.navigation.feedsNavGraph
import org.mochios.forums.navigation.ForumsApp
import org.mochios.forums.navigation.forumsNavGraph
import org.mochios.people.navigation.PeopleApp
import org.mochios.people.navigation.peopleNavGraph
import org.mochios.wikis.navigation.WikisApp
import org.mochios.wikis.navigation.wikisNavGraph
import org.mochios.chess.navigation.ChessApp
import org.mochios.chess.navigation.chessNavGraph
import org.mochios.go.navigation.GoApp
import org.mochios.go.navigation.goNavGraph
import org.mochios.words.navigation.WordsApp
import org.mochios.words.navigation.wordsNavGraph
import org.mochios.projects.navigation.ProjectsApp
import org.mochios.projects.navigation.projectsNavGraph
import org.mochios.settings.navigation.SettingsApp
import org.mochios.settings.navigation.settingsNavGraph
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
        targetApp = resolveStartTargetApp(intent, savedInstanceState)
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
                        // Alias-switch transition. The previous attempt
                        // (Snapshot-tracked mutableStateOf + key(startApp)
                        // wrap) still left Android's surface showing the
                        // last frame of the old feature until Compose
                        // measured + drew the new NavHost. Wrapping the
                        // NavHost in Crossfade explicitly animates both
                        // pages during the swap, so the old feature
                        // fades out while the new fades in instead of
                        // staying frozen on screen. The 120ms tween is
                        // imperceptible for an in-app switch but covers
                        // the recomposition + first-draw window. The
                        // outer Box paints the theme background so any
                        // moment Crossfade hasn't drawn yet is the
                        // theme colour, not the old pixels.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            Crossfade(
                                targetState = startApp,
                                animationSpec = tween(durationMillis = 120),
                                label = "alias-switch",
                            ) { app ->
                                val navController = rememberNavController()
                                val pendingLink by PendingDeepLink.link.collectAsState()
                                LaunchedEffect(pendingLink) {
                                    val link = pendingLink ?: return@LaunchedEffect
                                    navigateToLink(navController, link)
                                    PendingDeepLink.consume()
                                }
                                val openNotifications: () -> Unit = {
                                    navController.navigate(SettingsApp.NOTIFICATIONS) { launchSingleTop = true }
                                }
                                NavHost(navController = navController, startDestination = startDestinationFor(app)) {
                                    feedsNavGraph(
                                        navController,
                                        onLogout = onLogout,
                                        onOpenNotifications = openNotifications,
                                    )
                                    chatNavGraph(
                                        navController,
                                        onLogout = onLogout,
                                        onOpenNotifications = openNotifications,
                                    )
                                    forumsNavGraph(
                                        navController,
                                        onLogout = onLogout,
                                        onOpenNotifications = openNotifications,
                                    )
                                    projectsNavGraph(
                                        navController,
                                        onLogout = onLogout,
                                        onOpenNotifications = openNotifications,
                                    )
                                    crmsNavGraph(
                                        navController,
                                        onLogout = onLogout,
                                        onOpenNotifications = openNotifications,
                                    )
                                    peopleNavGraph(
                                        navController,
                                        onLogout = onLogout,
                                        onOpenNotifications = openNotifications,
                                        onOpenLink = { link -> navigateToLink(navController, link) },
                                    )
                                    settingsNavGraph(
                                        navController,
                                        onLogout = onLogout,
                                        onOpenLink = { link -> navigateToLink(navController, link) },
                                    )
                                    wikisNavGraph(
                                        navController,
                                        onLogout = onLogout,
                                        onOpenNotifications = openNotifications,
                                        onOpenLink = { link -> navigateToLink(navController, link) },
                                    )
                                    chessNavGraph(
                                        navController,
                                        onLogout = onLogout,
                                        onOpenNotifications = openNotifications,
                                        onOpenLink = { link -> navigateToLink(navController, link) },
                                    )
                                    goNavGraph(
                                        navController,
                                        onLogout = onLogout,
                                        onOpenNotifications = openNotifications,
                                        onOpenLink = { link -> navigateToLink(navController, link) },
                                    )
                                    wordsNavGraph(
                                        navController,
                                        onLogout = onLogout,
                                        onOpenNotifications = openNotifications,
                                        onOpenLink = { link -> navigateToLink(navController, link) },
                                    )
                                }
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

    /**
     * Cold-start target resolution. Differs from [resolveTargetApp] only in
     * the post-install relaunch case: when the system installer hands the
     * upgraded APK back to Android, Android relaunches the package via the
     * default LAUNCHER intent — which picks one alias out of the five
     * (settings, on our manifest), regardless of which feature the user
     * was actually in when they tapped Update. Detect that window via
     * [PackageInfo.lastUpdateTime] and prefer the last-active feature
     * saved by [onPause] instead, so the user lands back where they were.
     */
    private fun resolveStartTargetApp(intent: Intent?, savedInstanceState: Bundle?): String? {
        val resolved = resolveTargetApp(intent)
        // Configuration changes / process death restores: trust the saved
        // state (Compose will rehydrate), don't second-guess the alias.
        if (savedInstanceState != null) return resolved
        // Explicit shortcut hint: user picked a specific feature, honour it.
        if (intent?.getStringExtra(EXTRA_APP_HINT) != null) return resolved
        val timeSinceUpdate = try {
            System.currentTimeMillis() -
                packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        } catch (_: PackageManager.NameNotFoundException) {
            Long.MAX_VALUE
        }
        if (timeSinceUpdate > POST_INSTALL_RELAUNCH_WINDOW_MS) return resolved
        val saved = lastActiveAppPrefs().getString(KEY_LAST_ACTIVE_APP, null)
        if (saved == null) return resolved
        Log.i(TAG, "Post-install relaunch (${timeSinceUpdate}ms ago); restoring last-active=$saved over alias=$resolved")
        return saved
    }

    override fun onPause() {
        super.onPause()
        // Remember the active feature so the post-install relaunch can land
        // the user back here. Saved on every pause so a notification deep
        // link / OAuth return / install prompt that follows still preserves
        // the right feature.
        targetApp?.let {
            lastActiveAppPrefs().edit().putString(KEY_LAST_ACTIVE_APP, it).apply()
        }
    }

    private fun lastActiveAppPrefs() =
        getSharedPreferences("mochi_main_activity", MODE_PRIVATE)

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
            "oauth-link-return" -> applyOAuthLinkReturn(params["oauth_linked"], params["oauth_error"])
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
            "oauth-link-return" -> applyOAuthLinkReturn(uri.getQueryParameter("oauth_linked"), uri.getQueryParameter("oauth_error"))
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

    private fun applyOAuthLinkReturn(provider: String?, error: String?) {
        if (provider == null && error == null) return
        runBlocking { sessionManager.setOAuthLinkReturn(provider, error) }
    }

    private fun navigateToLink(navController: NavController, link: String) {
        // Split off an optional query string before path tokenisation so links
        // like "chat/new?friend=<id>" survive intact for the matcher below.
        val pathAndQuery = link.trimStart('/').split('?', limit = 2)
        val path = pathAndQuery[0]
        val query = pathAndQuery.getOrNull(1).orEmpty()
        val parts = path.split('/')
        val firstSegment = parts.firstOrNull()?.lowercase() ?: return
        val id = parts.getOrNull(1)
        when (firstSegment) {
            "feeds" -> {
                navController.navigate(FeedsApp.HOME) { launchSingleTop = true }
                if (id != null) navController.navigate(FeedsApp.feed(id)) { launchSingleTop = true }
            }
            "chat" -> {
                if (id == "new") {
                    val friendId = parseQueryParam(query, "friend")
                    navController.navigate(ChatApp.HOME) { launchSingleTop = true }
                    navController.navigate(ChatApp.newChat(friendId.orEmpty())) {
                        launchSingleTop = true
                    }
                    return
                }
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

    /**
     * Pull a single key out of an URL-style query string ("a=1&b=2"). Returns
     * null when the key isn't present. Values are URL-decoded.
     */
    private fun parseQueryParam(query: String, key: String): String? {
        if (query.isBlank()) return null
        for (pair in query.split('&')) {
            val idx = pair.indexOf('=')
            if (idx <= 0) continue
            val k = pair.substring(0, idx)
            if (k != key) continue
            val raw = pair.substring(idx + 1)
            return try {
                java.net.URLDecoder.decode(raw, Charsets.UTF_8.name())
            } catch (_: IllegalArgumentException) {
                raw
            }
        }
        return null
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
        "crm" -> CrmsApp.HOME
        "people" -> PeopleApp.HOME
        "settings" -> SettingsApp.HOME
        "wikis" -> WikisApp.HOME
        "chess" -> ChessApp.HOME
        "go" -> GoApp.HOME
        "words" -> WordsApp.HOME
        else -> FeedsApp.HOME
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val META_TARGET_APP = "org.mochios.targetApp"

        /** Intent extra a per-app `XxxListScreen.kt` shortcut sets to skip directory lookup. */
        const val EXTRA_APP_HINT = "app"

        /** SharedPreferences key holding the feature active at last onPause. */
        private const val KEY_LAST_ACTIVE_APP = "last_active_app"

        /**
         * How long after a package update we treat a fresh launch as a
         * post-install relaunch (and restore the previously-active feature
         * instead of trusting the alias the system happened to pick).
         * Tight window so a legitimate launcher-icon tap 30+ seconds later
         * isn't second-guessed: by then the install dialog is long gone
         * and the tap is a deliberate user choice.
         */
        private const val POST_INSTALL_RELAUNCH_WINDOW_MS = 30_000L

        // Notifications / Settings / Profile routes moved into the Settings
        // app module (`apps/settings`). The bell in each feature's TopAppBar
        // navigates to SettingsApp.NOTIFICATIONS; the Mochi Settings launcher
        // alias targets SettingsApp.HOME via `targetApp = "settings"`.

        private val LEGACY_SYSTEM_INTENT_AUTHORITIES = setOf("notification", "oauth-return", "oauth-link-return")

        /**
         * Every Mochi-app the super-app bundles. The bootstrap path mints a JWT
         * for each so cross-feature navigation (notification deep-links, in-app
         * routing) doesn't surface "app token required" on the
         * first API call — only one of the four would otherwise get its token
         * minted (the cold-start alias's app).
         */
        private val SUPER_APP_MOCHI_APPS = listOf("feeds", "chat", "forums", "projects", "crm", "people", "settings", "wikis", "chess", "go", "words")
    }
}
