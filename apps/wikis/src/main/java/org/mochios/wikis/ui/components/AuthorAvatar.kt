// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mochios.android.ui.components.EntityAvatar

/**
 * Standard avatar tier set. Pick by intent rather than pixels — these mirror
 * web's `AVATAR_SIZES` in `lib/web/src/components/entity-avatar.tsx`:
 *   xs  - inline next to text (history rows, recent-changes rows)
 *   sm  - sidebar items, list rows
 *   md  - member lists, table cells, search-result rows
 *   lg  - dense feature list rows
 *   xl  - page header
 *   xxl - profile hero
 */
enum class AvatarSize(val px: Dp) {
    XS(20.dp),
    SM(28.dp),
    MD(32.dp),
    LG(40.dp),
    XL(48.dp),
    XXL(80.dp),
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
    val baseURL = wiki.baseURL
    // styleUrl is computed for parity with web (page-history.tsx /
    // changes-list.tsx) — the lib EntityAvatar will read it once accent
    // fetching lands on Android; until then it's reserved for that wiring.
    @Suppress("UNUSED_VARIABLE")
    val styleUrl = "${baseURL}revision/$revisionId/asset/style"
    val src = "${baseURL}revision/$revisionId/asset/avatar"

    EntityAvatar(
        name = authorName,
        src = src,
        seed = authorFingerprint,
        size = size.px,
        modifier = modifier,
    )
}
