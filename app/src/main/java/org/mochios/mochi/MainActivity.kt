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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mochios.android.auth.SessionManager
import org.mochios.android.auth.OAuthReturnKind
import org.mochios.android.auth.oauthReturnKind
import org.mochios.android.auth.shouldAcceptOAuthReturn
import org.mochios.android.i18n.FormatProvider
import org.mochios.android.i18n.PreferencesManager
import org.mochios.android.push.NonceStore
import org.mochios.android.push.OemBackgroundHintDialog
import org.mochios.android.push.PendingDeepLink
import org.mochios.android.push.PushTransport
import org.mochios.android.push.RequestNotificationPermission
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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var okHttpClient: okhttp3.OkHttpClient
    @Inject lateinit var notificationsRepository: org.mochios.android.notifications.NotificationsRepository
    @Inject lateinit var webSocket: org.mochios.android.websocket.MochiWebSocket

    // Alias / shortcut hint from the launching intent, updated on every
    // onNewIntent. mutableStateOf rather than a Flow: the write is
    // Snapshot-tracked, so the new feature recomposes in the same frame as
    // onResume instead of a frame later.
    private var targetApp by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleMochiUri(intent)
        // Restore a deep link persisted across process death - the update
        // installer kills the process between a notification tap and the
        // relaunch.
        if (PendingDeepLink.link.value == null) {
            lastActiveAppPrefs().getString(KEY_PENDING_DEEP_LINK, null)
                ?.let { PendingDeepLink.set(it) }
        }
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
                    // Cover the content with the theme background while
                    // stopped, so a launcher-icon switch (singleTop reuse via
                    // onNewIntent) never flashes the old app's last frame. Set
                    // on ON_STOP, after the recents snapshot; lifted only once
                    // the new app has painted.
                    var backgroundedCover by remember { mutableStateOf(false) }
                    // The feature on screen when we backgrounded — lets us tell a
                    // same-app resume from a switch to a different app.
                    var coveredFromApp by remember { mutableStateOf<String?>(null) }
                    DisposableEffect(Unit) {
                        val observer = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_STOP -> {
                                    backgroundedCover = true
                                    coveredFromApp = targetApp
                                }
                                // onNewIntent runs before onResume, so a switch
                                // already reads targetApp != coveredFromApp
                                // here; the effect below lifts its cover.
                                Lifecycle.Event.ON_RESUME ->
                                    if (targetApp == coveredFromApp) backgroundedCover = false
                                else -> {}
                            }
                        }
                        this@MainActivity.lifecycle.addObserver(observer)
                        onDispose { this@MainActivity.lifecycle.removeObserver(observer) }
                    }
                    // On a switch, lift the cover only after the new app has
                    // painted one frame.
                    LaunchedEffect(startApp) {
                        if (backgroundedCover && startApp != coveredFromApp) {
                            withFrameNanos {}
                            backgroundedCover = false
                        }
                    }
                    AppBootstrapHost(
                        appName = startApp ?: "feeds",
                        oauthScheme = "mochi",
                        onLocaleChangeRequested = { recreate() },
                        prefetchApps = MOCHI_APPS,
                    ) { onLogout ->
                        // Every feature's logout button routes through here, so a
                        // single confirmation dialog covers them all.
                        var showLogoutConfirm by remember { mutableStateOf(false) }
                        val requestLogout: () -> Unit = { showLogoutConfirm = true }
                        // Alias switch: the Box paints the theme background
                        // behind the swap so no frame shows the old app's
                        // pixels; the 120ms fade covers the first draw.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            AnimatedContent(
                                targetState = startApp,
                                // Snap the outgoing app out rather than fading
                                // it: a crossfade keeps the previous app's
                                // content on screen for the whole fade.
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(durationMillis = 120)) togetherWith
                                        fadeOut(animationSpec = snap())
                                },
                                label = "alias-switch",
                            ) { app ->
                                val navController = rememberNavController()
                                val pendingLink by PendingDeepLink.link.collectAsState()
                                LaunchedEffect(pendingLink) {
                                    val link = pendingLink ?: return@LaunchedEffect
                                    navigateToLink(navController, link)
                                    PendingDeepLink.consume()
                                    clearPersistedDeepLink()
                                }
                                val openNotifications: () -> Unit = {
                                    navController.navigate(SettingsApp.NOTIFICATIONS) { launchSingleTop = true }
                                }
                                NavHost(navController = navController, startDestination = startDestinationFor(app)) {
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
                                    marketNavGraph(navController)
                                    staffNavGraph(navController)
                                }
                            }
                            if (backgroundedCover) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background),
                                )
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleMochiUri(intent)
        val resolved = resolveTargetApp(intent)
        if (resolved != null) targetApp = resolved
    }

    /**
     * Alias meta-data first; shortcut intents target MainActivity itself (no
     * meta-data), so fall back to [EXTRA_APP_HINT].
     */
    private fun resolveTargetApp(intent: Intent?): String? =
        resolveAliasTargetApp(intent?.component)
            ?: intent?.getStringExtra(EXTRA_APP_HINT)

    /**
     * Cold-start target. After an in-place upgrade Android relaunches via the
     * default LAUNCHER alias whatever feature was active, so when the running
     * versionName differs from the last cold start's, prefer the feature saved
     * by [onPause].
     */
    private fun resolveStartTargetApp(intent: Intent?, savedInstanceState: Bundle?): String? {
        val resolved = resolveTargetApp(intent)
        // Configuration changes / process death restores: trust the saved
        // state (Compose will rehydrate), don't second-guess the alias.
        if (savedInstanceState != null) return resolved
        // Explicit shortcut hint: user picked a specific feature, honour it.
        if (intent?.getStringExtra(EXTRA_APP_HINT) != null) return resolved
        val current = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        val prefs = lastActiveAppPrefs()
        val lastSeen = prefs.getString(KEY_LAST_SEEN_VERSION, null)
        // Record what we're running now for the next cold start to compare against.
        if (current != null) prefs.edit().putString(KEY_LAST_SEEN_VERSION, current).apply()
        val upgraded = lastSeen != null && current != null && lastSeen != current
        if (!upgraded) return resolved
        val saved = prefs.getString(KEY_LAST_ACTIVE_APP, null) ?: return resolved
        Log.i(TAG, "Upgrade relaunch ($lastSeen -> $current); restoring last-active=$saved over alias=$resolved")
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
        PendingDeepLink.set(link)
        targetApp = "market"
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
        PendingDeepLink.set(link)
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
                navController.navigate(CrmsApp.HOME) { launchSingleTop = true }
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
                navController.navigate(MarketApp.HOME) { launchSingleTop = true }
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
                }
            }
            "wikis" -> {
                navController.navigate(WikisApp.HOME) { launchSingleTop = true }
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
                navController.navigate(PeopleApp.HOME) { launchSingleTop = true }
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
                navController.navigate(ChessApp.HOME) { launchSingleTop = true }
                if (id != null) navController.navigate(ChessApp.gameDetail(id)) { launchSingleTop = true }
            }
            "go" -> {
                navController.navigate(GoApp.HOME) { launchSingleTop = true }
                if (id != null) navController.navigate(GoApp.gameDetail(id)) { launchSingleTop = true }
            }
            "words" -> {
                navController.navigate(WordsApp.HOME) { launchSingleTop = true }
                if (id != null) navController.navigate(WordsApp.gameDetail(id)) { launchSingleTop = true }
            }
            "staff" -> {
                navController.navigate(StaffApp.HOME) { launchSingleTop = true }
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
        "market" -> MarketApp.HOME
        "staff" -> StaffApp.HOME
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
        // alias targets SettingsApp.HOME via `targetApp = "settings"`.

        private val LEGACY_SYSTEM_INTENT_AUTHORITIES = setOf("notification", "oauth-return", "oauth-link-return")

        /**
         * Every bundled app; the bootstrap mints a JWT for each so
         * cross-feature navigation never hits "app token required".
         */
        private val MOCHI_APPS = listOf("feeds", "chat", "forums", "projects", "crm", "people", "settings", "wikis", "chess", "go", "words", "market", "staff", "menu")
    }
}
