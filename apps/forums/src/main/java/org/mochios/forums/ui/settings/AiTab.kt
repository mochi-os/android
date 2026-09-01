// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mochios.android.model.Account
import org.mochios.android.ui.components.AiChoiceRow
import org.mochios.android.ui.components.AiPromptField
import org.mochios.android.ui.components.aiAccountOptions
import org.mochios.forums.R
import org.mochios.forums.model.AiPrompts
import org.mochios.forums.model.AiSettings

/**
 * AI settings tab: a row-based form to pick the AI mode, account, and prompt
 * source. Only reachable when the account has at least one AI-capable account.
 */
@Composable
fun AiTab(viewModel: ForumSettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    // Load the prompt defaults in the background; the mode/account rows come from
    // the already-loaded forum row and render immediately.
    LaunchedEffect(Unit) { viewModel.loadAiPrompts() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // Extra bottom padding so the last prompt's Save button can scroll up
            // clear of the screen edge instead of sitting flush against it.
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        AiSettingsContent(
            settings = AiSettings(
                mode = uiState.forum.aiMode,
                account = uiState.forum.aiAccount,
            ),
            prompts = uiState.aiPrompts,
            aiAccounts = uiState.aiAccounts,
            onSetSettings = { mode, account -> viewModel.setAiSettings(mode, account) },
            onSetPrompt = { type, prompt -> viewModel.setAiPrompt(type, prompt) },
        )
    }
}

@Composable
private fun AiSettingsContent(
    settings: AiSettings,
    prompts: AiPrompts?,
    aiAccounts: List<Account>,
    onSetSettings: (mode: String, account: String) -> Unit,
    onSetPrompt: (type: String, prompt: String) -> Unit,
) {
    val mode = settings.mode
    val account = settings.account
    val context = LocalContext.current

    val modeOff = stringResource(R.string.forums_ai_mode_off)
    val modes = listOf(
        "" to modeOff,
        "tag" to stringResource(R.string.forums_ai_mode_tag),
    )
    val defaultAccount = stringResource(R.string.forums_ai_account_default)
    val promptDefault = stringResource(R.string.forums_ai_prompt_default)
    val promptCustom = stringResource(R.string.forums_ai_prompt_custom)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AiChoiceRow(
            label = stringResource(R.string.forums_ai_actions_label),
            options = modes,
            selected = mode,
            fallbackLabel = modeOff,
            onSelect = { value -> onSetSettings(value, account) },
        )

        // The account and prompt rows only appear once AI is switched on (not off).
        if (mode.isNotEmpty()) {
            AiChoiceRow(
                label = stringResource(R.string.forums_ai_account_label),
                options = aiAccountOptions(defaultAccount, aiAccounts),
                selected = account,
                fallbackLabel = defaultAccount,
                onSelect = { value -> onSetSettings(mode, value) },
            )

            if (prompts != null) {
                listOf(
                    "tag" to stringResource(R.string.forums_ai_prompt_tag_label),
                    "score" to stringResource(R.string.forums_ai_prompt_score_label),
                ).forEach { (type, label) ->
                    AiPromptField(
                        label = label,
                        stored = prompts.prompts[type].orEmpty(),
                        template = prompts.defaults[type].orEmpty(),
                        defaultLabel = promptDefault,
                        customLabel = promptCustom,
                        variablesLabel = { names ->
                            context.getString(R.string.forums_ai_prompt_variables, names)
                        },
                        onSaveCustom = { text -> onSetPrompt(type, text) },
                        onResetDefault = { onSetPrompt(type, "") },
                    )
                }
            }
        }
    }
}
