// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.wikis.model

data class WikiPermissions(
    val view: Boolean = false,
    val edit: Boolean = false,
    val delete: Boolean = false,
    val manage: Boolean = false,
    /**
     * Whether the caller owns the wiki entity. Separate from [manage], which is
     * grantable: comment delete authorises on ownership, so testing [manage] (or
     * [delete]) offered Delete on other people's comments to every editor and
     * the server refused every one of them.
     */
    val owner: Boolean = false,
)
