// Copyright © 2026 Mochi OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.android.model

data class PlaceData(
    val name: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val country: String = "",
    val state: String = "",
    val city: String = "",
    val category: String = ""
)
