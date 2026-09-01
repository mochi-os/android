// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.mochios.android.R
import org.mochios.android.api.MochiError
import org.mochios.android.api.userMessage

/**
 * The chrome every "create a feed / forum / wiki / project" screen wears: a top
 * bar that goes back, and a bottom bar holding the last error above a
 * full-width submit button that turns into a spinner while the request is in
 * flight. Both bars lock themselves while [isBusy].
 *
 * Wrap the fields in [CreateEntityForm] unless the body scrolls itself.
 *
 * @param title Screen title.
 * @param submitLabel Label of the submit button.
 * @param submitEnabled Whether the form may be submitted.
 * @param isBusy Whether a create request is in flight.
 * @param error Last failure, shown above the button; null hides the line.
 * @param onBack Called by the top bar's back button.
 * @param onSubmit Called by the submit button.
 * @param content Screen body, given the scaffold's inner padding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEntityScaffold(
    title: String,
    submitLabel: String,
    submitEnabled: Boolean,
    isBusy: Boolean,
    error: MochiError?,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    MochiIconButton(onClick = onBack, enabled = !isBusy) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    error?.let { failure ->
                        Text(
                            text = failure.userMessage(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    MochiButton(
                        onClick = onSubmit,
                        enabled = submitEnabled,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(submitLabel)
                        }
                    }
                }
            }
        },
        content = content
    )
}

/**
 * The scrolling body of a [CreateEntityScaffold]: the scaffold's padding, a
 * scroll, and the 16dp inset the create screens share.
 *
 * @param padding Inner padding handed to the scaffold's content slot.
 * @param fields The form's fields, stacked.
 */
@Composable
fun CreateEntityForm(
    padding: PaddingValues,
    fields: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        content = fields
    )
}

/**
 * A form row pairing a wrapping label with a trailing switch, as the create
 * screens use for "allow anyone to search for this".
 *
 * @param label Text describing what the switch turns on.
 * @param checked Whether the switch is on.
 * @param onCheckedChange Called with the new state.
 * @param enabled Whether the switch accepts input.
 * @param labelStyle Style of the label text.
 */
@Composable
fun LabeledSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    labelStyle: TextStyle = LocalTextStyle.current
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = labelStyle,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
