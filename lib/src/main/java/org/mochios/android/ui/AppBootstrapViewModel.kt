// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mochios.android.account.MochiAccount
import org.mochios.android.auth.AuthRepository
import org.mochios.android.auth.Identity
import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.LanguageRepository
import org.mochios.android.i18n.LanguageStore
import org.mochios.android.i18n.LocaleHelper
import org.mochios.android.i18n.PreferencesManager
import org.mochios.android.push.PushTransport
import org.mochios.android.ui.theme.ThemeRepository
import javax.inject.Inject

/**
 * Each Mochi app passes through these stages exactly once per process.
 *
 *   Booting → (NeedsAccountChoice | NeedsLogin) → Bootstrapping → Ready
 *
 * The ViewModel owns the transitions; the host activity just renders the
 * stage. This eliminates the ad-hoc onCreate runBlocking dance and the
 * "tokenFetched" boolean that used to gate the main navigation.
 *
 * No state can be reached out of order — the JWT request is only issued
 * from [Bootstrapping], which is only entered after a session is committed
 * to local DataStore. "App token required" 403s become architecturally
 * impossible because we never render the main UI before the JWT is in hand.
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
     * The account is pending self-service closure (soft-deleted): the session
     * is valid but every app action is refused server-side. The user lands on
     * the reactivation interstitial to cancel the closure or sign out.
     * [purge] is the unix-seconds deletion deadline (0 if unknown).
     */
    data class NeedsReactivation(val purge: Long) : AuthStage()

    /**
     * Fully ready. [recreateForLocale] is true iff the language fetched
     * during bootstrap differs from what the host activity was using; the
     * activity should call `recreate()` once.
     *
     * [epoch] is monotonically incremented on each fresh transition into
     * Ready so the host can `key()` the ready scope by it and force a clean
     * NavController + back stack after every logout + re-login (otherwise
     * rememberSaveable inside rememberNavController restores stale entries
     * from the previous session and a per-app detail screen surfaces as
     * NotFoundState against the new session).
     */
    data class Ready(val epoch: Long, val recreateForLocale: Boolean = false) : AuthStage()
}

@HiltViewModel
class AppBootstrapViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
    private val themeRepository: ThemeRepository,
    private val languageRepository: LanguageRepository,
    private val preferencesManager: PreferencesManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _stage = MutableStateFlow<AuthStage>(AuthStage.Booting)
    val stage: StateFlow<AuthStage> = _stage.asStateFlow()

    private var appName: String = ""
    private var prefetchApps: List<String> = emptyList()
    private var justAuthenticated: Boolean = false
    private var readyEpoch: Long = 0L

    init {
        // The ViewModel survives Activity.recreate(), so a `clearAll` in the
        // logout path doesn't naturally bring the host back to the login UI
        // — _stage would stay Ready. Observing the canonical session flow
        // bridges that: when the session goes null while we're Ready, the
        // logout actually surfaced.
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
        // Cross-app awareness: another Mochi app added or removed an account.
        // Important: bootstrap()'s own publishAccount triggers an emission on
        // this flow, so we'd loop if we re-entered evaluate() unconditionally.
        // Restrict reaction to steady-state stages — never to Bootstrapping or
        // Booting, which are transient states actively writing AccountManager.
        viewModelScope.launch {
            MochiAccount.accountsFlow(context).collect { accounts ->
                when (val s = _stage.value) {
                    is AuthStage.Ready -> {
                        // Cross-app logout: our bound identity vanished.
                        val boundId = sessionManager.getBoundIdentity()
                        if (boundId != null && accounts.none { it.identity == boundId }) {
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

        // Pure alias switch within an already-bootstrapped session: same
        // prefetch list, only the "primary" appName changed (user tapped a
        // different launcher icon). The tokens for every entry in
        // prefetchApps were minted on the original bootstrap, so we have
        // nothing new to fetch — silently update appName so the inner
        // NavHost re-keys without flipping the stage through
        // Bootstrapping → Ready (which leaves the previous feature's Ready
        // content visible for ~half a second of token re-mint).
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

    /** User picked an account from the picker. */
    fun pickAccount(snap: MochiAccount.Snapshot) {
        viewModelScope.launch {
            adopt(snap)
            bootstrap()
        }
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
     * User-initiated logout. Stage flips to NeedsLogin synchronously so the
     * host swaps the ready scope out *before* clearAll's DataStore emission
     * and MochiAccount.remove fire, sidestepping the observer races:
     *  - `currentToken.collect` sees stage != Ready/Bootstrapping → skips evaluate.
     *  - `accountsFlow.collect` for the NeedsLogin branch only auto-evaluates
     *    when accounts are *added* (sibling sign-in), not removed.
     * Without this, the in-flight evaluate() could race a sibling-app account
     * lookup and momentarily bounce stage back through NeedsAccountChoice /
     * Ready, restoring the navController's saved back stack from the previous
     * session and surfacing a per-app NotFoundState against the new context.
     */
    fun logout() {
        _stage.value = AuthStage.NeedsLogin
        viewModelScope.launch {
            // Stop the push service and delete the FCM token from Firebase
            // before dropping the session, so this device stops receiving
            // pushes for the account we're leaving.
            PushTransport.tearDown(context)
            sessionManager.clearAll()
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

        // Mint the per-app JWT. This is the single source of truth — if it
        // fails the session is dead, drop to login.

        val result = authRepository.fetchToken(appName)
        if (result.isFailure) {
            sessionManager.clearAll()
            _stage.value = AuthStage.NeedsLogin
            return
        }

        // Single `_/identity` fetch reused for both the closing-account check
        // below and publishAccount() further down — they used to hit the same
        // endpoint twice per launch. Best-effort: a failed fetch shouldn't wedge
        // the launch, so fall through to the normal Ready path.
        val identityInfo = runCatching { authRepository.getIdentityInfo() }
            .onFailure { e -> Log.w(TAG, "getIdentityInfo failed at bootstrap", e) }
            .getOrNull()

        // A soft-deleted ("closing") account has a valid session but every app
        // action is refused server-side. Route to the reactivation interstitial
        // instead of into the app.
        if (identityInfo?.status?.status == "closing") {
            _stage.value = AuthStage.NeedsReactivation(identityInfo.status.purge)
            return
        }


        // Prefetch tokens for the client's other Mochi-apps so navigating
        // to them via the launchpad or a notification doesn't surface "app
        // token required" on the first API call. Fired off the bootstrap
        // coroutine into its own IO-dispatched job so the main app reaches
        // Ready as soon as the cold-start app's JWT (above) is in hand,
        // instead of stalling on N extra token round-trips. Best-effort per
        // app: a mint failure (user lacks access to that app on this server,
        // etc.) is swallowed; the cold-start app's mint above is the
        // canonical session check.
        prefetchApps
            .filter { other -> other != appName }
            .forEach { other ->
                viewModelScope.launch(Dispatchers.IO) {
                    authRepository.fetchToken(other)
                }
            }

        // Reconcile AccountManager with our just-validated session. The local
        // session is canonical "is logged in"; AccountManager is for sharing.
        // Republishing every bootstrap means a missing record (legacy state,
        // wiped authenticator, etc.) self-heals — only an explicit logout
        // (clearAll) or runtime account-removed event takes us back to login.
        runCatching { publishAccount(identityInfo?.identity) }

        // Theme + preferences are best-effort warm-ups.
        runCatching { themeRepository.fetchAndCacheTheme() }
        runCatching { preferencesManager.refresh() }

        // Language is fetched only after a fresh authentication. Returning
        // users keep whatever locale they last set.
        var recreate = false
        if (justAuthenticated) {
            justAuthenticated = false
            runCatching {
                val previousTag = LanguageStore.get(context)
                val newTag = languageRepository.fetchAndStore()
                if (newTag != null && newTag != previousTag) {
                    LocaleHelper.apply(context, newTag)
                    recreate = true
                }
            }
        }

        _stage.value = AuthStage.Ready(epoch = ++readyEpoch, recreateForLocale = recreate)
    }

    /**
     * Write/refresh our AccountManager record so cross-app sharing works.
     * Best-effort — failure here doesn't block this app, just means a
     * sibling install won't be able to silent-adopt this session.
     */
    private suspend fun publishAccount(identity: Identity?) {
        val session = sessionManager.currentToken.first() ?: return
        val server = sessionManager.serverUrl.first()
        if (identity == null || identity.identity.isBlank()) return

        // Local binding is the canonical "who am I" and must be written
        // independently of the best-effort AccountManager sharing below.
        // Historically the upsert ran first, so an addAccountExplicitly
        // SecurityException (e.g. a device where the authenticator isn't
        // registered) aborted the whole function and left boundIdentity empty —
        // which broke every screen that resolves the current person from it.
        sessionManager.setBoundAccount(identity.identity, server)

        val displayName = identity.name.takeIf { it.isNotBlank() }
            ?: identity.email.takeIf { it.isNotBlank() }
            ?: ""
        // Cross-app session sharing. Best-effort: a failure here only means a
        // sibling install can't silent-adopt this session, so it must not
        // propagate and undo the local binding above.
        runCatching {
            MochiAccount.upsert(
                context,
                identity = identity.identity,
                name = displayName,
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
