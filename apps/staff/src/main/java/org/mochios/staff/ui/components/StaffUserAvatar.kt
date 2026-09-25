// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.ui.components.personAvatarPath

/**
 * Avatar of the user [id] anywhere in the staff console. Tries the person's
 * People avatar first, then the staff app's copy, then seeded initials; a
 * blank [id] goes straight to the initials.
 */
@Composable
fun StaffUserAvatar(
    name: String,
    id: String,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier,
) {
    EntityAvatar(
        name = name,
        src = personAvatarPath(id),
        fallbackSrc = staffAvatarPath(id),
        seed = id.ifBlank { name },
        size = size,
        modifier = modifier,
    )
}

private fun staffAvatarPath(id: String): String? =
    id.takeIf { value -> value.isNotBlank() }?.let { value -> "/staff/-/user/$value/asset/avatar" }
