// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.auth.StepUpClient
import org.mochios.android.ui.components.StepUpDialog

/**
 * Reusable step-up re-authentication plumbing, lifted from [LoginViewModel] so
 * the token / replication / system-pair screens can gate a sensitive mutation
 * on re-verification without each copying the dialog wiring.
 *
 * Embed one in a ViewModel, call [request] from a gated action (the run block
 * receives the proof token once the user re-verifies), render [StepUpHost] once
 * in the screen, and route errors through [onError].
 */
class StepUpController(
    val client: StepUpClient,
    private val scope: CoroutineScope,
    private val onError: (Throwable) -> Unit,
) {
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    private var pending: (suspend (String) -> Unit)? = null

    /** Open the step-up dialog; [run] fires with the proof token once the user
     *  re-verifies their login factor(s). */
    fun request(run: suspend (String) -> Unit) {
        pending = run
        _visible.value = true
    }

    fun onVerified(token: String) {
        _visible.value = false
        val run = pending
        pending = null
        if (run != null) {
            scope.launch {
                try {
                    run(token)
                } catch (e: Exception) {
                    onError(e)
                }
            }
        }
    }

    fun cancel() {
        _visible.value = false
        pending = null
    }
}

/** Renders the shared [StepUpDialog] while [controller] has a step-up in flight. */
@Composable
fun StepUpHost(controller: StepUpController) {
    val visible by controller.visible.collectAsState()
    if (visible) {
        StepUpDialog(
            client = controller.client,
            onDismiss = controller::cancel,
            onVerified = controller::onVerified,
        )
    }
}
