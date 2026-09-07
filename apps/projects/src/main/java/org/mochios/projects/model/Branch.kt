// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.model

import com.google.gson.annotations.SerializedName

/**
 * A branch of a repository a merge request can run between.
 *
 * @property name Branch name, which may contain slashes.
 * @property hash Commit the branch points at.
 * @property isDefault Whether this is the repository's default branch, which
 *   seeds the target of a new merge request. The wire key is read under every
 *   spelling the server has used, since a miss here silently reads as false.
 */
data class Branch(
    val name: String = "",
    val hash: String = "",
    @SerializedName("is_default", alternate = ["isDefault", "default"])
    val isDefault: Boolean = false
)
