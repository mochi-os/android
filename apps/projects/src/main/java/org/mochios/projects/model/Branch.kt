// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.model

/**
 * A branch of a repository a merge request can run between.
 *
 * @property name Branch name, which may contain slashes.
 * @property hash Commit the branch points at.
 */
data class Branch(
    val name: String = "",
    val hash: String = ""
)

/**
 * The branch to seed a new merge request's target with, or null for an empty
 * list.
 *
 * Chosen by name, because a name is all the branch list carries: core sends no
 * per-branch default flag, and the repository's default is dropped before it
 * reaches us. An `is_default` field was read here under three spellings and was
 * false under all of them, which left the target unset every time.
 */
fun List<Branch>.defaultTarget(): Branch? =
    firstOrNull { branch -> branch.name == "main" }
        ?: firstOrNull { branch -> branch.name == "master" }
        ?: firstOrNull()
