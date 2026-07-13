// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.dialog

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import org.mochios.android.api.toMochiError
import org.mochios.android.api.userMessage
import org.mochios.wikis.R
import org.mochios.wikis.repository.WikisRepository

/**
 * Rename-page dialog mirroring `apps/wikis/web/src/features/wiki/rename-page-dialog.tsx`.
 *
 * The dialog owns its own form state and runs the rename mutation via the
 * injected [WikisRepository] (resolved with a Hilt entry point so the dialog
 * doesn't require its own ViewModel scope — callers from PageView can just
 * drop it in next to other action-menu surfaces).
 *
 * @param onRenamed Fired on success with `(newSlug, renamedCount, updatedLinks)`
 *                  so the caller can navigate to the new URL and surface the
 *                  toast with the right plural copy.
 */
@Composable
fun RenamePageDialog(
    open: Boolean,
    wikiId: String,
    currentSlug: String,
    onDismiss: () -> Unit,
    onRenamed: (newSlug: String, renamedCount: Int, updatedLinks: Int) -> Unit,
) {
    if (!open) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val repository = remember(context) {
        EntryPointAccessors
            .fromApplication(context.applicationContext, RenamePageEntryPoint::class.java)
            .wikisRepository()
    }

    var newSlug by remember(currentSlug) { mutableStateOf(currentSlug) }
    var createRedirect by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    val newSlugRequired = stringResource(R.string.wikis_rename_page_new_required)
    val differentRequired = stringResource(R.string.wikis_rename_page_must_differ)
    val failedFallback = stringResource(R.string.wikis_rename_page_failed)

    // Reset the slug field every time the dialog (re-)opens so a previous
    // edit doesn't leak across dismissals.
    LaunchedEffect(open) {
        if (open) {
            newSlug = currentSlug
            createRedirect = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(stringResource(R.string.wikis_rename_page_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = newSlug,
                    onValueChange = { newSlug = it },
                    label = { Text(stringResource(R.string.wikis_rename_page_label)) },
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Checkbox(
                        checked = createRedirect,
                        onCheckedChange = { createRedirect = it },
                        enabled = !isSubmitting,
                    )
                    Text(
                        text = stringResource(R.string.wikis_rename_page_redirects),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = {
                    val trimmed = newSlug.trim()
                    when {
                        trimmed.isEmpty() -> {
                            Toast.makeText(context, newSlugRequired, Toast.LENGTH_SHORT).show()
                        }
                        trimmed == currentSlug -> {
                            Toast.makeText(context, differentRequired, Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            scope.launch {
                                isSubmitting = true
                                try {
                                    val r = repository.renamePage(
                                        wiki = wikiId,
                                        slug = currentSlug,
                                        newSlug = trimmed,
                                        createRedirect = createRedirect,
                                    )
                                    val renamedCount = r.renamed.size.coerceAtLeast(1)
                                    onRenamed(trimmed, renamedCount, r.updatedLinks)
                                } catch (e: Exception) {
                                    val msg = e.toMochiError().userMessage().ifEmpty { failedFallback }
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                } finally {
                                    isSubmitting = false
                                }
                            }
                        }
                    }
                },
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.wikis_rename_page_renaming))
                } else {
                    Text(stringResource(R.string.wikis_rename_page_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { if (!isSubmitting) onDismiss() },
                enabled = !isSubmitting,
            ) {
                Text(stringResource(R.string.wikis_rename_page_cancel))
            }
        },
    )
}

/**
 * Compose the post-rename success toast — "Renamed N pages, updated M links" —
 * with the same plural logic as web's `rename-page-dialog.tsx`. The "updated"
 * clause is omitted entirely when no links were rewritten, matching web.
 */
@Composable
fun rememberRenameSuccessMessage(renamedCount: Int, updatedLinks: Int): String {
    val pages = pluralStringResource(
        R.plurals.wikis_rename_page_renamed,
        renamedCount,
        renamedCount,
    )
    return if (updatedLinks > 0) {
        val links = pluralStringResource(
            R.plurals.wikis_rename_page_updated_links,
            updatedLinks,
            updatedLinks,
        )
        "$pages, $links"
    } else {
        pages
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RenamePageEntryPoint {
    fun wikisRepository(): WikisRepository
}
