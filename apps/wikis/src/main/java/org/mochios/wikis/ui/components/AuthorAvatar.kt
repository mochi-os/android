// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.components

import org.mochios.android.api.OwnedAddress
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.EntityAvatar
import org.mochios.android.util.segment

/**
 * The avatar tiers the wiki draws, picked by intent rather than pixels; they
 * mirror web's `AVATAR_SIZES` in `lib/web/src/components/entity-avatar.tsx`:
 *   xs  - inline next to text (history rows, recent-changes rows)
 */
enum class AvatarSize(val px: Dp) {
    XS(20.dp),
}

/**
 * Avatar for a revision's author. The wiki proxies the author's avatar and
 * style at `revision/<id>/asset/{avatar,style}`, so remote authors render
 * without fetching foreign peers.
 */
@Composable
fun AuthorAvatar(
    revisionId: String,
    authorFingerprint: String,
    authorName: String,
    size: AvatarSize = AvatarSize.XS,
    modifier: Modifier = Modifier,
) {
    val wiki = LocalWikiContext.current
        ?: error("AuthorAvatar requires LocalWikiContext")
    val base = wiki.path
    // An id no segment can carry has the placeholder.
    val src = segment(revisionId)?.let { OwnedAddress.of("${base}revision/$it/asset/avatar") }

    EntityAvatar(
        name = authorName,
        src = src,
        seed = authorFingerprint,
        size = size.px,
        modifier = modifier,
    )
}
