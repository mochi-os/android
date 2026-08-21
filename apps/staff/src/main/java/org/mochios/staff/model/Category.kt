// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.staff.model

import com.google.gson.annotations.SerializedName

/**
 * Mirrors `Category` in `apps/staff/web/src/types/categories.ts`. `parent` is
 * null for a root category; `children` is the count of direct sub-categories.
 */
data class Category(
    val id: String = "",
    val parent: String? = null,
    val name: String = "",
    val slug: String = "",
    val icon: String = "",
    val digital: Boolean = false,
    val physical: Boolean = false,
    val position: Long = 0,
    val active: Boolean = true,
    val children: Long = 0,
)
