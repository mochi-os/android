// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.util

/** The hierarchy entry meaning "may sit at the top level, without a parent". */
const val HIERARCHY_ROOT = ""

/**
 * The `parents` parameter for `hierarchy/set`. The server reads an empty
 * parameter as "may be root", so an empty list - no parent allowed at all -
 * has to be spelt with its sentinel.
 */
fun hierarchyParameter(parents: List<String>): String =
    if (parents.isEmpty()) "_none_" else parents.joinToString(",")
