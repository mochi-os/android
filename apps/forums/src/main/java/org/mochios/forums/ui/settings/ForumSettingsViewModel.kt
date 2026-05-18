package org.mochios.forums.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.model.AccessRule
import org.mochios.android.util.NaturalCompare
import org.mochios.forums.model.AiPrompts
import org.mochios.forums.model.AiSettings
import org.mochios.forums.model.Forum
import org.mochios.forums.model.ForumMember
import org.mochios.forums.repository.ForumsRepository
import javax.inject.Inject

data class ForumSettingsUiState(
    val forum: Forum = Forum(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: MochiError? = null,
    val deleted: Boolean = false,
    val accessRules: List<AccessRule> = emptyList(),
    val members: List<ForumMember> = emptyList(),
    val memberSearchResults: List<ForumMember> = emptyList(),
    val banner: String = "",
    val aiSettings: AiSettings? = null,
    val aiPrompts: AiPrompts? = null,
    val aiAccounts: List<org.mochios.android.model.Account> = emptyList(),
    val rssToken: String = "",
    val rssUrl: String = "",
)

@HiltViewModel
class ForumSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ForumsRepository
) : ViewModel() {

    val forumId: String = savedStateHandle["forumId"] ?: ""

    private val _uiState = MutableStateFlow(ForumSettingsUiState())
    val uiState: StateFlow<ForumSettingsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val r = repository.viewForum(forumId)
                _uiState.value = _uiState.value.copy(forum = r.forum, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun rename(newName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            try {
                repository.renameForum(forumId, newName)
                _uiState.value = _uiState.value.copy(
                    forum = _uiState.value.forum.copy(name = newName),
                    isSaving = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.toMochiError())
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            try {
                repository.deleteForum(forumId)
                _uiState.value = _uiState.value.copy(deleted = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun loadAccess() {
        viewModelScope.launch {
            try {
                val r = repository.getAccess(forumId)
                _uiState.value = _uiState.value.copy(
                    accessRules = r.rules.sortedWith(compareBy(NaturalCompare) { it.name ?: it.subject })
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun setAccess(target: String, level: String) {
        viewModelScope.launch {
            try {
                repository.setAccess(forumId, target, level)
                loadAccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun revokeAccess(target: String) {
        viewModelScope.launch {
            try {
                repository.revokeAccess(forumId, target)
                loadAccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun loadMembers() {
        viewModelScope.launch {
            try {
                val r = repository.getMembers(forumId)
                _uiState.value = _uiState.value.copy(
                    members = r.members.sortedWith(compareBy(NaturalCompare) { it.name })
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun searchMembers(query: String) {
        viewModelScope.launch {
            try {
                val r = repository.searchMembers(forumId, query)
                _uiState.value = _uiState.value.copy(memberSearchResults = r.members)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun removeMember(memberId: String) {
        viewModelScope.launch {
            try {
                repository.removeMember(forumId, memberId)
                loadMembers()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun loadBanner() {
        viewModelScope.launch {
            try {
                val r = repository.getBanner(forumId)
                _uiState.value = _uiState.value.copy(banner = r.banner)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun saveBanner(banner: String) {
        viewModelScope.launch {
            try {
                repository.setBanner(forumId, banner)
                _uiState.value = _uiState.value.copy(banner = banner)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun loadAi() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    // Drive aiSettings off the already-loaded forum row — there's
                    // no separate read endpoint; calling the POST ai/settings as
                    // a GET zeroed the columns.
                    aiSettings = AiSettings(
                        mode = _uiState.value.forum.aiMode,
                        account = _uiState.value.forum.aiAccount.toString(),
                    ),
                    aiPrompts = repository.getAiPrompts(forumId),
                    aiAccounts = repository.listAiAccounts()
                        .sortedWith(compareBy(NaturalCompare) { it.label }),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun setAiSettings(mode: String, account: Int) {
        viewModelScope.launch {
            try {
                repository.setAiSettings(forumId, mode, account)
                _uiState.value = _uiState.value.copy(
                    aiSettings = AiSettings(mode = mode, account = account.toString()),
                    forum = _uiState.value.forum.copy(aiMode = mode, aiAccount = account),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun setAiPrompt(type: String, prompt: String) {
        viewModelScope.launch {
            try {
                repository.setAiPrompt(forumId, type, prompt)
                val current = _uiState.value.aiPrompts?.prompts.orEmpty()
                _uiState.value = _uiState.value.copy(
                    aiPrompts = _uiState.value.aiPrompts?.copy(prompts = current + (type to prompt))
                        ?: AiPrompts(prompts = mapOf(type to prompt)),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun clearNotifications() {
        viewModelScope.launch {
            try {
                repository.clearNotifications(forumId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun unsubscribe(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.unsubscribe(forumId)
                onDone()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun loadRssToken() {
        viewModelScope.launch {
            try {
                val r = repository.getRssToken()
                _uiState.value = _uiState.value.copy(rssToken = r.token, rssUrl = r.url)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }
}
