package org.mochios.forums.ui.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.mochios.forums.R
import org.mochios.forums.model.ModerationSettings
import org.mochios.android.R as MochiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumModerationSettingsScreen(
    onBack: () -> Unit,
    viewModel: ModerationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadSettings() }

    var moderationPosts by remember(uiState.settings) {
        mutableStateOf(uiState.settings?.moderationPosts ?: false)
    }
    var moderationComments by remember(uiState.settings) {
        mutableStateOf(uiState.settings?.moderationComments ?: false)
    }
    var moderationNew by remember(uiState.settings) {
        mutableStateOf(uiState.settings?.moderationNew ?: false)
    }
    var newUserDays by remember(uiState.settings) {
        mutableStateOf((uiState.settings?.newUserDays ?: 0).toString())
    }
    var postLimit by remember(uiState.settings) {
        mutableStateOf((uiState.settings?.postLimit ?: 0).toString())
    }
    var commentLimit by remember(uiState.settings) {
        mutableStateOf((uiState.settings?.commentLimit ?: 0).toString())
    }
    var limitWindow by remember(uiState.settings) {
        mutableStateOf((uiState.settings?.limitWindow ?: 3600).toString())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.forums_moderation_settings)) },
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
        if (uiState.settings == null) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.forums_moderation_section_premoderation),
                style = MaterialTheme.typography.titleMedium,
            )
            ToggleRow(
                label = stringResource(R.string.forums_moderation_require_posts),
                checked = moderationPosts,
                onCheckedChange = { moderationPosts = it },
            )
            ToggleRow(
                label = stringResource(R.string.forums_moderation_require_comments),
                checked = moderationComments,
                onCheckedChange = { moderationComments = it },
            )
            ToggleRow(
                label = stringResource(R.string.forums_moderation_require_new_users),
                checked = moderationNew,
                onCheckedChange = { moderationNew = it },
            )
            if (moderationNew) {
                NumericField(
                    label = stringResource(R.string.forums_moderation_new_user_days),
                    value = newUserDays,
                    onValueChange = { newUserDays = it },
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.forums_moderation_section_ratelimiting),
                style = MaterialTheme.typography.titleMedium,
            )
            NumericField(
                label = stringResource(R.string.forums_moderation_post_limit),
                value = postLimit,
                onValueChange = { postLimit = it },
            )
            NumericField(
                label = stringResource(R.string.forums_moderation_comment_limit),
                value = commentLimit,
                onValueChange = { commentLimit = it },
            )
            NumericField(
                label = stringResource(R.string.forums_moderation_limit_window),
                value = limitWindow,
                onValueChange = { limitWindow = it },
            )

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    viewModel.saveSettings(
                        ModerationSettings(
                            moderationPosts = moderationPosts,
                            moderationComments = moderationComments,
                            moderationNew = moderationNew,
                            newUserDays = newUserDays.toIntOrNull() ?: 0,
                            postLimit = postLimit.toIntOrNull() ?: 0,
                            commentLimit = commentLimit.toIntOrNull() ?: 0,
                            limitWindow = limitWindow.toIntOrNull() ?: 3600,
                        )
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(MochiR.string.common_save))
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NumericField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onValueChange(v.filter { it.isDigit() }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
