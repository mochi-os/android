// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.model

import com.google.gson.annotations.SerializedName

data class Link(
    val source: String = "",
    val target: String = "",
    val linktype: String = "",
    val created: Long = 0,
    val number: Int = 0,
    @SerializedName("class") val objectClass: String = "",
    val title: String = ""
)
