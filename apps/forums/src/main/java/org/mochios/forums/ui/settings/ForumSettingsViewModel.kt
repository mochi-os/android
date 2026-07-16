// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

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
import org.mochios.forums.R
import org.mochios.forums.model.AiPrompts
import org.mochios.forums.model.Forum
import org.mochios.forums.model.ForumMember
import org.mochios.forums.repository.ForumsRepository
import javax.inject.Inject

data class ForumSettingsUiState(
    val forum: Forum = Forum(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: MochiError? = null,
    val actionMessage: Int? = null,
    val deleted: Boolean = false,
    val accessRules: List<AccessRule> = emptyList(),
    val members: List<ForumMember> = emptyList(),
    val memberSearchResults: List<ForumMember> = emptyList(),
    val aiPrompts: AiPrompts? = null,
    val aiAccounts: List<org.mochios.android.model.Account> = emptyList(),
    val rssToken: String = "",
    val rssUrl: String = "",
    val userSearchResults: List<org.mochios.forums.model.User> = emptyList(),
    val groups: List<org.mochios.forums.model.Group> = emptyList(),
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
                val info = repository.getForumInfo(forumId)
                val accounts = repository.listAiAccounts()
                    .sortedWith(compareBy(NaturalCompare) { it.label })
                _uiState.value = _uiState.value.copy(forum = info.forum, aiAccounts = accounts, isLoading = false)
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
                    isSaving = false,
                    actionMessage = R.string.forums_settings_forum_renamed,
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
                    accessRules = r.rules.sortedWith(compareBy(NaturalCompare) {
                        it.name ?: it.subject
                    })
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
                _uiState.value = _uiState.value.copy(
                    actionMessage = R.string.forums_settings_access_updated,
                )
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
                _uiState.value = _uiState.value.copy(
                    actionMessage = R.string.forums_settings_access_revoked,
                )
                loadAccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun searchUsers(query: String) {
        if (query.trim().length < 2) {
            _uiState.value = _uiState.value.copy(userSearchResults = emptyList())
            return
        }
        viewModelScope.launch {
            try {
                val results = repository.searchUsers(query.trim())
                _uiState.value = _uiState.value.copy(userSearchResults = results)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(userSearchResults = emptyList())
            }
        }
    }

    fun loadGroups() {
        viewModelScope.launch {
            try {
                val groups = repository.listGroups()
                    .sortedWith(compareBy(NaturalCompare) { it.name })
                _uiState.value = _uiState.value.copy(groups = groups)
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(groups = emptyList())
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
                _uiState.value = _uiState.value.copy(
                    actionMessage = R.string.forums_settings_member_removed,
                )
                loadMembers()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun saveBanner(banner: String) {
        viewModelScope.launch {
            try {
                repository.setBanner(forumId, banner)
                _uiState.value = _uiState.value.copy(
                    forum = _uiState.value.forum.copy(banner = banner),
                    actionMessage = R.string.forums_settings_banner_saved,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    /**
     * Load the prompt defaults for the AI section's "reset to default" action.
     * The current mode/account/prompt values come from the forum row itself.
     */
    fun loadAiPrompts() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(aiPrompts = repository.getAiPrompts(forumId))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }

    fun setAiSettings(mode: String, account: String) {
        viewModelScope.launch {
            try {
                repository.setAiSettings(forumId, mode, account)
                _uiState.value = _uiState.value.copy(
                    forum = _uiState.value.forum.copy(aiMode = mode, aiAccount = account),
                    actionMessage = R.string.forums_settings_ai_updated,
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
                    actionMessage = R.string.forums_settings_prompt_saved,
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

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearActionMessage() {
        _uiState.value = _uiState.value.copy(actionMessage = null)
    }

    fun loadRssToken() {
        viewModelScope.launch {
            try {
                val r = repository.getRssToken(forumId, "posts")
                _uiState.value = _uiState.value.copy(rssToken = r.token, rssUrl = r.url)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.toMochiError())
            }
        }
    }
}
