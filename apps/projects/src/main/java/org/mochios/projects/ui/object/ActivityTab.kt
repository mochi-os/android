// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.ui.`object`

import org.mochios.android.api.OwnedAddress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import org.mochios.android.format.personName
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.i18n.formatRelativeTime
import org.mochios.android.model.Checklist
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.InfiniteList
import org.mochios.android.ui.components.LoadState
import org.mochios.android.ui.components.fieldDisplay
import org.mochios.android.util.fingerprint
import org.mochios.projects.R
import org.mochios.projects.model.Activity
import org.mochios.projects.model.ProjectField
import org.mochios.android.R as MochiR

/**
 * An object's history, newest first, read a page at a time as the list
 * reaches its end ([onLoadMore]). A failed read, the first included, shows
 * its reason with a retry rather than an empty history.
 *
 * @param fields The fields of the object's class, which name what an entry
 *   changed when the server did not.
 */
@Composable
fun ActivityTab(
    activity: List<Activity>,
    load: LoadState,
    more: Boolean,
    onLoadMore: () -> Unit,
    fields: List<ProjectField>,
    // Builds the avatar proxy path for an activity actor. Should return a
    // server-relative path to the projects app's proxy action, e.g.
    // "/projects/<project>/-/activity/<activity.id>/asset/avatar".
    avatarUrlBuilder: ((Activity) -> OwnedAddress?)? = null
) {
    InfiniteList(
        items = activity,
        load = load,
        hasMore = more,
        onLoadMore = onLoadMore,
        key = { entry -> entry.id },
        emptyContent = {
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
        },
    ) { item ->
        ActivityItem(
            item = item,
            fields = fields,
            avatarUrl = avatarUrlBuilder?.invoke(item)
        )
        HorizontalDivider()
    }
}

/**
 * An activity entry as it reads: what was done ([phrase]) and, for a change,
 * the value before and after it; null for a side with nothing to show.
 */
internal data class ActivityLine(val phrase: String, val before: String?, val after: String?)

/**
 * [entry] as a reader should see it. What the server resolved ([Activity.display])
 * wins; otherwise a value is formatted by its field's type, found among
 * [fields], the object's own class. A value naming an object, an option, a
 * class or a person is never shown as its id.
 */
@Composable
internal fun activityLine(entry: Activity, fields: List<ProjectField>): ActivityLine {
    val format = LocalFormat.current
    val field = entry.field.takeIf { id -> id.isNotBlank() }?.let { id -> fields.firstOrNull { candidate -> candidate.id == id } }
    val name = entry.display?.field ?: field?.name
    val type = entry.display?.type ?: field?.fieldtype
    // A class change is logged as an update of "class", which names no field
    // of the class; the server then names the classes themselves.
    val classChange = entry.action == "updated" && entry.field == "class" && name == null
    val unknown = stringResource(R.string.projects_activity_unknown)
    val nobody = stringResource(MochiR.string.person_unknown)
    val yes = stringResource(R.string.projects_field_yes)
    val no = stringResource(R.string.projects_field_no)
    val topLevel = stringResource(R.string.projects_parent_none)

    val phrase = when {
        classChange -> stringResource(R.string.projects_activity_class)
        entry.action == "created" -> stringResource(R.string.projects_activity_created)
        entry.action == "deleted" -> stringResource(R.string.projects_activity_deleted)
        entry.action == "attached" -> stringResource(R.string.projects_activity_attached)
        entry.action == "commented" -> stringResource(R.string.projects_activity_commented)
        entry.action == "moved" -> stringResource(R.string.projects_activity_moved)
        entry.action == "linked" -> when (entry.field) {
            "relates" -> stringResource(R.string.projects_links_type_relates)
            "blocks" -> stringResource(R.string.projects_links_type_blocks)
            "blocked by" -> stringResource(R.string.projects_links_type_blocked_by)
            "duplicates" -> stringResource(R.string.projects_links_type_duplicates)
            else -> stringResource(R.string.projects_activity_linked)
        }
        name != null -> stringResource(R.string.projects_activity_updated, name)
        else -> stringResource(R.string.projects_activity_made_change)
    }

    fun side(stored: String, named: String?): String? {
        if (!named.isNullOrBlank()) return named
        if (entry.action == "moved") {
            if (entry.oldvalue.isBlank() && entry.newvalue.isBlank()) return null
            return if (stored.isNotBlank()) unknown else topLevel
        }
        if (entry.action == "linked" || classChange) return stored.takeIf { it.isNotBlank() }?.let { unknown }
        if (stored.isBlank()) return null
        return when (type) {
            "checkbox" -> if (stored == "1" || stored.equals("true", ignoreCase = true)) yes else no
            "checklist" -> {
                val items = Checklist.parse(stored)
                "${format.formatNumber(items.count { item -> item.done })}/${format.formatNumber(items.size)}"
            }
            "user" -> nobody
            "enumerated", "object" -> unknown
            null -> stored
            else -> fieldDisplay(type, stored, format)?.text ?: stored
        }
    }

    return ActivityLine(phrase, side(entry.oldvalue, entry.display?.old), side(entry.newvalue, entry.display?.new))
}

@Composable
private fun ActivityItem(
    item: Activity,
    fields: List<ProjectField>,
    avatarUrl: OwnedAddress?
) {
    val line = activityLine(item, fields)
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val who = personName(item.name, fingerprint(item.user))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = buildAnnotatedString {
                append(line.phrase)
                if (line.before != null || line.after != null) {
                    append(": ")
                    if (line.before != null) {
                        withStyle(
                            SpanStyle(
                                textDecoration = TextDecoration.LineThrough,
                                color = mutedColor
                            )
                        ) {
                            append(line.before)
                        }
                        append(" → ")
                    }
                    append(line.after ?: "—")
                }
            },
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntityAvatar(
                name = who,
                src = avatarUrl,
                seed = item.user,
                size = 18.dp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$who · ${LocalFormat.current.formatRelativeTime(item.created)}",
                style = MaterialTheme.typography.labelSmall,
                color = mutedColor
            )
        }
    }
}
