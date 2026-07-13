// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.domains

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.util.NaturalCompare
import org.mochios.settings.api.Delegation
import org.mochios.settings.api.Domain
import org.mochios.settings.api.DomainDetailsData
import org.mochios.settings.api.DomainsApi
import org.mochios.settings.api.Route
import org.mochios.settings.api.RouteApp
import org.mochios.settings.api.RouteEntity
import org.mochios.settings.api.UserSearchResult
import retrofit2.Response
import javax.inject.Inject

data class DomainsUiState(
    val isLoading: Boolean = true,
    val isAdmin: Boolean = false,
    val domains: List<Domain> = emptyList(),
    val details: Map<String, DomainDetailsData> = emptyMap(),
    val loadingDetails: Set<String> = emptySet(),
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

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val data = api.getDomains().bodyOrThrow()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    domains = data.domains,
                    isAdmin = data.admin,
                )
                // Refresh any expanded details
                _uiState.value.details.keys.forEach { loadDetails(it) }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun loadDetails(domain: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                loadingDetails = _uiState.value.loadingDetails + domain,
            )
            try {
                val data = api.getDomain(domain).bodyOrThrow()
                _uiState.value = _uiState.value.copy(
                    details = _uiState.value.details + (domain to data),
                    loadingDetails = _uiState.value.loadingDetails - domain,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.toMochiError(),
                    loadingDetails = _uiState.value.loadingDetails - domain,
                )
            }
        }
    }

    fun createDomain(name: String) = mutate { api.createDomain(name).bodyOrThrow() }

    fun deleteDomain(name: String) = mutate { api.deleteDomain(name).bodyOrThrow() }

    fun verifyDomain(name: String) = mutate { api.verifyDomain(name).bodyOrThrow() }

    fun setTls(name: String, enabled: Boolean) =
        mutate { api.updateDomain(name, tls = enabled.toString()).bodyOrThrow() }

    fun createRoute(
        domain: String,
        path: String,
        method: String,
        target: String,
        priority: Int,
    ) = mutate {
        api.createRoute(domain, path, method, target, priority).bodyOrThrow()
        loadDetails(domain)
    }

    fun updateRoute(
        domain: String,
        path: String,
        method: String,
        target: String,
        priority: Int,
        enabled: Boolean,
    ) = mutate {
        api.updateRoute(domain, path, method, target, priority, enabled.toString()).bodyOrThrow()
        loadDetails(domain)
    }

    fun deleteRoute(domain: String, path: String) = mutate {
        api.deleteRoute(domain, path).bodyOrThrow()
        loadDetails(domain)
    }

    fun createDelegation(domain: String, path: String, owner: String) = mutate {
        api.createDelegation(domain, path, owner).bodyOrThrow()
        loadDetails(domain)
    }

    fun deleteDelegation(domain: String, path: String, owner: String) = mutate {
        api.deleteDelegation(domain, path, owner).bodyOrThrow()
        loadDetails(domain)
    }

    /** Load the route-target pickers (installed apps and the user's entities)
     *  the first time a route dialog opens. Idempotent: skips the round-trips
     *  once both lists are populated. */
    fun loadRouteTargets() {
        if (_uiState.value.apps.isNotEmpty() && _uiState.value.entities.isNotEmpty()) return
        viewModelScope.launch {
            try {
                val apps = api.listApps().bodyOrThrow().apps
                    .sortedWith(compareBy(NaturalCompare) { it.name })
                _uiState.value = _uiState.value.copy(apps = apps)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
            try {
                val entities = api.listEntities().bodyOrThrow().entities
                    .sortedWith(compareBy(NaturalCompare) { it.name })
                _uiState.value = _uiState.value.copy(entities = entities)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    /** Autocomplete users for the delegation dialog (admin only). The server
     *  returns at most 10 matches; empty query yields no results. */
    fun searchUsers(query: String) {
        val q = query.trim()
        if (q.length < 2) {
            _uiState.value = _uiState.value.copy(userResults = emptyList())
            return
        }
        viewModelScope.launch {
            try {
                val users = api.searchUsers(q).bodyOrThrow().users
                _uiState.value = _uiState.value.copy(userResults = users)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun clearUserResults() {
        _uiState.value = _uiState.value.copy(userResults = emptyList())
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    private fun <T> Response<T>.bodyOrThrow(): T {
        if (!isSuccessful) throw RuntimeException("HTTP ${code()}")
        @Suppress("UNCHECKED_CAST")
        return (body() ?: Unit) as T
    }
}

// Convenience aliases used by the screen
typealias DomainModel = Domain
typealias RouteModel = Route
typealias DelegationModel = Delegation
