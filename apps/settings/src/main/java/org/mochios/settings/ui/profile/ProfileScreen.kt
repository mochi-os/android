package org.mochios.settings.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.R
import org.mochios.android.ui.components.EntityAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val id = uiState.identity

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val avatarUrl = if (id.fingerprint.isNotBlank() && viewModel.serverUrl.isNotBlank()) {
                    "${viewModel.serverUrl}/${id.fingerprint.replace("-", "")}/-/avatar"
                } else null
                EntityAvatar(
                    name = id.name.ifBlank { id.username },
                    src = avatarUrl,
                    seed = id.entity,
                    size = 80.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = id.name.ifBlank { id.username },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (id.username.isNotBlank()) {
                        Text(
                            text = "@${id.username}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider()

            Column {
                Text(
                    text = stringResource(R.string.profile_display_name),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = uiState.nameDraft,
                    onValueChange = viewModel::updateName,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = viewModel::saveName,
                    enabled = uiState.nameDraft.trim().isNotEmpty()
                        && uiState.nameDraft.trim() != id.name
                        && !uiState.isSaving,
                ) {
                    Text(stringResource(R.string.profile_save_name))
                }
            }

            HorizontalDivider()

            Column {
                Text(
                    text = stringResource(R.string.profile_privacy),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.profile_privacy_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = id.privacy == "public",
                        onClick = { viewModel.setPrivacy("public") },
                        label = { Text(stringResource(R.string.profile_privacy_public)) },
                    )
                    FilterChip(
                        selected = id.privacy == "private",
                        onClick = { viewModel.setPrivacy("private") },
                        label = { Text(stringResource(R.string.profile_privacy_private)) },
                    )
                }
            }

            HorizontalDivider()

            Column {
                Text(
                    text = stringResource(R.string.profile_fingerprint),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = id.fingerprint,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (uiState.error != null) {
                Text(
                    text = uiState.error?.toString().orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
