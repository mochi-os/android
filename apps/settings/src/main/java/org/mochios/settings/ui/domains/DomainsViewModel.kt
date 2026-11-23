// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.domains

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.messageOrFallback
import org.mochios.android.api.toMochiError
import org.mochios.android.api.unwrapEmpty
import org.mochios.android.api.unwrapRaw
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.AppContext
import org.mochios.android.util.NaturalCompare
import org.mochios.android.util.SEARCH_DEBOUNCE
import org.mochios.settings.R
import org.mochios.settings.api.Domain
import org.mochios.settings.api.DomainDetailsData
import org.mochios.settings.api.DomainsApi
import org.mochios.settings.api.RouteApp
import org.mochios.settings.api.RouteEntity
import org.mochios.settings.api.UserSearchResult
import javax.inject.Inject

data class DomainsUiState(
    val isLoading: Boolean = true,
    val isAdmin: Boolean = false,
    val domains: List<Domain> = emptyList(),
    val details: Map<String, DomainDetailsData> = emptyMap(),
    val loadingDetails: Set<String> = emptySet(),
    /** Why a domain's details could not be read, by domain. */
    val detailsErrors: Map<String, MochiError> = emptyMap(),
    /** The domains whose verification record is being looked up. */
    val verifying: Set<String> = emptySet(),
    /** A failed read of the list; an action's failure is a message instead. */
    val error: MochiError? = null,
    // Route-target pickers: installed apps and the user's own entities. Loaded
    // lazily the first time a route dialog opens. Mirror the web useApps /
    // useEntities hooks; sorted by name (naturalCompare equivalent) here so the
    // pickers render alphabetically.
    val apps: List<RouteApp> = emptyList(),
    val entities: List<RouteEntity> = emptyList(),
    // Delegation user-search autocomplete results, keyed off the latest query.
    val userResults: List<UserSearchResult> = emptyList(),
)

@HiltViewModel
class DomainsViewModel @Inject constructor(
    private val api: DomainsApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DomainsUiState())
    val uiState: StateFlow<DomainsUiState> = _uiState.asStateFlow()

    /** What an action found or why it failed, for the screen's snackbar. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** In-flight user search, cancelled by the next keystroke. */
    private var userSearchJob: Job? = null

    init { refresh() }

    /**
     * Read the list. A [quiet] read, after an action, leaves the list on
     * screen while it is read again rather than blanking it to a spinner.
     */
    fun refresh(quiet: Boolean = false) {
        viewModelScope.launch {
            if (!quiet) _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val data = api.getDomains().unwrapRaw()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    domains = data.domains,
                    isAdmin = data.admin,
                    error = null,
                )
                // Refresh any expanded details
                _uiState.value.details.keys.forEach { loadDetails(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    /** Dismiss a surfaced error once the screen has shown it. */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun loadDetails(domain: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loadingDetails = _uiState.value.loadingDetails + domain,
                detailsErrors = _uiState.value.detailsErrors - domain,
            )
            try {
                val data = api.getDomain(domain).unwrapRaw()
                _uiState.value = _uiState.value.copy(
                    details = _uiState.value.details + (domain to data),
                    loadingDetails = _uiState.value.loadingDetails - domain,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    detailsErrors = _uiState.value.detailsErrors + (domain to e.toMochiError()),
                    loadingDetails = _uiState.value.loadingDetails - domain,
                )
            }
        }
    }

    fun createDomain(name: String) = mutate(R.string.domain_create_failed) { api.createDomain(name).unwrapEmpty() }

    fun deleteDomain(name: String) = mutate(R.string.domain_delete_failed) { api.deleteDomain(name).unwrapEmpty() }

    /** Look the verification record up, and say what was found, as the web does. */
    fun verifyDomain(name: String) {
        if (name in _uiState.value.verifying) return
        _uiState.value = _uiState.value.copy(verifying = _uiState.value.verifying + name)
        viewModelScope.launch {
            try {
                val verified = api.verifyDomain(name).unwrapRaw().verified
                _messages.emit(string(if (verified) R.string.domain_verified else R.string.domain_verify_missing))
                if (verified) refresh(quiet = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.emit(failure(e, R.string.domain_verify_failed))
            } finally {
                _uiState.value = _uiState.value.copy(verifying = _uiState.value.verifying - name)
            }
        }
    }

    /**
     * The TLS switch: it moves on the tap, and goes back if the server
     * refuses, unless a later tap has moved it since, as the RSS feeds' do.
     */
    fun setTls(name: String, enabled: Boolean) {
        val value = if (enabled) 1 else 0
        val before = _uiState.value.domains.firstOrNull { it.domain == name }?.tls ?: return
        setTlsShown(name, value)
        viewModelScope.launch {
            try {
                api.updateDomain(name, tls = enabled.toString()).unwrapEmpty()
                refresh(quiet = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_uiState.value.domains.any { it.domain == name && it.tls == value }) setTlsShown(name, before)
                _messages.emit(failure(e, R.string.domain_update_failed))
            }
        }
    }

    private fun setTlsShown(name: String, tls: Int) {
        _uiState.value = _uiState.value.copy(
            domains = _uiState.value.domains.map { if (it.domain == name) it.copy(tls = tls) else it },
            details = _uiState.value.details.mapValues { (key, value) ->
                if (key == name) value.copy(domain = value.domain.copy(tls = tls)) else value
            },
        )
    }

    fun createRoute(
        domain: String,
        path: String,
        method: String,
        target: String,
        priority: Int,
    ) = mutate(R.string.route_create_failed) {
        api.createRoute(domain, path, method, target, priority).unwrapEmpty()
        loadDetails(domain)
    }

    fun updateRoute(
        domain: String,
        path: String,
        method: String,
        target: String,
        priority: Int,
        enabled: Boolean,
    ) = mutate(R.string.route_update_failed) {
        api.updateRoute(domain, path, method, target, priority, enabled.toString()).unwrapEmpty()
        loadDetails(domain)
    }

    fun deleteRoute(domain: String, path: String) = mutate(R.string.route_delete_failed) {
        api.deleteRoute(domain, path).unwrapEmpty()
        loadDetails(domain)
    }

    fun createDelegation(domain: String, path: String, owner: String) = mutate(R.string.domain_delegation_create_failed) {
        api.createDelegation(domain, path, owner).unwrapEmpty()
        loadDetails(domain)
    }

    fun deleteDelegation(domain: String, path: String, owner: String) = mutate(R.string.domain_delegation_delete_failed) {
        api.deleteDelegation(domain, path, owner).unwrapEmpty()
        loadDetails(domain)
    }

    /** Load the route-target pickers (installed apps and the user's entities)
     *  the first time a route dialog opens. Idempotent: skips the round-trips
     *  once both lists are populated. */
    fun loadRouteTargets() {
        if (_uiState.value.apps.isNotEmpty() && _uiState.value.entities.isNotEmpty()) return
        viewModelScope.launch {
            try {
                val apps = api.listApps().unwrapRaw().apps
                    .sortedWith(compareBy(NaturalCompare) { it.name })
                _uiState.value = _uiState.value.copy(apps = apps)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.emit(e.toMochiError().userMessage())
            }
            try {
                val entities = api.listEntities().unwrapRaw().entities
                    .sortedWith(compareBy(NaturalCompare) { it.name })
                _uiState.value = _uiState.value.copy(entities = entities)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.emit(e.toMochiError().userMessage())
            }
        }
    }

    /** Autocomplete users for the delegation dialog (admin only). The server
     *  returns at most 10 matches; empty query yields no results. */
    fun searchUsers(query: String) {
        // Each keystroke replaces the last: without this a typed name is one
        // request per letter, and a slow early response can land after a later
        // one and overwrite the newer results.
        userSearchJob?.cancel()
        val q = query.trim()
        if (q.length < 2) {
            _uiState.value = _uiState.value.copy(userResults = emptyList())
            return
        }
        userSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE)
            try {
                val users = api.searchUsers(q).unwrapRaw().users
                _uiState.value = _uiState.value.copy(userResults = users)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.emit(e.toMochiError().userMessage())
            }
        }
    }

    fun clearUserResults() {
        _uiState.value = _uiState.value.copy(userResults = emptyList())
    }

    /** Run an action, then read the list again quietly; a failure is said with [fallback] as its words. */
    private fun mutate(fallback: Int, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                refresh(quiet = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.emit(failure(e, fallback))
            }
        }
    }

    /** The server's reason for a refusal, else the action's own words for its failure. */
    private fun failure(e: Throwable, fallback: Int): String = e.toMochiError().messageOrFallback(string(fallback))

    private fun string(id: Int): String = AppContext.get().getString(id)
}
