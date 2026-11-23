// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.ui.comments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.mochios.android.ui.components.AttachmentGallery
import org.mochios.wikis.model.Attachment
import org.mochios.wikis.ui.components.LocalWikiContext
import org.mochios.wikis.ui.components.attachmentPath
import org.mochios.wikis.ui.components.library

/**
 * Attachments under a comment body, through the library's gallery as the web
 * draws them: image thumbnails open the lightbox, and any other file is
 * fetched with the reader's credential and handed to an app that can show it,
 * since the wiki serves a comment's files only to those who may read it.
 */
@Composable
fun CommentAttachments(
    attachments: List<Attachment>,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return

    val wiki = LocalWikiContext.current
    val baseURL = wiki?.baseURL ?: return
    val shown = remember(attachments) { attachments.map { attachment -> attachment.library() } }

    // An attachment whose id no segment can carry has no address, and the
    // gallery leaves it out rather than showing it broken.
    AttachmentGallery(
        attachments = shown,
        urlBuilder = { attachment -> attachmentPath(baseURL, attachment.id) },
        thumbnailUrlBuilder = { attachment -> attachmentPath(baseURL, attachment.id, "thumbnail") },
        compact = true,
        modifier = modifier,
    )
}
