// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mochios.android.account.MochiAccount
import org.mochios.android.api.ApiException
import org.mochios.android.auth.AuthRepository
import org.mochios.android.auth.Identity
import org.mochios.android.auth.IdentityInfo
import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.LanguageStore
import org.mochios.android.i18n.LocaleHelper
import org.mochios.android.i18n.PreferencesManager
import org.mochios.android.notifications.NotificationsUnreadStore
import org.mochios.android.push.MochiPushClient
import org.mochios.android.push.PushService
import org.mochios.android.push.PushTransport
import org.mochios.android.websocket.MochiWebSocket
import javax.inject.Inject

/**
 * Booting -> (NeedsAccountChoice | NeedsLogin) -> Bootstrapping -> Ready. The
 * JWT is minted only from Bootstrapping, except after a transport-level
 * failure: Ready is then entered on the cached JWT and the mint retries, since
 * an unreachable server is not a dead session.
 */
sealed class AuthStage {
    /** Initial; ViewModel is evaluating account state. */
    data object Booting : AuthStage()

    /** A session exists in AccountManager; user must choose / confirm it. */
    data class NeedsAccountChoice(val accounts: List<MochiAccount.Snapshot>) : AuthStage()

    /** No usable session anywhere; user must run the AuthNavigation flow. */
    data object NeedsLogin : AuthStage()

    /** Session in hand; fetching app JWT + theme + preferences + language. */
    data object Bootstrapping : AuthStage()

    /**
     * Account pending self-service closure: the session is valid but every app
     * action is refused. [purge] is the unix-seconds deletion deadline, 0 if
     * unknown.
     */
    data class NeedsReactivation(val purge: Long) : AuthStage()

    /**
     * [recreateForLocale] is set when bootstrap fetched a different language
     * and the activity should `recreate()` once. [epoch] increments on each
     * fresh Ready so the host can `key()` the ready scope and clear saved nav
     * state.
     */
    data class Ready(val epoch: Long, val recreateForLocale: Boolean = false) : AuthStage()
}

@HiltViewModel
class AppBootstrapViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
    private val preferencesManager: PreferencesManager,
    private val webSocket: MochiWebSocket,
    private val unreadStore: NotificationsUnreadStore,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _stage = MutableStateFlow<AuthStage>(AuthStage.Booting)
    val stage: StateFlow<AuthStage> = _stage.asStateFlow()

    private var appName: String = ""
    private var prefetchApps: List<String> = emptyList()
    private var justAuthenticated: Boolean = false
    private var readyEpoch: Long = 0L
    private var remint: Job? = null

    // The account a tapped sign-in prompt named, until the app's start takes
    // it up; see [reauthenticate].
    private var hinted: String? = null

    // The server of the account being signed in again, for the sign-in screen
    // once its session is found dead.
    private var resigning: String? = null

    init {
        // The ViewModel survives Activity.recreate(), so logout's clearAll
        // would leave _stage at Ready. Re-evaluate when the session goes null.
        viewModelScope.launch {
            sessionManager.currentToken.collect { session ->
                if (session == null) {
                    val s = _stage.value
                    if (s is AuthStage.Ready || s is AuthStage.Bootstrapping) {
                        evaluate()
                    }
                }
            }
        }
        // React to cross-app account changes only from steady-state stages:
        // bootstrap's own publishAccount emits here, and re-entering evaluate()
        // would loop.
        viewModelScope.launch {
            MochiAccount.accountsFlow(context).collect { accounts ->
                when (val s = _stage.value) {
                    is AuthStage.Ready -> {
                        // Cross-app logout: our bound identity vanished.
                        val boundId = sessionManager.getBoundIdentity()
                        if (boundId != null && accounts.none { it.identity == boundId }) {
                            unreadStore.stop()
                            webSocket.disconnectAll()
                            sessionManager.clearAll()
                            _stage.value = AuthStage.NeedsLogin
                        }
                    }

                    is AuthStage.NeedsLogin, is AuthStage.NeedsAccountChoice -> {
                        // Sibling login surfaced a new account — re-evaluate so
                        // we offer adoption.
                        if (accounts.isNotEmpty()) evaluate()
                    }

                    else -> Unit  // Booting / Bootstrapping — ignore.
                }
            }
        }
    }

    /** Host activity calls this once per onCreate with its own app name. */
    fun start(appName: String, prefetchApps: List<String> = emptyList()) {
        val same = this.appName == appName && this.prefetchApps == prefetchApps
        if (same && _stage.value !is AuthStage.Booting) return

        // Pure alias switch inside a bootstrapped session: every prefetched
        // app's token is already minted, so just update appName. Flipping
        // through Bootstrapping would leave the previous feature on screen
        // during a pointless re-mint.
        if (_stage.value is AuthStage.Ready &&
            this.prefetchApps == prefetchApps &&
            prefetchApps.contains(appName)
        ) {
            this.appName = appName
            return
        }

        this.appName = appName
        this.prefetchApps = prefetchApps
        viewModelScope.launch { evaluate() }
    }

    /**
     * A sign-in prompt for [identity]'s dead session was tapped: lead to
     * signing that account in. Another account than the signed-in one is
     * switched to; either way the session is tried again, found dead, and the
     * sign-in screen opens on the account's server. Tapped before the app has
     * started, the start takes it up.
     */
    fun reauthenticate(identity: String) {
        if (_stage.value is AuthStage.Booting) {
            hinted = identity
            return
        }
        viewModelScope.launch { resign(identity) }
    }

    /**
     * Start [identity]'s account afresh, switching to it if it is not the
     * signed-in one.
     *
     * @return whether it is an account on the device.
     */
    private suspend fun resign(identity: String): Boolean {
        val snap = MochiAccount.all(context).firstOrNull { account -> account.identity == identity } ?: return false
        resigning = snap.server
        if (sessionManager.getBoundIdentity() != identity) adopt(snap)
        bootstrap()
        return true
    }

    /** User picked an account from the picker. */
    fun pickAccount(snap: MochiAccount.Snapshot) {
        viewModelScope.launch {
            adopt(snap)
            bootstrap()
        }
    }

    /** The host is about to rebuild the Activity for a new locale (see [ReadyStage]). */
    fun localeApplied() {
        _stage.update { stage -> stage.localeApplied() }
    }

    /** User declined every offered account; show the login flow. */
    fun useDifferentServer() {
        _stage.value = AuthStage.NeedsLogin
    }

    /** AuthNavigation reports successful login. */
    fun onAuthSuccess() {
        justAuthenticated = true
        viewModelScope.launch { bootstrap() }
    }

    /** Force a re-evaluation (e.g. after AccountManager update). */
    fun refresh() {
        viewModelScope.launch { evaluate() }
    }

    /**
     * User-initiated logout. The stage flips to NeedsLogin synchronously,
     * before clearAll and MochiAccount.remove emit, so the observers above
     * cannot bounce it back through Ready and restore the previous session's
     * back stack.
     */
    fun logout() {
        _stage.value = AuthStage.NeedsLogin
        remint?.cancel()
        viewModelScope.launch {
            // Stop the notifications poller + websocket first: otherwise the
            // socket keeps reconnect-looping and the unread-count call 401s
            // once clearAll() drops the session cookie.
            unreadStore.stop()
            webSocket.disconnectAll()
            // Drop the device's push account server-side while the session can
            // still mint a notifications token — once clearAll() runs the call
            // would 401 and the server would keep pushing to this device.
            PushService.removeAccount(context)
            // Retire the UnifiedPush instance too: removeAccount drops the
            // server-side push account, but without this the distributor keeps
            // the local registration and its endpoint, and the next sign-in
            // registers a second one.
            MochiPushClient.unregister(context, context.packageName)
            // Clear the session before tearDown(): Firebase re-registers after
            // unregister() and fires onRegistered, which only skips
            // re-registering while no session is active.
            sessionManager.clearAll()
            PushTransport.tearDown(context)
        }
    }

    /** Cancel a pending account closure from the reactivation interstitial,
     *  then re-run bootstrap so the now-active session lands in the app. */
    fun reactivate() {
        _stage.value = AuthStage.Bootstrapping
        viewModelScope.launch {
            runCatching { authRepository.cancelClose() }
            bootstrap()
        }
    }

    private suspend fun evaluate() {
        hinted?.let { identity ->
            hinted = null
            if (resign(identity)) return
        }
        val hasSession = sessionManager.currentToken.first() != null
        if (hasSession) {
            bootstrap()
            return
        }

        val accounts = MochiAccount.all(context)
        val boundIdentity = sessionManager.getBoundIdentity()
        val rebind = boundIdentity?.let { id -> accounts.firstOrNull { it.identity == id } }
        if (rebind != null) {
            adopt(rebind)
            bootstrap()
            return
        }

        _stage.value = if (accounts.isEmpty()) {
            AuthStage.NeedsLogin
        } else {
            AuthStage.NeedsAccountChoice(accounts)
        }
    }

    private suspend fun adopt(snap: MochiAccount.Snapshot) {
        sessionManager.setServerUrl(snap.server)
        sessionManager.saveSession(snap.session)
        sessionManager.setBoundAccount(snap.identity, snap.server)
    }

    private suspend fun bootstrap() {
        _stage.value = AuthStage.Bootstrapping
        remint?.cancel()

        // Read the language before the warm-up starts: preferencesManager.refresh()
        // stores and applies the server's tag itself, so a comparison made
        // after it always sees the new tag and can never detect a change.
        val previousTag = LanguageStore.get(context)
        val fresh = justAuthenticated

        // Only an authoritative 401 means the session is dead. Transport
        // failures say nothing, so keep the session, run on the cached JWT and
        // re-mint in the background - clearing on any failure logs the user out
        // on every offline launch.
        val outcome = startup(
            mint = { authRepository.fetchToken(appName, fresh = true) },
            // One `_/identity` fetch, shared with publishAccount below.
            identity = {
                runCatching { authRepository.getIdentityInfo() }
                    .onFailure { e ->
                        // Abandoned after a 401 or an unreachable mint: not a failure.
                        if (e !is CancellationException) Log.w(TAG, "getIdentityInfo failed at bootstrap", e)
                    }
                    .getOrNull()
            },
            // The preferences, the theme among them, are a best-effort warm-up.
            preferences = { preferencesManager.refresh() },
        )
        val going = when (outcome) {
            Startup.SignedOut -> {
                sessionManager.clearAll()
                // Signing an account in again: the sign-in screen opens on
                // its server rather than the default.
                resigning?.let { server -> sessionManager.setServerUrl(server) }
                resigning = null
                _stage.value = AuthStage.NeedsLogin
                return
            }
            is Startup.Closing -> {
                resigning = null
                _stage.value = AuthStage.NeedsReactivation(outcome.purge)
                return
            }
            is Startup.Continue -> outcome
        }
        // The session lives after all; nothing to sign in again.
        resigning = null
        val identityInfo = going.identity
        val unreachable = going.unreachable
        if (unreachable) {
            Log.w(TAG, "Token mint unreachable; continuing with cached credentials", going.failure)
            remintWhenReachable(appName)
        }

        // Prefetch the other apps' tokens so a launchpad or notification jump
        // does not hit "app token required". Off the bootstrap path so Ready is
        // not delayed; per-app failures are ignored.
        if (!unreachable) {
            prefetchApps
                .filter { other -> other != appName }
                .forEach { other ->
                    viewModelScope.launch(Dispatchers.IO) {
                        authRepository.fetchToken(other)
                    }
                }
        }

        // Republish every bootstrap so a missing AccountManager record
        // self-heals. The local session is canonical; AccountManager only
        // shares it.
        runCatching { publishAccount(identityInfo?.identity) }

        // The locale matters only after a fresh authentication; returning users
        // keep whatever they last set. refresh() has already stored and applied
        // the tag, so there is nothing left to fetch - only to decide whether
        // the running Activity must be rebuilt to pick up the new resources.
        // On API 33+ LocaleManager triggers that recreate itself; below it
        // LocaleHelper only sets the default Locale, so we ask for it here.
        var recreate = false
        if (fresh) {
            justAuthenticated = false
            val newTag = LanguageStore.get(context)
            if (newTag != null && newTag != previousTag &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            ) {
                recreate = true
            }
        }

        _stage.value = AuthStage.Ready(epoch = ++readyEpoch, recreateForLocale = recreate)
    }

    /**
     * Retry the app's token mint after a transport failure, backing off to once
     * a minute. A 401 clears the session, which flips the stage back to login.
     */
    private fun remintWhenReachable(app: String) {
        remint?.cancel()
        remint = viewModelScope.launch(Dispatchers.IO) {
            var wait = 5_000L
            while (true) {
                delay(wait)
                val result = authRepository.fetchToken(app, fresh = true)
                if (result.isSuccess) return@launch
                val failure = result.exceptionOrNull()
                if (failure is ApiException && failure.code == 401) {
                    sessionManager.clearAll()
                    return@launch
                }
                wait = (wait * 2).coerceAtMost(60_000L)
            }
        }
    }

    /** Write/refresh the AccountManager record so sibling installs can adopt this
     *  session. Best-effort. */
    private suspend fun publishAccount(identity: Identity?) {
        val session = sessionManager.currentToken.first() ?: return
        val server = sessionManager.serverUrl.first()
        if (identity == null || identity.identity.isBlank()) return

        // Write the local binding before the best-effort AccountManager upsert:
        // an addAccountExplicitly failure must not leave boundIdentity empty.
        sessionManager.setBoundAccount(identity.identity, server)

        val displayName = identity.name.takeIf { it.isNotBlank() }
            ?: identity.email.takeIf { it.isNotBlank() }
            ?: ""
        runCatching {
            MochiAccount.upsert(
                context,
                identity = identity.identity,
                name = displayName,
                email = identity.email,
                server = server,
                fingerprint = identity.fingerprint.takeIf { it.isNotBlank() },
                session = session
            )
        }.onFailure { e -> Log.e(TAG, "AccountManager sharing upsert failed", e) }
    }

    private companion object {
        const val TAG = "AppBootstrap"
    }
}

/**
 * The stage once the Activity has been asked to rebuild for a new locale: the
 * same Ready, its epoch kept so the app's saved state survives, no longer
 * asking for the rebuild.
 */
internal fun AuthStage.localeApplied(): AuthStage =
    if (this is AuthStage.Ready && recreateForLocale) copy(recreateForLocale = false) else this

/** What the start-up requests decided. */
internal sealed class Startup {
    /** The session is dead: the mint answered 401. */
    object SignedOut : Startup()

    /** The account is being closed; the reactivation interstitial comes first. */
    data class Closing(val purge: Long) : Startup()

    /**
     * Go on to Ready, with the identity when it was read. [unreachable] when
     * the mint could not reach the server, [failure] saying why.
     */
    data class Continue(
        val identity: IdentityInfo?,
        val unreachable: Boolean = false,
        val failure: Throwable? = null,
    ) : Startup()
}

/**
 * The start-up requests, sent together rather than one after another: the
 * app token's mint, the identity read and the preferences warm-up. Each was a
 * round trip in series behind the spinner, four or five of them with the
 * warm-up's own, before a first screen that may have a cached copy to show.
 * The preferences are waited for as well: only the theme's colours and the
 * language are kept on the phone, so without them the first screen would
 * draw with the default appearance, font size and formats and switch a round
 * trip later - and sent alongside the rest they add no round trip of their
 * own. A 401 from the mint signs out, "closing" shows the reactivation
 * interstitial, and a transport failure goes on with the cached credentials;
 * each abandons whatever is still in flight, which after a transport failure
 * would only spend its connect timeout on the spinner.
 */
internal suspend fun startup(
    mint: suspend () -> Result<String>,
    identity: suspend () -> IdentityInfo?,
    preferences: suspend () -> Unit,
): Startup = coroutineScope {
    val warm = async { runCatching { preferences() } }
    val read = async { identity() }
    val result = mint()
    val failure = result.exceptionOrNull()
    if (failure is ApiException && failure.code == 401) {
        read.cancel()
        warm.cancel()
        return@coroutineScope Startup.SignedOut
    }
    if (failure != null) {
        read.cancel()
        warm.cancel()
        return@coroutineScope Startup.Continue(null, unreachable = true, failure = failure)
    }
    val info = read.await()
    if (info?.status?.status == "closing") {
        warm.cancel()
        return@coroutineScope Startup.Closing(info.status.purge)
    }
    // The scope ends only once the preferences have answered too.
    Startup.Continue(info)
}
