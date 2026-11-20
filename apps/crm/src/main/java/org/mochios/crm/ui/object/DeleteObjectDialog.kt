// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.ui.`object`

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.mochios.android.i18n.LocalFormat
import org.mochios.android.ui.components.MochiAlertDialog
import org.mochios.crm.R
import org.mochios.crm.model.CrmObject
import org.mochios.android.R as MochiR

/**
 * Asks before an object is deleted, naming it and, since the server deletes
 * everything under it too, how many objects that is.
 */
@Composable
internal fun DeleteObjectDialog(
    title: String,
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val message = if (count > 0) {
        pluralStringResource(
            R.plurals.crm_object_delete_children,
            count,
            title,
            LocalFormat.current.formatNumber(count),
        )
    } else {
        stringResource(R.string.crm_object_delete_named, title)
    }
    MochiAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.crm_object_delete_title),
        text = message,
        confirmText = stringResource(MochiR.string.common_delete),
        onConfirm = onConfirm,
        destructive = true,
        dismissText = stringResource(MochiR.string.common_cancel),
    )
}

/** How many of [objects] sit under [id], at any depth. */
internal fun descendantCount(objects: List<CrmObject>, id: String): Int {
    val byParent = objects.groupBy { obj -> obj.parent }
    val seen = mutableSetOf<String>()
    val stack = ArrayDeque(listOf(id))
    while (stack.isNotEmpty()) {
        for (child in byParent[stack.removeLast()].orEmpty()) {
            if (child.id != id && seen.add(child.id)) stack.add(child.id)
        }
    }
    return seen.size
}
