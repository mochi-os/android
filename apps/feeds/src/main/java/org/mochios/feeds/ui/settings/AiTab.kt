// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.ui.settings

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
import org.mochios.android.ui.components.AiChoiceRow
import org.mochios.android.ui.components.AiPromptField
import org.mochios.android.ui.components.aiAccountOptions
import org.mochios.feeds.R

@Composable
fun AiTab(viewModel: FeedSettingsViewModel) {
    val aiMode by viewModel.aiMode.collectAsState()
    val aiAccount by viewModel.aiAccount.collectAsState()
    val aiAccounts by viewModel.aiAccounts.collectAsState()
    val aiOverrides by viewModel.aiOverrides.collectAsState()
    val aiDefaults by viewModel.aiDefaults.collectAsState()
    val context = LocalContext.current

    // Load the prompt defaults in the background; the mode/account rows come from
    // the already-loaded feed row and render immediately.
    LaunchedEffect(Unit) { viewModel.loadAiPrompts() }

    val modeOff = stringResource(R.string.feeds_ai_mode_off)
    val modes = listOf(
        "" to modeOff,
        "tag" to stringResource(R.string.feeds_ai_mode_tag),
        "tag+deduplicate" to stringResource(R.string.feeds_ai_mode_tag_deduplicate),
    )
    val promptTypes = listOf(
        "new" to stringResource(R.string.feeds_ai_prompt_new),
        "batch" to stringResource(R.string.feeds_ai_prompt_batch),
        "rank" to stringResource(R.string.feeds_ai_prompt_rank),
        "credibility" to stringResource(R.string.feeds_ai_prompt_credibility),
    )
    val defaultAccount = stringResource(R.string.feeds_ai_account_default)
    val promptDefault = stringResource(R.string.feeds_ai_prompt_default)
    val promptCustom = stringResource(R.string.feeds_ai_prompt_custom)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AiChoiceRow(
            label = stringResource(R.string.feeds_ai_processing_mode),
            options = modes,
            selected = aiMode,
            fallbackLabel = modeOff,
            onSelect = { mode -> viewModel.setAiMode(mode) },
        )

        // Account and prompt rows only appear once AI is switched on (not off).
        if (aiMode.isNotEmpty()) {
            AiChoiceRow(
                label = stringResource(R.string.feeds_ai_account),
                options = aiAccountOptions(defaultAccount, aiAccounts),
                selected = aiAccount,
                fallbackLabel = defaultAccount,
                onSelect = { account -> viewModel.setAiAccount(account) },
            )

            promptTypes.forEach { (type, label) ->
                AiPromptField(
                    label = label,
                    stored = aiOverrides[type].orEmpty(),
                    template = aiDefaults[type].orEmpty(),
                    defaultLabel = promptDefault,
                    customLabel = promptCustom,
                    variablesLabel = { names ->
                        context.getString(R.string.feeds_ai_prompt_variables, names)
                    },
                    onSaveCustom = { text -> viewModel.saveAiPrompt(type, text) },
                    onResetDefault = { viewModel.resetAiPrompt(type) },
                )
            }
        }
    }
}
