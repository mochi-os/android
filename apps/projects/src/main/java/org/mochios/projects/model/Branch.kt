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
 * @property sha Commit the branch points at.
 * @property isDefault Whether the repository treats this as its default
 *   branch, resolved against git rather than the stored column. False on every
 *   branch from a repositories release older than the one that flags them.
 */
data class Branch(
    val name: String = "",
    val sha: String = "",
    @SerializedName("default")
    val isDefault: Boolean = false
)

/**
 * The branch to seed a new merge request's target with, or null for an empty
 * list.
 *
 * The server flags the default branch on the row it belongs to. An older
 * repositories release flags none, so fall back to the names a default almost
 * always carries, and finally to the first branch, rather than opening the
 * dialog with no target at all.
 */
fun List<Branch>.defaultTarget(): Branch? =
    firstOrNull { branch -> branch.isDefault }
        ?: firstOrNull { branch -> branch.name == "main" }
        ?: firstOrNull { branch -> branch.name == "master" }
        ?: firstOrNull()
