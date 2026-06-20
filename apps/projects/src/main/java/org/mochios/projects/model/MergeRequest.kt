// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.projects.model

import com.google.gson.annotations.SerializedName

data class MergeRequest(
    val id: String = "",
    @SerializedName("object") val objectId: String = "",
    val type: String = "",
    val repository: String = "",
    val source: String = "",
    val target: String = "",
    val status: String = "",
    val title: String = "",
    val description: String = "",
    val draft: Boolean = false,
    val created: Long = 0,
    val updated: Long = 0
)
