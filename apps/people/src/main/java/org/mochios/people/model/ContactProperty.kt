// Copyright © 2026 Mochisoft OÜ
// SPDX-License-Identifier: AGPL-3.0-only
// This file is part of Mochi, licensed under the GNU AGPL v3 with the
// Mochi Application Interface Exception - see license.txt and license-exception.md.

package org.mochios.people.model

/**
 * One vCard property of a contact's card: the property [name] (a protocol
 * token such as `FN` or `EMAIL`, exempt from the single-word rule), its
 * parameters — `TYPE` chiefly — and its [value]. Structured values keep the
 * vCard component order, `;` separated: `N` is
 * family;given;additional;prefix;suffix and `ADR` is
 * pobox;extended;street;city;region;postcode;country.
 */
data class ContactProperty(
    val name: String = "",
    val params: Map<String, List<String>> = emptyMap(),
    val value: String = "",
)
