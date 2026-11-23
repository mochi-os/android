// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.mochios.android.R

/**
 * Holds an action that would drop unsaved edits - leaving an editor, or
 * switching what it shows - until the user discards them or keeps editing.
 * [DiscardDialog] asks the question while one is held.
 */
class DiscardGuard {
    /** The action waiting on an answer, or null when nothing is held. */
    var pending by mutableStateOf<(() -> Unit)?>(null)
        private set

    /** Run [action] now, or, when [dirty], hold it for [discard]. */
    fun request(dirty: Boolean, action: () -> Unit) {
        if (dirty) pending = action else action()
    }

    /** Drop the edits: run the held action. */
    fun discard() {
        val action = pending
        pending = null
        action?.invoke()
    }

    /** Keep editing: forget the held action. */
    fun keep() {
        pending = null
    }
}

/** A [DiscardGuard] kept for the life of the composition. */
@Composable
fun rememberDiscardGuard(): DiscardGuard = remember { DiscardGuard() }

/** Asks whether to discard unsaved changes while [guard] holds an action. */
@Composable
fun DiscardDialog(guard: DiscardGuard) {
    if (guard.pending == null) return
    MochiAlertDialog(
        onDismissRequest = guard::keep,
        title = stringResource(R.string.common_discard_title),
        confirmText = stringResource(R.string.common_discard),
        onConfirm = guard::discard,
        destructive = true,
        dismissText = stringResource(R.string.common_cancel),
    )
}
