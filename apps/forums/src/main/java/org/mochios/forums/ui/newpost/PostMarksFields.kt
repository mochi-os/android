// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.forums.ui.newpost

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.MochiDropdownMenuItem
import org.mochios.android.ui.components.MochiTextField
import java.util.Locale

/**
 * The languages forums is translated into, by their primary subtag: what a
 * post may say it is in, as the web composer offers them.
 */
internal val POST_LANGUAGES = listOf(
    "af", "am", "ar", "ay", "az", "be", "bg", "bho", "bn", "bs", "ca", "ckb", "cs", "cy", "da", "de", "el",
    "en", "es", "et", "eu", "fa", "fi", "fr", "ga", "gd", "gl", "gn", "gu", "ha", "he", "hi", "hr", "ht",
    "hu", "hy", "id", "is", "it", "ja", "jv", "ka", "kk", "km", "kn", "ko", "ku", "ky", "lo", "lt", "lv",
    "mk", "ml", "mn", "mr", "ms", "mt", "my", "nb", "ne", "nl", "nn", "om", "pa", "pl", "ps", "pt", "qu",
    "ro", "ru", "sd", "si", "sk", "sl", "sq", "sr", "su", "sv", "sw", "ta", "te", "tg", "th", "tk", "tl",
    "tr", "uk", "ur", "uz", "vi", "xh", "yi", "yo", "yue", "zh", "zu",
)

/** A language in its own name, so a reader finds theirs whatever language the app is in. */
internal fun languageName(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    val name = locale.getDisplayName(locale)
    if (name.isNullOrBlank() || name == tag) return tag
    return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

/** The link field of a forum post, with what is wrong with what was typed. */
@Composable
internal fun PostLinkField(link: String, onLink: (String) -> Unit, modifier: Modifier = Modifier) {
    val error = linkError(link)
    MochiTextField(
        value = link,
        onValueChange = onLink,
        label = { Text(stringResource(org.mochios.forums.R.string.forums_post_link)) },
        placeholder = { Text(stringResource(org.mochios.forums.R.string.forums_post_link_hint)) },
        isError = error != null,
        supportingText = error?.let { message -> { Text(stringResource(message)) } },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A post's marks: the language it is in, which communities on other networks
 * filter by, and whether it is sensitive, which readers see behind a warning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PostMarksFields(
    language: String,
    sensitive: Boolean,
    onLanguage: (String) -> Unit,
    onSensitive: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val none = stringResource(org.mochios.forums.R.string.forums_post_language_none)
    val languages = remember { POST_LANGUAGES.map { it to languageName(it) }.sortedBy { it.second.lowercase() } }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
            MochiTextField(
                value = if (language.isEmpty()) none else languageName(language),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(org.mochios.forums.R.string.forums_post_language)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                MochiDropdownMenuItem(
                    text = { Text(none) },
                    onClick = {
                        open = false
                        onLanguage("")
                    },
                )
                languages.forEach { (tag, name) ->
                    MochiDropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            open = false
                            onLanguage(tag)
                        },
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = sensitive, role = Role.Switch, onValueChange = onSensitive)
                .padding(vertical = 4.dp),
        ) {
            Text(
                stringResource(org.mochios.forums.R.string.forums_sensitive),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = sensitive, onCheckedChange = null)
        }
    }
}
