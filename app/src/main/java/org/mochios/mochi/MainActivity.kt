// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mochios.android.auth.SessionManager
import org.mochios.android.auth.OAuthReturnKind
import org.mochios.android.auth.oauthReturnKind
import org.mochios.android.auth.shouldAcceptOAuthReturn
import org.mochios.android.util.entityDeepLink
import org.mochios.android.i18n.FormatProvider
import org.mochios.android.i18n.PreferencesManager
import org.mochios.android.push.NonceStore
import org.mochios.android.push.OemBackgroundHintDialog
import org.mochios.android.push.PushTransport
import org.mochios.android.push.RequestNotificationPermission
import org.mochios.android.push.launcherComponentFor
import org.mochios.android.ui.AppBootstrapHost
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.android.ui.theme.MochiTheme
import org.mochios.android.R as MochiR
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
import org.mochios.market.navigation.MarketApp
import org.mochios.market.navigation.marketNavGraph
import org.mochios.staff.navigation.StaffApp
import org.mochios.staff.navigation.staffNavGraph
import org.mochios.projects.navigation.ProjectsApp
import org.mochios.projects.navigation.projectsNavGraph
import org.mochios.settings.navigation.SettingsApp
import org.mochios.settings.navigation.settingsNavGraph
import javax.inject.Inject

/**
 * The shell activity. Every launcher icon is a subclass of this (Launchers.kt)
 * whose manifest entry names the Mochi app it hosts and gives it a task of
 * its own, and an instance renders that one app for its whole life. The bare
 * MainActivity hosts nothing: it receives every `mochi:` URI and forwards the
 * launch to the owning app's class - see [onCreate].
 */
@AndroidEntryPoint
open class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var okHttpClient: okhttp3.OkHttpClient
    @Inject lateinit var notificationsRepository: org.mochios.android.notifications.NotificationsRepository
    @Inject lateinit var webSocket: org.mochios.android.websocket.MochiWebSocket

    /** The app this instance hosts; null for one that only forwards. */
    private var app: String? = null

    // A deep link waiting for this instance's NavHost: a tapped notification, a
    // pinned shortcut, a checkout or Stripe return. Held per instance, not
    // process-wide - with a task per app, a shared slot navigates every live
    // Mochi task to the link, not just the one that received it.
    private val pendingLink = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hosted = targetAppOf(componentName)
        val target = resolveStartTargetApp(intent, savedInstanceState, hosted)
        // A launch for another app - a URI at the bare MainActivity, a
        // shortcut's hint, the upgrade relaunch through the default launcher
        // entry - is handed to that app's class, so it lands in that app's
        // task rather than drawing here.
        if (hosted == null || target != hosted) {
            forward(target)
            finish()
            return
        }
        app = hosted
        enableEdgeToEdge()
        handleMochiUri(intent)
        // Restore a deep link persisted across process death - the update
        // installer kills the process between a notification tap and the
        // relaunch. One for another app stays on disk for that app's launch.
        if (pendingLink.value == null) {
            lastActiveAppPrefs().getString(KEY_PENDING_DEEP_LINK, null)
                ?.takeIf { link -> appForLink(link).let { it == null || it == hosted } }
                ?.let { pendingLink.value = it }
        }
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
                    AppBootstrapHost(
                        appName = hosted,
                        oauthScheme = "mochi",
                        onLocaleChangeRequested = { recreate() },
                        prefetchApps = MOCHI_APPS,
                    ) { onLogout ->
                        // Every feature's logout button routes through here, so a
                        // single confirmation dialog covers them all.
                        var showLogoutConfirm by remember { mutableStateOf(false) }
                        val requestLogout: () -> Unit = { showLogoutConfirm = true }
                        val navController = rememberNavController()
                        val link by pendingLink.collectAsState()
                        LaunchedEffect(link) {
                            val target = link ?: return@LaunchedEffect
                            navigateToLink(navController, target)
                            pendingLink.value = null
                            clearPersistedDeepLink()
                        }
                        val openNotifications: () -> Unit = {
                            navController.navigate(SettingsApp.NOTIFICATIONS) { launchSingleTop = true }
                        }
                        NavHost(navController = navController, startDestination = startDestinationFor(hosted)) {
                            feedsNavGraph(
                                navController,
                                onLogout = requestLogout,
                                onOpenNotifications = openNotifications,
                            )
                            chatNavGraph(
                                navController,
                                onLogout = requestLogout,
                                onOpenNotifications = openNotifications,
                            )
                            forumsNavGraph(
                                navController,
                                onLogout = requestLogout,
                                onOpenNotifications = openNotifications,
                            )
                            projectsNavGraph(
                                navController,
                                onLogout = requestLogout,
                                onOpenNotifications = openNotifications,
                            )
                            crmsNavGraph(
                                navController,
                                onLogout = requestLogout,
                                onOpenNotifications = openNotifications,
                            )
                            peopleNavGraph(
                                navController,
                                onLogout = requestLogout,
                                onOpenNotifications = openNotifications,
                                onOpenLink = { link -> navigateToLink(navController, link) },
                            )
                            settingsNavGraph(
                                navController,
                                onLogout = requestLogout,
                                onOpenLink = { link -> navigateToLink(navController, link) },
                            )
                            wikisNavGraph(
                                navController,
                                onLogout = requestLogout,
                                onOpenNotifications = openNotifications,
                                onOpenLink = { link -> navigateToLink(navController, link) },
                            )
                            chessNavGraph(
                                navController,
                                onLogout = requestLogout,
                                onOpenNotifications = openNotifications,
                                onOpenLink = { link -> navigateToLink(navController, link) },
                            )
                            goNavGraph(
                                navController,
                                onLogout = requestLogout,
                                onOpenNotifications = openNotifications,
                                onOpenLink = { link -> navigateToLink(navController, link) },
                            )
                            wordsNavGraph(
                                navController,
                                onLogout = requestLogout,
                                onOpenNotifications = openNotifications,
                                onOpenLink = { link -> navigateToLink(navController, link) },
                            )
                            marketNavGraph(navController, onOpenNotifications = openNotifications)
                            staffNavGraph(navController, onOpenNotifications = openNotifications)
                        }

                        if (showLogoutConfirm) {
                            MochiAlertDialog(
                                onDismissRequest = { showLogoutConfirm = false },
                                title = stringResource(MochiR.string.common_logout),
                                text = stringResource(MochiR.string.common_logout_confirm_message),
                                confirmText = stringResource(MochiR.string.common_logout),
                                onConfirm = {
                                    showLogoutConfirm = false
                                    onLogout()
                                },
                                destructive = true,
                                dismissText = stringResource(MochiR.string.common_cancel),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // singleTop delivers only this class's own intents here, so the app
        // never changes; the URI may carry a deep link or a return.
        handleMochiUri(intent)
    }

    /**
     * The app a launcher class hosts, from the `org.mochios.targetApp`
     * meta-data on its manifest entry; null for the bare MainActivity.
     */
    private fun targetAppOf(component: ComponentName?): String? {
        component ?: return null
        return try {
            val info = packageManager.getActivityInfo(component, PackageManager.GET_META_DATA)
            info.metaData?.getString(META_TARGET_APP)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Which app this launch is for. A shortcut's [EXTRA_APP_HINT] or a `mochi:`
     * URI names it; otherwise the class's own app. After an in-place upgrade
     * Android relaunches through the default launcher entry whatever was
     * active, so when the running versionName differs from the last cold
     * start's, a plain launch prefers the app saved by [onPause]. A launch
     * naming no app goes to the last active one, then to the default.
     */
    private fun resolveStartTargetApp(intent: Intent?, savedInstanceState: Bundle?, hosted: String?): String {
        // Configuration changes / process death restores: this class is
        // already the right one, and Compose rehydrates.
        if (savedInstanceState != null && hosted != null) return hosted
        val hinted = intent?.getStringExtra(EXTRA_APP_HINT) ?: appForUri(intent)
        val prefs = lastActiveAppPrefs()
        if (hinted == null && hosted != null) {
            val current = try {
                packageManager.getPackageInfo(packageName, 0).versionName
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
            val lastSeen = prefs.getString(KEY_LAST_SEEN_VERSION, null)
            // Record what we're running now for the next cold start to compare against.
            if (current != null) prefs.edit().putString(KEY_LAST_SEEN_VERSION, current).apply()
            val upgraded = lastSeen != null && current != null && lastSeen != current
            val saved = prefs.getString(KEY_PENDING_DEEP_LINK, null)?.let(::appForLink)
                ?: prefs.getString(KEY_LAST_ACTIVE_APP, null)
            if (upgraded && saved != null) {
                Log.i(TAG, "Upgrade relaunch ($lastSeen -> $current); restoring $saved over $hosted")
                return saved
            }
            return hosted
        }
        return hinted ?: hosted ?: prefs.getString(KEY_LAST_ACTIVE_APP, null) ?: DEFAULT_APP
    }

    /**
     * The app a `mochi:` URI belongs to, when the URI says: a notification's
     * link starts with its app, and the market checkout return is the
     * market's. OAuth returns and bare entity URIs name none. Only an app with
     * a launcher class counts.
     */
    private fun appForUri(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        if (uri.scheme != "mochi") return null
        val link = when {
            !uri.isHierarchical -> {
                val ssp = uri.encodedSchemeSpecificPart ?: return null
                val q = ssp.indexOf('?')
                if ((if (q >= 0) ssp.substring(0, q) else ssp) != "notification") return null
                parseOpaqueQuery(if (q >= 0) ssp.substring(q + 1) else "")["link"]
            }
            uri.authority == "notification" -> uri.getQueryParameter("link")
            uri.authority == "market" -> return "market"
            else -> null
        } ?: return null
        return appForLink(link)
    }

    /** The app a deep link path such as `/feeds/<id>` belongs to, when it has a launcher class. */
    private fun appForLink(link: String): String? {
        val app = link.trimStart('/').substringBefore('/').substringBefore('?').lowercase()
        return app.takeIf { it.isNotEmpty() && launcherComponentFor(this, it) != null }
    }

    /**
     * Hand this launch to [app]'s own class, so it lands in that app's task.
     * The intent goes across whole - data, extras, flags - and the receiver
     * handles its URI once. Flags that would give the receiver a throwaway
     * task or history entry are dropped; a caller's launcher-style flags
     * (clear top, clear task) are kept, as they were meant for the app.
     */
    private fun forward(app: String) {
        val component = launcherComponentFor(this, app)
            ?: launcherComponentFor(this, DEFAULT_APP)
            ?: return
        val forwarded = Intent(intent ?: Intent(Intent.ACTION_MAIN)).setComponent(component)
        val dropped = Intent.FLAG_ACTIVITY_NO_HISTORY or
            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
            Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
            Intent.FLAG_ACTIVITY_FORWARD_RESULT
        forwarded.flags = (forwarded.flags and dropped.inv()) or Intent.FLAG_ACTIVITY_NEW_TASK
        Log.i(TAG, "Forwarding ${intent?.data ?: intent?.action} to $app")
        startActivity(forwarded)
    }

    override fun onPause() {
        super.onPause()
        // Remember the active app so the post-install relaunch, and a URI
        // that names no app, can land the user back here. Saved on every
        // pause so a notification deep link / OAuth return / install prompt
        // that follows still preserves the right app.
        app?.let {
            lastActiveAppPrefs().edit().putString(KEY_LAST_ACTIVE_APP, it).apply()
        }
    }

    private fun lastActiveAppPrefs() =
        getSharedPreferences("mochi_main_activity", MODE_PRIVATE)

    private fun clearPersistedDeepLink() {
        lastActiveAppPrefs().edit().remove(KEY_PENDING_DEEP_LINK).apply()
    }

    override fun onResume() {
        super.onResume()
        // When the daily worker has staged a newer APK in cacheDir/updates/,
        // hand it off to the system installer now. Android shows its own
        // confirmation dialog; we can't suppress that, but pre-downloading
        // means the user never sees the browser/file-picker chain.
        UpdateInstaller.promptIfPending(this)

        // A socket dropped in the background otherwise waits out its backoff
        // timer.
        webSocket.reconnectNow()

        // The LaunchedEffect in setContent only fires on isAuthenticated
        // transitions, so a registration row deleted out-of-band would never be
        // re-landed. configure() is idempotent.
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
            uri.authority == "market" && uri.pathSegments.firstOrNull() == "checkout" ->
                handleMarketCheckoutDeepLink(uri)
            uri.authority == "market" && uri.pathSegments == listOf("stripe", "oauth") ->
                handleMarketStripeOauthDeepLink(uri)
            else -> handleCrossPeerEntityIntent(intent, uri)
        }
    }

    /**
     * Stripe Checkout return (`mochi://market/checkout/success|cancel`, minted
     * by the Comptroller for `client_platform=android`). Order state comes from
     * the webhook, not from this; it only navigates, so unlike the OAuth
     * returns it is deliberately not nonce-gated.
     */
    private fun handleMarketCheckoutDeepLink(uri: Uri) {
        val outcome = uri.pathSegments.getOrNull(1) ?: return
        val link = when (outcome) {
            "success" -> "/market/purchases?paid=1"
            "cancel" -> {
                // The listing id rides in an intent any app can send; accept
                // only an identifier shape so it cannot smuggle path segments
                // or a query of its own into the navigation route.
                val listing = uri.getQueryParameter("listing")
                    ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,64}")) }
                if (listing.isNullOrBlank()) "/market" else "/market/listing/$listing"
            }
            else -> {
                Log.w(TAG, "Unknown market checkout outcome: $uri")
                return
            }
        }
        pendingLink.value = link
    }

    /**
     * Stripe Connect return (`mochi://market/stripe/oauth?code&state&error&
     * error_description`): the market callback hands an app-platform state
     * here with Stripe's raw parameters, and the seller settings screen
     * completes the exchange as the signed-in seller. The state is opaque
     * to the app, so it is only bounded, never interpreted.
     */
    private fun handleMarketStripeOauthDeepLink(uri: Uri) {
        val state = uri.getQueryParameter("state")?.takeIf { it.length in 1..200 } ?: return
        val route = MarketApp.sellerSettings(
            code = uri.getQueryParameter("code"),
            state = state,
            error = uri.getQueryParameter("error"),
            errorDescription = uri.getQueryParameter("error_description"),
        )
        pendingLink.value = "/market/account/seller?" + route.substringAfter('?', "")
    }

    /**
     * mochi:<intent>?<query> is opaque and Uri.getQueryParameter throws on
     * opaque URIs, so split the encoded SSP by hand and decode each value once
     * after splitting.
     */
    private fun handleSystemIntent(uri: Uri) {
        val ssp = uri.encodedSchemeSpecificPart ?: return
        val qIndex = ssp.indexOf('?')
        val name = if (qIndex >= 0) ssp.substring(0, qIndex) else ssp
        val query = if (qIndex >= 0) ssp.substring(qIndex + 1) else ""
        val params = parseOpaqueQuery(query)
        when (name) {
            "notification" -> setNotificationDeepLink(params["link"], params["id"], params["nonce"])
            else -> when (oauthReturnKind(name)) {
                OAuthReturnKind.LOGIN -> applyOAuthReturn(params["code"], params["error"], params["nonce"])
                OAuthReturnKind.LINK -> applyOAuthLinkReturn(params["code"], params["error"], params["nonce"])
                null -> Log.w(TAG, "Unknown system intent in $uri")
            }
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
     * Legacy `mochi://notification?...` / `mochi://oauth-return?...` shapes,
     * still emitted by older servers and shortcuts.
     */
    private fun handleLegacySystemIntent(uri: Uri) {
        when (uri.authority) {
            "notification" -> setNotificationDeepLink(
                uri.getQueryParameter("link"),
                uri.getQueryParameter("id"),
                uri.getQueryParameter("nonce"),
            )
            else -> when (oauthReturnKind(uri.authority.orEmpty())) {
                OAuthReturnKind.LOGIN -> applyOAuthReturn(
                    uri.getQueryParameter("code"),
                    uri.getQueryParameter("error"),
                    uri.getQueryParameter("nonce"),
                )
                OAuthReturnKind.LINK -> applyOAuthLinkReturn(
                    uri.getQueryParameter("code"),
                    uri.getQueryParameter("error"),
                    uri.getQueryParameter("nonce"),
                )
                null -> Unit
            }
        }
    }

    /**
     * mochi:/<entity>[/<sub>...] - entity in the current session. Routing needs
     * the owning app, which only the [EXTRA_APP_HINT] extra supplies; without
     * it the URI is a no-op.
     */
    private fun handleEntityIntent(intent: Intent, uri: Uri) {
        // The activity is exported, so the app hint and every path segment ride
        // in an intent any installed app can send; entityDeepLink refuses an
        // unknown app and any segment that could smuggle a path or query of its
        // own into the route.
        val link = entityDeepLink(
            intent.getStringExtra(EXTRA_APP_HINT), uri.pathSegments, MOCHI_APPS,
        )
        if (link == null) {
            Log.w(TAG, "Entity URI refused: $uri")
            return
        }
        pendingLink.value = link
    }

    /**
     * mochi://<peer>/<entity>[/<sub>...] - entity on another peer; not yet
     * routed.
     */
    private fun handleCrossPeerEntityIntent(intent: Intent, uri: Uri) {
        Log.w(TAG, "Cross-peer URI not yet supported: $uri")
    }

    /**
     * Tapped system notification. The activity is exported, so any app can send
     * `mochi:notification?link=...&id=...`; both the mark-read and the pending
     * link are gated on consuming the nonce issued when the notification was
     * posted.
     */
    private fun setNotificationDeepLink(link: String?, id: String?, nonce: String?) {
        link ?: return
        if (!NonceStore(this).consume(nonce)) {
            Log.w(TAG, "Ignoring mochi:notification with no outstanding nonce")
            return
        }
        pendingLink.value = link
        // Mirror to disk so the update-installer relaunch (or any other
        // process-death window between tap and consume) can restore it.
        // Cleared by the Compose LaunchedEffect after navigateToLink fires.
        lastActiveAppPrefs().edit().putString(KEY_PENDING_DEEP_LINK, link).apply()
        // Tapping the system notification dismisses it on the device but
        // leaves the matching unread row on the server, so the web bell
        // keeps showing it. Hit -/read so the row is marked read and
        // disappears from the web bell / drops the unread count.
        if (!id.isNullOrEmpty()) {
            lifecycleScope.launch {
                try {
                    notificationsRepository.markRead(id)
                } catch (e: Exception) {
                    Log.w(TAG, "markRead($id) failed: ${e.message}")
                }
            }
        }
    }

    private fun applyOAuthReturn(code: String?, error: String?, nonce: String?) {
        // Both halves read and write DataStore, so they run off the main thread.
        // Nothing here is consumed synchronously - the sign-in screen observes
        // the ceremony state as a flow - so the launch is not raced.
        lifecycleScope.launch {
            // Exported and BROWSABLE, so an unsolicited mochi:oauth-return must not
            // burn the ceremony (see shouldAcceptOAuthReturn). One snapshot:
            // verifier and nonce must describe the same ceremony.
            val ceremony = sessionManager.oauthCeremony()
            if (!shouldAcceptOAuthReturn(ceremony.hasVerifier, ceremony.nonce, nonce, code, error)) {
                Log.w(TAG, "Ignoring mochi:oauth-return that matches no outstanding ceremony")
                return@launch
            }
            sessionManager.setOAuthReturn(code, error)
        }
    }

    /**
     * LINK ceremony return, gated against the link ceremony: handed to the
     * sign-in handler it would be exchanged as a login.
     */
    private fun applyOAuthLinkReturn(code: String?, error: String?, nonce: String?) {
        lifecycleScope.launch {
            val ceremony = sessionManager.oauthLinkCeremony()
            if (!shouldAcceptOAuthReturn(ceremony.hasVerifier, ceremony.nonce, nonce, code, error)) {
                Log.w(TAG, "Ignoring mochi:oauth-link-return that matches no outstanding ceremony")
                return@launch
            }
            sessionManager.setOAuthLinkReturn(code, error)
        }
    }

    /**
     * Whether [route] already has an entry on the back stack.
     *
     * @param route a destination's route pattern.
     * @return true when the stack holds one.
     */
    private fun NavController.holds(route: String): Boolean =
        currentBackStack.value.any { entry -> entry.destination.route == route }

    /**
     * Whether [route] is the screen already on top, arguments and all.
     *
     * @param route a filled route, as the navigate calls below build it.
     * @return true when navigating there would re-open the current screen.
     */
    private fun NavController.isAt(route: String): Boolean {
        val entry = currentBackStackEntry ?: return false
        return entry.destination.hasRoute(route, entry.arguments)
    }

    /**
     * Make [home] the parent of the destination a deep link is about to open:
     * return to the copy already on the stack, dropping whatever sits above it,
     * or push it when the app has not been opened yet. Stacking a second copy
     * instead would leave the first one alive - its ViewModel, its websocket
     * subscription and the refresh it runs on every event all still going.
     */
    private fun NavController.openAppHome(home: String) {
        if (holds(home)) {
            popBackStack(home, inclusive = false)
        } else {
            navigate(home) { launchSingleTop = true }
        }
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
                // Feeds' HOME is the router, which resolves the last-viewed feed
                // and pops itself; left under a deep-linked feed it traps Back in
                // a resolve loop, so the feed replaces it rather than stacking on
                // top of it.
                if (id == null) {
                    navController.navigate(FeedsApp.HOME) { launchSingleTop = true }
                } else if (!navController.isAt(FeedsApp.feed(id))) {
                    val popTo = if (navController.holds(FeedsApp.ROUTER)) {
                        FeedsApp.ROUTER
                    } else {
                        FeedsApp.FEED
                    }
                    navController.navigate(FeedsApp.feed(id)) {
                        popUpTo(popTo) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            "chat" -> {
                if (id == "new") {
                    val friendId = parseQueryParam(query, "friend")
                    navController.openAppHome(ChatApp.HOME)
                    navController.navigate(ChatApp.newChat(friendId.orEmpty())) {
                        launchSingleTop = true
                    }
                    return
                }
                if (id != null && navController.isAt(ChatApp.chat(id))) return
                navController.openAppHome(ChatApp.HOME)
                if (id != null) navController.navigate(ChatApp.chat(id)) { launchSingleTop = true }
            }
            "forums" -> {
                if (id != null && navController.isAt(ForumsApp.forum(id))) return
                navController.openAppHome(ForumsApp.HOME)
                if (id != null) navController.navigate(ForumsApp.forum(id)) { launchSingleTop = true }
            }
            "projects" -> {
                navController.openAppHome(ProjectsApp.HOME)
                if (id != null) {
                    val objectId = parts.getOrNull(2)
                    if (objectId != null) {
                        navController.navigate(ProjectsApp.projectObject(id, objectId)) { launchSingleTop = true }
                    } else {
                        navController.navigate(ProjectsApp.project(id)) { launchSingleTop = true }
                    }
                }
            }
            "crm" -> {
                navController.openAppHome(CrmsApp.HOME)
                if (id != null) {
                    val objectId = parts.getOrNull(2)
                    if (objectId != null) {
                        navController.navigate(CrmsApp.crmObject(id, objectId)) { launchSingleTop = true }
                    } else {
                        navController.navigate(CrmsApp.crm(id)) { launchSingleTop = true }
                    }
                }
            }
            "market" -> {
                navController.openAppHome(MarketApp.HOME)
                when (id) {
                    "listing" -> parts.getOrNull(2)
                        ?.let { navController.navigate(MarketApp.listingDetail(it)) { launchSingleTop = true } }
                    "purchases" -> {
                        val orderId = parts.getOrNull(2)
                        if (orderId != null) {
                            navController.navigate(MarketApp.purchaseDetail(orderId)) {
                                launchSingleTop = true
                            }
                        } else {
                            navController.navigate(MarketApp.PURCHASES) { launchSingleTop = true }
                        }
                    }
                    "subscriptions" -> navController.navigate(MarketApp.SUBSCRIPTIONS) {
                        launchSingleTop = true
                    }
                    "account" -> if (parts.getOrNull(2) == "seller") {
                        navController.navigate(
                            MarketApp.sellerSettings(
                                code = parseQueryParam(query, "code"),
                                state = parseQueryParam(query, "state"),
                                error = parseQueryParam(query, "error"),
                                errorDescription = parseQueryParam(query, "error_description"),
                            ),
                        ) { launchSingleTop = true }
                    }
                }
            }
            "wikis" -> {
                navController.openAppHome(WikisApp.HOME)
                if (id != null) {
                    navController.navigate(WikisApp.wikiHome(id)) { launchSingleTop = true }
                    val page = parts.getOrNull(2)
                    if (page != null) {
                        if (parts.getOrNull(3) == "comments") {
                            navController.navigate(WikisApp.comments(id, page)) { launchSingleTop = true }
                        } else {
                            navController.navigate(WikisApp.pageView(id, page)) { launchSingleTop = true }
                        }
                    }
                }
            }
            "people" -> {
                navController.openAppHome(PeopleApp.HOME)
                if (id == "invitations") {
                    navController.navigate(PeopleApp.INVITATIONS) { launchSingleTop = true }
                }
                // "people?action=add" carries no id, so without this the link
                // landed on the people home and stopped. Chess emits it to
                // send a player with no opponents to the add-friend dialog.
                val action = parseQueryParam(query, "action")
                if (id == null && !action.isNullOrBlank()) {
                    navController.navigate(PeopleApp.friends(action)) { launchSingleTop = true }
                }
            }
            "chess" -> {
                if (id != null && navController.isAt(ChessApp.gameDetail(id))) return
                navController.openAppHome(ChessApp.HOME)
                if (id != null) navController.navigate(ChessApp.gameDetail(id)) { launchSingleTop = true }
            }
            "go" -> {
                if (id != null && navController.isAt(GoApp.gameDetail(id))) return
                navController.openAppHome(GoApp.HOME)
                if (id != null) navController.navigate(GoApp.gameDetail(id)) { launchSingleTop = true }
            }
            "words" -> {
                if (id != null && navController.isAt(WordsApp.gameDetail(id))) return
                navController.openAppHome(WordsApp.HOME)
                if (id != null) navController.navigate(WordsApp.gameDetail(id)) { launchSingleTop = true }
            }
            "staff" -> {
                navController.openAppHome(StaffApp.HOME)
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
        "market" -> MarketApp.HOME
        "staff" -> StaffApp.HOME
        else -> FeedsApp.HOME
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val META_TARGET_APP = "org.mochios.targetApp"

        /** The app a launch lands in when nothing names one. */
        private const val DEFAULT_APP = "feeds"

        /** Intent extra a per-app `XxxListScreen.kt` shortcut sets to skip directory lookup. */
        const val EXTRA_APP_HINT = "app"

        /** SharedPreferences key holding the app active at last onPause. */
        private const val KEY_LAST_ACTIVE_APP = "last_active_app"

        /**
         * Deep link not yet consumed by the nav, persisted so it survives the
         * update installer's process death.
         */
        private const val KEY_PENDING_DEEP_LINK = "pending_deep_link"

        /**
         * versionName seen on the previous cold start; a change means an
         * in-place upgrade (see [resolveStartTargetApp]).
         */
        private const val KEY_LAST_SEEN_VERSION = "last_seen_version"

        // Notifications / Settings / Profile routes moved into the Settings
        // app module (`apps/settings`). The bell in each feature's TopAppBar
        // navigates to SettingsApp.NOTIFICATIONS; the Mochi Settings launcher
        // class hosts SettingsApp.HOME.

        private val LEGACY_SYSTEM_INTENT_AUTHORITIES = setOf("notification", "oauth-return", "oauth-link-return")

        /**
         * Every bundled app; the bootstrap mints a JWT for each so
         * cross-feature navigation never hits "app token required".
         */
        private val MOCHI_APPS = listOf("feeds", "chat", "forums", "projects", "crm", "people", "settings", "wikis", "chess", "go", "words", "market", "staff", "menu")
    }
}
