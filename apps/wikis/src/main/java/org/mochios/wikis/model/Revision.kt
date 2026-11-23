// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.model


data class Revision(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val name: String = "",
    val created: Long = 0,
    val version: Int = 0,
    val comment: String = "",
)

data class RevisionDetail(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val name: String = "",
    val created: Long = 0,
    val version: Int = 0,
    val comment: String = "",
    val content: String = "",
)

data class PageHistoryResponse(
    val page: String = "",
    val revisions: List<Revision> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
)

data class PageRevisionResponse(
    val page: String = "",
    val revision: RevisionDetail = RevisionDetail(),
    val version: RevisionVersion = RevisionVersion(),
)

data class RevisionVersion(
    val current: Int = 0,
    /**
     * The highest revision version below the one shown, 0 when there is none.
     * Page versions are not contiguous - deleting a page bumps its version
     * without a revision - so the one before is not simply one less. Null
     * from a server that predates the field.
     */
    val previous: Int? = null,
)
