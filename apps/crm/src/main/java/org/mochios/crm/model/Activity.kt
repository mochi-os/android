// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.crm.model

data class Activity(
    val id: String = "",
    val user: String = "",
    val name: String = "",
    val action: String = "",
    val field: String = "",
    val oldvalue: String = "",
    val newvalue: String = "",
    val created: Long = 0
)
