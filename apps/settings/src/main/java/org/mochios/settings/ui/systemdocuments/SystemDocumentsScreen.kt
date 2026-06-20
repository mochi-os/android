// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.settings.ui.systemdocuments

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.mochios.android.api.userMessage
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.settings.R
import org.mochios.android.R as MochiR
import org.mochios.settings.api.SystemDocument
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemDocumentsScreen(
    onBack: () -> Unit,
    viewModel: SystemDocumentsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.system_documents_saved)
    val saveFailedMessage = stringResource(R.string.system_documents_save_failed)

    LaunchedEffect(state.savedToast) {
        if (state.savedToast) {
            snackbarHostState.showSnackbar(savedMessage)
            viewModel.consumeSavedToast()
        }
    }
    LaunchedEffect(state.saveError) {
        state.saveError?.let { err ->
            val msg = err.userMessage().ifBlank { saveFailedMessage }
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSaveError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.system_documents_title)) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
                state.error != null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.error!!.userMessage(),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(MochiR.string.common_retry))
                    }
                }
                else -> Content(
                    state = state,
                    onTabChange = viewModel::setTab,
                    onLanguageChange = viewModel::setLanguage,
                    onSave = viewModel::save,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Content(
    state: SystemDocumentsUiState,
    onTabChange: (DocumentKind) -> Unit,
    onLanguageChange: (String) -> Unit,
    onSave: (name: String, language: String, body: String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = state.tab.ordinal) {
            DocumentKind.values().forEach { kind ->
                Tab(
                    selected = state.tab == kind,
                    onClick = { onTabChange(kind) },
                    text = { Text(stringResource(tabLabelRes(kind))) },
                )
            }
        }

        // Languages available for the active tab. Latin-script bucket first
        // (mirrors web's sortedLanguages); we then sort each bucket case-insensitively.
        val languages = remember(state.documents, state.tab) {
            sortedLanguages(
                state.documents
                    .filter { it.name == state.tab.value }
                    .map { it.language }
            )
        }

        // Resolve the active language. Honour the user's explicit choice if it's
        // still available for the current tab, otherwise fall back to the device
        // locale, then English, then the first available language.
        val deviceLang = Locale.getDefault().language.lowercase()
        val activeLanguage = state.language?.takeIf { languages.contains(it) }
            ?: languages.firstOrNull { it == deviceLang }
            ?: languages.firstOrNull { it == "en" }
            ?: languages.firstOrNull()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.system_documents_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (languages.isEmpty()) {
                Text(
                    text = stringResource(R.string.system_documents_no_language),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            LanguagePicker(
                languages = languages,
                selected = activeLanguage,
                onChange = onLanguageChange,
            )

            val document = state.documents.firstOrNull {
                it.name == state.tab.value && it.language == activeLanguage
            }
            if (document == null) {
                Text(
                    text = stringResource(R.string.system_documents_no_language),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                DocumentEditor(
                    document = document,
                    isSaving = state.savingKey == "${document.name}/${document.language}",
                    onSave = { body -> onSave(document.name, document.language, body) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePicker(
    languages: List<String>,
    selected: String?,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.system_documents_language),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = selected?.let { languageDisplayName(it) } ?: "",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                languages.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text(languageDisplayName(lang)) },
                        onClick = {
                            expanded = false
                            onChange(lang)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentEditor(
    document: SystemDocument,
    isSaving: Boolean,
    onSave: (String) -> Unit,
) {
    val format = LocalFormat.current
    // Reset the local draft whenever the upstream document (name/language/body)
    // changes, mirroring the web useEffect that snaps the textarea to the
    // refetched body after a save.
    var body by remember(document.name, document.language, document.body) {
        mutableStateOf(document.body)
    }
    val customised = document.body != document.default
    val dirty = body != document.body

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        AssistChip(
            onClick = {},
            enabled = false,
            label = {
                Text(
                    text = if (customised) {
                        stringResource(R.string.system_documents_customised)
                    } else {
                        stringResource(R.string.system_documents_using_default)
                    },
                )
            },
            colors = AssistChipDefaults.assistChipColors(),
        )
        if (document.updated > 0) {
            Text(
                text = stringResource(
                    R.string.system_documents_last_edited,
                    format.formatRelativeTime(document.updated),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    OutlinedTextField(
        value = body,
        onValueChange = { body = it },
        modifier = Modifier.fillMaxWidth(),
        minLines = 16,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        if (body != document.default) {
            OutlinedButton(
                onClick = { body = document.default },
                enabled = !isSaving,
            ) {
                Text(stringResource(R.string.system_documents_revert))
            }
            Spacer(Modifier.size(8.dp))
        }
        Button(
            onClick = { onSave(body) },
            enabled = !isSaving && dirty,
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.system_documents_save))
            }
        }
    }
}

private fun tabLabelRes(kind: DocumentKind): Int = when (kind) {
    DocumentKind.RULES -> R.string.system_documents_tab_rules
    DocumentKind.TERMS -> R.string.system_documents_tab_terms
    DocumentKind.PRIVACY -> R.string.system_documents_tab_privacy
}

// Mirrors web's LANGUAGE_OVERRIDES: where Intl/Locale gives a generic name,
// substitute a clearer regional label.
private val LANGUAGE_OVERRIDES: Map<String, String> = mapOf(
    "en" to "English (international)",
    "en-us" to "English (USA)",
    "es" to "Espanol (Espana)",
    "es-419" to "Espanol (latinoamericano)",
)

private fun languageDisplayName(tag: String): String {
    LANGUAGE_OVERRIDES[tag.lowercase()]?.let { return it }
    val locale = Locale.forLanguageTag(tag)
    val name = locale.getDisplayName(locale)
    if (name.isNullOrBlank()) return tag
    return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

// Latin-script bucket first, non-Latin second; sort case-insensitively within each.
private fun sortedLanguages(tags: List<String>): List<String> {
    return tags.distinct()
        .map { it to languageDisplayName(it) }
        .sortedWith(
            compareBy(
                { languageBucket(it.second) },
                { it.second.lowercase() },
            )
        )
        .map { it.first }
}

private fun languageBucket(name: String): Int {
    for (ch in name) {
        if (ch.isLetter()) {
            return if (ch.code in 0x0000..0x024F) 0 else 1
        }
    }
    return 0
}
