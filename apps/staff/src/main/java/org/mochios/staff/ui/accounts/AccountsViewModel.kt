// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.api.userMessage
import org.mochios.staff.model.Account
import org.mochios.staff.model.AccountSummary
import org.mochios.staff.repository.StaffRepository
import javax.inject.Inject

/**
 * UI state for [AccountsScreen]. Mirrors the same axes as the web
 * `AccountsPage`:
 *
 *   - `status`  — account moderation filter (`active`, `suspended`,
 *                 `banned`) or null for "any".
 *   - `seller`  — seller filter (`yes` / `no`) or null for "any".
 *   - `query`   — substring search against biography / name; the screen
 *                 debounces user input by 300 ms before firing.
 *
 * Pagination is page-based; the repository's `listAccounts` accepts
 * `page` / `limit`, so the Android model mirrors that.
 */
data class AccountsUiState(
    val status: String? = null,
    val seller: String? = null,
    val query: String = "",
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val total: Long = 0,
    val error: MochiError? = null,
    val pendingAction: PendingAccountAction? = null,
    val historyAccount: Account? = null,
    val submitting: Boolean = false,
)

/** Four moderator actions the dialog can drive. */
enum class AccountActionType { SUSPEND, UNSUSPEND, BAN, UNBAN }

/** Inflight action context — the dialog binds this to its inputs. */
data class PendingAccountAction(
    val type: AccountActionType,
    val account: Account,
)

/** One-shot events emitted to the screen (toasts). */
sealed class AccountsEvent {
    data class Toast(val message: String) : AccountsEvent()
}

private const val PAGE_SIZE = 20

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val repository: StaffRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AccountsUiState(
            status = savedStateHandle.get<String>(KEY_STATUS),
            seller = savedStateHandle.get<String>(KEY_SELLER),
            query = savedStateHandle.get<String>(KEY_QUERY) ?: "",
        ),
    )
    val state: StateFlow<AccountsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AccountsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AccountsEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null

    init {
        reload()
    }

    /** Update the status filter and refetch from page 1. */
    fun setStatus(status: String?) {
        if (_state.value.status == status) return
        _state.value = _state.value.copy(status = status)
        savedStateHandle[KEY_STATUS] = status
        reload()
    }

    /** Update the seller filter and refetch from page 1. */
    fun setSeller(seller: String?) {
        if (_state.value.seller == seller) return
        _state.value = _state.value.copy(seller = seller)
        savedStateHandle[KEY_SELLER] = seller
        reload()
    }

    /**
     * Set the search query. The caller (screen) is responsible for
     * debouncing; the ViewModel always refetches when called.
     */
    fun setQuery(query: String) {
        if (_state.value.query == query) return
        _state.value = _state.value.copy(query = query)
        savedStateHandle[KEY_QUERY] = query
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = _state.value
            _state.value = s.copy(isLoading = true, error = null)
            try {
                val r = repository.listAccounts(
                    status = s.status,
                    seller = s.seller,
                    query = s.query.takeIf { it.isNotBlank() },
                    page = 1,
                    limit = PAGE_SIZE,
                )
                _state.value = _state.value.copy(
                    isLoading = false,
                    accounts = r.accounts,
                    total = r.total,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoadingMore || s.isLoading || s.accounts.size >= s.total) return
        val nextPage = (s.accounts.size / PAGE_SIZE) + 1
        viewModelScope.launch {
            _state.value = s.copy(isLoadingMore = true)
            try {
                val r = repository.listAccounts(
                    status = s.status,
                    seller = s.seller,
                    query = s.query.takeIf { it.isNotBlank() },
                    page = nextPage,
                    limit = PAGE_SIZE,
                )
                _state.value = _state.value.copy(
                    isLoadingMore = false,
                    accounts = _state.value.accounts + r.accounts,
                    total = r.total,
                )
            } catch (_: Exception) {
                _state.value = _state.value.copy(isLoadingMore = false)
            }
        }
    }

    // ---- Dialog plumbing ----

    fun openAction(type: AccountActionType, account: Account) {
        _state.value = _state.value.copy(
            pendingAction = PendingAccountAction(type, account),
        )
    }

    fun dismissAction() {
        _state.value = _state.value.copy(pendingAction = null, submitting = false)
    }

    fun openHistory(account: Account) {
        _state.value = _state.value.copy(historyAccount = account)
    }

    fun dismissHistory() {
        _state.value = _state.value.copy(historyAccount = null)
    }

    /**
     * Submit the currently-pending action. `reason` is required for
     * SUSPEND / BAN on the wire; the dialog enforces presence so we
     * just forward whatever was typed.
     */
    fun submitAction(reason: String, notes: String) {
        val pending = _state.value.pendingAction ?: return
        if (_state.value.submitting) return
        _state.value = _state.value.copy(submitting = true)
        viewModelScope.launch {
            try {
                val id = pending.account.id
                val updated: AccountSummary = when (pending.type) {
                    AccountActionType.SUSPEND ->
                        repository.suspendAccount(
                            id,
                            reason.takeIf { it.isNotBlank() },
                            notes.takeIf { it.isNotBlank() },
                        )
                    AccountActionType.UNSUSPEND ->
                        repository.unsuspendAccount(
                            id,
                            notes.takeIf { it.isNotBlank() },
                        )
                    AccountActionType.BAN ->
                        repository.banAccount(
                            id,
                            reason.takeIf { it.isNotBlank() },
                            notes.takeIf { it.isNotBlank() },
                        )
                    AccountActionType.UNBAN ->
                        repository.unbanAccount(
                            id,
                            notes.takeIf { it.isNotBlank() },
                        )
                }
                // Refresh the affected row in-place so the new status shows
                // immediately. Other rows are untouched.
                val replaced = _state.value.accounts.map { row ->
                    if (row.id == id) row.copy(
                        status = updated.status,
                        reason = updated.reason,
                        rating = if (updated.rating != 0.0) updated.rating else row.rating,
                        reviews = if (updated.reviews != 0L) updated.reviews else row.reviews,
                        sales = if (updated.sales != 0L) updated.sales else row.sales,
                        updated = if (updated.updated != 0L) updated.updated else row.updated,
                    ) else row
                }
                _state.value = _state.value.copy(
                    submitting = false,
                    pendingAction = null,
                    accounts = replaced,
                )
                _events.tryEmit(AccountsEvent.Toast(successMessage(pending.type)))
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false)
                _events.tryEmit(AccountsEvent.Toast(e.toMochiError().userMessage()))
            }
        }
    }

    private fun successMessage(type: AccountActionType): String = when (type) {
        AccountActionType.SUSPEND -> SUCCESS_SUSPENDED
        AccountActionType.UNSUSPEND -> SUCCESS_UNSUSPENDED
        AccountActionType.BAN -> SUCCESS_BANNED
        AccountActionType.UNBAN -> SUCCESS_UNBANNED
    }

    companion object {
        // Sentinel markers that the screen swaps for localised strings
        // before showing the snackbar — keeps the ViewModel free of
        // Android Context.
        const val SUCCESS_SUSPENDED = "@suspended"
        const val SUCCESS_UNSUSPENDED = "@unsuspended"
        const val SUCCESS_BANNED = "@banned"
        const val SUCCESS_UNBANNED = "@unbanned"

        // SavedStateHandle keys for filter persistence across process death.
        private const val KEY_STATUS = "filter_status"
        private const val KEY_SELLER = "filter_seller"
        private const val KEY_QUERY = "filter_query"
    }
}
