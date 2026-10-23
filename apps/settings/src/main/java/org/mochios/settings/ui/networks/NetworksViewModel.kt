// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.networks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
import javax.inject.Inject

/** The scheme the server hands an OAuth consent back to the app on. */
private const val OAUTH_SCHEME = "mochi"

data class NetworksUiState(
    val isLoading: Boolean = true,
    val data: NetworksData? = null,
    val error: MochiError? = null,
    /** What a change is running for: a handle's network, an account, or a dialog's action. */
    val busy: String? = null,
)

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
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetworksUiState())
    val uiState: StateFlow<NetworksUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Outcomes to show the user once, success and failure alike. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _launch = MutableStateFlow<String?>(null)
    /** A remote server's consent page for the screen to open in the browser. */
    val launch: StateFlow<String?> = _launch.asStateFlow()

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
        viewModelScope.launch {
            if (!quiet) _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val data = api.data().unwrapRaw()
                _uiState.value = _uiState.value.copy(isLoading = false, data = data, error = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    /** Dismiss a surfaced error once the screen has shown it. */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun consumeLaunch() {
        _launch.value = null
    }

    /** Whether a name may be reserved; null when the server could not say. */
    suspend fun available(name: String, domain: String): Availability? =
        runCatching { api.available(name, domain).unwrapRaw() }.getOrNull()

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

    /** Switch a network on for a handle: the hosted account it makes. */
    fun create(handle: Handle, network: String, onMade: (NetworkAccount) -> Unit) =
        change("${handle.name}/$network", R.string.networks_create_failed) {
            val made = api.create(network, handle.name, handle.domain).unwrapRaw()
            say(R.string.networks_account_created)
            onMade(made)
        }

    /**
     * A Bluesky account moving here from another server: the account waits for
     * it, and the moving app is given a password minted for it.
     */
    fun arrive(handle: Handle, onMade: (NetworkAccount) -> Unit) =
        change("${handle.name}/bluesky", R.string.networks_create_failed) {
            val made = api.create("bluesky", handle.name, handle.domain, arriving = "true").unwrapRaw()
            say(R.string.networks_arrive_ready)
            onMade(made)
        }

    fun remove(account: NetworkAccount) = change(account.id, R.string.networks_remove_failed) {
        api.remove(account.id).unwrapEmpty()
        say(R.string.networks_removed)
    }

    /**
     * Link an existing account: a form provider signs in at once; an OAuth one
     * sends the browser to the remote server's consent, which hands back to
     * the app.
     */
    fun link(type: String, fields: Map<String, String>, oauth: Boolean, onLinked: () -> Unit) =
        change("link", R.string.networks_link_failed) {
            val payload = HashMap<String, String>(fields)
            payload["type"] = type
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
            } catch (e: Exception) {
                _messages.emit(failure(e, R.string.networks_link_failed))
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
            if (choice == HOME_NEW) {
                api.home(account.id, "", create = "true").unwrapRaw()
            } else {
                api.home(account.id, choice).unwrapRaw()
            }
            say(R.string.networks_home_saved)
            onSaved()
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

    /** A hosted account's keys; null when they could not be read. */
    suspend fun keys(account: NetworkAccount): List<AccountKey>? =
        runCatching { api.keys(account.id).unwrapRaw() }.getOrNull()

    fun replaceKeys(account: NetworkAccount, onReplaced: () -> Unit) =
        change("keys", R.string.networks_keys_failed) {
            api.replaceKeys(account.id).unwrapEmpty()
            say(R.string.networks_keys_replaced)
            onReplaced()
        }

    /** A Bluesky recovery key, whose private half is handed to [onMade] and kept nowhere. */
    fun recovery(account: NetworkAccount, onMade: (String) -> Unit) =
        change("recovery", R.string.networks_recovery_failed) {
            onMade(api.recovery(account.id).unwrapRaw().secret)
        }

    /** A password for signing in from another app, shown once. */
    fun password(account: NetworkAccount, device: String, onMade: (String) -> Unit) =
        change("password", R.string.networks_password_failed) {
            onMade(api.password(account.id, device.trim()).unwrapRaw().password)
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

    /**
     * Run a change under [key], with [fallback] as what a failure says when the
     * server gave no reason; whatever it changed is refetched.
     */
    private fun change(key: String, fallback: Int, block: suspend () -> Unit) {
        if (_uiState.value.busy != null) return
        _uiState.value = _uiState.value.copy(busy = key)
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _messages.emit(failure(e, fallback))
            } finally {
                _uiState.value = _uiState.value.copy(busy = null)
            }
            refresh(quiet = true)
        }
    }

    private suspend fun say(id: Int) {
        _messages.emit(AppContext.get().getString(id))
    }

    /** The server's reason for a refusal, else the action's own words for its failure. */
    private fun failure(e: Exception, fallback: Int): String = when (val error = e.toMochiError()) {
        is MochiError.Unknown -> error.message ?: AppContext.get().getString(fallback)
        else -> error.userMessage()
    }

    companion object {
        /** The home-feed choice that makes a new feed named after the account; a feed's id is never this. */
        const val HOME_NEW = "new"
    }
}
