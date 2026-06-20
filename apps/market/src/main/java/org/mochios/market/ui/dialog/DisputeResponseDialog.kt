// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.market.ui.dialog

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import org.mochios.android.i18n.LocalFormat
import org.mochios.market.R
import org.mochios.market.model.DisputeEvidence
import org.mochios.market.ui.components.StatusBadge

/**
 * Dispute response dialog. The seller types a response, optionally
 * attaches evidence files, and submits. Evidence picking uses
 * [ActivityResultContracts.GetMultipleContents] — the parent screen
 * forwards the picked URIs to the ViewModel which uploads them and
 * threads them through to the comptroller's evidence-set call.
 *
 * The status badge in the title slot indicates whether the dispute is
 * still open, awaiting Stripe decision, or resolved — the host screen
 * supplies the canonical state string.
 */
@Composable
fun DisputeResponseDialog(
    open: Boolean,
    disputeStatus: String,
    existingEvidence: List<DisputeEvidence> = emptyList(),
    submitting: Boolean = false,
    errorMessage: String? = null,
    onSubmit: (response: String, evidence: List<Uri>) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!open) return

    var response by remember { mutableStateOf("") }
    val evidence = remember { mutableStateListOf<Uri>() }

    LaunchedEffect(open) {
        if (open) {
            response = ""
            evidence.clear()
        }
    }

    val pickFiles = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isNotEmpty()) evidence.addAll(uris)
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.market_dispute_dialog_title))
                Spacer(Modifier.size(8.dp))
                if (disputeStatus.isNotBlank()) {
                    StatusBadge(status = disputeStatus)
                }
            }
        },
        text = {
            Column {
                val fileEvidence = existingEvidence.filter { it.name.isNotBlank() || it.url.isNotBlank() }
                if (fileEvidence.isNotEmpty()) {
                    SubmittedEvidenceSection(evidence = fileEvidence)
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = response,
                    onValueChange = { response = it },
                    label = { Text(stringResource(R.string.market_dispute_dialog_response_label)) },
                    enabled = !submitting,
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        enabled = !submitting,
                        onClick = { pickFiles.launch("*/*") },
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.market_dispute_dialog_attach))
                    }
                    if (evidence.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.market_dispute_dialog_attach_count,
                                evidence.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!errorMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting && response.isNotBlank(),
                onClick = { onSubmit(response.trim(), evidence.toList()) },
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.market_dispute_dialog_submitting))
                } else {
                    Text(stringResource(R.string.market_dispute_dialog_submit))
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !submitting,
                onClick = { if (!submitting) onDismiss() },
            ) {
                Text(stringResource(R.string.market_dispute_dialog_cancel))
            }
        },
    )
}

/**
 * "Submitted evidence" section listing previously-uploaded files attached
 * to the dispute. Tap a row to open the file URL in a Chrome Custom Tab.
 */
@Composable
private fun SubmittedEvidenceSection(evidence: List<DisputeEvidence>) {
    val context = LocalContext.current
    val format = LocalFormat.current
    Text(
        text = stringResource(R.string.market_dispute_dialog_evidence_section_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Card(colors = CardDefaults.outlinedCardColors(), modifier = Modifier.fillMaxWidth()) {
        Column {
            evidence.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider()
                val rowModifier = if (item.url.isNotBlank()) {
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            CustomTabsIntent.Builder().build()
                                .launchUrl(context, item.url.toUri())
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                }
                Row(
                    modifier = rowModifier,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = evidenceFileIcon(item),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = item.name.ifBlank { item.url },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = true),
                            )
                            if (item.size > 0L) {
                                Text(
                                    text = format.formatFileSize(item.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        val submitter = evidenceSubmitter(item)
                        val date = format.formatDate(item.created)
                        val parts = listOfNotNull(
                            submitter.takeIf { it.isNotBlank() },
                            date.takeIf { it.isNotBlank() },
                        )
                        if (parts.isNotEmpty()) {
                            Text(
                                text = stringResource(
                                    R.string.market_dispute_dialog_evidence_submitter_line,
                                    parts.joinToString(" · "),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun evidenceSubmitter(item: DisputeEvidence): String {
    return when (item.role.lowercase()) {
        "buyer" -> stringResource(R.string.market_dispute_dialog_evidence_role_buyer)
        "seller" -> stringResource(R.string.market_dispute_dialog_evidence_role_seller)
        "staff" -> stringResource(R.string.market_dispute_dialog_evidence_role_staff)
        else -> item.actorName.ifBlank { item.actor }
    }
}

/**
 * File-type icon for an evidence row. Cheap heuristic: prefer the MIME
 * prefix, fall back to the filename extension.
 */
private fun evidenceFileIcon(item: DisputeEvidence): ImageVector {
    val mime = item.mime.lowercase()
    if (mime.startsWith("image/")) return Icons.Default.Image
    if (mime.startsWith("video/")) return Icons.Default.VideoFile
    if (mime == "application/pdf") return Icons.Default.PictureAsPdf
    if (mime.startsWith("text/")) return Icons.Default.Description

    val ext = item.name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return when (ext) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif" -> Icons.Default.Image
        "mp4", "mov", "webm", "mkv", "avi" -> Icons.Default.VideoFile
        "pdf" -> Icons.Default.PictureAsPdf
        "txt", "md", "log" -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }
}
