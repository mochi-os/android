// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.ui.auth

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.mochios.android.R
import org.mochios.android.api.userMessage

@Composable
fun IdentityScreen(
    uiState: AuthUiState,
    onUpdateName: (String) -> Unit,
    onUpdatePrivacy: (String) -> Unit,
    onCreate: () -> Unit,
    onAbandon: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = stringResource(R.string.auth_create_identity_title),
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.auth_create_identity_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = uiState.identityName,
            onValueChange = onUpdateName,
            label = { Text(stringResource(R.string.auth_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onCreate() }),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Privacy radio group — matches web's identity-form public/private
        // selector. Public identities appear in the user directory; private
        // ones do not.
        Text(
            text = stringResource(R.string.auth_privacy_label),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            PrivacyOption(
                selected = uiState.identityPrivacy == "public",
                onClick = { onUpdatePrivacy("public") },
                title = stringResource(R.string.auth_privacy_public),
                description = stringResource(R.string.auth_privacy_public_description),
            )
            PrivacyOption(
                selected = uiState.identityPrivacy == "private",
                onClick = { onUpdatePrivacy("private") },
                title = stringResource(R.string.auth_privacy_private),
                description = stringResource(R.string.auth_privacy_private_description),
            )
        }

        if (uiState.error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = uiState.error.userMessage(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onCreate,
            enabled = !uiState.isLoading && uiState.identityName.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(stringResource(R.string.auth_create))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Terms / privacy / rules links — open in browser since they're
        // long-form markdown pages, not part of the in-app navigation.
        // Mirrors web's identity-form bottom links to /login/{rules,terms,privacy}.
        val server = uiState.serverUrl.trimEnd('/')
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextButton(onClick = { openExternal(context, "$server/login/rules") }) {
                Text(stringResource(R.string.auth_link_rules), style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { openExternal(context, "$server/login/terms") }) {
                Text(stringResource(R.string.auth_link_terms), style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { openExternal(context, "$server/login/privacy") }) {
                Text(stringResource(R.string.auth_link_privacy), style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onAbandon) {
            Text(
                text = stringResource(R.string.auth_use_different_account),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PrivacyOption(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun openExternal(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    context.startActivity(intent)
}
