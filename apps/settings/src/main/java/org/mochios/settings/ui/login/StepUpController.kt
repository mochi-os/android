// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mochios.android.auth.StepUpClient
import org.mochios.android.ui.components.StepUpDialog

/**
 * Step-up re-authentication for a ViewModel: call [request] from a gated action
 * (the block receives the proof token), render [StepUpHost] once in the screen,
 * errors go to [onError].
 */
class StepUpController(
    val client: StepUpClient,
    private val scope: CoroutineScope,
    private val onError: (Throwable) -> Unit,
) {
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    private var pending: (suspend (String) -> Unit)? = null
    private var cancelled: (() -> Unit)? = null

    /** Open the step-up dialog; [run] fires with the proof token once the user
     *  re-verifies their login factor(s), and [onCancel] when they back out,
     *  so a control that showed the change at once can put itself back. A
     *  request that replaces one still waiting cancels it. */
    fun request(onCancel: () -> Unit = {}, run: suspend (String) -> Unit) {
        val replaced = cancelled
        pending = run
        cancelled = onCancel
        _visible.value = true
        replaced?.invoke()
    }

    fun onVerified(token: String) {
        _visible.value = false
        val run = pending
        pending = null
        cancelled = null
        if (run != null) {
            scope.launch {
                try {
                    run(token)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onError(e)
                }
            }
        }
    }

    fun cancel() {
        _visible.value = false
        val back = cancelled
        pending = null
        cancelled = null
        back?.invoke()
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
