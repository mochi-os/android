// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.moderation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import org.mochios.android.i18n.AppContext
import org.mochios.settings.R
import org.mochios.settings.api.ModerationApi
import org.mochios.settings.api.Report
import org.mochios.settings.ui.login.SettingsStepUpClient
import org.mochios.settings.ui.login.StepUpController
import javax.inject.Inject

enum class ReportState(val value: String) { OPEN("open"), RESOLVED("resolved") }

/**
 * What an administrator can do about a report: block the person it is
 * about, where they are on another server, block the other server of either
 * party, or suspend the user it is about. A reporter on another server is
 * blocked by their server, since on the fediverse a server reports as a
 * representative of its own.
 */
sealed class ReportAction {
    data class Person(val target: String, val name: String) : ReportAction()
    data class Server(val host: String) : ReportAction()
    data class Suspend(val username: String) : ReportAction()
}

internal fun reportActions(report: Report): List<ReportAction> {
    val actions = mutableListOf<ReportAction>()
    val person = report.person
    if (person != null && person.blockable) actions += ReportAction.Person(person.id, person.name)
    listOfNotNull(person?.host, report.reporter.host)
        .filter { it.isNotEmpty() }
        .distinct()
        .forEach { actions += ReportAction.Server(it) }
    val account = report.account
    if (account != null && account.status != "suspended") actions += ReportAction.Suspend(account.username)
    return actions
}

data class SystemReportsUiState(
    val state: ReportState = ReportState.OPEN,
    val isLoading: Boolean = true,
    val reports: List<Report> = emptyList(),
    val error: MochiError? = null,
    val busy: String? = null,
)

/**
 * The reports that reach the server from other networks (the federation
 * plan, phase 40): those waiting, dismissed or resolved by blocking or
 * suspending whoever they are about, and those resolved.
 */
@HiltViewModel
class SystemReportsViewModel @Inject constructor(
    private val api: ModerationApi,
    stepUpClient: SettingsStepUpClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SystemReportsUiState())
    val uiState: StateFlow<SystemReportsUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** A suspension is gated by a step-up, as it is on the Users screen. */
    val stepUp = StepUpController(
        client = stepUpClient,
        scope = viewModelScope,
        onError = { e -> _uiState.value = _uiState.value.copy(error = e.toMochiError()) },
    )

    init { refresh() }

    fun show(state: ReportState) {
        if (state == _uiState.value.state) return
        _uiState.value = _uiState.value.copy(state = state, reports = emptyList())
        refresh()
    }

    fun refresh(quiet: Boolean = false) {
        val state = _uiState.value.state
        viewModelScope.launch {
            if (!quiet) _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val answer = api.reports(state.value).unwrapRaw()
                if (_uiState.value.state == state) {
                    _uiState.value = _uiState.value.copy(isLoading = false, reports = answer.reports, error = null)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.toMochiError())
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun dismiss(report: Report) = change(report.id, R.string.reports_dismissed) {
        api.resolve(report.id, "dismissed").unwrapEmpty()
    }

    fun act(report: Report, action: ReportAction) {
        when (action) {
            is ReportAction.Person -> change(report.id, R.string.networks_blocked) {
                api.resolve(report.id, "blocked", target = action.target).unwrapEmpty()
            }
            is ReportAction.Server -> change(report.id, R.string.networks_blocked) {
                api.resolve(report.id, "blocked", target = action.host).unwrapEmpty()
            }
            is ReportAction.Suspend -> stepUp.request { token ->
                change(report.id, R.string.system_users_toast_suspended) {
                    api.resolve(report.id, "suspended", token = token).unwrapEmpty()
                }
            }
        }
    }

    private fun change(key: String, done: Int, block: suspend () -> Unit) {
        if (_uiState.value.busy != null) return
        _uiState.value = _uiState.value.copy(busy = key)
        viewModelScope.launch {
            try {
                block()
                _messages.emit(AppContext.get().getString(done))
            } catch (e: Exception) {
                val error = e.toMochiError()
                _messages.emit(
                    if (error is MochiError.Unknown) error.message ?: AppContext.get().getString(R.string.reports_failed) else error.userMessage(),
                )
            } finally {
                _uiState.value = _uiState.value.copy(busy = null)
            }
            refresh(quiet = true)
        }
    }
}
