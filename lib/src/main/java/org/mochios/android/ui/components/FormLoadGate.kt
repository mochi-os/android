// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.mochios.android.api.MochiError

/**
 * Body of a [CreateEntityScaffold] whose form needs data first: a spinner while
 * it loads, the error with a retry if the load failed, then the form in a
 * [CreateEntityForm].
 *
 * @param padding Inner padding handed to the scaffold's content slot.
 * @param isReady Whether the form has what it needs.
 * @param loadError Failure loading that data, or null.
 * @param onRetry Called by the error state's retry button.
 * @param fields The form's fields, stacked.
 */
@Composable
fun FormLoadGate(
    padding: PaddingValues,
    isReady: Boolean,
    loadError: MochiError?,
    onRetry: () -> Unit,
    fields: @Composable ColumnScope.() -> Unit
) {
    if (isReady) {
        CreateEntityForm(padding, fields)
        return
    }
    Box(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (loadError != null) {
            ErrorState(error = loadError, onRetry = onRetry)
        } else {
            CircularProgressIndicator()
        }
    }
}

/**
 * [FormLoadGate] for a form built from loaded data: shows the form once [data]
 * is there and hands it over, so the form never has to deal with null.
 *
 * @param padding Inner padding handed to the scaffold's content slot.
 * @param data Data the form is built from, null until loaded.
 * @param loadError Failure loading that data, or null.
 * @param onRetry Called by the error state's retry button.
 * @param fields The form's fields, stacked, given the loaded data.
 */
@Composable
fun <T : Any> FormLoadGate(
    padding: PaddingValues,
    data: T?,
    loadError: MochiError?,
    onRetry: () -> Unit,
    fields: @Composable ColumnScope.(T) -> Unit
) {
    FormLoadGate(
        padding = padding,
        isReady = data != null,
        loadError = loadError,
        onRetry = onRetry
    ) {
        if (data != null) {
            fields(data)
        }
    }
}
