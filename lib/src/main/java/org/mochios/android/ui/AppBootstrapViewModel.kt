package org.mochios.android.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.mochios.android.account.MochiAccount
import org.mochios.android.auth.AuthRepository
import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.LanguageRepository
import org.mochios.android.i18n.LanguageStore
import org.mochios.android.i18n.LocaleHelper
import org.mochios.android.i18n.PreferencesManager
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
     * Fully ready. [recreateForLocale] is true iff the language fetched
     * during bootstrap differs from what the host activity was using; the
     * activity should call `recreate()` once.
     */
    data class Ready(val recreateForLocale: Boolean = false) : AuthStage()
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
    private var justAuthenticated: Boolean = false

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
    fun start(appName: String) {
        if (this.appName == appName && _stage.value !is AuthStage.Booting) return
        this.appName = appName
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
     * User-initiated logout. Wipes local + AccountManager and sets stage
     * deterministically. Avoids relying on observer races (the DataStore
     * change and the AccountManager change fire on different paths and the
     * order isn't predictable).
     */
    fun logout() {
        viewModelScope.launch {
            sessionManager.clearAll()
            _stage.value = AuthStage.NeedsLogin
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
        try {
            authRepository.fetchToken(appName)
        } catch (e: Exception) {
            sessionManager.clearAll()
            _stage.value = AuthStage.NeedsLogin
            return
        }

        // Reconcile AccountManager with our just-validated session. The local
        // session is canonical "is logged in"; AccountManager is for sharing.
        // Republishing every bootstrap means a missing record (legacy state,
        // wiped authenticator, etc.) self-heals — only an explicit logout
        // (clearAll) or runtime account-removed event takes us back to login.
        runCatching { publishAccount() }

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

        _stage.value = AuthStage.Ready(recreateForLocale = recreate)
    }

    /**
     * Write/refresh our AccountManager record so cross-app sharing works.
     * Best-effort — failure here doesn't block this app, just means a
     * sibling install won't be able to silent-adopt this session.
     */
    private suspend fun publishAccount() {
        val session = sessionManager.currentToken.first() ?: return
        val server = sessionManager.serverUrl.first()
        val identity = runCatching { authRepository.getIdentity() }.getOrNull() ?: return
        if (identity.identity.isBlank()) return
        val displayName = identity.name.takeIf { it.isNotBlank() }
            ?: identity.email.takeIf { it.isNotBlank() }
            ?: ""
        MochiAccount.upsert(
            context,
            identity = identity.identity,
            name = displayName,
            server = server,
            fingerprint = identity.fingerprint.takeIf { it.isNotBlank() },
            session = session
        )
        sessionManager.setBoundAccount(identity.identity, server)
    }
}
