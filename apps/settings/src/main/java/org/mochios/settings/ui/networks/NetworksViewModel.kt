// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.networks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.unwrapEmpty
import org.mochios.android.api.unwrapRaw
import org.mochios.android.api.userMessage
import org.mochios.android.auth.AuthRepository
import org.mochios.android.auth.OAuthPkce
import org.mochios.android.auth.SessionManager
import org.mochios.android.i18n.AppContext
import org.mochios.settings.R
import org.mochios.settings.api.AccountKey
import org.mochios.settings.api.Availability
import org.mochios.settings.api.Handle
import org.mochios.settings.api.NetworkAccount
import org.mochios.settings.api.NetworksApi
import org.mochios.settings.api.NetworksData
import org.mochios.settings.ui.login.SettingsStepUpClient
import org.mochios.settings.ui.login.StepUpController
import javax.inject.Inject

/** The scheme the server hands an OAuth consent back to the app on. */
private const val OAUTH_SCHEME = "mochi"

data class NetworksUiState(
    val isLoading: Boolean = true,
    val data: NetworksData? = null,
    val error: MochiError? = null,
    /** What changes are waiting or running for: a handle's network, an account, or a dialog's action. */
    val busy: Set<String> = emptySet(),
    /**
     * Where a network switch was moved to while its change goes, by handle's
     * network or account: it moves when tapped, or for a removal once that is
     * confirmed, and shows what the server holds once the list is read again.
     */
    val moving: Map<String, Boolean> = emptyMap(),
)

/**
 * The Keys dialog while it is open: its account, whether it opened on the
 * recovery key's question, the keys once read or why they could not be, and
 * the recovery key once made. The key is shown this once and kept nowhere
 * else, so it lives here, where turning the phone does not lose it.
 */
data class KeysDialogState(
    val account: NetworkAccount,
    val offer: Boolean = false,
    val keys: List<AccountKey>? = null,
    val failure: String? = null,
    val secret: String = "",
)

/**
 * The dialog that signs another app in while it is open: its account, with
 * the home feed the server last answered for it, which says whether one is
 * still to be chosen, and the password once minted, shown this once.
 */
data class PasswordDialogState(
    val account: NetworkAccount,
    val password: String = "",
)

/**
 * The words for a failure: the server's reason for a refusal, else
 * [fallback], the action's own words for it.
 */
internal fun failureText(e: Throwable, fallback: Int): String = when (val error = e.toMochiError()) {
    is MochiError.Unknown -> error.message ?: AppContext.get().getString(fallback)
    else -> error.userMessage()
}

/**
 * A Mastodon or Bluesky account becomes a way to sign in once linked, so
 * linking one takes the step-up first, as linking a sign-in provider does;
 * another network's account links at once.
 */
internal fun linkGated(type: String, stepUp: StepUpController, link: (String?) -> Unit) {
    if (type == "mastodon" || type == "bluesky") stepUp.request { token -> link(token) } else link(null)
}

/**
 * The Networks screen (the federation plan, phases 9 to 11): the user's
 * handles, the hosted account each network makes of one, the accounts linked
 * on other servers, and what is blocked and muted. Every change refetches the
 * whole, as the web page does, since one shows up in several places at once.
 */
@HiltViewModel
class NetworksViewModel @Inject constructor(
    private val api: NetworksApi,
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
    stepUpClient: SettingsStepUpClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetworksUiState())
    val uiState: StateFlow<NetworksUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Outcomes to show the user once, success and failure alike. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _launch = MutableStateFlow<String?>(null)
    /** A remote server's consent page for the screen to open in the browser. */
    val launch: StateFlow<String?> = _launch.asStateFlow()

    private val _keysDialog = MutableStateFlow<KeysDialogState?>(null)
    /** The Keys dialog, while it is open. */
    val keysDialog: StateFlow<KeysDialogState?> = _keysDialog.asStateFlow()

    private val _passwordDialog = MutableStateFlow<PasswordDialogState?>(null)
    /** The dialog that signs another app in, while it is open. */
    val passwordDialog: StateFlow<PasswordDialogState?> = _passwordDialog.asStateFlow()

    /**
     * Step-up gate for a link that becomes a way to sign in, and for the
     * recovery key and the minted password, each of which hands over the
     * account. Each gated action runs as a change, which reports its own
     * failure in its own words; this reports only a failure to start one.
     */
    val stepUp = StepUpController(
        client = stepUpClient,
        scope = viewModelScope,
        onError = { e -> viewModelScope.launch { _messages.emit(failureText(e, R.string.networks_change_failed)) } },
    )

    init {
        refresh()
        // The consent comes back to the app as a link return: the link is
        // landed against the verifier this screen began it with.
        viewModelScope.launch {
            sessionManager.oauthLinkReturn.collect { (code, error) ->
                if (error != null) {
                    sessionManager.clearOAuthLinkReturn()
                    sessionManager.consumeOAuthLinkVerifier()
                    say(if (error == "access_denied") R.string.networks_link_denied else R.string.networks_link_failed)
                } else if (code != null) {
                    sessionManager.clearOAuthLinkReturn()
                    land(code)
                }
            }
        }
    }

    fun refresh(quiet: Boolean = false) {
        viewModelScope.launch { load(quiet) }
    }

    /** Read the list, returning once it has landed or failed. */
    private suspend fun load(quiet: Boolean) {
        if (!quiet) _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        try {
            val data = api.data().unwrapRaw()
            _uiState.value = _uiState.value.copy(isLoading = false, data = data, error = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
        }
    }

    /** Dismiss a surfaced error once the screen has shown it. */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun consumeLaunch() {
        _launch.value = null
    }

    /** Whether a name may be reserved, or why the server could not say. */
    suspend fun available(name: String, domain: String): Result<Availability> = try {
        Result.success(api.available(name, domain).unwrapRaw())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun reserve(name: String, domain: String, onReserved: () -> Unit) =
        change("reserve", R.string.networks_reserve_failed) {
            api.reserve(name, domain).unwrapEmpty()
            say(R.string.networks_reserved)
            onReserved()
        }

    /** A name under a handle, by its suffix. */
    fun addName(parent: Handle, name: String, onAdded: () -> Unit) =
        change("name", R.string.networks_name_failed) {
            api.reserve(name, parent.domain).unwrapEmpty()
            say(R.string.networks_name_added)
            onAdded()
        }

    fun release(handle: Handle) = change("release", R.string.networks_release_failed) {
        api.release(handle.name, handle.domain).unwrapEmpty()
        say(R.string.networks_released)
    }

    /**
     * Switch a network on for a handle: the hosted account it makes. A
     * Bluesky account is offered its recovery key the moment it exists.
     */
    fun create(handle: Handle, network: String) =
        change("${handle.name}/$network", R.string.networks_create_failed, moved = true) {
            val made = api.create(network, handle.name, handle.domain).unwrapRaw()
            say(R.string.networks_account_created)
            if (network == "bluesky" && made.id.isNotEmpty()) openKeys(made, offer = true)
        }

    /**
     * A Bluesky account moving here from another server: the account waits for
     * it, and the moving app is given a password minted for it.
     */
    fun arrive(handle: Handle) =
        change("${handle.name}/bluesky", R.string.networks_create_failed) {
            val made = api.create("bluesky", handle.name, handle.domain, arriving = "true").unwrapRaw()
            say(R.string.networks_arrive_ready)
            if (made.id.isNotEmpty()) openPassword(made)
        }

    fun remove(account: NetworkAccount) = change(account.id, R.string.networks_remove_failed, moved = false) {
        api.remove(account.id).unwrapEmpty()
        say(R.string.networks_removed)
    }

    /**
     * Link an existing account: a form provider signs in at once; an OAuth one
     * sends the browser to the remote server's consent, which hands back to
     * the app.
     */
    fun link(type: String, fields: Map<String, String>, oauth: Boolean, onLinked: () -> Unit) =
        linkGated(type, stepUp) { token -> linkWith(type, fields, oauth, token, onLinked) }

    private fun linkWith(type: String, fields: Map<String, String>, oauth: Boolean, token: String?, onLinked: () -> Unit) =
        change("link", R.string.networks_link_failed) {
            val payload = HashMap<String, String>(fields)
            payload["type"] = type
            if (token != null) payload["token"] = token
            if (oauth) {
                val verifier = OAuthPkce.generateVerifier()
                payload["scheme"] = OAUTH_SCHEME
                payload["challenge"] = OAuthPkce.challengeFor(verifier)
                val answer = api.link(payload).unwrapRaw()
                // Recorded only once the server has answered, and in one write
                // with the nonce: a verifier left behind by a failed begin is
                // what an injected return needs.
                sessionManager.saveOAuthLinkVerifier(verifier, answer.nonce.ifEmpty { null })
                _launch.value = answer.url
            } else {
                api.link(payload).unwrapRaw()
                say(R.string.networks_linked)
            }
            onLinked()
        }

    private fun land(code: String) {
        viewModelScope.launch {
            try {
                val verifier = sessionManager.consumeOAuthLinkVerifier()
                val token = sessionManager.getToken("settings")
                if (verifier == null || token == null) {
                    say(R.string.networks_link_failed)
                    return@launch
                }
                authRepository.exchangeOAuthLink(code, verifier, token)
                say(R.string.networks_linked)
                refresh(quiet = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.emit(failureText(e, R.string.networks_link_failed))
            }
        }
    }

    /** The name an account's profile shows. */
    fun rename(account: NetworkAccount, label: String, onSaved: () -> Unit) =
        change("profile", R.string.networks_update_failed) {
            api.update(account.id, label = label.trim()).unwrapRaw()
            say(R.string.networks_account_updated)
            onSaved()
        }

    /** Whether the account is linked from the profile page. */
    fun toggleProfile(account: NetworkAccount) = change(account.id, R.string.networks_profile_failed) {
        api.update(account.id, profile = !account.profile).unwrapRaw()
    }

    /** Whether a hosted account is shown on its network as automated. */
    fun toggleAutomated(account: NetworkAccount) = change(account.id, R.string.networks_change_failed) {
        api.update(account.id, automated = !account.automated).unwrapRaw()
    }

    /** A hosted fediverse account's aliases, the whole list each time. */
    fun aliases(account: NetworkAccount, aliases: List<String>, onSaved: () -> Unit) =
        change("aliases", R.string.networks_update_failed) {
            api.aliases(account.id, Gson().toJson(aliases)).unwrapRaw()
            say(R.string.networks_account_updated)
            onSaved()
        }

    /** The labellers a hosted Bluesky account reads, the whole list each time. */
    fun labelers(account: NetworkAccount, labelers: List<String>, onSaved: () -> Unit) =
        change("labelers", R.string.networks_update_failed) {
            api.update(account.id, labelers = Gson().toJson(labelers)).unwrapRaw()
            say(R.string.networks_account_updated)
            onSaved()
        }

    /** Where posts from other apps signed in to the account land: a feed's id, [HOME_NEW] or "". */
    fun home(account: NetworkAccount, choice: String, onSaved: () -> Unit) =
        change("home", R.string.networks_home_failed) {
            saveHome(account, choice)
            say(R.string.networks_home_saved)
            onSaved()
        }

    /**
     * Make [choice] the account's home feed and answer the account as it now
     * stands. A new feed names the home it replaces, the one [account] has,
     * so a retry whose first attempt made one keeps that instead of making
     * another.
     */
    private suspend fun saveHome(account: NetworkAccount, choice: String): NetworkAccount =
        if (choice == HOME_NEW) {
            api.home(account.id, "", create = "true", previous = account.home).unwrapRaw()
        } else {
            api.home(account.id, choice).unwrapRaw()
        }

    fun move(account: NetworkAccount, address: String, onMoved: () -> Unit) =
        change("move", R.string.networks_move_failed) {
            api.move(account.id, address.trim()).unwrapRaw()
            say(R.string.networks_moved)
            onMoved()
        }

    fun test(account: NetworkAccount) = change(account.id, R.string.networks_test_failed) {
        val result = api.test(account.id).unwrapRaw()
        _messages.emit(result.message)
    }

    /**
     * Open the Keys dialog on [account], on the recovery key's question when
     * [offer], and read its keys.
     */
    fun openKeys(account: NetworkAccount, offer: Boolean = false) {
        _keysDialog.value = KeysDialogState(account = account, offer = offer)
        readKeys(account)
    }

    /** Close the Keys dialog, forgetting any recovery key it showed. */
    fun closeKeys() {
        _keysDialog.value = null
    }

    /** Read the open Keys dialog's keys again, after they could not be read. */
    fun retryKeys() {
        _keysDialog.value?.let { readKeys(it.account) }
    }

    /** Change the Keys dialog while it is still open on [account]. */
    private fun keysFor(account: NetworkAccount, change: (KeysDialogState) -> KeysDialogState) {
        _keysDialog.update { open -> if (open != null && open.account.id == account.id) change(open) else open }
    }

    /** A hosted account's keys into its open dialog, or why they could not be read. */
    private fun readKeys(account: NetworkAccount) {
        viewModelScope.launch {
            keysFor(account) { it.copy(keys = null, failure = null) }
            try {
                val keys = api.keys(account.id).unwrapRaw()
                keysFor(account) { it.copy(keys = keys) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                keysFor(account) { it.copy(failure = failureText(e, R.string.networks_keys_load_failed)) }
            }
        }
    }

    fun replaceKeys(account: NetworkAccount) =
        change("keys", R.string.networks_keys_failed) {
            api.replaceKeys(account.id).unwrapEmpty()
            say(R.string.networks_keys_replaced)
            readKeys(account)
        }

    /**
     * A Bluesky recovery key, behind a step-up since it can take the account
     * away: its private half goes into the open Keys dialog and nowhere else.
     */
    fun recovery(account: NetworkAccount) = stepUp.request { token ->
        change("recovery", R.string.networks_recovery_failed) {
            val secret = api.recovery(account.id, token).unwrapRaw().secret
            keysFor(account) { it.copy(secret = secret, offer = false) }
            readKeys(account)
        }
    }

    /** Open the dialog that signs another app in to [account]. */
    fun openPassword(account: NetworkAccount) {
        _passwordDialog.value = PasswordDialogState(account = account)
    }

    /** Close the sign-in dialog, forgetting any password it showed. */
    fun closePassword() {
        _passwordDialog.value = null
    }

    /** Change the sign-in dialog while it is still open on [account]. */
    private fun passwordFor(account: NetworkAccount, change: (PasswordDialogState) -> PasswordDialogState) {
        _passwordDialog.update { open -> if (open != null && open.account.id == account.id) change(open) else open }
    }

    /**
     * A password for signing in from another app on the device, behind a
     * step-up since it never expires. [home], a feed's id or [HOME_NEW], is
     * first made the account's home feed, where a Bluesky app's posts land;
     * null leaves it as it is. The home feed the server answers then stands
     * on the dialog's account, so a retry after a failed mint does not ask
     * for one again.
     */
    fun password(account: NetworkAccount, device: String, home: String?) = stepUp.request { token ->
        change("password", R.string.networks_password_failed) {
            if (home != null) {
                val homed = try {
                    saveHome(account, home)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _messages.emit(failureText(e, R.string.networks_home_failed))
                    return@change
                }
                passwordFor(account) { it.copy(account = it.account.copy(home = homed.home)) }
            }
            val minted = api.password(account.id, device.trim(), token).unwrapRaw().password
            passwordFor(account) { it.copy(password = minted) }
        }
    }

    fun block(target: String, onBlocked: () -> Unit) = change("block", R.string.networks_block_failed) {
        api.block(target.trim()).unwrapEmpty()
        say(R.string.networks_blocked)
        onBlocked()
    }

    fun unblock(target: String) = change(target, R.string.networks_block_failed) {
        api.unblock(target).unwrapEmpty()
        say(R.string.networks_unblocked)
    }

    fun unmute(actor: String) = change(actor, R.string.networks_block_failed) {
        api.unmute(actor).unwrapEmpty()
        say(R.string.networks_unmuted)
    }

    // Actions run one at a time, in the order tapped: one tapped while another
    // runs waits here instead of being dropped, and shows as busy meanwhile.
    private val queue = Mutex()

    /**
     * Run a change under [key], queued behind any under way, with [fallback]
     * as what a failure says when the server gave no reason; whatever it
     * changed is refetched. A switch's change names where the switch was
     * [moved] to, which it shows until the list has been read again.
     */
    private fun change(key: String, fallback: Int, moved: Boolean? = null, block: suspend () -> Unit) {
        // The same action tapped again while it waits or runs is the one tap.
        if (key in _uiState.value.busy) return
        _uiState.update { state ->
            state.copy(busy = state.busy + key, moving = if (moved == null) state.moving else state.moving + (key to moved))
        }
        viewModelScope.launch {
            queue.withLock {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _messages.emit(failureText(e, fallback))
                } finally {
                    _uiState.update { it.copy(busy = it.busy - key) }
                }
                // The switch lets go of where it was moved only once the list
                // it falls back to has landed (or failed), never in between.
                load(quiet = true)
                if (moved != null) _uiState.update { it.copy(moving = it.moving - key) }
            }
        }
    }

    private suspend fun say(id: Int) {
        _messages.emit(AppContext.get().getString(id))
    }

    companion object {
        /** The home-feed choice that makes a new feed named after the account; a feed's id is never this. */
        const val HOME_NEW = "new"
    }
}
