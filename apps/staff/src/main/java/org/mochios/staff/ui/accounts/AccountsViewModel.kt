// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.annotation.StringRes
import org.mochios.android.api.MochiError
import org.mochios.android.api.toMochiError
import org.mochios.android.ui.components.LoadState
import org.mochios.android.util.Paging
import org.mochios.android.util.append
import org.mochios.staff.R
import org.mochios.staff.model.Account
import org.mochios.staff.model.AccountSummary
import org.mochios.staff.repository.StaffRepository
import javax.inject.Inject

/**
 * Filters mirror the web `AccountsPage`: `status`
 * (`active`/`suspended`/`banned`) and `seller` (`yes`/`no`), null for any;
 * `query` is debounced 300 ms by the screen.
 */
data class AccountsUiState(
    val status: String? = null,
    val seller: String? = null,
    val query: String = "",
    val isLoading: Boolean = true,
    /** The next page, for the list's foot. */
    val load: LoadState = LoadState.Idle,
    val accounts: List<Account> = emptyList(),
    val total: Long = 0,
    /** How far the list has been read. */
    val paging: Paging = Paging(),
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

/** One-shot events emitted to the screen. */
sealed interface AccountsEvent {
    data class Toast(@StringRes val messageRes: Int) : AccountsEvent
    data class Error(val error: MochiError) : AccountsEvent
}

/** What the snackbar says once [type] has been done. */
@StringRes
internal fun accountActionDone(type: AccountActionType): Int = when (type) {
    AccountActionType.SUSPEND -> R.string.staff_accounts_suspended_toast
    AccountActionType.UNSUSPEND -> R.string.staff_accounts_unsuspended_toast
    AccountActionType.BAN -> R.string.staff_accounts_banned_toast
    AccountActionType.UNBAN -> R.string.staff_accounts_unbanned_toast
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
    private var moreJob: Job? = null

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
        // A load-more still in flight belongs to the filter being replaced;
        // left running, it would splice that filter's rows into the new list.
        moreJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = _state.value
            _state.value = s.copy(isLoading = true, load = LoadState.Idle, error = null)
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
                    paging = Paging().advance(r.accounts.size, r.total, r.next),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.toMochiError(),
                )
            }
        }
    }

    /** Reads the next page, from the list's foot or its retry. */
    fun loadMore() {
        val s = _state.value
        if (s.load == LoadState.Loading || s.isLoading || !s.paging.more) return
        moreJob = viewModelScope.launch {
            _state.value = s.copy(load = LoadState.Loading)
            try {
                val r = repository.listAccounts(
                    status = s.status,
                    seller = s.seller,
                    query = s.query.takeIf { it.isNotBlank() },
                    page = s.paging.page,
                    limit = PAGE_SIZE,
                    after = s.paging.cursor,
                )
                _state.value = _state.value.copy(
                    load = LoadState.Idle,
                    accounts = _state.value.accounts.append(r.accounts) { account -> account.id },
                    total = r.total,
                    paging = s.paging.advance(r.accounts.size, r.total, r.next),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(load = LoadState.Failed(e.toMochiError()))
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
     * `reason` is required by the server for suspend and ban; the dialog
     * enforces it.
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
                _events.tryEmit(AccountsEvent.Toast(accountActionDone(pending.type)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false)
                _events.tryEmit(AccountsEvent.Error(e.toMochiError()))
            }
        }
    }

    companion object {
        // SavedStateHandle keys for filter persistence across process death.
        private const val KEY_STATUS = "filter_status"
        private const val KEY_SELLER = "filter_seller"
        private const val KEY_QUERY = "filter_query"
    }
}
