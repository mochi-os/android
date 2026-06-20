// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.settings

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
import org.mochios.android.auth.SessionManager
import org.mochios.android.model.AccessRule
import org.mochios.android.model.User
import org.mochios.android.util.NaturalCompare
import org.mochios.feeds.R
import org.mochios.feeds.model.Feed
import org.mochios.feeds.model.Group
import org.mochios.feeds.model.Member
import org.mochios.feeds.model.Source
import org.mochios.feeds.repository.FeedsRepository
import javax.inject.Inject

@HiltViewModel
class FeedSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FeedsRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    val feedId: String = savedStateHandle.get<String>("feedId") ?: ""

    // General tab
    private val _feedInfo = MutableStateFlow<Feed?>(null)
    val feedInfo: StateFlow<Feed?> = _feedInfo.asStateFlow()

    private val _feedName = MutableStateFlow("")
    val feedName: StateFlow<String> = _feedName.asStateFlow()

    private val _rssToken = MutableStateFlow<String?>(null)
    val rssToken: StateFlow<String?> = _rssToken.asStateFlow()

    private val _rssMode = MutableStateFlow("posts")
    val rssMode: StateFlow<String> = _rssMode.asStateFlow()

    private val _banner = MutableStateFlow("")
    val banner: StateFlow<String> = _banner.asStateFlow()

    private val _bannerOriginal = MutableStateFlow("")

    // Sources tab
    private val _sources = MutableStateFlow<List<Source>>(emptyList())
    val sources: StateFlow<List<Source>> = _sources.asStateFlow()

    private val _isLoadingSources = MutableStateFlow(false)
    val isLoadingSources: StateFlow<Boolean> = _isLoadingSources.asStateFlow()

    // After addSource: if the server returned a suggested credibility, this
    // holds the id+score so the UI can show a confirm dialog. Cleared once
    // the user accepts or dismisses.
    private val _suggestedCredibility = MutableStateFlow<SuggestedCredibility?>(null)
    val suggestedCredibility: StateFlow<SuggestedCredibility?> = _suggestedCredibility.asStateFlow()

    // Access tab
    private val _accessRules = MutableStateFlow<List<AccessRule>>(emptyList())
    val accessRules: StateFlow<List<AccessRule>> = _accessRules.asStateFlow()

    private val _isLoadingAccess = MutableStateFlow(false)
    val isLoadingAccess: StateFlow<Boolean> = _isLoadingAccess.asStateFlow()

    // AI tab
    private val _aiMode = MutableStateFlow("")
    val aiMode: StateFlow<String> = _aiMode.asStateFlow()

    private val _aiAccount = MutableStateFlow(0)
    val aiAccount: StateFlow<Int> = _aiAccount.asStateFlow()

    private val _aiAccounts = MutableStateFlow<List<org.mochios.android.model.Account>>(emptyList())
    val aiAccounts: StateFlow<List<org.mochios.android.model.Account>> = _aiAccounts.asStateFlow()

    private val _aiPrompts = MutableStateFlow<Map<String, String>>(emptyMap())
    val aiPrompts: StateFlow<Map<String, String>> = _aiPrompts.asStateFlow()

    private val _aiDefaults = MutableStateFlow<Map<String, String>>(emptyMap())
    val aiDefaults: StateFlow<Map<String, String>> = _aiDefaults.asStateFlow()

    // Common
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<MochiError?>(null)
    val error: StateFlow<MochiError?> = _error.asStateFlow()

    /** Snackbar message resource — screens render via stringResource. */
    private val _actionMessage = MutableStateFlow<Int?>(null)
    val actionMessage: StateFlow<Int?> = _actionMessage.asStateFlow()

    // User search for access control
    private val _userSearchResults = MutableStateFlow<List<User>>(emptyList())
    val userSearchResults: StateFlow<List<User>> = _userSearchResults.asStateFlow()

    // Groups for access control
    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = _groups.asStateFlow()

    // Members tab
    private val _members = MutableStateFlow<List<Member>>(emptyList())
    val members: StateFlow<List<Member>> = _members.asStateFlow()

    private val _isLoadingMembers = MutableStateFlow(false)
    val isLoadingMembers: StateFlow<Boolean> = _isLoadingMembers.asStateFlow()

    init {
        loadFeedInfo()
    }

    fun loadFeedInfo() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val info = repository.getFeedInfo(feedId)
                _feedInfo.value = info.feed
                _feedName.value = info.feed.name
                _aiMode.value = info.feed.aiMode ?: ""
                _aiAccount.value = info.feed.aiAccount
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // --- General ---

    fun setFeedName(name: String) {
        _feedName.value = name
    }

    fun saveFeedName() {
        val name = _feedName.value.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.renameFeed(feedId, name)
                _actionMessage.value = R.string.feeds_settings_feed_renamed
                _feedInfo.value = _feedInfo.value?.copy(name = name)
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            }
        }
    }

    fun deleteFeed(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.deleteFeed(feedId)
                onSuccess()
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            }
        }
    }

    fun setRssMode(mode: String) {
        _rssMode.value = mode
        _rssToken.value = null
    }

    fun generateRssToken() {
        viewModelScope.launch {
            try {
                val token = repository.getRssToken(feedId, _rssMode.value)
                val serverUrl = sessionManager.getServerUrlBlocking().trimEnd('/')
                _rssToken.value = "$serverUrl/feeds/$feedId/-/rss?token=$token"
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            }
        }
    }

    // --- Banner ---

    fun loadBanner() {
        viewModelScope.launch {
            try {
                val text = repository.getBanner(feedId)
                _banner.value = text
                _bannerOriginal.value = text
            } catch (_: Exception) {
                // Non-critical
            }
        }
    }

    fun setBannerText(text: String) {
        _banner.value = text
    }

    fun saveBanner() {
        viewModelScope.launch {
            try {
                repository.setBanner(feedId, _banner.value)
                _bannerOriginal.value = _banner.value
                _actionMessage.value = R.string.feeds_settings_banner_saved
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            }
        }
    }

    fun clearBanner() {
        _banner.value = ""
        viewModelScope.launch {
            try {
                repository.setBanner(feedId, "")
                _bannerOriginal.value = ""
                _actionMessage.value = R.string.feeds_settings_banner_cleared
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            }
        }
    }

    // --- Sources ---

    fun loadSources() {
        viewModelScope.launch {
            _isLoadingSources.value = true
            try {
                // Server returns sources in insertion order. Sort
                // case-insensitively by name for the UI, matching the web
                // sources page which sorts with naturalCompare.
                _sources.value = repository.getSources(feedId)
                    .sortedWith(compareBy(NaturalCompare) { it.name.ifBlank { it.url } })
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            } finally {
                _isLoadingSources.value = false
            }
        }
    }

    fun addSource(url: String, type: String) {
        viewModelScope.launch {
            try {
                val result = repository.addSource(feedId, url, type)
                _actionMessage.value = R.string.feeds_settings_source_added
                loadSources()
                val suggested = result.suggestedCredibility
                if (suggested != null && result.source.id.isNotEmpty()) {
                    _suggestedCredibility.value = SuggestedCredibility(
                        sourceId = result.source.id,
                        suggested = suggested
                    )
                }
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            }
        }
    }

    fun acceptSuggestedCredibility() {
        val pending = _suggestedCredibility.value ?: return
        _suggestedCredibility.value = null
        viewModelScope.launch {
            try {
                repository.editSource(feedId, pending.sourceId, credibility = pending.suggested)
                loadSources()
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            }
        }
    }

    fun dismissSuggestedCredibility() {
        _suggestedCredibility.value = null
    }

    fun editSource(id: String, name: String?, credibility: Int?, transform: String?) {
        viewModelScope.launch {
            try {
                repository.editSource(feedId, id, name, credibility, transform)
                _actionMessage.value = R.string.feeds_settings_source_updated
                loadSources()
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            }
        }
    }

    fun removeSource(id: String, deletePosts: Boolean) {
        viewModelScope.launch {
            try {
                repository.removeSource(feedId, id, deletePosts)
                _actionMessage.value = R.string.feeds_settings_source_removed
                loadSources()
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            }
        }
    }

    fun pollSource(source: String) {
        viewModelScope.launch {
            try {
                repository.pollSource(feedId, source)
                _actionMessage.value = R.string.feeds_settings_source_polling
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            }
        }
    }

    // --- Access ---

    fun loadAccessRules() {
        viewModelScope.launch {
            _isLoadingAccess.value = true
            try {
                _accessRules.value = repository.getAccessRules(feedId)
                    .sortedWith(compareBy(NaturalCompare) { it.name ?: it.subject })
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            } finally {
                _isLoadingAccess.value = false
            }
        }
    }

    fun setAccess(subject: String, level: String) {
        viewModelScope.launch {
            try {
                repository.setAccess(feedId, subject, level)
                _actionMessage.value = R.string.feeds_settings_access_updated
                loadAccessRules()
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            }
        }
    }

    fun revokeAccess(subject: String) {
        viewModelScope.launch {
            try {
                repository.revokeAccess(feedId, subject)
                _actionMessage.value = R.string.feeds_settings_access_revoked
                loadAccessRules()
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            }
        }
    }

    fun searchUsers(query: String) {
        viewModelScope.launch {
            try {
                _userSearchResults.value = repository.searchUsers(query)
            } catch (_: Exception) {
                _userSearchResults.value = emptyList()
            }
        }
    }

    fun loadGroups() {
        viewModelScope.launch {
            try {
                _groups.value = repository.getGroups()
                    .sortedWith(compareBy(NaturalCompare) { it.name })
            } catch (_: Exception) {
                _groups.value = emptyList()
            }
        }
    }

    // --- Members ---

    fun loadMembers() {
        viewModelScope.launch {
            _isLoadingMembers.value = true
            try {
                _members.value = repository.getMembers(feedId)
                    .sortedWith(compareBy(NaturalCompare) { it.name })
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            } finally {
                _isLoadingMembers.value = false
            }
        }
    }

    fun addMember(memberEntityId: String) {
        viewModelScope.launch {
            try {
                repository.addMember(feedId, memberEntityId)
                _actionMessage.value = R.string.feeds_settings_member_added
                loadMembers()
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            }
        }
    }

    fun removeMember(memberEntityId: String) {
        viewModelScope.launch {
            try {
                repository.removeMember(feedId, memberEntityId)
                _actionMessage.value = R.string.feeds_settings_member_removed
                loadMembers()
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            }
        }
    }

    // --- AI ---

    fun setAiMode(mode: String) {
        _aiMode.value = mode
        viewModelScope.launch {
            try {
                repository.setAiSettings(feedId, mode, _aiAccount.value)
                _actionMessage.value = R.string.feeds_settings_ai_mode_updated
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            }
        }
    }

    fun setAiAccount(accountId: Int) {
        _aiAccount.value = accountId
        viewModelScope.launch {
            try {
                repository.setAiSettings(feedId, _aiMode.value, accountId)
                _actionMessage.value = R.string.feeds_settings_ai_account_updated
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            }
        }
    }

    fun loadAiAccounts() {
        viewModelScope.launch {
            _aiAccounts.value = repository.listAiAccounts()
                .sortedWith(compareBy(NaturalCompare) { it.label })
        }
    }

    fun loadAiPrompts() {
        viewModelScope.launch {
            try {
                val (defaults, overrides) = repository.getAiPrompts(feedId)
                _aiDefaults.value = defaults
                _aiPrompts.value = defaults + overrides
            } catch (_: Exception) {
                // Non-critical
            }
        }
    }

    fun setAiPromptText(type: String, text: String) {
        _aiPrompts.value = _aiPrompts.value + (type to text)
    }

    fun saveAiPrompt(type: String) {
        val prompt = _aiPrompts.value[type] ?: ""
        viewModelScope.launch {
            try {
                repository.setAiPrompt(feedId, type, prompt)
                _actionMessage.value = R.string.feeds_settings_prompt_saved
            } catch (e: Exception) {
                _error.value = e.toMochiError()
            }
        }
    }

    fun resetAiPrompt(type: String) {
        val defaultVal = _aiDefaults.value[type] ?: ""
        _aiPrompts.value = _aiPrompts.value + (type to defaultVal)
        viewModelScope.launch {
            try {
                repository.setAiPrompt(feedId, type, "")
                _actionMessage.value = R.string.feeds_settings_prompt_reset
            } catch (_: Exception) { }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun setActionMessage(messageRes: Int) {
        _actionMessage.value = messageRes
    }
}

data class SuggestedCredibility(
    val sourceId: String,
    val suggested: Int
)
