// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.feeds.model

import com.google.gson.annotations.SerializedName

data class Feed(
    val id: String = "",
    val fingerprint: String = "",
    @SerializedName("fingerprint_hyphens") val fingerprintHyphens: String = "",
    val name: String = "",
    val privacy: String = "private",
    val owner: Int = 0,
    val subscribers: Int = 0,
    val updated: Long = 0,
    val server: String? = null,
    val location: String? = null,
    val read: Int = 0,
    val unread: Int = 0,
    @SerializedName("ai_mode") val aiMode: String? = null,
    @SerializedName("ai_account") val aiAccount: String = "",
    val sort: String = "",
    val banner: String = ""
)

data class Permissions(
    val view: Boolean = false,
    val react: Boolean = false,
    val comment: Boolean = false,
    val manage: Boolean = false
)
