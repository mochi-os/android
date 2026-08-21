// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.model

/**
 * Public profile of a person, local or remote. [avatar], [banner] and [favicon]
 * are attachment ids, not URLs.
 */
data class PersonInformation(
    val id: String = "",
    val fingerprint: String = "",
    val name: String = "",
    val privacy: String = "",
    val profile: String = "",
    val style: PersonStyle = PersonStyle(),
    val avatar: String = "",
    val banner: String = "",
    val favicon: String = ""
)
