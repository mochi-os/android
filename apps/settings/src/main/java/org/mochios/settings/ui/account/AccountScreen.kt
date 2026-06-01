package org.mochios.settings.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.android.R as MochiR
import org.mochios.settings.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(MochiR.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading && state.identity.entity.isBlank()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            IdentitySection(
                state = state,
                onNameDraftChange = viewModel::updateName,
                onSaveName = viewModel::saveName,
                onPrivacy = viewModel::setPrivacy,
            )

            state.error?.let { err ->
                Text(text = err.toString(), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun IdentitySection(
    state: AccountUiState,
    onNameDraftChange: (String) -> Unit,
    onSaveName: () -> Unit,
    onPrivacy: (String) -> Unit,
) {
    val id = state.identity
    var editingName by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.account_section_identity))

        IdentityFieldRow(label = stringResource(R.string.account_identity_name)) {
            if (editingName) {
                OutlinedTextField(
                    value = state.nameDraft,
                    onValueChange = onNameDraftChange,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    onSaveName()
                    editingName = false
                }) { Text(stringResource(R.string.account_save)) }
                TextButton(onClick = {
                    onNameDraftChange(id.name)
                    editingName = false
                }) { Text(stringResource(R.string.account_cancel)) }
            } else {
                Text(
                    text = id.name.ifBlank { id.username },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { editingName = true }) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                }
            }
        }
        IdentityFieldRow(label = stringResource(R.string.account_identity_username)) {
            Text(
                text = id.username,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
        IdentityFieldRow(label = stringResource(R.string.account_identity_fingerprint)) {
            ValueChip(text = chunkedFingerprint(id.fingerprint), monospace = true)
        }
        IdentityFieldRow(label = stringResource(R.string.account_identity_identity)) {
            ValueChip(text = id.entity, monospace = true, modifier = Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.account_identity_directory_toggle),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = id.privacy == "public",
                onCheckedChange = { on -> onPrivacy(if (on) "public" else "private") },
                enabled = !state.isSaving,
            )
        }
    }
}

@Composable
private fun IdentityFieldRow(
    label: String,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        content()
    }
}

@Composable
private fun ValueChip(text: String, monospace: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
        )
    }
}

/** Insert a hyphen every 3 chars — matches the web UI's fingerprint chunking. */
private fun chunkedFingerprint(raw: String): String {
    if (raw.isBlank()) return raw
    return raw.chunked(3).joinToString("-")
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}
