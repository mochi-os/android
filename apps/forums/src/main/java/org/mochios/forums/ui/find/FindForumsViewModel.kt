package org.mochios.forums.ui.find

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.forums.model.DirectoryEntry
import org.mochios.forums.model.RecommendedForum
import org.mochios.forums.repository.ForumsRepository
import javax.inject.Inject

data class FindForumsUiState(
    val recommended: List<RecommendedForum> = emptyList(),
    val results: List<DirectoryEntry> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val isLoading: Boolean = false,
    val subscribed: Set<String> = emptySet(),
    // A forum found by pasting its URL (probe), distinct from directory results.
    val probeResult: ProbeForum? = null,
    val isProbing: Boolean = false,
    val error: MochiError? = null
)

/** A forum resolved from a pasted URL via `-/probe`. */
data class ProbeForum(
    val id: String,
    val name: String,
    val fingerprint: String,
    val server: String,
)

@HiltViewModel
class FindForumsViewModel @Inject constructor(
    private val repository: ForumsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FindForumsUiState())
    val uiState: StateFlow<FindForumsUiState> = _uiState.asStateFlow()

    init {
        loadRecommendations()
    }

    fun loadRecommendations() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val r = repository.getRecommendations()
                _uiState.value = _uiState.value.copy(recommended = r.forums, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun updateSearchQuery(q: String) {
        _uiState.value = _uiState.value.copy(searchQuery = q)
    }

    private fun looksLikeUrl(q: String): Boolean =
        q.startsWith("http://", ignoreCase = true) ||
            q.startsWith("https://", ignoreCase = true)

    fun search() {
        val q = _uiState.value.searchQuery.trim()
        if (q.isBlank()) return
        if (looksLikeUrl(q)) {
            probe(q)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearching = true, error = null, probeResult = null,
            )
            try {
                val r = repository.searchForums(q)
                _uiState.value = _uiState.value.copy(results = r, isSearching = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSearching = false, error = e.toMochiError())
            }
        }
    }

    private fun probe(url: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProbing = true, error = null, results = emptyList(), probeResult = null,
            )
            try {
                val r = repository.probe(url)
                _uiState.value = _uiState.value.copy(
                    isProbing = false,
                    probeResult = ProbeForum(
                        id = r.id,
                        name = r.name,
                        fingerprint = r.fingerprint,
                        server = r.server,
                    ),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isProbing = false, error = e.toMochiError())
            }
        }
    }

    fun subscribe(forumId: String, server: String? = null) {
        viewModelScope.launch {
            try {
                repository.subscribe(forumId, server)
                _uiState.value = _uiState.value.copy(subscribed = _uiState.value.subscribed + forumId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }
}
