// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.`object`

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.projects.R
import org.mochios.projects.model.Activity
import org.mochios.projects.model.ProjectDetails

@Composable
fun ActivityTab(
    activity: List<Activity>,
    projectDetails: ProjectDetails,
    // Builds the avatar proxy path for an activity actor. Should return a
    // server-relative path to the projects app's proxy action, e.g.
    // "/projects/<project>/-/activity/<activity.id>/asset/avatar".
    avatarUrlBuilder: ((Activity) -> String?)? = null
) {
    if (activity.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.projects_activity_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(activity, key = { it.id }) { item ->
            ActivityItem(
                item = item,
                projectDetails = projectDetails,
                avatarUrl = avatarUrlBuilder?.invoke(item)
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ActivityItem(
    item: Activity,
    projectDetails: ProjectDetails,
    avatarUrl: String?
) {
    val fieldName = if (item.field.isNotBlank()) {
        // Try to find field name from project details
        var name = item.field
        for ((_, fields) in projectDetails.fields) {
            val found = fields.find { it.id == item.field }
            if (found != null) {
                name = found.name
                break
            }
        }
        name
    } else ""

    // Resolve option IDs to names for enumerated fields
    fun resolveValue(value: String): String {
        if (value.isBlank()) return value
        for ((_, classOptions) in projectDetails.options) {
            for ((_, fieldOptions) in classOptions) {
                val opt = fieldOptions.find { it.id == value }
                if (opt != null) return opt.name
            }
        }
        return value
    }

    val oldDisplay = resolveValue(item.oldvalue)
    val newDisplay = resolveValue(item.newvalue)

    // The action phrase — "updated <field>", "attached", "commented", etc. Value
    // changes (old → new) are appended below with the old value struck through.
    val actionPhrase = when (item.action) {
        "created" -> stringResource(R.string.projects_activity_created)
        "deleted" -> stringResource(R.string.projects_activity_deleted)
        "attached" -> stringResource(R.string.projects_activity_attached)
        "commented" -> stringResource(R.string.projects_activity_commented)
        // Field name is shown lower-cased (e.g. "updated title"), not title-cased.
        else -> if (fieldName.isNotBlank()) {
            stringResource(R.string.projects_activity_updated, fieldName.lowercase())
        } else {
            stringResource(R.string.projects_activity_made_change)
        }
    }
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = buildAnnotatedString {
                append(actionPhrase)
                if (oldDisplay.isNotBlank() || newDisplay.isNotBlank()) {
                    append(": ")
                    if (oldDisplay.isNotBlank()) {
                        withStyle(
                            SpanStyle(
                                textDecoration = TextDecoration.LineThrough,
                                color = mutedColor
                            )
                        ) {
                            append(oldDisplay)
                        }
                        if (newDisplay.isNotBlank()) {
                            append(" → ")
                        }
                    }
                    if (newDisplay.isNotBlank()) {
                        append(newDisplay)
                    }
                }
            },
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntityAvatar(
                name = item.name,
                src = avatarUrl,
                seed = item.user,
                size = 18.dp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${item.name} · ${LocalFormat.current.formatRelativeTime(item.created)}",
                style = MaterialTheme.typography.labelSmall,
                color = mutedColor
            )
        }
    }
}

