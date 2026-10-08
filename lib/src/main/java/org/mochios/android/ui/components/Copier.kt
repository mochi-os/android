// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import android.content.ClipData
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.mochios.android.R
import org.mochios.android.util.sensitiveClip

/**
 * Whether the system confirms a copy itself. Android 13 and later show their
 * own clipboard preview on every copy, so a message from the app would say
 * it twice; earlier versions show nothing, so the app has to.
 */
val systemConfirmsCopy: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * Copies text to the clipboard the same way everywhere in the app: the text
 * goes to the clipboard, and on a version of Android that does not confirm a
 * copy itself a short message says so, in the screen's snackbar when it has
 * one and as a toast otherwise. Get one with [rememberCopier].
 */
@Stable
class Copier internal constructor(
    private val clipboard: Clipboard,
    private val scope: CoroutineScope,
    private val context: Context,
    private val snackbar: SnackbarHostState?,
    private val copied: String,
) {

    /**
     * Copies [value] under [label], the name the clipboard keeps it by. A
     * [sensitive] value, such as a token or a secret, is kept out of the
     * system's preview. [message] replaces the general "Copied to clipboard"
     * where the copy has more to say. [always] shows [message] even where
     * the system confirms the copy, for one that says more than that it was
     * copied, such as that the address is a new one. [quiet] leaves the
     * message to the caller, as [CopyButton]'s check mark does.
     */
    fun copy(
        value: String,
        label: String = "value",
        sensitive: Boolean = false,
        message: String? = null,
        always: Boolean = false,
        quiet: Boolean = false,
    ) {
        val clip = if (sensitive) {
            sensitiveClip(label, value)
        } else {
            ClipData.newPlainText(label, value)
        }
        scope.launch {
            clipboard.setClipEntry(clip.toClipEntry())
        }
        if (quiet || (systemConfirmsCopy && !always)) {
            return
        }
        val text = message ?: copied
        if (snackbar != null) {
            scope.launch {
                snackbar.showSnackbar(text)
            }
        } else {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * A [Copier] for this screen. Pass the screen's [snackbar] when it has one,
 * so a confirmation shows there rather than as a toast.
 */
@Composable
fun rememberCopier(snackbar: SnackbarHostState? = null): Copier {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val copied = stringResource(R.string.common_copied)
    return remember(clipboard, scope, context, snackbar, copied) {
        Copier(clipboard, scope, context, snackbar, copied)
    }
}
