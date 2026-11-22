// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.`object`

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
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.InfiniteList
import org.mochios.android.ui.components.fieldDisplay
import org.mochios.android.util.fingerprint
import org.mochios.crm.R
import org.mochios.crm.model.Activity
import org.mochios.crm.model.CrmDetails
import org.mochios.crm.model.CrmField

/**
 * An object's activity, newest first, read a page at a time as the list's end
 * comes into view ([onLoadMore], which also retries a failed page).
 */
@Composable
fun ActivityTab(
    activity: ActivityPages,
    crmDetails: CrmDetails,
    /** The object's class, whose fields and options its entries name. */
    objectClass: String,
    onLoadMore: () -> Unit,
    // Builds the avatar proxy path for an activity actor. Should return a
    // server-relative path to the crm app's proxy action, e.g.
    // "/crm/<crm>/-/activity/<activity.id>/asset/avatar".
    avatarUrlBuilder: ((Activity) -> OwnedAddress?)? = null
) {
    val fields = crmDetails.fields[objectClass].orEmpty()
    InfiniteList(
        items = activity.entries,
        load = activity.load,
        hasMore = activity.more,
        onLoadMore = onLoadMore,
        key = { entry -> entry.id },
        emptyContent = {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.crm_activity_empty),
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
 * What an activity entry reads as: its [phrase], then the [old] value struck
 * through and the [new] one, each null when the entry shows none.
 */
internal data class ActivityLine(val phrase: String, val old: String?, val new: String?)

/**
 * The line [item] reads as. What it names comes from the server where it
 * resolved it: a field's name, an option's, a person's, a parent's or a link
 * target's title, a class's name. Anything else is formatted by the field's
 * type, from [fields], the object's class's own: a date as its day, a number
 * in the user's format, a box as Yes or No, a checklist as its progress. An
 * id is never shown: an object without a title is Untitled, a person without
 * a name their fingerprint, and an option the class no longer has nothing.
 */
@Composable
internal fun activityLine(item: Activity, fields: List<CrmField>): ActivityLine {
    val format = LocalFormat.current
    val display = item.display
    val field = fields.find { candidate -> candidate.id == item.field }
    val name = display?.field?.takeIf { text -> text.isNotBlank() } ?: field?.name
    val type = display?.type?.takeIf { text -> text.isNotBlank() } ?: field?.fieldtype
    val untitled = stringResource(R.string.crm_untitled)
    val yes = stringResource(R.string.crm_field_yes)
    val no = stringResource(R.string.crm_field_no)
    val none = stringResource(R.string.crm_parent_none)

    /** An object a value names: its title, Untitled without one, nothing for none. */
    fun titled(raw: String, named: String?): String? =
        named?.takeIf { text -> text.isNotBlank() } ?: untitled.takeIf { raw.isNotBlank() }

    /** One side of a field's change. */
    fun value(raw: String, named: String?): String? {
        named?.takeIf { text -> text.isNotBlank() }?.let { text -> return text }
        if (raw.isBlank()) return null
        return when (type) {
            "checkbox" -> if (raw == "1" || raw.equals("true", ignoreCase = true)) yes else no
            "object" -> untitled
            else -> fieldDisplay(type ?: "text", raw, format, name.orEmpty())?.text
        }
    }

    return when (item.action) {
        "created" -> ActivityLine(stringResource(R.string.crm_activity_created), null, null)
        "deleted" -> ActivityLine(stringResource(R.string.crm_activity_deleted), null, null)
        "commented" -> ActivityLine(stringResource(R.string.crm_activity_commented), null, null)
        // The file's name.
        "attached" -> ActivityLine(
            stringResource(R.string.crm_activity_attached),
            null,
            item.newvalue.takeIf { text -> text.isNotBlank() },
        )
        // From one parent to another; the top level has none.
        "moved" -> ActivityLine(
            stringResource(R.string.crm_activity_moved),
            titled(item.oldvalue, display?.old) ?: none,
            titled(item.newvalue, display?.new) ?: none,
        )
        // The field is the link's type, the new value the object linked to.
        "linked" -> ActivityLine(
            when (item.field) {
                "relates" -> stringResource(R.string.crm_links_type_relates)
                "blocks" -> stringResource(R.string.crm_links_type_blocks)
                "blocked by" -> stringResource(R.string.crm_links_type_blocked_by)
                "duplicates" -> stringResource(R.string.crm_links_type_duplicates)
                else -> stringResource(R.string.crm_activity_linked)
            },
            null,
            titled(item.newvalue, display?.new),
        )
        "updated" -> when {
            name != null -> ActivityLine(
                stringResource(R.string.crm_activity_updated, name),
                value(item.oldvalue, display?.old),
                value(item.newvalue, display?.new),
            )
            // The object was given another class, which is no field of it.
            item.field == "class" -> ActivityLine(
                stringResource(R.string.crm_activity_class),
                display?.old?.takeIf { text -> text.isNotBlank() },
                display?.new?.takeIf { text -> text.isNotBlank() },
            )
            // A field the design no longer has: what it held cannot be read.
            else -> ActivityLine(stringResource(R.string.crm_activity_made_change), null, null)
        }
        else -> ActivityLine(stringResource(R.string.crm_activity_made_change), null, null)
    }
}

@Composable
private fun ActivityItem(
    item: Activity,
    fields: List<CrmField>,
    avatarUrl: OwnedAddress?
) {
    val line = activityLine(item, fields)
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val actor = personName(item.name, fingerprint(item.user))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = buildAnnotatedString {
                append(line.phrase)
                if (line.old != null || line.new != null) {
                    append(": ")
                    if (line.old != null) {
                        withStyle(
                            SpanStyle(
                                textDecoration = TextDecoration.LineThrough,
                                color = mutedColor
                            )
                        ) {
                            append(line.old)
                        }
                        if (line.new != null) {
                            append(" → ")
                        }
                    }
                    if (line.new != null) {
                        append(line.new)
                    }
                }
            },
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntityAvatar(
                name = actor,
                src = avatarUrl,
                seed = item.user,
                size = 18.dp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$actor · ${LocalFormat.current.formatRelativeTime(item.created)}",
                style = MaterialTheme.typography.labelSmall,
                color = mutedColor
            )
        }
    }
}
