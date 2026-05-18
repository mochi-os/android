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
 * Avatar for the author of a wiki revision. Each revision is identified by a
 * `revisionId`, and the owning wiki proxies that author's avatar + style at
 * per-revision asset URLs so remote authors render correctly without the
 * client having to fetch from foreign peers.
 *
 * Web reference: `apps/wikis/web/src/features/wiki/page-history.tsx` and
 * `changes-list.tsx`, which both build:
 *   src      = `<baseURL>revision/<revisionId>/asset/avatar`
 *   styleUrl = `<baseURL>revision/<revisionId>/asset/style`
 *
 * `baseURL` here is the same per-entity prefix used for attachments — it's
 * pulled from [LocalWikiContext].
 *
 * @param revisionId Revision UID; the wiki proxies the author's avatar /
 *                   style at `revision/<revisionId>/asset/{avatar,style}`.
 * @param authorFingerprint Stable seed for the initials placeholder, used
 *                          when the avatar image hasn't loaded yet or fails.
 * @param authorName Display name; shown as the avatar `alt` and used to
 *                   derive initials for the placeholder.
 * @param size Tier from [AvatarSize]; defaults to [AvatarSize.XS] for the
 *             inline use in history / changes rows.
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
