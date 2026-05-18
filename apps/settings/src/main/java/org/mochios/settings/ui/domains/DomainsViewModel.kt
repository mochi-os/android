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
import org.mochios.settings.api.Delegation
import org.mochios.settings.api.Domain
import org.mochios.settings.api.DomainDetailsData
import org.mochios.settings.api.DomainsApi
import org.mochios.settings.api.Route
import retrofit2.Response
import javax.inject.Inject

data class DomainsUiState(
    val isLoading: Boolean = true,
    val isAdmin: Boolean = false,
    val domains: List<Domain> = emptyList(),
    val details: Map<String, DomainDetailsData> = emptyMap(),
    val loadingDetails: Set<String> = emptySet(),
    val error: MochiError? = null,
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

    fun createDelegation(domain: String, path: String, owner: Long) = mutate {
        api.createDelegation(domain, path, owner).bodyOrThrow()
        loadDetails(domain)
    }

    fun deleteDelegation(domain: String, path: String, owner: Long) = mutate {
        api.deleteDelegation(domain, path, owner).bodyOrThrow()
        loadDetails(domain)
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
